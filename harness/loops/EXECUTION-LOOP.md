# Execution loop

1. Read the current state, request, relevant local source, and evaluator.
2. For every screen planning task, route to `ui-ux-designer` before creating the plan or implementation. Pass the objective, users, evidence, existing UI, and constraints.
3. The specialist reads only applicable project design skills and writes a plan under `docs/ux/` using the screen-plan template.
4. The router collects the plan, source references, actual tool output, and checks not run.
5. The reviewer applies `ui-plan-review`. A failed required criterion blocks implementation until remedied.
6. Implement the reviewed plan only within the user's requested scope; return newly discovered screen decisions to the specialist.
7. Run affected task checks, append the verdict and evidence paths, and update the next action.
8. Write a short Change Report; archive substantial documents after their local source is complete.

Retry a bounded defect at most three times. Record stable defect keys and evidence for every attempt; preserve failed attempts and escalate unresolved blockers with a concrete next action.
Unchanged backend-only work uses the ordinary repository workflow. Never fabricate a delegation, a review, or a browser check.
