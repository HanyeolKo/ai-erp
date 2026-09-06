#!/usr/bin/env ruby
# frozen_string_literal: true

require "psych"

ROOT = File.expand_path("..", __dir__)
AUTOMATIC_GUARD = "(github.event_name == 'workflow_run' && github.event.workflow_run.conclusion == 'success' && github.event.workflow_run.event == 'push' && github.event.workflow_run.head_branch == 'main' && github.event.workflow_run.head_repository.full_name == github.repository)"
MANUAL_GUARD = "(github.event_name == 'workflow_dispatch' && github.ref == 'refs/heads/main' && github.event.repository.default_branch == 'main')"
EXPECTED_DEPLOY_GUARD = "#{AUTOMATIC_GUARD} || #{MANUAL_GUARD}"
EXPECTED_CHECKOUT_REF = "refs/heads/main"
EXPECTED_EVENT_ENV = { "EVENT_NAME" => "${{ github.event_name }}", "WORKFLOW_RUN_SHA" => "${{ github.event.workflow_run.head_sha }}", "EVENT_REF" => "${{ github.ref }}", "DEFAULT_BRANCH" => "${{ github.event.repository.default_branch }}" }.freeze
SENSITIVE_OUTPUT = /\bssh\b|secrets\.|\.env|docker inspect|docker logs|upload-artifact/i
RESOLVER_RUN = <<~BASH
  set -euo pipefail
  RELEASE_SHA="$(git rev-parse HEAD)"
  [[ "$RELEASE_SHA" =~ ^[a-f0-9]{40}$ ]] || { echo "Invalid checked-out SHA" >&2; exit 1; }
  case "$EVENT_NAME" in
    workflow_run)
      [[ "$WORKFLOW_RUN_SHA" =~ ^[a-f0-9]{40}$ ]] || { echo "Invalid CI SHA" >&2; exit 1; }
      [[ "$RELEASE_SHA" == "$WORKFLOW_RUN_SHA" ]] || { echo "Checked-out SHA differs from CI SHA" >&2; exit 1; }
      ;;
    workflow_dispatch)
      [[ "$EVENT_REF" == "refs/heads/main" && "$DEFAULT_BRANCH" == "main" ]] || { echo "Manual deployment is restricted to main" >&2; exit 1; }
      MAIN_SHA="$(git rev-parse origin/main)"
      [[ "$MAIN_SHA" =~ ^[a-f0-9]{40}$ && "$RELEASE_SHA" == "$MAIN_SHA" ]] || { echo "Checked-out SHA differs from origin/main" >&2; exit 1; }
      ;;
    *)
      echo "Unexpected deployment event" >&2
      exit 1
      ;;
  esac
  printf 'RELEASE_SHA=%s\\n' "$RELEASE_SHA" >> "$GITHUB_ENV"
BASH
SUMMARY_RUN = <<~BASH
  printf 'Deployment status: %s\\n' "$JOB_STATUS" >> "$GITHUB_STEP_SUMMARY"
  if [[ -n "${RELEASE_SHA:-}" ]]; then
    printf 'Validated release SHA: %s\\n' "$RELEASE_SHA" >> "$GITHUB_STEP_SUMMARY"
  fi
BASH
SYNTAX_RUN = <<~BASH
  bash -n scripts/preflight.sh
  bash -n scripts/backup.sh
  bash -n scripts/deploy.sh
  bash -n scripts/rollback.sh
  bash -n scripts/smoke.sh
BASH
PROD_COMPOSE_CONFIG_RUN = "APP_IMAGE=ai-erp:config-check docker compose --env-file infra/.env.prod.example -f infra/compose.prod.yml config --quiet"
CADDY_NO_SNI_TEST_RUN = "bash scripts/tests/caddy-no-sni.sh"

class ContractError < StandardError; end

def assert!(condition, message)
  raise ContractError, message unless condition
end

def normalized(value)
  value.to_s.gsub(/\s+/, " ").strip
end

def parsed_workflow(path)
  Psych.safe_load_file(path, aliases: true)
rescue Psych::Exception => error
  raise ContractError, "invalid YAML #{File.basename(path)}: #{error.message}"
end

def assert_step!(actual, expected, description)
  assert!(actual.is_a?(Hash), "#{description} is a mapping")
  assert!(actual.keys.sort == expected.keys.sort, "#{description} allowed keys")
  expected.each do |key, value|
    actual_value = actual.fetch(key)
    if key == "run" || (key == "with" && value.is_a?(Hash))
      if key == "with"
        assert!(actual_value.keys.sort == value.keys.sort, "#{description} with keys")
        value.each { |with_key, with_value| assert!(actual_value.fetch(with_key) == with_value, "#{description} with #{with_key}") }
      else
        assert!(normalized(actual_value) == normalized(value), "#{description} exact run")
      end
    else
      assert!(actual_value == value, "#{description} #{key}")
    end
  end
end

def assert_steps!(actual, expected, description)
  assert!(actual.length == expected.length, "#{description} step count")
  actual.zip(expected).each_with_index { |(step, contract), index| assert_step!(step, contract, "#{description} step #{index + 1}") }
end

def ci_steps
  [
    { "uses" => "actions/checkout@v5" },
    { "uses" => "gradle/actions/wrapper-validation@v5" },
    { "uses" => "actions/setup-java@v5", "with" => { "distribution" => "temurin", "java-version" => "25" } },
    { "uses" => "actions/setup-node@v5", "with" => { "node-version" => "22.19.0", "package-manager-cache" => false } },
    { "uses" => "pnpm/action-setup@v5", "with" => { "version" => "10.33.0" } },
    { "run" => "pnpm install --frozen-lockfile" },
    { "run" => "ruby .github/workflows/tests/validate_workflows.rb" },
    { "run" => "docker compose --env-file infra/.env.example -f infra/compose.dev.yml config --quiet" },
    { "run" => PROD_COMPOSE_CONFIG_RUN },
    { "run" => SYNTAX_RUN },
    { "run" => "bash scripts/tests/deployment-contract.sh" },
    { "run" => CADDY_NO_SNI_TEST_RUN },
    { "run" => "./gradlew clean test integrationTest openapi3 bootJar", "working-directory" => "backend" },
    { "run" => "pnpm api:generate" },
    { "run" => "pnpm frontend:test" },
    { "run" => "pnpm frontend:typecheck" },
    { "run" => "pnpm frontend:build" },
    { "run" => "docker build --tag ai-erp:ci ." },
    { "uses" => "actions/upload-artifact@v6", "if" => "always()", "with" => { "name" => "verification-artifacts", "path" => "backend/build/reports\nbackend/build/api-spec\nbackend/build/api-docs\n" } }
  ]
end

def deploy_steps
  [
    { "uses" => "actions/checkout@v5", "with" => { "ref" => EXPECTED_CHECKOUT_REF, "fetch-depth" => 0, "persist-credentials" => false, "clean" => true } },
    { "name" => "Resolve validated release SHA", "shell" => "bash", "run" => RESOLVER_RUN },
    { "name" => "Deploy validated release", "shell" => "bash", "run" => "bash scripts/deploy.sh \"$RELEASE_SHA\"" },
    { "name" => "Deployment summary", "if" => "always()", "shell" => "bash", "env" => { "JOB_STATUS" => "${{ job.status }}" }, "run" => SUMMARY_RUN }
  ]
end

def validate_ci!(ci)
  assert!(ci.keys.sort == ["concurrency", "jobs", "name", "on", "permissions"], "CI top-level keys")
  assert!(ci["name"] == "CI", "CI workflow name")
  assert!(ci["on"] == { "push" => { "branches" => ["main"] }, "pull_request" => nil }, "CI triggers")
  assert!(ci["permissions"] == { "contents" => "read" }, "CI permissions")
  assert!(ci["concurrency"] == { "group" => "ci-${{ github.workflow }}-${{ github.ref }}", "cancel-in-progress" => true }, "CI concurrency")
  assert!(ci.fetch("jobs").keys == ["verify"], "CI job list")
  verify = ci.fetch("jobs").fetch("verify")
  assert!(verify.keys.sort == ["runs-on", "steps"], "CI job keys")
  assert!(verify["runs-on"] == "ubuntu-latest", "CI GitHub-hosted runner")
  assert_steps!(verify.fetch("steps"), ci_steps, "CI")
end

def validate_deploy!(deploy)
  assert!(deploy.keys.sort == ["concurrency", "jobs", "name", "on", "permissions"], "deployment top-level keys")
  assert!(deploy["name"] == "Deploy Production", "deployment workflow name")
  assert!(deploy["on"] == { "workflow_run" => { "workflows" => ["CI"], "types" => ["completed"] }, "workflow_dispatch" => {} }, "deployment triggers")
  assert!(deploy["permissions"] == { "contents" => "read" }, "deployment permissions")
  assert!(deploy["concurrency"] == { "group" => "production-deploy", "cancel-in-progress" => false }, "production concurrency")
  assert!(deploy.fetch("jobs").keys == ["deploy"], "deployment job list")
  job = deploy.fetch("jobs").fetch("deploy")
  assert!(job.keys.sort == ["env", "if", "runs-on", "steps", "timeout-minutes"], "deployment job keys")
  assert!(normalized(job["if"]) == EXPECTED_DEPLOY_GUARD, "exact trusted-main deployment guard")
  assert!(job["runs-on"] == ["self-hosted", "linux", "x64", "ai-erp-prod"], "exact static production runner labels")
  assert!(job["timeout-minutes"].is_a?(Integer) && job["timeout-minutes"].positive?, "positive deployment timeout")
  assert!(job.fetch("env").keys.none? { |key| key.start_with?("GITHUB_") }, "deployment custom env must not use reserved GITHUB_* names")
  assert!(job["env"] == EXPECTED_EVENT_ENV, "exact trusted event environment")
  assert_steps!(job.fetch("steps"), deploy_steps, "deployment")
  assert!(!Psych.dump(deploy).match?(SENSITIVE_OUTPUT), "deployment has no sensitive workflow content")
end

def validate_runner_boundaries!(documents)
  documents.each do |path, document|
    document.fetch("jobs", {}).each do |name, job|
      expected_deploy = File.basename(path) == "deploy-production.yml" && name == "deploy"
      if expected_deploy
        assert!(job["runs-on"] == ["self-hosted", "linux", "x64", "ai-erp-prod"], "guarded deployment runner is static")
      else
        assert!(job["runs-on"] == "ubuntu-latest", "#{File.basename(path)} #{name} must use literal GitHub-hosted runner")
      end
    end
  end
end

def validate!(documents)
  validate_ci!(documents.fetch("ci.yml"))
  validate_deploy!(documents.fetch("deploy-production.yml"))
  validate_runner_boundaries!(documents)
rescue KeyError, TypeError => error
  raise ContractError, "malformed workflow structure: #{error.message}"
end

def deep_copy(value)
  Marshal.load(Marshal.dump(value))
end

def assert_rejected!(description)
  yield
rescue ContractError
  return
else
  raise ContractError, "negative self-test accepted #{description}"
end

def deploy_job(documents)
  documents.fetch("deploy-production.yml").fetch("jobs").fetch("deploy")
end

def self_test!(documents)
  baseline = deep_copy(documents)
  automatic_atoms = ["github.event_name == 'workflow_run'", "github.event.workflow_run.conclusion == 'success'", "github.event.workflow_run.event == 'push'", "github.event.workflow_run.head_branch == 'main'", "github.event.workflow_run.head_repository.full_name == github.repository"]
  manual_atoms = ["github.event_name == 'workflow_dispatch'", "github.ref == 'refs/heads/main'", "github.event.repository.default_branch == 'main'"]
  (automatic_atoms + manual_atoms).each do |atom|
    assert_rejected!("missing guard #{atom}") { mutated = deep_copy(baseline); job = deploy_job(mutated); job["if"] = job["if"].sub(atom, "removed"); validate!(mutated) }
  end
  assert_rejected!("an extra OR bypass") { mutated = deep_copy(baseline); deploy_job(mutated)["if"] += " || true"; validate!(mutated) }
  assert_rejected!("free-form manual input") { mutated = deep_copy(baseline); mutated["deploy-production.yml"]["on"]["workflow_dispatch"] = { "inputs" => { "ref" => { "required" => true } } }; validate!(mutated) }
  assert_rejected!("free-form manual ref") { mutated = deep_copy(baseline); deploy_job(mutated)["steps"][0]["with"]["ref"] = "${{ inputs.ref }}"; validate!(mutated) }
  assert_rejected!("workflow_run checkout of event SHA") { mutated = deep_copy(baseline); deploy_job(mutated)["steps"][0]["with"]["ref"] = "${{ github.event_name == 'workflow_run' && github.event.workflow_run.head_sha || 'refs/heads/main' }}"; validate!(mutated) }
  assert_rejected!("reserved GITHUB_ custom event environment") do
    mutated = deep_copy(baseline)
    job = deploy_job(mutated)
    job["env"]["GITHUB_REF_NAME"] = job["env"].delete("EVENT_REF")
    job["steps"][1]["run"] = job["steps"][1]["run"].gsub("$EVENT_REF", "$GITHUB_REF_NAME")
    validate!(mutated)
  end
  deploy_steps.each_index do |index|
    assert_rejected!("disabled deployment step #{index}") { mutated = deep_copy(baseline); deploy_job(mutated)["steps"][index]["if"] = "false"; validate!(mutated) }
    assert_rejected!("continue-on-error deployment step #{index}") { mutated = deep_copy(baseline); deploy_job(mutated)["steps"][index]["continue-on-error"] = true; validate!(mutated) }
    assert_rejected!("shell substitution deployment step #{index}") { mutated = deep_copy(baseline); deploy_job(mutated)["steps"][index]["shell"] = "sh"; validate!(mutated) }
  end
  ci_steps.each_index do |index|
    assert_rejected!("disabled CI step #{index}") { mutated = deep_copy(baseline); mutated["ci.yml"]["jobs"]["verify"]["steps"][index]["if"] = "false"; validate!(mutated) }
    assert_rejected!("continue-on-error CI step #{index}") { mutated = deep_copy(baseline); mutated["ci.yml"]["jobs"]["verify"]["steps"][index]["continue-on-error"] = true; validate!(mutated) }
    assert_rejected!("shell substitution CI step #{index}") { mutated = deep_copy(baseline); mutated["ci.yml"]["jobs"]["verify"]["steps"][index]["shell"] = "sh"; validate!(mutated) }
  end
  caddy_test_index = ci_steps.index { |step| step["run"] == CADDY_NO_SNI_TEST_RUN }
  assert!(caddy_test_index, "Caddy no-SNI test is part of the expected CI steps")
  assert_rejected!("deleted Caddy no-SNI integration test") { mutated = deep_copy(baseline); mutated["ci.yml"]["jobs"]["verify"]["steps"].delete_at(caddy_test_index); validate!(mutated) }
  assert_rejected!("bypassed Caddy no-SNI integration test") { mutated = deep_copy(baseline); mutated["ci.yml"]["jobs"]["verify"]["steps"][caddy_test_index]["run"] = "true"; validate!(mutated) }
  assert_rejected!("comment-only syntax check") { mutated = deep_copy(baseline); mutated["ci.yml"]["jobs"]["verify"]["steps"][9]["run"] = "# bash -n scripts/deploy.sh"; validate!(mutated) }
  assert_rejected!("production Compose config without APP_IMAGE") { mutated = deep_copy(baseline); mutated["ci.yml"]["jobs"]["verify"]["steps"][8]["run"] = "docker compose --env-file infra/.env.prod.example -f infra/compose.prod.yml config --quiet"; validate!(mutated) }
  assert_rejected!("second deploy invocation") { mutated = deep_copy(baseline); deploy_job(mutated)["steps"] << { "name" => "Bypass", "shell" => "bash", "run" => "bash scripts/deploy.sh deadbeef" }; validate!(mutated) }
  assert_rejected!("checkout credential persistence") { mutated = deep_copy(baseline); deploy_job(mutated)["steps"][0]["with"]["persist-credentials"] = true; validate!(mutated) }
  assert_rejected!("secret output") { mutated = deep_copy(baseline); deploy_job(mutated)["steps"] << { "name" => "Leak", "shell" => "bash", "run" => "echo ${{ secrets.TOKEN }}" }; validate!(mutated) }
  assert_rejected!("dynamic self-hosted YAML runner") { mutated = deep_copy(baseline); mutated["bypass.yaml"] = { "jobs" => { "bypass" => { "runs-on" => "${{ matrix.runner }}" } } }; validate!(mutated) }
end

documents = (Dir.glob(File.join(ROOT, "*.yml")) + Dir.glob(File.join(ROOT, "*.yaml"))).to_h { |path| [File.basename(path), parsed_workflow(path)] }
validate!(documents)
self_test!(documents)
puts "workflow contract passed (positive and negative cases)"
