# Journal format

`journal.jsonl` is append-only UTF-8 JSON Lines. Each event records `event`, `unit`, `timestamp`, and relevant evidence.

Use `task-start`, `specialist-handoff`, `evaluation`, `defect`, `task-complete`, or `checkpoint` events.
An evaluation includes `evaluator`, `verdict`, `runner`, `owner`, `evidence`, and `checks_not_run`.
A defect includes a stable `defect_key`, observed behavior, attempt number, and next action.
A specialist handoff records the role, bounded task, actual output path, and the orchestrator's evidence of delegation; never invent an invocation ID.
Raw command output belongs in a referenced evidence file. Record a pass only after its declared evidence exists.
Existing entries, failed attempts, and source links must be preserved.
