---
name: "ai-erp"
description: "Route AI ERP requests through the required UI specialist and task-review-driven implementation flow."
---

# ai-erp

1. Read `harness/harness-spec.json`, `harness/state/state.json`, and `harness/team/agents/router.md`.
2. For every screen planning request, route to `ui-ux-designer` before drafting the plan or implementation.
3. For any code, test, or behavior-affecting configuration task, require an explicit contract, then route through `ai-erp-implement` and `harness/templates/IMPLEMENTATION-CONTRACT.md`; UI work also requires the specialist plan and passing `ui-plan-review` first.
4. Collect specialist plans, implementer results, contract artifacts, and validation output before invoking review.
5. Submit results to `reviewer` via `task-review` and require a passing review before implementation-ready state.
6. Preserve unresolved routing/precondition gaps and return concrete blockers to the orchestrator.
7. Preserve local evidence, append the review result, update next action, and follow reporting policy for substantial documents.
