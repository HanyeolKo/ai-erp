# Implementation result

For bounded low/standard implementation work, record the minimal result in `TASK-RECORD.md`; use this detailed result for high/complex work or when the parent assignment requires it.

- Task id, matching contract path, and contract revision:
- Runtime model and invocation evidence:
  - Selected authorized rung: `gpt-5.6-luna`/`high` by default, explicitly selected `gpt-5.3-codex-spark`/`high`, or parent-authorized `gpt-5.6-terra`/`medium` or `gpt-5.6-sol`/`medium` with escalation evidence
  - Selected model:
  - `model_reasoning_effort`:
  - Invocation command/session evidence:
  - Model reason and selection/availability evidence:
  - Fallback/alternative reason, if Spark was explicitly selected:
- Input context manifest and context budget:
- Output budget:
- Fork/reuse choice and bounded-context rationale:
- Provider token/cache usage availability (null when unavailable; never infer zero):
- `MODEL-ESCALATION.md` path and parent authorization (if applicable):
- Status: `ready-for-review` or `blocked` (never final approval)
  - Blocking reason:
- External dependency status and mode; owner, sanitized evidence, and resume checks (unknown is never ready):
- Exact changed paths:
- Reviewed revision and evidence validity:
  - Reviewed revision or commit:
  - Evidence collected after the final edit: yes / no
  - Affected checks rerun after edits:
- Numbered acceptance criteria mapping to concrete diff/test evidence:
  1. Contract criterion → evidence path / result
  2. Contract criterion → evidence path / result
  3. Contract criterion → evidence path / result
- Regression evidence:
  - Failure-before-fix and pass-after-fix evidence, when a regression check was warranted:
  - If not run, conditional rationale and alternative evidence:
- Checks and evidence (actual command, exit code, output path):
- Checks not run:
- Deviations and uncertainties:
- Goal-mode limitation: continuation does not override external prerequisites; a blocked task remains blocked until evidence resolves the blocker.
- Next action for parent and applicable reviewer (if required by `harness/policies/VERIFICATION.json`):
- Release linkage or justified not-requested reason:
