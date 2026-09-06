#!/usr/bin/env ruby
# frozen_string_literal: true

require "psych"

ROOT = File.expand_path("..", __dir__)

def workflow(path)
  Psych.safe_load_file(path, aliases: true)
rescue Psych::Exception => error
  abort "invalid YAML #{path}: #{error.message}"
end

def assert(condition, message)
  abort "workflow contract failed: #{message}" unless condition
end

def step_runs(job)
  Array(job.fetch("steps", [])).filter_map { |step| step["run"] if step.is_a?(Hash) }.join("\n")
end

ci = workflow(File.join(ROOT, "ci.yml"))
deploy = workflow(File.join(ROOT, "deploy-production.yml"))

assert(ci["name"] == "CI", "CI workflow name")
assert(ci.dig("on", "push", "branches") == ["main"], "CI push must be main-only")
assert(ci.fetch("on").key?("pull_request"), "CI must validate pull requests")
verify = ci.dig("jobs", "verify")
assert(verify && verify["runs-on"] == "ubuntu-latest", "CI runs on GitHub-hosted Ubuntu")
ci_runs = step_runs(verify)
ci_uses = Array(verify.fetch("steps")).filter_map { |step| step["uses"] }
assert(ci_uses.include?("gradle/actions/wrapper-validation@v5"), "CI preserves Gradle wrapper validation")
assert(ci_uses.include?("actions/setup-java@v5"), "CI preserves Java setup")
assert(ci_uses.include?("actions/setup-node@v5"), "CI preserves Node setup")
assert(ci_uses.include?("pnpm/action-setup@v5"), "CI preserves pnpm setup")
assert(ci_runs.include?("./gradlew clean test integrationTest openapi3 bootJar"), "CI preserves backend verification")
assert(ci_runs.include?("pnpm api:generate") && ci_runs.include?("pnpm frontend:test") && ci_runs.include?("pnpm frontend:typecheck") && ci_runs.include?("pnpm frontend:build"), "CI preserves API and frontend verification")
%w[preflight.sh backup.sh deploy.sh rollback.sh smoke.sh].each do |script|
  assert(ci_runs.include?("bash -n scripts/#{script}"), "CI validates #{script} syntax")
end
assert(ci_runs.include?("bash scripts/tests/deployment-contract.sh"), "CI runs deployment contract test")
assert(ci_runs.include?("compose.prod.yml config --quiet"), "CI validates production Compose config")
assert(ci_runs.include?("docker build --tag ai-erp:ci ."), "CI builds the immutable image")

assert(deploy["name"] == "Deploy Production", "deployment workflow name")
trigger = deploy.fetch("on")
assert(trigger.key?("workflow_run") && trigger.key?("workflow_dispatch"), "only workflow_run and manual dispatch triggers")
assert(trigger.keys.sort == ["workflow_dispatch", "workflow_run"], "deployment has no pull-request trigger")
assert(trigger["workflow_dispatch"] == {}, "manual dispatch takes no free-form input")
assert(trigger["workflow_run"]["workflows"] == ["CI"], "deployment follows CI only")
assert(trigger["workflow_run"]["types"] == ["completed"], "deployment waits for CI completion")
assert(deploy["permissions"] == { "contents" => "read" }, "read-only repository permissions")
concurrency = deploy.fetch("concurrency")
assert(concurrency["group"] == "production-deploy", "fixed production concurrency group")
assert(concurrency["cancel-in-progress"] == false, "production deploys never cancel in progress")
jobs = deploy.fetch("jobs")
assert(jobs.keys == ["deploy"], "exactly one deployment job")
job = jobs.fetch("deploy")
assert(job["runs-on"] == ["self-hosted", "linux", "x64", "ai-erp-prod"], "exact production runner labels")
assert(job["timeout-minutes"].is_a?(Integer) && job["timeout-minutes"] > 0, "positive deployment timeout")
condition = job.fetch("if")
[
  "github.event.workflow_run.conclusion == 'success'",
  "github.event.workflow_run.event == 'push'",
  "github.event.workflow_run.head_branch == 'main'",
  "github.event.workflow_run.head_repository.full_name == github.repository",
  "github.event_name == 'workflow_dispatch'",
  "github.ref == 'refs/heads/main'",
  "github.event.repository.default_branch == 'main'"
].each { |term| assert(condition.include?(term), "trusted-main guard includes #{term}") }

steps = Array(job.fetch("steps"))
checkout = steps.find { |step| step["uses"] == "actions/checkout@v5" }
assert(checkout, "checkout v5")
checkout_with = checkout.fetch("with")
assert(checkout_with["fetch-depth"] == 0, "full checkout history")
assert(checkout_with["persist-credentials"] == false, "checkout does not persist credentials")
assert(checkout_with["clean"] == true, "checkout cleans self-hosted workspace")
assert(checkout_with["ref"].include?("workflow_run.head_sha"), "automatic checkout uses completed CI SHA")
assert(checkout_with["ref"].include?("refs/heads/main"), "manual checkout only uses main")

runs = step_runs(job)
assert(runs.include?("^[a-f0-9]{40}$"), "validated SHA is lowercase 40 hex")
assert(job.dig("env", "WORKFLOW_RUN_SHA")&.include?("workflow_run.head_sha"), "automatic SHA comes from CI SHA")
assert(runs.include?("WORKFLOW_RUN_SHA"), "automatic SHA is compared to CI SHA")
assert(runs.include?("origin/main"), "manual SHA is resolved from origin/main")
assert(runs.include?("bash scripts/deploy.sh \"$RELEASE_SHA\""), "deploy receives validated SHA only")
assert(!runs.match?(/\bssh\b|secrets\.|\.env|docker inspect|docker logs|upload-artifact/i), "deployment has no secret or raw host output")
assert(!Psych.dump(deploy).match?(/pull_request|secrets\.|upload-artifact|docker inspect|docker logs|\.env/i), "deployment has no untrusted or sensitive path")
Dir.glob(File.join(ROOT, "*.yml")).each do |path|
  workflow(path).fetch("jobs", {}).each_value do |candidate|
    labels = candidate["runs-on"]
    assert(!Array(labels).include?("self-hosted") || File.basename(path) == "deploy-production.yml", "only deployment uses a self-hosted runner")
  end
end
puts "workflow contract passed"
