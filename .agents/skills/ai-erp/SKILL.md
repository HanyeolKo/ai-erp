---
name: "ai-erp"
description: "Route AI ERP requests through the required UI specialist and task-review-driven implementation flow."
---

# ai-erp

1. Read `harness/harness-spec.json`, `harness/state/state.json`, and `harness/team/agents/router.md`.
2. For product planning, route to `product-planner` with `TASK-ASSIGNMENT.md`; accepted plans requiring screens then route to `ui-ux-designer` before drafting screen decisions or implementation.
3. For any code, test, or behavior-affecting configuration task, require a parent-owned contract; high/complex work uses `harness/templates/IMPLEMENTATION-CONTRACT.md`, while bounded low/standard work may use one `harness/templates/TASK-RECORD.md`; UI work also requires the specialist plan and passing `ui-plan-review` first.
4. Select `low`, `standard`, or `high` risk in `harness/policies/VERIFICATION.json`; the parent records a short reason and workers cannot downgrade it.
5. Collect specialist plans, implementer results, contract artifacts, and validation output before the applicable review or parent acceptance.
6. Apply proportionate checks: low uses applicable quick checks, standard uses changed-area tests with optional Sol/medium review, and high requires relevant integration checks plus independent Astra/high review.
7. Preserve unresolved routing/precondition gaps and return concrete blockers to the orchestrator.
8. Preserve local evidence, append the review result, update next action, and follow reporting policy for substantial documents.
9. Parent-controlled assignments and returns follow `harness/workflows/DELEGATION-PROTOCOL.md`; use one `TASK-RECORD.md` for low/standard work.
10. Release preparation follows accepted implementation evidence to `release-manager` and independent `release-review`; execution requires parent authorization.
11. Model policy is explicit: Astra owns top-level orchestration and final review; Sol/medium owns lower planning/design roles; Luna/high owns implementation and release execution. Parent-owned executor escalation follows Luna/high -> Terra/medium -> Sol/medium, never Astra execution; record `MODEL-ESCALATION.md`. Dispatch at most two workers at depth one with minimal bounded context.

External gate: declare mode and target-specific dependency readiness. If a required external prerequisite is missing or unknown, stop the affected task before dependent edits or execution and return a `BLOCKER-REPORT` to the parent. Goal-mode continuation does not override external prerequisites; a blocked task remains blocked until evidence resolves the blocker.
