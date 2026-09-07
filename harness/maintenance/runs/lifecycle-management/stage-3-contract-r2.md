<!-- document-budget exception: required-sequential-instruction | External dependency stop rules and goal-mode behavior extend the existing bounded implementation contract. -->
# Stage 3 contract amendment, revision 2

Task: lifecycle-management-stage-3. Parent: /root. Status: pending stage-2 acceptance.
Read stage-3-contract.md plus this amendment as effective revision 2. This amendment supersedes conflicting narrower file limits only.
Source: user explicitly requested stopping work and proposing resolution when required external API/service setup is missing, including during goal-mode persistence.
Objective: prevent forced integration, invented external state, and unapproved workaround implementation while retaining legitimate scoped offline development.

## Additional exact permitted files
- Create harness/workflows/EXTERNAL-DEPENDENCIES.md and harness/templates/BLOCKER-REPORT.md.
- Modify harness/templates/TASK-ASSIGNMENT.md, IMPLEMENTATION-CONTRACT.md, IMPLEMENTATION-RESULT.md, INCREMENT-PLAN.md, RELEASE-CONTRACT.md, RELEASE-RESULT.md.
- Modify harness/team/agents/router.md, product-planner.md, implementer.md, release-manager.md, reviewer.md.
- Modify harness/skills/ai-erp/SKILL.md, ai-erp-implement/SKILL.md, ai-erp-plan/SKILL.md, ai-erp-release/SKILL.md, ai-erp-eval/SKILL.md and their byte-identical .agents projections.
- Modify harness/evaluation/TASK-REVIEW-RUBRIC.md, INCREMENT-PLAN-RUBRIC.md, RELEASE-REVIEW-RUBRIC.md.
- Modify harness/loops/EXECUTION-LOOP.md, EVAL-LOOP.md, harness/recovery/CHECKPOINT.md, RECOVERY-PLAYBOOK.md, harness/ledger/JOURNAL-FORMAT.md.
- Existing stage-3 verifier/tests/spec/docs scope remains; no new roles, callable skills, scheduler, adaptive layers or external service operations.
- Parent owns this amendment, delta receipt, state/journal events, actual acceptance and archive; do not edit them.

## Assignment and preflight
- Parent assignment/implementation contract declares external dependencies as ready, blocked, or justified N/A, with current evidence and the work that depends on each dependency.
- Declare mode none / offline-contract-only / live-integration; each prerequisite is verified / missing / unknown / not-required with owner, evidence date, safe check and expected result. Unknown is never ready; not-required needs scope-based justification.
- Per dependency identify provider/service, required account/tenant/project, API contract/version, externally managed enablement/permissions/OAuth callback or other settings, configuration/key names without secret values, external owner, and readiness verification.
- Distinguish code work, explicitly scoped offline contract tests, and live activation. Existing authorized offline work can proceed only when it does not depend on missing facts and is not presented as integration success.
- If the desired implementation requires unavailable external setup, undecided external behavior, missing authority or an unknown contract, return blocked before dependent edits or execution. Do not invent values or silently expand scope.
- Readiness evidence must be current and correspond to the actual target/environment; a placeholder, mock response, local test or existence of an environment-variable name is not proof of external readiness.
- Parent reuses explicit existing authorization; this rule does not create repetitive approval for already authorized available dependencies.

## Fail-stop and return
- Stop the affected task on first confirmed external prerequisite blocker; do not consume the general three correction rounds retrying a known configuration/permission/quota blocker.
- If detected during execution, preserve current diff and command evidence, record which acceptance criteria are blocked and return to parent. Do not erase unrelated work or silently continue dependent implementation.
- No invented credentials/accounts/tenant IDs, auth bypass, disabled security/validation, automatic fake production responses, unapproved alternate provider, purchases or resource creation to force completion.
- Normal labeled test doubles remain valid for explicitly scoped tests; they cannot substitute for live integration evidence or close blocked acceptance criteria.
- A bounded retry is allowed only for an evidenced transient failure when the parent contract permits the retry count/action; unchanged external state is not a reason to retry.
- Goal/autonomous continuation does not expand authority or override external prerequisites. Keep the affected task blocked, avoid repeated side effects, and use supported runtime goal/status semantics; never claim a goal was paused/stopped by an unavailable tool.
- Resolve conflicting loop text explicitly: three correction rounds apply only to repairable implementation defects, and EVAL-LOOP must permit returning an external blocker instead of saying to stop only at parent-ready. A goal tool's repeated-turn threshold is not permission to repeat the blocked work.
- BLOCKER-REPORT records task/increment/plan/contract revision, dependency/target, observed failure and sanitized evidence, classification, attempted actions/results, affected scope/current diff, external owner, granted/ungranted actions, resolution options and exact resume checks.
- Resolution options must be concrete: the external owner supplies the specific missing setup; use a verified authorized existing integration; or obtain an explicit scoped deferral/offline alternative. Do not choose a workaround on the user's behalf.
- Return a concise explanation with what is blocked, why, who can resolve it, what they must change, and how readiness will be checked. Ask only for truly missing input/authorization.
- Resume only after new evidence resolves the blocker, affected plan/contract revisions and independent reviews are current, and parent reissues or confirms the matching assignment. Preserve old failure evidence and blocked criteria until verified.

## Review, tests and reader guidance
- task-review/release-review must fail required unresolved external prerequisites; a blocked result cannot be relabeled pass/deployed through mocks, omitted checks or goal persistence.
- Planning may describe an unresolved external dependency for future work, but current implementation readiness must be blocked when the dependency is required now.
- Add structural reference/essential-field guards and meaningful negative mutations for missing blocker workflow/report, omitted external-readiness/owner/resume fields, and removed stop-rule references. They verify policy structure, not live service state.
- Protect the explicit immediate-stop/no-unchanged-retry policy against negated or reversed wording, not only keyword presence; a mutation allowing continued dependent work despite missing/unknown required external setup must fail for that intended guard.
- Capture actual red/green outputs directly into JSON using subprocess capture; label any old abbreviated evidence as excerpts and never reconstruct historical raw output.
- Update Korean guide/report with one concise external API/OAuth setup example and the goal-mode limitation. State current native planner smoke returned unknown agent_type; new-role runtime behavior is not certified.
- Parent extends scenario-cases.json for missing external setup, explicitly scoped offline tests, persistent goal retries and evidence-based resume; implementer uses them as acceptance examples without editing that parent evidence.
- Existing stage-3 checks, preservation, provider preflight, model policy, independent review and no-publication rules remain.
