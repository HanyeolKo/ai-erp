---
name: "ai-erp-plan"
description: "Create bounded product increment plans with cross-layer requirements and independent review evidence."
---

# ai-erp-plan

1. Read `harness/harness-spec.json`, `harness/team/agents/product-planner.md`, and the parent assignment.
2. Confirm matching task, increment, plan, and contract revisions plus source evidence before planning.
3. Write only the parent-scoped `INCREMENT-PLAN.md`; screen structure waits for `ui-ux-designer`.
4. Capture problem, users/value, priorities, current/deferred scope, business/API/data/permission/FE/BE effects, dependencies, assumptions, and observable acceptance.
5. Return the plan to the parent for the required `increment-plan-review`; normal plan review uses the read-only reviewer role at Sol/medium, while a high-risk plan uses Astra/high. Do not own routing, implementation, release, or verdicts.
6. Record stale downstream evidence when a plan revision changes and follow the assignment/delegation protocol.
7. Record release requirements for the parent; release execution and evidence belong to `release-manager`.
8. Use Sol/medium for planning and record the model, reason, context manifest/budget, output budget, fork/reuse choice, and usage availability. Parent-owned escalation is recorded in `MODEL-ESCALATION.md`; the planner cannot self-escalate.

External gate: record provider/service, target, contract/version, managed settings, owner, evidence date, safe check, expected result, and status for dependencies. Unknown is never ready; planning may retain a future unresolved dependency, while required current setup blocks implementation.
