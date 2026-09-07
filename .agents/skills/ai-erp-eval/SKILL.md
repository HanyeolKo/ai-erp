---
name: "ai-erp-eval"
description: "Review UI plans and implementation contracts against explicit, evidence-backed criteria."
---

# ai-erp-eval

Read `harness/harness-spec.json` and `harness/team/agents/reviewer.md`.
Use `ui-plan-review` for planning-only UI tasks and `task-review` for implementation contracts/results.
The evaluator is `reviewer`; `router` is evidence runner; `implementer` or `ui-ux-designer` is executor based on task type.
Collect specialist or contract/result evidence and route it to the independent reviewer with concrete check outputs.
Missing evidence cannot pass.
Return the verdict and checks not run to the orchestrator. Failed required criteria block progression until corrected through the execution loop.
