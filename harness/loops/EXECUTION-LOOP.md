# Execution loop

1. Read the current state, parent request, assignment, and evaluator.
2. Route product planning to `product-planner`, then to `reviewer` via `increment-plan-review`; accepted screen planning then routes to `ui-ux-designer` and `ui-plan-review`.
3. For any code, test, or behavior-affecting configuration, select the parent-owned verification tier and route to `implementer`; high/complex work writes or selects an `IMPLEMENTATION-CONTRACT.md`, while bounded low/standard work may use one compact `TASK-RECORD.md` for assignment, contract, result, and parent disposition. UI work requires the reviewed specialist plan first.
4. For release work, require accepted implementation evidence and a parent release assignment, then route preparation to `release-manager` and independent `release-review`; execution still requires authorization.
5. Use relevant local source, evidence, and scoped specialist guidance.
6. Collect reproducible tool output and checks not run.
7. Reviewer applies the declared evaluator. A failed required criterion blocks forward progress.
8. Run requested checks, write result artifacts, and update `harness/state/state.json` next action.
9. Follow `DELEGATION-PROTOCOL.md`; allow at most three total attempts for a repairable implementation task, including the initial attempt and any escalation, and preserve failed attempts. External or quota blockers stop immediately, consume no attempt, and cannot authorize escalation.

If a required external prerequisite is missing or unknown, stop the affected task before dependent edits or execution and return a BLOCKER-REPORT to the parent. Do not retry an unchanged external blocker; At most three total attempts apply only to repairable implementation defects, including the initial attempt and any escalation. External blockers stop immediately, consume no retry attempt, and cannot authorize model escalation or a model sweep. External or quota blockers stop immediately, consume no attempt, and cannot authorize escalation. Goal-mode continuation does not override external prerequisites; a blocked task remains blocked until evidence resolves the blocker.
