# Model orchestration amendment, revision 3

Task: lifecycle-management-model-orchestration. Parent: /root. Status: ready-to-implement.
Read model-orchestration-contract.md and r2 with this amendment as effective revision 3; this file supersedes conflicting model allocation/no-upgrade wording.
Source: user clarified that only highest-level orchestration/final judgment needs Astra; lower agents may use medium effort or Sol. The final clarification sets the worker escalation ladder to Luna -> Terra -> Sol, with failed Sol work triggering orchestrator self-review, not Astra execution.

## New defaults and escalation
- Keep root orchestrator and final independent reviewer gpt-6-astra/high.
- Set router, product-planner and ui-ux-designer defaults to gpt-5.6-sol/medium. Their existing planning/control responsibilities, tiers, access and UI prerequisites remain.
- Keep implementer and release-manager defaults gpt-5.6-luna/high; Spark/high remains an explicitly selected bounded implementation alternative.
- The parent Astra orchestrator owns escalation decisions. Workers and the Sol router may report a problem/proposal but cannot select a higher model themselves.
- For a clear complete contract that an executor demonstrably misunderstands or cannot correctly carry out, parent may reassign the affected scope using the next sufficient rung: Luna/high (or explicit Spark/high alternative) -> gpt-5.6-terra/medium -> gpt-5.6-sol/medium. No delegated execution or lower planning/design escalation reaches Astra.
- A lower role already using Sol is at the ceiling. Failed Sol work returns to the top-level Astra orchestrator for self-review of assignment clarity, decomposition, source/context sufficiency, interfaces, prerequisites and acceptance criteria; do not spawn an Astra executor.
- Self-review may produce a materially corrected/re-scoped assignment with rationale and linked prior evidence, or a precise blocker/request for missing information. Do not reset retry counts merely by changing an ID/revision, repeat unchanged work, or treat the controller's self-review as independent final acceptance.
- Model names do not guarantee success. Every escalation needs concrete expected-versus-actual failure evidence, classification, prior attempts, the next task/revision, target model/effort, acceptance checks and parent authorization.
- Distinguish ambiguous/incomplete assignments, ordinary repairable code defects, capability/comprehension failure, external prerequisites and capacity/quota failure. Clarify a defective assignment; use a bounded same-model correction for a normal fix when sufficient; escalate only when the evidence supports a capability/comprehension gap.
- Missing external setup or quota is not evidence of model incapability. Do not escalate to Astra or sweep models to bypass those blockers. A documented available, already approved same-cost implementation alternative may be explicitly assigned by the parent; otherwise return blocked.
- Keep at most three bounded attempts per affected task, including the initial attempt and any escalation; preserve failed evidence and return an unresolved blocker at the ceiling. External prerequisite blockers stop immediately without consuming retry rounds.
- Escalation is a task-specific invocation override; saved cheap defaults remain unchanged. Every implementation still requires a separate independent final Astra reviewer; Astra never becomes the implementation fallback.
- Final reviewer cannot be downgraded as an automatic fallback. No worker may widen scope, change provider permissions, fabricate readiness or purchase/reset credits.

## Additional artifacts and required evidence
- Additionally create harness/templates/MODEL-ESCALATION.md and reference it from MODEL-ORCHESTRATION policy, DELEGATION-PROTOCOL, assignment/usage/result templates and relevant role/skill/rubric instructions already in scope.
- Template fields: task/contract revision; role; failed attempt and expected/actual evidence; failure classification and prerequisite readiness; previous model/effort and attempts; selected next model/effort and justification; exact affected scope; validation; parent decision/authorization; remaining attempts and stop condition; separate reviewer.
- Extend focused structural regressions for Sol defaults, expensive saved-default drift, missing escalation artifact/owner/evidence/attempt-limit fields, rejected Astra executor escalation and required Sol-ceiling orchestrator self-review, plus policy rejecting quota/external-blocker escalation or worker self-escalation.
- Capture fresh actual checks for revision 3, preserve earlier revision-2 results as superseded snapshots; do not claim old test counts apply to the changed source.
- Also permit the minimal retry-counter consistency edit in harness/workflows/EXTERNAL-DEPENDENCIES.md and harness/loops/EXECUTION-LOOP.md alongside the already permitted EVAL-LOOP and verifier constants/tests: replace any initial-plus-three-corrections wording with at most three total attempts including initial execution and escalation; preserve immediate external stop and no unchanged retries.
- Existing limits2/1, minimal context, usage honesty, factory required-key validation, preservation, preflight and no-external-operation rules remain.
- Only the parent updates docs/architecture/orchestration-model-policy.md and archive. Final result/checks must identify effective revision3 and return ready-for-review.

## Parent-authorized remaining stage-3 correction
- The final independent reviewer reproduced S3-NONUI-PRECONDITION, S3-HANDOFF-ARTIFACTS and S3-EXTERNAL-NEGATION in stage-3-review-1.json. Reopen only the existing verifier/tests/safety-rule scope now to enforce exact non-UI prerequisites, all mandatory safety-edge artifacts and rejection of negated external-stop prefixes/suffixes. Preserve topology and permissions. Map these fixes in the final result; reviewer owns the verdict.
