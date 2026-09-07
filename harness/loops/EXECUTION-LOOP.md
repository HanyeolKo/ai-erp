# Execution loop

1. Read the current state, parent request, and evaluator.
2. Route screen planning to `ui-ux-designer`, then to `reviewer` via `ui-plan-review`.
3. For any code, test, or behavior-affecting configuration, write or select an `IMPLEMENTATION-CONTRACT.md`, route to `implementer`, then return results to the parent for independent `task-review`; UI work requires the reviewed specialist plan first.
4. Use relevant local source, evidence, and scoped specialist guidance.
5. Collect reproducible tool output and checks not run.
6. Reviewer applies the declared evaluator. A failed required criterion blocks forward progress.
7. Run requested checks, write result artifacts, and update `harness/state/state.json` next action.
8. Retry bounded defects at most three times and preserve failed attempts.
