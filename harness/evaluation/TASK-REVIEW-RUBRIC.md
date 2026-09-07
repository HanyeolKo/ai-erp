# Implementation task-review rubric

Runner: `router`. Verdict owner: `reviewer`. Executor: `implementer` for UI and non-UI implementation.
Use `harness/templates/IMPLEMENTATION-CONTRACT.md` and `harness/templates/IMPLEMENTATION-RESULT.md`.
For planning-only tasks, use `ui-plan-review`; implementation criteria are explicitly N/A. A required failed or pending criterion cannot pass.

| Criterion | Required evidence |
| --- | --- |
| Parent contract | Matching task/revision, objective, exact scope, acceptance criteria, no unresolved decisions, and parent ready determination |
| Role boundaries | Correct router/designer/reviewer/implementer route; implementer has execution+verification only and owns no evaluator or verdict |
| Model invocation | Actual selected `gpt-5.3-codex-spark` or approved `gpt-5.6-luna`, `high` reasoning, invocation/session evidence, and fallback reason when used; static config is not proof |
| Scope safety | Diff paths fit the contract and all code/tests/behavior configuration are covered, regardless of domain path |
| Method consistency | Contracted approach and declared commands were followed or deviation is explained |
| Verification | Actual commands, exit codes, output paths, and checks not run are recorded |
| UI gate compliance | UI behavior has specialist plan and passing independent `ui-plan-review` evidence before implementation |
| Acceptance mapping | Every numbered criterion maps to concrete diff, test, or check evidence |
| Readiness | Result is `ready-for-review` or `blocked`; only the independent reviewer issues the verdict and the parent accepts/merges |

All required criteria must pass. Only an explicitly out-of-scope criterion may be marked N/A with justification.
