<!-- document-budget exception: required-sequential-instruction | The bounded execution contract and acceptance checks must be read together. -->
# Stage 1 implementation contract

Task: lifecycle-management-stage-1. Contract revision: 1.
Parent decision owner: /root. Base Git state: main/a7f9b329d676a5babd6feae4b95043b5010a506d plus preserved docs/reports.
Objective: install incremental product planning/design with independent plan review before bounded implementation.
Current behavior/source: approved docs/reports/harness-review-2026-09-07.md; harness currently only distinguishes UI planning and implementation.
Chosen design: extend current four-role core; parent writes plan, router routes, reviewer judges increment plan, designer still owns all screen decisions.
Read IMPLEMENTATION-PLAN.md and delta-plan.json in this directory. This contract authorizes stage 1 only.
UI prerequisite: no screen behavior or screen design changed. Existing UI specialist policy must remain intact.
Unresolved decisions: none. Parent ready-to-implement: ready.
Selected execution: native ai-erp-implementer, fixed gpt-5.3-codex-spark/high; return invocation evidence and do not re-delegate.

## Exact permitted files
- Create harness/templates/INCREMENT-PLAN.md, harness/templates/INCREMENT-REVIEW.md, harness/evaluation/INCREMENT-PLAN-RUBRIC.md, harness/workflows/INCREMENT-LIFECYCLE.md.
- Modify harness/harness-spec.json, harness/HARNESS.md, harness/team/TEAM-ARCHITECTURE.md.
- Modify harness/team/agents/router.md and harness/team/agents/reviewer.md (no access/model/capability changes).
- Modify harness/skills/ai-erp/SKILL.md, harness/skills/ai-erp-eval/SKILL.md and their exact .agents/skills/ai-erp*/SKILL.md projections for those two skills only.
- Modify harness/loops/EXECUTION-LOOP.md, harness/loops/EVAL-LOOP.md, harness/templates/IMPLEMENTATION-CONTRACT.md, harness/evaluation/TASK-REVIEW-RUBRIC.md.
- Modify scripts/verify-harness.py and scripts/test_verify_harness.py for planning coverage only.
- Modify only the harness-managed block of AGENTS.md to expose new planning references; preserve all outside text.
- Add stage-1-result.md and stage-1-checks.json in this run directory. No other run artifact edits.
- All other paths are excluded, especially historical evidence, application/deployment files, state/journal, vendor, .codex, existing docs/reports, parent contracts and receipt.

## Interface and behavior rules
- Add planning domain with paths [docs/planning], coordinator router; add this domain to router/reviewer and entry/eval skill domains only.
- Add increment-plan-review: scope task, type rubric, runner router, owner reviewer; command names INCREMENT-PLAN-RUBRIC and review template; every applicable criterion must pass, missing/stale/failed/pending evidence cannot pass.
- Retain all existing evaluator IDs/meaning; task-review routes general planning to increment-plan-review and screen planning also requires ui-plan-review.
- Preserve all handoff edges and existing UI gate; parent-requested reviews are not new graph edges.
- The plan fields are increment_id, plan_revision, source evidence, problem, users/value, prioritized goals, current scope, deferred scope, business/API/data/permission/FE/BE impact, dependencies, assumptions, numbered acceptance, review references, feedback.
- Current implementation decisions must be resolved; deferred future decisions do not block a separate ready slice.
- Changed plan revision invalidates affected downstream contract/review/test/release evidence; parent records impact and re-reviews before continuing.
- Review template requires matching increment_id/plan_revision, evaluator, actual independent reviewer, evidence per criterion, unresolved items, verdict and checks_not_run.
- Implementation contract adds matching increment_id/plan_revision, plan and review paths without removing current required fields.
- Workflow allows planning-only completion, reuse of a still-valid plan for small fixes, and explicit scoped amendments; no fabricated full release or implementation evidence.
- Keep canonical Markdown concise English; aim <=50 lines; use required factory exception marker when 51-100, never exceed 100.

## Numbered acceptance criteria
1. New artifacts, planning domain/evaluator and declared references exist and project validation passes.
2. Plan and rubric cover current/deferred scope, cross-layer design impact, revisions, feedback, and genuine independent evidence.
3. Generic planning cannot bypass UI specialist review; existing DAG/permissions/model policies remain identical.
4. Missing plan/review/rubric or wrong increment-plan-review owner/runner fails new focused checks; actual failing-before/passing-after regression output is recorded.
5. Contract plan linkage fields and entry/eval discovery parity are checked; remove a required linkage field in a regression and reject it.
6. Historical baseline files outside permitted scope remain unchanged; no state/journal reset or provider path escape.

## Execution and verification
Run provider preflight before provider writes using the recorded delta-plan and factory root; parent preflight evidence is available.
Implement tests first for new Python guards, record targeted red output, then minimal guard logic/canonical files; no unrelated verifier fixes yet.
Run python -B scripts/verify-harness.py; python -B scripts/test_verify_harness.py; python -B scripts/smoke-ux-skills.py; git diff --check.
Do not run --require-tracked against the normal index before new files are staged. No commit/push/deploy/publish.
Result status is ready-for-review or blocked; never issue final verdict. Report actual model/effort/invocation, paths, criteria mapping, commands/exits/outputs, deviations and checks not run.

