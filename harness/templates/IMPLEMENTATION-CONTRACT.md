# Implementation contract

For bounded low/standard implementation work, `TASK-RECORD.md` may carry the minimal assignment, contract, result, and parent disposition. Complete this detailed contract for high/complex work and specialist/release gates.

Complete this contract before any implementation worker starts.

- Task id, increment id, plan revision, contract revision, base Git state, and parent decision owner:
- Task assignment path and status; plan/review paths and statuses:
- Objective and requested changeset:
- Current behavior and source evidence:
- Chosen design and routing rationale:
- Exact permitted files, symbols, and commands:
- Preserved user changes and explicit exclusions:
- Interfaces, data, permissions, state, and test rules:
- External dependency readiness: ready / blocked / justified N/A; mode: none / offline-contract-only / live-integration; provider, target, contract/version, owner, evidence date, safe check, expected result, and dependent work:
- Verification strategy:
  - Defect evidence (errors/recent changes/reproduction), including configuration defects:
  - Root-cause hypothesis and focused change; N/A only for non-defect wording/configuration maintenance:
  - If reproduction is infeasible, reason and alternative diagnostic evidence:
  - Conditional regression check: required / not warranted / infeasible
  - If required: failure-before-fix and pass-after-fix evidence:
  - If not run, rationale and alternative evidence:
- Review and evidence validity:
  - Reviewed revision or commit:
  - Affected checks to rerun after edits:
- Numbered observable acceptance criteria (each required):
  1.
  2.
  3.
- Validation commands and expected outcomes:
- Model reason and selection/invocation evidence requirements:
- Input context manifest and context budget:
- Output budget:
- Fork/reuse choice and bounded-context rationale:
- Provider token/cache usage availability (record null when unavailable):
- `MODEL-ESCALATION.md` path and parent authorization (if applicable):
- UI prerequisite:
  - Screen plan required: yes / no
  - Specialist plan path and independent `ui-plan-review` evidence:
- Unresolved decisions or assumptions: none permitted; return to parent for revision.
- Missing or unknown required external setup: stop before dependent edits or execution, return `BLOCKER-REPORT`, and do not retry unchanged external blockers.
- Checks not run:
- Parent ready-to-implement determination: ready / blocked
- Release linkage or justified not-requested reason:
