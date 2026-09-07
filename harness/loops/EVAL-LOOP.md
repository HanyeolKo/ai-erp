# Task evaluation loop

1. Select the evaluator declared by the skill and keep the routed path.
2. For increment plans: use `increment-plan-review` with evidence from `harness/templates/INCREMENT-PLAN.md` and `harness/templates/INCREMENT-REVIEW.md`.
3. For UI plans: use `ui-plan-review` with evidence from `harness/templates/UI-REVIEW.md`.
4. For implementation contracts/results: use `task-review` when required by the selected risk tier or explicitly requested, with evidence from `harness/templates/IMPLEMENTATION-CONTRACT.md`, `harness/templates/IMPLEMENTATION-RESULT.md`, and `TASK-RECORD.md`.
5. For release preparation/outcomes: use `release-review` with evidence from `harness/templates/RELEASE-CONTRACT.md`, `RELEASE-RESULT.md`, and `RELEASE-REVIEW.md`.
6. Keep criterion evidence by path, actual outputs, and checks not run.
7. For harness changes, run `python scripts/verify-harness.py` and preserve its exit code and output.
8. Reviewer issues `pass`/`fail` only for a selected review gate; parent acceptance completes low/standard work, and high-risk work requires reviewer pass. All applicable criteria must pass, and only explicitly out-of-scope criteria may be N/A with justification.
9. Record stable defects and route the next action in the journal. Return immediately on a recorded external or required-model-capacity blocker; forward progression requires the applicable independent pass and parent acceptance.

A structural pass proves contract integrity, not runtime behavior completeness.

If a required external prerequisite is missing or unknown, stop the affected task before dependent edits or execution and return a BLOCKER-REPORT to the parent. Do not retry an unchanged external blocker; At most three total attempts apply only to repairable implementation defects, including the initial attempt and any escalation. External blockers stop immediately, consume no retry attempt, and cannot authorize model escalation or a model sweep. External or quota blockers stop immediately, consume no attempt, and cannot authorize escalation. Goal-mode continuation does not override external prerequisites; a blocked task remains blocked until evidence resolves the blocker.
