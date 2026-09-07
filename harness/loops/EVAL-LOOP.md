# Task evaluation loop

1. Select the evaluator declared by the skill and keep the routed path.
2. For UI plans: use `ui-plan-review` with evidence from `harness/templates/UI-REVIEW.md`.
3. For implementation contracts/results: use `task-review` with evidence from `harness/templates/IMPLEMENTATION-CONTRACT.md` and `harness/templates/IMPLEMENTATION-RESULT.md`.
4. Keep criterion evidence by path, actual outputs, and checks not run.
5. For harness changes, run `python scripts/verify-harness.py` and preserve its exit code and output.
6. Only reviewer may issue `pass`/`fail`; all required criteria must pass, and only explicitly out-of-scope criteria may be marked N/A with justification.
7. Record stable defects, route next action in journal, and stop only on a parent-ready state.

A structural pass proves contract integrity, not runtime behavior completeness.
