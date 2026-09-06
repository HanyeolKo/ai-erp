#!/usr/bin/env ruby
# frozen_string_literal: true

require "psych"

ROOT = File.expand_path("..", __dir__)
AUTOMATIC_GUARD = "(github.event_name == 'workflow_run' && github.event.workflow_run.conclusion == 'success' && github.event.workflow_run.event == 'push' && github.event.workflow_run.head_branch == 'main' && github.event.workflow_run.head_repository.full_name == github.repository)"
MANUAL_GUARD = "(github.event_name == 'workflow_dispatch' && github.ref == 'refs/heads/main' && github.event.repository.default_branch == 'main')"
EXPECTED_DEPLOY_GUARD = "#{AUTOMATIC_GUARD} || #{MANUAL_GUARD}"
EXPECTED_CHECKOUT_REF = "${{ github.event_name == 'workflow_run' && github.event.workflow_run.head_sha || 'refs/heads/main' }}"
EXPECTED_EVENT_ENV = {
  "EVENT_NAME" => "${{ github.event_name }}",
  "WORKFLOW_RUN_SHA" => "${{ github.event.workflow_run.head_sha }}",
  "GITHUB_REF_NAME" => "${{ github.ref }}",
  "DEFAULT_BRANCH" => "${{ github.event.repository.default_branch }}"
}.freeze
SENSITIVE_OUTPUT = /\bssh\b|secrets\.|\.env|docker inspect|docker logs|upload-artifact/i

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

def steps(job)
  Array(job.fetch("steps", []))
end

def run_steps(job)
  steps(job).filter_map { |step| step["run"] if step.is_a?(Hash) }
end

def used_steps(job)
  steps(job).filter_map { |step| step["uses"] if step.is_a?(Hash) }
end

def one_step!(job, description)
  matches = yield(steps(job))
  assert!(matches.length == 1, "exactly one #{description}")
  matches.first
end

def validate_ci!(ci)
  assert!(ci["name"] == "CI", "CI workflow name")
  assert!(ci.dig("on", "push", "branches") == ["main"], "CI push must be main-only")
  assert!(ci.fetch("on").key?("pull_request"), "CI must validate pull requests")
  assert!(ci["permissions"] == { "contents" => "read" }, "CI permissions")
  assert!(ci["concurrency"] == { "group" => "ci-${{ github.workflow }}-${{ github.ref }}", "cancel-in-progress" => true }, "CI concurrency")
  jobs = ci.fetch("jobs")
  jobs.each_value { |job| assert!(!Array(job["runs-on"]).include?("self-hosted"), "CI cannot use self-hosted runner") }
  verify = jobs.fetch("verify")
  assert!(verify["runs-on"] == "ubuntu-latest", "CI runs on GitHub-hosted Ubuntu")
  assert!(!verify.key?("permissions"), "CI job has no permission override")
  uses = used_steps(verify)
  assert!(uses.include?("gradle/actions/wrapper-validation@v5"), "Gradle wrapper validation")
  java = one_step!(verify, "Java setup") { |all| all.select { |step| step["uses"] == "actions/setup-java@v5" } }
  assert!(java["with"] == { "distribution" => "temurin", "java-version" => "25" }, "Java 25 Temurin setup")
  node = one_step!(verify, "Node setup") { |all| all.select { |step| step["uses"] == "actions/setup-node@v5" } }
  assert!(node["with"] == { "node-version" => "22.19.0", "package-manager-cache" => false }, "Node 22.19.0 setup without cache")
  pnpm = one_step!(verify, "pnpm setup") { |all| all.select { |step| step["uses"] == "pnpm/action-setup@v5" } }
  assert!(pnpm["with"] == { "version" => "10.33.0" }, "pnpm 10.33.0 setup")
  runs = run_steps(verify).map { |run| normalized(run) }
  required = ["pnpm install --frozen-lockfile", "ruby .github/workflows/tests/validate_workflows.rb", "docker compose --env-file infra/.env.example -f infra/compose.dev.yml config --quiet", "docker compose --env-file infra/.env.prod.example -f infra/compose.prod.yml config --quiet", "bash scripts/tests/deployment-contract.sh", "./gradlew clean test integrationTest openapi3 bootJar", "pnpm api:generate", "pnpm frontend:test", "pnpm frontend:typecheck", "pnpm frontend:build", "docker build --tag ai-erp:ci ."]
  required.each { |command| assert!(runs.include?(command), "CI command #{command}") }
  %w[preflight.sh backup.sh deploy.sh rollback.sh smoke.sh].each do |script|
    assert!(runs.any? { |run| run.include?("bash -n scripts/#{script}") }, "shell syntax check #{script}")
  end
end

def validate_deploy!(deploy)
  assert!(deploy["name"] == "Deploy Production", "deployment workflow name")
  trigger = deploy.fetch("on")
  assert!(trigger.keys.sort == ["workflow_dispatch", "workflow_run"], "deployment has only trusted triggers")
  assert!(trigger["workflow_dispatch"] == {}, "manual dispatch accepts no inputs")
  assert!(trigger.dig("workflow_run", "workflows") == ["CI"], "deployment follows CI only")
  assert!(trigger.dig("workflow_run", "types") == ["completed"], "deployment waits for CI completion")
  assert!(deploy["permissions"] == { "contents" => "read" }, "deployment permissions")
  assert!(deploy["concurrency"] == { "group" => "production-deploy", "cancel-in-progress" => false }, "non-cancelling production concurrency")
  jobs = deploy.fetch("jobs")
  assert!(jobs.keys == ["deploy"], "exactly one deployment job")
  job = jobs.fetch("deploy")
  assert!(!job.key?("permissions"), "deployment job has no permission override")
  assert!(job["runs-on"] == ["self-hosted", "linux", "x64", "ai-erp-prod"], "exact production runner labels")
  assert!(job["timeout-minutes"].is_a?(Integer) && job["timeout-minutes"].positive?, "positive deployment timeout")
  assert!(normalized(job["if"]) == EXPECTED_DEPLOY_GUARD, "exact trusted-main deployment guard")
  assert!(job["env"] == EXPECTED_EVENT_ENV, "event values stay in exact job environment")
  checkout = one_step!(job, "checkout") { |all| all.select { |step| step["uses"] == "actions/checkout@v5" } }
  assert!(checkout["with"] == { "ref" => EXPECTED_CHECKOUT_REF, "fetch-depth" => 0, "persist-credentials" => false, "clean" => true }, "safe checkout configuration")
  resolve = one_step!(job, "release resolution") { |all| all.select { |step| step["name"] == "Resolve validated release SHA" } }
  resolve_run = normalized(resolve["run"])
  ["set -euo pipefail", "RELEASE_SHA=\"$(git rev-parse HEAD)\"", "[[ \"$RELEASE_SHA\" =~ ^[a-f0-9]{40}$ ]]", "case \"$EVENT_NAME\" in", "[[ \"$RELEASE_SHA\" == \"$WORKFLOW_RUN_SHA\" ]]", "MAIN_SHA=\"$(git rev-parse origin/main)\"", "[[ \"$MAIN_SHA\" =~ ^[a-f0-9]{40}$ && \"$RELEASE_SHA\" == \"$MAIN_SHA\" ]]", "printf 'RELEASE_SHA=%s\\n' \"$RELEASE_SHA\" >> \"$GITHUB_ENV\""]
    .each { |fragment| assert!(resolve_run.include?(fragment), "validated release resolution includes #{fragment}") }
  deploy_step = one_step!(job, "deploy invocation") { |all| all.select { |step| normalized(step["run"]) == "bash scripts/deploy.sh \"$RELEASE_SHA\"" } }
  assert!(!deploy_step.key?("env"), "deploy invocation has no alternate environment source")
  assert!(run_steps(job).sum { |run| run.scan(/\bdeploy\.sh\b/).length } == 1, "no second deployment invocation")
  summary = one_step!(job, "deployment summary") { |all| all.select { |step| step["name"] == "Deployment summary" } }
  assert!(summary["if"] == "always()", "summary always runs")
  assert!(summary.dig("env", "JOB_STATUS") == "${{ job.status }}", "summary receives job status")
  assert!(normalized(summary["run"]).include?("$GITHUB_STEP_SUMMARY"), "summary writes GitHub step summary")
  assert!(!normalized(summary["run"]).match?(SENSITIVE_OUTPUT), "summary has no sensitive output")
  assert!(!Psych.dump(deploy).match?(SENSITIVE_OUTPUT), "deployment has no sensitive workflow content")
end

def validate_self_hosted_usage!(documents)
  documents.each do |path, document|
    document.fetch("jobs", {}).each do |name, job|
      next unless Array(job["runs-on"]).include?("self-hosted")

      assert!(File.basename(path) == "deploy-production.yml" && name == "deploy", "only guarded deploy job may use self-hosted runner")
    end
  end
end

def validate!(documents)
  validate_ci!(documents.fetch("ci.yml"))
  validate_deploy!(documents.fetch("deploy-production.yml"))
  validate_self_hosted_usage!(documents)
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

def self_test!(documents)
  baseline = deep_copy(documents)
  assert_rejected!("an extra OR bypass") { mutated = deep_copy(baseline); mutated["deploy-production.yml"]["jobs"]["deploy"]["if"] += " || true"; validate!(mutated) }
  ["conclusion == 'success'", "event == 'push'", "head_branch == 'main'", "head_repository.full_name == github.repository"].each do |guard|
    assert_rejected!("missing automatic guard #{guard}") { mutated = deep_copy(baseline); job = mutated["deploy-production.yml"]["jobs"]["deploy"]; job["if"] = job["if"].sub(guard, "removed"); validate!(mutated) }
  end
  assert_rejected!("free-form manual input") { mutated = deep_copy(baseline); mutated["deploy-production.yml"]["on"]["workflow_dispatch"] = { "inputs" => { "ref" => { "required" => true } } }; validate!(mutated) }
  assert_rejected!("free-form manual ref") { mutated = deep_copy(baseline); mutated["deploy-production.yml"]["jobs"]["deploy"]["steps"].first["with"]["ref"] = "${{ inputs.ref }}"; validate!(mutated) }
  assert_rejected!("unvalidated second deploy call") { mutated = deep_copy(baseline); mutated["deploy-production.yml"]["jobs"]["deploy"]["steps"] << { "name" => "Bypass", "run" => "bash scripts/deploy.sh deadbeef" }; validate!(mutated) }
  assert_rejected!("persisted checkout credentials") { mutated = deep_copy(baseline); mutated["deploy-production.yml"]["jobs"]["deploy"]["steps"].first["with"]["persist-credentials"] = true; validate!(mutated) }
  assert_rejected!("artifact or sensitive output") { mutated = deep_copy(baseline); mutated["deploy-production.yml"]["jobs"]["deploy"]["steps"] << { "name" => "Leak", "run" => "docker logs app-blue" }; validate!(mutated) }
  assert_rejected!("self-hosted YAML document") { mutated = deep_copy(baseline); mutated["bypass.yaml"] = { "jobs" => { "bypass" => { "runs-on" => ["self-hosted"] } } }; validate!(mutated) }
end

documents = (Dir.glob(File.join(ROOT, "*.yml")) + Dir.glob(File.join(ROOT, "*.yaml"))).to_h { |path| [File.basename(path), parsed_workflow(path)] }
validate!(documents)
self_test!(documents)
puts "workflow contract passed (positive and negative cases)"
