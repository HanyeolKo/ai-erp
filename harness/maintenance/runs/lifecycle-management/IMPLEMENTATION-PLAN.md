<!-- document-budget exception: required-sequential-instruction | Sequential stages and shared preservation constraints form one parent plan. -->
# Lifecycle management implementation plan

> For agentic workers: use ai-erp-implement and parent-requested independent ai-erp-eval after each stage.
Goal: connect iterative product planning and design to frontend/backend development, validation, authorized release/recovery and feedback.
Architecture: retain core and the existing UI/implementation artifact DAG; add product-planner and release-manager. Product-planner writes scoped product plans, release-manager prepares/executes authorized release operations, router routes read-only, designer owns screens, implementer owns code, reviewer owns verdicts. The latest model-orchestration-contract.md supersedes earlier model/limit defaults.
Stack: English Markdown/JSON/TOML contracts and standard-library Python validation; existing CI/deployment commands are referenced, not changed.
Spec: docs/reports/harness-review-2026-09-07.md, user-approved sequential improvement and explicit planning/other warranted role separation.
Authorization: user requested the high-priority improvements in sequence; reuse this approval for the bounded local edits.

## Global constraints
- Preserve schema 1.2, core, Codex, all existing IDs, gates, reporting target, pinned sources and historical evidence.
- Preserve existing UI specialist prerequisites; no product screens are being designed or changed here.
- Keep canonical Markdown at most 50 lines where practical; 51-100 needs the factory exception marker; never exceed 100.
- Parent writes this contract and evidence; all behavior-affecting Markdown/config, code and tests are implementer-owned.
- No application code, deployment script/workflow, production execution, commit, push or remote publication by implementer.
- No adaptive memory, self-evaluation, learning, extra agents beyond the two requested role splits, DAG back edges or unsupported spec fields.
- Existing docs/reports files are preserved. Reader documents are Korean and archived after local completion.
- Exact allowed files are delta-plan.json paths plus this run's stage result/check artifacts; parent owns delta-plan and preservation records.
- Update common files first, run provider preflight, then copy changed skills byte-identically. Preserve root text outside the managed block.
- Preserve journal entries exactly and existing queue units; add new stage units. Parent owns final verdict/state acceptance.

## Stage 1: iterative product planning and design
- [ ] Add TASK-ASSIGNMENT and DELEGATION-PROTOCOL for parent dispatch, recipient acknowledgement, bounded execution, return, independent review, correction and parent acceptance.
- [ ] Add product-planner role, native wrapper and ai-erp-plan skill; add INCREMENT-PLAN, INCREMENT-REVIEW and INCREMENT-PLAN-RUBRIC; add INCREMENT-LIFECYCLE workflow.
- [ ] Add increment-plan-review (task/rubric, runner router, owner reviewer) and planning references to entry/eval/roles/loops.
- [ ] Link increment_id, plan_revision, plan/review evidence in IMPLEMENTATION-CONTRACT.
- [ ] Plan captures problem, users/value, priorities, current slice, deferred scope, assumptions, business/API/data/permissions/FE/BE effects and observable acceptance.
- [ ] Current implementation assumptions must be resolved; future requirements may remain deferred. Changed plans invalidate affected downstream evidence.
- [ ] Product-planner returns plans to parent for independent reviewer; screen decisions still require designer then ui-plan-review. Preserve the existing DAG.
- [ ] Add focused regressions for missing planning artifacts/evaluator ownership; run them failing before implementing the guard.
- [ ] Run structural/regression/smoke checks and return stage-1 result for independent review before stage 2.

## Stage 2: development, release and feedback linkage
- [ ] Add release-manager role, native wrapper and ai-erp-release skill; add INCREMENT-RECORD, RELEASE-CONTRACT, RELEASE-RESULT, RELEASE-REVIEW and RELEASE-REVIEW-RUBRIC.
- [ ] Add VERIFICATION-MATRIX and RELEASE-FLOW; add release-review task evaluator with router runner/reviewer owner.
- [ ] Connect increment/plan revision, FE/BE contract and dependencies, API/data design, reviewed commit, CI run and release evidence.
- [ ] Use existing phase/queue/next_action and journal links, preserve historical records and only read the relevant increment on resume.
- [ ] Distinguish implementation-reviewed, release-ready, observing, deployed, rolled-back and operator-action-required states.
- [ ] Release-manager manages deployment through existing tools under the complete parent release contract and applicable authorization; implementer still cannot deploy or own verdicts.
- [ ] Require same-SHA successful CI evidence for manual release; main publication may trigger deployment and must be within authorization.
- [ ] Record environment/SHA, CI, approval, DB compatibility, backup, smoke scope, manifest pointer, active release and recovery next action.
- [ ] Do not copy server state or secrets into harness. App rollback is not DB restore; insecure resolved-IP smoke is not public DNS/TLS validation.
- [ ] Matrix selects FE, BE/API, DB/migration and deploy checks from actual CI. New migration means inventory/checksum/test review.
- [ ] Feedback revises the next bounded product plan, not the harness. Planning-only/non-release tasks record justified N/A without fabricated release.
- [ ] Add failing then passing regressions for release artifacts/evaluator ownership and linkage fields; run checks and independent review.

## Stage 3: verifier guards and reader guidance
- [ ] PRIORITY: implement model-orchestration-contract.md before resuming stage 3; explicit Astra decisions/review, Luna execution, Spark explicit alternative, bounded minimal context and actual usage availability.
- [ ] Independently review and accept the priority model contract; then apply stage-3-contract-r3.md to the remaining stage-3 criteria.
- [ ] Apply user-requested external-dependency fail-stop, blocker/resolution/resume artifacts and goal-mode boundaries under stage-3-contract-r2.md before the remaining guard/report work.
- [ ] Require Spark/high in saved implementer wrapper; preserve Luna/high only as invocation fallback policy with actual result evidence.
- [ ] Protect UI reviewer-to-implementer prerequisites and exact required artifacts, including negative condition mutation.
- [ ] Require router/reviewer read-only independent of three-way parity. Keep designer/implementer expected write access.
- [ ] Make success output say structural policy checks, not proof of actual model invocation or deployed state.
- [ ] Complete focused regressions for the two reported gaps and permission drift; retain unrelated regression coverage.
- [ ] Update managed root guidance, ENVIRONMENT, team/recovery references, README and Korean operating guide for final lifecycle.
- [ ] Preserve historical English reports; put new Korean reader report under docs/reports with no broad validator language exemption.
- [ ] Review synthetic planning/non-UI/UI/release success/failure/resume scenarios using the declared rubrics, clearly labeled simulations.
- [ ] Run final project/regression/smoke/diff/preservation checks and independent task-review; parent records final evidence and archive sync.

## Acceptance and evidence
Every stage returns matching task/revision, selected model/effort/invocation, exact changed paths, numbered acceptance/evidence, commands/exits/output and checks not run.
Use python -B scripts/verify-harness.py, python -B scripts/test_verify_harness.py, python -B scripts/smoke-ux-skills.py, and git diff --check.
Run --require-tracked after adding the new harness files to an isolated temporary index or explicitly staging only authorized artifacts; do not create a commit.
Existing Factory 0.3.0 optional model-key failure remains documented; provider-path preflight must pass.
Final acceptance is all applicable criteria passed by independent reviewer plus no unauthorized diff/preservation loss.
