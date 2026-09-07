# Screen-plan review rubric

Runner: `router`. Verdict owner: `reviewer`. Executor: `ui-ux-designer`.
Use `harness/templates/UI-REVIEW.md`; inspect actual local evidence rather than checking headings alone.

| Criterion | Required evidence |
| --- | --- |
| Specialist routing | Actual handoff to `ui-ux-designer` and its resulting local plan; no invented invocation evidence |
| User and task | Named users, goals, entry points, assumptions, and in-scope/out-of-scope decisions |
| Workflow | Primary path, navigation, required steps, permissions, and recovery paths |
| Screen contract | Content hierarchy, controls, data, selection/filter/sort behavior, and responsive behavior where relevant |
| State coverage | Initial, loading, empty, partial, error, success, disabled, and permission states, or explicit justified exclusions |
| Accessibility | Keyboard flow, focus, semantic names, contrast, feedback, and assistive-technology considerations |
| Design rationale | Existing product conventions and selected vendor guidance tied to concrete decisions |
| Validation | Observable acceptance criteria, planned checks, open decisions, and checks not yet run |
| Ownership | Canonical local path, source references, and archive disposition for the substantial document |

Each applicable criterion must be `pass` with a cited section or check. An unsupported criterion is `fail`; a justified exclusion is `not-applicable`.
Unresolved required failures block implementation. Cosmetic preference alone is not a failure; explain the user impact.
Re-review only affected criteria after a bounded revision, while retaining previous evidence and defects.
A plan review does not certify browser implementation; code changes require appropriate frontend and browser checks.
