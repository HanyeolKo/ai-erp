---
name: ai-erp-eval
description: "Review ERP screen plans against explicit user-flow, state, accessibility, and specialist-routing criteria."
---

# ai-erp-eval

Read `harness/harness-spec.json` and `harness/team/agents/reviewer.md`.
Use evaluator `ui-plan-review`, `harness/evaluation/UI-PLAN-RUBRIC.md`, and `harness/templates/UI-REVIEW.md`.
The designer is executor, router is evidence runner, and reviewer owns the verdict.
Inspect actual specialist handoff and local plan evidence; missing evidence cannot pass.
Mark each applicable criterion with evidence and record stable defect keys for failures.
Return the verdict and checks not run to the orchestrator. Failed required criteria block implementation until addressed through the execution loop.
