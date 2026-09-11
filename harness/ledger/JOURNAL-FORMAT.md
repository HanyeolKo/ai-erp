# Journal format

`journal.jsonl` is append-only UTF-8 JSON Lines. Each event records `event`, `unit`, `timestamp`, and relevant evidence.

Use `task-start`, `specialist-handoff`, `evaluation`, `defect`, `task-complete`, or `checkpoint` events.
An evaluation includes `evaluator`, `verdict`, `runner`, `owner`, `evidence`, and `checks_not_run`.
A defect includes a stable `defect_key`, observed behavior, attempt number, and next action.
A specialist handoff records the role, bounded task, actual output path, and the orchestrator's evidence of delegation; never invent an invocation ID.
Model events record selected model/effort, selection reason and evidence, context manifest/budget, output budget, fork/reuse choice, and provider token/cache usage availability; unavailable usage is `null`, not zero.
Escalation events reference `MODEL-ESCALATION.md`, expected/actual evidence, classification, prior attempts, parent authorization, next model/effort, remaining attempts, and stop condition. The ceiling is three total attempts; external/quota blockers stop immediately without escalation, and workers never self-escalate.
Raw command output belongs in a referenced evidence file. Record a pass only after its declared evidence exists.
Release events include increment/plan/release revisions, exact SHA, CI, authorization, environment, active/prior release, recovery outcome, and next action; never copy credentials or active-state data.
Existing entries, failed attempts, and source links must be preserved.
- External blockers reference `BLOCKER-REPORT`, external owner, resolution options, and exact resume checks; goal-mode continuation cannot override a blocked task.
