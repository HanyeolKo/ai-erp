# Task evaluation loop

The executor is `ui-ux-designer`, the evidence runner is `router`, and the verdict owner is `reviewer`.

1. Select the evaluator declared by the task's skill; do not substitute a weaker check.
2. Collect specialist handoff evidence, local plan paths, source references, and actual check output.
3. For screen planning, use `harness/evaluation/UI-PLAN-RUBRIC.md`. Mark each criterion `pass`, `fail`, or justified `not-applicable` with evidence.
4. For harness changes, run `python scripts/verify-harness.py` and preserve its exit code and output.
5. Only the reviewer issues the task verdict. All required criteria must pass; missing evidence is not a pass.
6. Record defect keys, unrun checks, and the next action in the append-only journal.

A structural pass proves contract integrity, not the quality of a screen or real delegation. A screen-plan pass is not proof of browser behavior or backend correctness.
