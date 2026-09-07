# Task record

- Task/scope and acceptance:
- Assignment acknowledgement and status: assigned / acknowledged / running / ready-for-review / blocked:
- Risk tier and short parent reason: `low` / `standard` / `high`:
- Applicable gate: parent acceptance / optional Sol review / required Astra review:
- Selected role, model, and effort:
- Contract/assignment revision and parent decision owner:
- Changed paths and exclusions:
- Relevant checks (actual command, cwd, exit, stdout/stderr; brief output may be inline for trivial checks, otherwise link the raw evidence path):
- Checks not run and reason:
- Parent disposition: pending / accepted / changes-requested / blocked:
- Independent review: required / optional / N/A with one reason; evidence path:
- Blockers, deviations, and next action:
- `MODEL-ESCALATION.md` path (only for actual capability escalation):

Low-risk work may finish with applicable quick checks and parent acceptance; for trivial work this compact record may be carried inline in the task message or response. Standard-risk work uses changed-area tests; Sol/medium review is optional only for uncertainty, cross-module impact, or explicit request. High-risk work requires relevant integration checks and an independent Astra/high review before acceptance. Workers never downgrade risk or self-approve.

For low/standard work this record is the minimal parent assignment, contract, and result: it carries scope, acceptance, acknowledgement, selected role/model/effort, applicable checks, blockers, and parent disposition. Detailed budget, context, usage, and separate assignment/result artifacts remain required for high/complex work.
