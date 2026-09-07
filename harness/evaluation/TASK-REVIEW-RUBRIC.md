# Implementation task-review rubric

Runner: `router`. Verdict owner: the independent reviewer only when the selected policy gate requires review. Executor: `implementer` for UI and non-UI implementation.
Use `harness/templates/IMPLEMENTATION-CONTRACT.md` and `harness/templates/IMPLEMENTATION-RESULT.md`. General product planning uses `increment-plan-review`; screen decisions separately use `ui-plan-review`; release preparation/outcomes use `release-review`.
Read `harness/policies/VERIFICATION.json` and `harness/templates/TASK-RECORD.md`. Product plans still require `increment-plan-review`; screen decisions still require `ui-plan-review`; release work still requires `release-review`. Low and standard implementation work may complete with applicable checks and parent acceptance; standard Sol/medium review is optional with one N/A reason when unused. High-risk work requires relevant integration checks and an independent reviewer using Astra/high. A failed or pending applicable criterion cannot pass.

| Criterion | Required evidence |
| --- | --- |
| Parent contract | Matching task/revision, objective, exact scope, acceptance criteria, no unresolved decisions, and parent ready determination |
| Assignment linkage | Assignment identifies sender, recipient, plan/contract revisions, exact scope, acceptance, permissions, and return conditions |
| Role boundaries | Correct router/designer/reviewer/implementer route; implementer has execution+verification only and owns no evaluator or verdict |
| Model invocation | High/complex work requires the actual authorized rung: `gpt-5.6-luna`/high by default, explicitly selected `gpt-5.3-codex-spark`/high alternative, or parent-authorized `gpt-5.6-terra`/medium or `gpt-5.6-sol`/medium with capability-escalation evidence in `MODEL-ESCALATION.md` when applicable. Low/standard work may use compact `TASK-RECORD.md` model/effort, reason, and inline evidence; static config is not proof |
| Scope safety | Diff paths fit the contract and all code/tests/behavior configuration are covered, regardless of domain path |
| Method consistency | Contracted approach and declared commands were followed or deviation is explained |
| Verification | Actual commands, exit codes, output paths, and checks not run are recorded |
| UI gate compliance | UI behavior has specialist plan and passing independent `ui-plan-review` evidence before implementation |
| Acceptance mapping | Every numbered criterion maps to concrete diff, test, or check evidence |
| Readiness | Result is `ready-for-review` or `blocked`; reviewer owns required review verdicts and the parent owns acceptance |

All required criteria must pass. Only an explicitly out-of-scope criterion may be marked N/A with justification.

External readiness is part of every applicable contract: if a required external prerequisite is missing or unknown, stop the affected task before dependent edits or execution and return a BLOCKER-REPORT to the parent. A blocked result cannot pass through a mock, omitted check, alternate provider, or goal-mode continuation.
Quota/capacity blockers are also not model incapability evidence: stop without escalation or model sweep. Parent-owned executor escalation is Luna/high -> Terra/medium -> Sol/medium, with three total attempts including the initial attempt. High-risk and mandatory specialist/release gates retain separate required review; low/standard work may complete by parent acceptance. Astra never executes.
