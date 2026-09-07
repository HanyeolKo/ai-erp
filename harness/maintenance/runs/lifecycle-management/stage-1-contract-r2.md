<!-- document-budget exception: required-sequential-instruction | Parent-approved planning-role contract and acceptance criteria form one ordered unit. -->
# Stage 1 implementation contract, revision 2

Task: lifecycle-management-stage-1. Parent owner: /root. Parent ready-to-implement: ready.
Base: main/a7f9b329d676a5babd6feae4b95043b5010a506d, preserved docs/reports and run evidence.
Supersedes unexecuted stage-1-contract.md revision 1 after the explicit user request for a planning agent.
Objective: install a distinct product-planner and iterative product planning/design before bounded implementation.
Source: approved review report and IMPLEMENTATION-PLAN.md; use this revision if older documents conflict.
Decision: five roles after this stage; release-manager is stage 2. Keep core, Codex and all existing role boundaries.
UI prerequisite: no product screen design/code changed; future screen decisions always route to ui-ux-designer.
Unresolved decisions: none. Execute using native ai-erp-implementer (Spark/high); no re-delegation.
Parent invocation record will be supplied after spawn. Never claim a model identity beyond provided tool metadata.

## Exact permitted files
- Create harness/team/agents/product-planner.md and .codex/agents/ai-erp-product-planner.toml.
- Create harness/skills/ai-erp-plan/SKILL.md and .agents/skills/ai-erp-plan/SKILL.md.
- Create harness/templates/INCREMENT-PLAN.md, harness/templates/INCREMENT-REVIEW.md, harness/evaluation/INCREMENT-PLAN-RUBRIC.md, harness/workflows/INCREMENT-LIFECYCLE.md.
- Modify harness/harness-spec.json, harness/HARNESS.md, harness/team/TEAM-ARCHITECTURE.md.
- Modify harness/team/agents/router.md and harness/team/agents/reviewer.md; preserve their access/model/capabilities.
- Modify harness/skills/ai-erp/SKILL.md, harness/skills/ai-erp-eval/SKILL.md and only these two existing .agents skill projections.
- Modify harness/loops/EXECUTION-LOOP.md, harness/loops/EVAL-LOOP.md, harness/templates/IMPLEMENTATION-CONTRACT.md, harness/evaluation/TASK-REVIEW-RUBRIC.md.
- Modify scripts/verify-harness.py and scripts/test_verify_harness.py for new role/planning coverage only.
- Modify only the harness-managed block of AGENTS.md, preserving all outside content.
- Add stage-1-result.md and stage-1-checks.json in this run directory. Parent owns other run artifacts.
- Exclude state/journal, historical evidence, existing reader reports, app/deploy files, vendor, other .codex files.

## Role and interface decisions
- New product-planner: execution lane, workspace-write, deep tier, capability product-planning, domain planning.
- Planning domain paths [docs/planning], coordinator product-planner. Add planning domain to router/reviewer and entry/eval/plan skills as appropriate.
- Planner only writes parent-scoped planning documents. It owns problem/goals/priorities/business rules and cross-layer requirements; no screen/layout/interaction decisions, code/tests/config edits, releases, routing or verdicts.
- Native planner inherits model/effort (omit both keys), references canonical role using existing native wrapper format.
- Add ai-erp-plan: kind domain, entry_agent product-planner, domains [planning], evaluator increment-plan-review.
- Add router -> product-planner and product-planner -> reviewer artifact edges for product planning; never use them to bypass ui-plan-review on screens.
- Existing router/designer/reviewer/implementer edges and their UI prerequisites remain intact. Each stage returns to parent, not recursive delegation.
- Add increment-plan-review: task/rubric, runner router, owner reviewer, plan rubric/review artifact command.
- Every applicable criterion must pass; missing/stale/failed/pending required evidence fails. Keep all existing evaluator IDs and pass semantics.
- task-review routes general planning to increment-plan-review; planning involving screen decisions additionally requires ui-plan-review.

## Planning content and state rules
- Plan fields: increment_id, plan_revision, source evidence, problem/users/value, priorities, current/deferred scope, business/API/data/permission/FE/BE impact, dependencies, assumptions, acceptance, reviews, feedback.
- Product planning records functional/API/data requirements; parent owns unresolved architecture choices. Screen structure waits for ui-ux-designer.
- Current implementation decisions must be resolved; future deferred requirements do not block a ready slice.
- A changed plan revision marks affected downstream contracts/reviews/tests/releases stale and requires impact-based re-review.
- Review records matching ID/revision, evaluator, actual independent reviewer, criterion evidence, open items, verdict and checks_not_run.
- Implementation contract adds matching increment_id, plan_revision, plan/review links and preserves existing required fields.
- Workflow permits planning-only completion and reuse/amendment of a valid plan for small fixes; no fabricated implementation/release evidence.
- Keep English canonical Markdown <=50 lines normally; 51-100 needs the exact factory exception marker.

## Numbered acceptance criteria
1. Exactly five roles and nine skills after this stage; planner canonical/native metadata and skill parity pass.
2. Increment plan/review/rubric cover scope, cross-layer requirements, revisions, feedback and independent evidence.
3. Planner cannot perform screen decisions/code/verdict/release; existing specialist/implementation routes remain valid.
4. Missing planning artifacts, incorrect plan skill role/evaluator and wrong evaluator owner/runner fail focused tests.
5. Required implementation plan linkage removal fails; test new Python guards red before green.
6. No unrelated file changes, historical evidence loss, root-outside-block edit, or state reset.

## Execution and evidence
Set spec.harness.construction_receipt to this run's delta-plan.json before preflight. The parent's earlier preflight failed only because the old pointer remained.
Change common spec/canonical files first; then run:
python -B <resolved-factory-root>/scripts/validate_runtime_neutral.py . --provider-path-preflight --construction-mode improve --delta-plan harness/maintenance/runs/lifecycle-management/delta-plan.json
Only project adapters after successful preflight; use approved sandbox escalation for protected provider files if required, never bypass path checks.
Run python -B scripts/verify-harness.py; python -B scripts/test_verify_harness.py; python -B scripts/smoke-ux-skills.py; git diff --check.
New files are not indexed yet; do not use --require-tracked against normal index, commit, push, deploy or publish.
Return ready-for-review or blocked with actual invocation/model/effort metadata, changed paths, numbered evidence, command exits/output and checks not run. No self-verdict.
