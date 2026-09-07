<!-- document-budget exception: required-sequential-instruction | Release role, operational boundaries and acceptance checks must remain one bounded contract. -->
# Stage 2 implementation contract

Task: lifecycle-management-stage-2. Contract revision: 1. Parent: /root.
Activation: execute only after parent records stage-1 independent acceptance and explicitly dispatches this contract.
Objective: connect development, integration, release/recovery and feedback, with a separate release-manager.
Base: accepted stage-1 working tree plus preserved original snapshot. The user approved lifecycle improvements and warranted role separation.
No screen/application implementation or production operation is authorized in this task. Unresolved decisions: none.
Use the documented Luna/high fallback while Spark quota remains unavailable; parent records invocation.

## Exact permitted files
- Create harness/team/agents/release-manager.md and .codex/agents/ai-erp-release-manager.toml.
- Create harness/skills/ai-erp-release/SKILL.md and .agents/skills/ai-erp-release/SKILL.md.
- Create harness/templates/RELEASE-CONTRACT.md, RELEASE-RESULT.md, RELEASE-REVIEW.md, INCREMENT-RECORD.md in that templates directory.
- Create harness/evaluation/RELEASE-REVIEW-RUBRIC.md, harness/workflows/VERIFICATION-MATRIX.md, harness/workflows/RELEASE-FLOW.md.
- Modify harness/harness-spec.json, harness/HARNESS.md, harness/ENVIRONMENT.md, harness/team/TEAM-ARCHITECTURE.md.
- Modify harness/team/agents/router.md, reviewer.md, implementer.md, product-planner.md in that agents directory.
- Modify harness/skills/ai-erp/SKILL.md, ai-erp-eval/SKILL.md, ai-erp-implement/SKILL.md, ai-erp-plan/SKILL.md and their exact .agents projections.
- Modify harness/templates/IMPLEMENTATION-CONTRACT.md, IMPLEMENTATION-RESULT.md, INCREMENT-PLAN.md, INCREMENT-REVIEW.md, TASK-ASSIGNMENT.md in that templates directory.
- Modify harness/evaluation/TASK-REVIEW-RUBRIC.md, INCREMENT-PLAN-RUBRIC.md and harness/workflows/INCREMENT-LIFECYCLE.md, DELEGATION-PROTOCOL.md.
- Modify harness/loops/EXECUTION-LOOP.md, EVAL-LOOP.md, harness/recovery/CHECKPOINT.md, RECOVERY-PLAYBOOK.md, harness/ledger/JOURNAL-FORMAT.md.
- Modify scripts/verify-harness.py and scripts/test_verify_harness.py for release/lifecycle coverage.
- Modify only AGENTS.md managed block. Add stage-2-result.md and stage-2-checks.json in this run.
- Exclude state/journal/decisions, parent contracts, app/deploy scripts/workflows, vendor, unrelated native files and reader docs.

## Role topology and ownership
- Exactly six roles and ten skills after this stage. Keep max_parallelism 4/max_depth 2 and Codex limits unchanged.
- Release-manager: lane execution, access workspace-write, model_tier deep, inherited native model/effort, capabilities release-preparation/release-operations/verification.
- Release domain paths [docs/releases, scripts, infra, .github], coordinator release-manager; add domain to router/reviewer and entry/eval/release skills.
- Domain paths describe input scope, not code-write authority. Release-manager only writes authorized release artifacts and executes approved existing operational commands.
- Release-manager cannot edit source/tests/config, push, merge, own verdicts, expand permissions or re-delegate; code/config changes return to parent -> implementer.
- ai-erp-release is domain skill, entry_agent release-manager, domains [release], evaluator release-review.
- Add reviewer -> release-manager artifact edge. Preparation requires accepted implementation evidence plus a parent preparation assignment; execution requires independent release readiness and complete authorized release contract.
- Retain planner/UI/implementation DAG; no release-manager -> reviewer or implementer -> release-manager back/shortcut edges.
- Parent controls actual assignment, receives every result, requests independent review, accepts or revises. No worker-to-worker self-delegation.

## Contract, verification and release decisions
- Add release-review task/rubric, runner router, owner reviewer, with explicit mode readiness or outcome. Every applicable required criterion passes; missing/stale/failed/pending evidence fails.
- Readiness is not deployment completion. A release-manager prepares a release contract and evidence; parent owns final ready determination and authorization.
- For execution require environment, exact 40-hex reviewed commit/SHA, successful CI run for the same SHA, artifact identity, authorized operation/commands, DB compatibility, backup/restore boundaries, rollback plan and smoke scope.
- Main push/merge may trigger production deployment; preparation must expose this effect before parent authorizes publication. Do not introduce blanket repeated approval.
- Manual release also requires same-SHA CI success proof because current workflow does not check CI history itself.
- Record deployment observation, actual active release and manifest/events/output pointers, prior release, cleanup/recovery outcome and next owner/action.
- App rollback does not restore DB. Backup listing is not a restore drill. Existing --resolve/--insecure smoke does not prove public DNS/TLS trust.
- Operator-action-required (including exit 2 recovery failure) cannot be marked deployed/complete. Distinguish operational release success from cleanup follow-up.
- No production credentials or copies of active-state data; link durable authorized evidence and preserve old release/contract records.
- INCREMENT-RECORD links ID/plan revision -> designs -> FE/BE contracts/dependencies -> reviews -> integration checks -> commit/CI -> release -> feedback.
- Keep phase/queue/next_action and journal evidence pointers. Do not install a second state engine, adaptive memory or autonomous scheduler.
- Lifecycle states distinguish planned, implementation-reviewed, release-ready, observing, deployed, rolled-back, operator-action-required and justified not-requested.
- Feedback changes product priorities/next plan revision; stale downstream evidence is re-reviewed only for affected scope.
- Plan-only or non-release tasks mark release N/A with reason, not fabricated pass. Existing small fixes may reuse a valid plan with a scoped amendment.
- Verification matrix cites existing CI commands and cwd: FE tests/types/build; BE gradlew tests/integration/OpenAPI/bootJar; API generation; DB migration inventory/checksum/old-app compatibility; deployment contract tests.
- Commands unavailable on the platform are recorded not run and cannot satisfy a required release check. Preserve actual evidence versus static policy distinction.
- Add assignment protocol references to both new roles and cross-stage records; model invocation checks apply to implementer only, not planner/release-manager.

## Acceptance and checks
1. Six-role/ten-skill schema, metadata, DAG, references, ownership and parity pass.
2. New release/lifecycle templates and rubric enforce IDs/revisions and implementation-versus-deployment completion.
3. Existing deploy/CI/migration code is unchanged; limitations and same-SHA manual CI policy are explicit.
4. Missing release artifacts/link fields or wrong release evaluator owner/runner fail focused regressions (red then green).
5. Parent/worker/reviewer assignment, acknowledgement, evidence return, correction and acceptance remain unambiguous.
6. No historical/preserved evidence loss and no unauthorized code or external operation.
Common files first, successful provider preflight against delta-plan before projection; no path-check bypass.
Run python -B scripts/verify-harness.py, python -B scripts/test_verify_harness.py, python -B scripts/smoke-ux-skills.py and git diff --check.
Return ready-for-review with actual model/effort/dispatch/fallback evidence, exact paths, numbered criteria, commands/exits/output and checks not run. Parent/reviewer own verdicts.
