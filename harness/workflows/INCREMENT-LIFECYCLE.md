# Increment lifecycle

1. Parent accepts the problem and dispatches a complete `TASK-ASSIGNMENT.md`.
2. Router bounds the task; product-planner acknowledges and writes `INCREMENT-PLAN.md`.
3. Parent sends the plan to reviewer for independent `increment-plan-review`.
4. For screen work, parent then dispatches the accepted plan to `ui-ux-designer` and requires `ui-plan-review`.
5. Parent writes the implementation contract linking increment and plan/review revisions before implementation.
6. Implementer returns evidence; parent applies `VERIFICATION.json` and requests `task-review` only when required or explicitly selected; parent accepts or requests a bounded revision.
7. Any plan revision marks affected downstream contracts, reviews, tests, and release evidence stale.
8. A planning-only increment may complete after applicable independent `increment-plan-review` passes and the parent records acceptance; no implementation or release evidence is fabricated.
9. A small fix may reuse a valid increment ID, plan revision, and current review when validity is recorded. If scope or requirements change, write a scoped amendment, increment the plan revision, repeat review, and invalidate affected downstream evidence.
10. Accepted implementation evidence may enter release preparation only through the parent assignment and `release-manager`; release execution requires independent readiness and authorization.
