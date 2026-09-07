# Stage 1 contract amendment, revision 3

Task: lifecycle-management-stage-1. Parent: /root. Status: ready-to-implement.
This additive amendment supersedes revision 2 only where explicitly stated; read stage-1-contract-r2.md plus this file as contract revision 3.
User explicitly requested clear task delegation/assignment in addition to separate planning and release agents.

## Additional permitted files
- Create harness/templates/TASK-ASSIGNMENT.md.
- Create harness/workflows/DELEGATION-PROTOCOL.md.
- Modify harness/team/agents/implementer.md only to reference the common assignment/return protocol, preserving all existing restrictions and model policy.
- Stage-1-result.md/checks.json must identify contract revision 3.

## Required assignment protocol
- Parent owns task acceptance, architecture decisions, authorized scope and actual delegation. Router classifies and prepares a bounded assignment but does not independently expand scope.
- Required task assignment fields: task_id, increment_id or explicit infrastructure-maintenance applicability, plan_revision, contract_revision, sender/decision owner, recipient role, source/base Git evidence.
- Include prerequisite plan/review paths and statuses, exact allowed files/commands, exclusions, interfaces/dependencies, requested outputs, numbered acceptance and validation commands/expected outcomes.
- Include executor/model/effort requirements and actual invocation evidence, reviewer and evidence runner, permission already granted, ungranted external actions and stop/return conditions.
- Parent dispatches only a complete current assignment. Recipient acknowledges matching task/revision and scope; missing/stale/conflicting prerequisites return blocked before changes.
- States: assigned -> acknowledged -> running -> ready-for-review -> reviewed -> complete; blocked or changes-requested retain evidence and return to parent for a new/revised assignment.
- A worker returns actual changed paths/artifacts, acceptance mapping, checks/exits/output, deviations and unresolved risks. It never self-approves final completion.
- Parent requests independent reviewer. Reviewer supplies evidence-based verdict; parent accepts or sends bounded corrections with revision tracking.
- Workers do not re-delegate or change recipient/scope/architecture/permission. A handoff graph is artifact prerequisites; actual messages and dispatch are parent-controlled.
- Retry only the affected bounded work with at most three rounds and preserved failed evidence, then return the blocker; never loop with a stale contract.
- Reference the protocol from entry skill, router, planner, implementer, reviewer and execution loop without repeating a long checklist in every file.
- Record actual task/role/contract and tool-returned dispatch identifiers in run evidence/journal when available; no fabricated native invocation.

## Additional routing and acceptance
- Add product-planner -> ui-ux-designer artifact edge for accepted product planning that requires screen design; parent dispatches it after independent increment-plan-review.
- Product-planner -> reviewer remains product-plan-only; it never substitutes for ui-plan-review.
- Add regressions rejecting missing assignment template/protocol and omitted essential contract revision/recipient/acceptance fields.
- Structural task review verifies assignment ownership and role boundaries. Keep workflow instruction-based; do not build an autonomous scheduler or claim command-level runtime enforcement.
- All revision-2 preservation, code scope, preflight, English budget, tests and no-external-execution requirements remain.
