---
name: "ai-erp-eval"
description: "Review UI plans and implementation contracts against explicit, evidence-backed criteria."
---

# ai-erp-eval

Read `harness/harness-spec.json` and `harness/team/agents/reviewer.md`.
Use `increment-plan-review` for product increment plans, `ui-plan-review` for planning-only UI tasks, `task-review` for implementation contracts/results, and `release-review` for release preparation/outcomes.
The evaluator is `reviewer`; `router` is evidence runner; `product-planner`, `release-manager`, `implementer`, or `ui-ux-designer` is executor based on task type.
Read `harness/policies/VERIFICATION.json` and `harness/templates/TASK-RECORD.md`. Collect specialist or contract/result evidence and apply the selected risk tier: low and standard may use parent acceptance, standard review is optional Sol/medium, and high requires relevant integration checks plus independent Astra/high review.
Missing evidence cannot pass.
Verify `TASK-ASSIGNMENT.md` ownership and matching plan/contract revisions before review; product planning cannot replace the UI specialist gate.
Return the verdict, parent disposition, and checks not run to the orchestrator. Failed required criteria block progression until corrected through the execution loop. Release readiness is not deployment completion.
Require detailed model, effort, reason/evidence, context manifest/budget, output budget, fork/reuse choice, and usage availability for high/complex assignments; low/standard work may use the compact `TASK-RECORD.md` fields and inline evidence. Require `MODEL-ESCALATION.md` only for actual capability-based model escalation; same-model correction, ordinary handoff/reassignment, and same-cost alternatives do not require it. Parent owns escalation and the final Astra reviewer cannot be downgraded; Luna -> Terra -> Sol is the only executor ladder, with three total attempts including the initial attempt. Normal `increment-plan-review` and `ui-plan-review` use the read-only reviewer role at Sol/medium; high-risk plan/release review uses Astra/high. Quota or external blockers stop without escalation.

External gate: task-review and release-review fail required unresolved external prerequisites. Return a `BLOCKER-REPORT` for missing or unknown setup; goal-mode continuation and test doubles cannot relabel a blocked result as passed or deployed.
