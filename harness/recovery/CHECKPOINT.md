# Checkpoint

Before pausing, update `harness/state/state.json` with phase, unit status, evaluator, and a concrete non-empty next action.
Append actual findings, local evidence paths, and checks not run to `harness/ledger/journal.jsonl`.
For high/complex code work, preserve the specialist handoff and required review state for screen tasks, plus implementation contract revision, selected verification tier, selected model invocation evidence and reason, context manifest/budget, output budget, fork/reuse choice, usage availability, any `MODEL-ESCALATION.md`, result status, attempt count, and upper-review state. Bounded low/standard code work may preserve its compact `TASK-RECORD.md` instead.
For release work, preserve increment/contract/release revisions, exact reviewed SHA, same-SHA CI, authorization, environment, DB and recovery boundaries, release state, observation, and next owner/action.
Do not restart completed work or infer a reviewer pass from partial output.
- Preserve any external blocker, owner, sanitized evidence, affected scope/current diff, resolution options, and exact resume checks; goal-mode continuation cannot override it.
On resume, read `harness/HARNESS.md`, current state, and the latest journal before opening relevant source/evidence.
