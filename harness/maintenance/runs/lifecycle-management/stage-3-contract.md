<!-- document-budget exception: required-sequential-instruction | Guard behavior, preservation and final reader scope form one acceptance contract. -->
# Stage 3 implementation contract

Task: lifecycle-management-stage-3. Contract revision: 1. Parent: /root.
Activation: execute only after parent records stage-2 independent acceptance and explicitly dispatches this contract.
Objective: close verifier policy gaps and publish accurate operating guidance for six roles and explicit delegation.
Use accepted stage-2 tree, original preservation-before.json and the user-approved review. Unresolved decisions: none.
Execution: parent-selected Luna/high fallback with actual invocation record; no re-delegation.
UI prerequisite: this is harness-only behavior; no product screen decision/code change.

## Exact permitted files
- Modify scripts/verify-harness.py, scripts/test_verify_harness.py.
- Modify harness/harness-spec.json only to make existing UI/release/planning handoff condition and artifact prerequisites explicit; preserve topology, gates, roles and runtime policy.
- Modify harness/skills/ai-erp-verify/SKILL.md and its exact .agents projection.
- Modify harness/HARNESS.md, harness/ENVIRONMENT.md, harness/team/TEAM-ARCHITECTURE.md and harness/workflows/DELEGATION-PROTOCOL.md to remove remaining contradictory old-role wording.
- Modify only the managed block of AGENTS.md.
- Modify README.md and docs/architecture/ui-ux-agent-guide.md in Korean for the current lifecycle/roles/delegation contract.
- Create docs/reports/harness-lifecycle-improvements-2026-09-07.md as a factual current-stage report candidate; parent finalizes actual final validation/archive details.
- Add stage-3-result.md and stage-3-checks.json in this run directory.
- No other files. In particular, no historical report translation/deletion, vendor edits, app/deploy/CI changes, parent evidence edit, state/journal/decisions or normal Git index operations.

## Guard design
- Saved implementer native model must equal gpt-5.3-codex-spark and effort high. Luna/high remains allowed only through explicit invocation fallback evidence reviewed by task-review.
- All other five roles inherit model/effort; no model overrides in native wrappers or root config.
- Expected access is router/reviewer read-only; product-planner/ui-ux-designer/implementer/release-manager workspace-write. Check spec and native expectations in addition to canonical/native parity.
- Preserve all existing correct permissions, domain-path, role-capability, evaluator-ownership, vendor, language, schema and core-forbidden-layer checks.
- UI reviewer -> implementer condition must exactly express the approved canonical sentence: A UI plan has passed independent ui-plan-review and the parent has completed the implementation contract.
- Normalize whitespace only when comparing this required sentence. Reject negation, missing condition and arbitrary contradictory suffix/prefix; keywords alone are insufficient.
- Required UI edge artifacts include SCREEN-PLAN, UI-REVIEW, IMPLEMENTATION-CONTRACT and IMPLEMENTATION-RESULT exact existing template paths.
- Protect planning/release/assignment artifact references, evaluator owner/runner, plan/contract revision linkage and role boundaries introduced in stages 1/2.
- Enforce the eight accepted edge pairs exactly: router->product-planner, router->ui-ux-designer, router->implementer, product-planner->reviewer, product-planner->ui-ux-designer, ui-ux-designer->reviewer, reviewer->implementer, reviewer->release-manager. Reject duplicate edges and any added shortcut, including planner->implementer or router->release-manager; test representative forbidden additions.
- Protect safety-critical conditions on router->implementer, product-planner->ui-ux-designer and reviewer->release-manager against omitted or negated prerequisites. Use explicit approved canonical sentences from accepted stage 2 (whitespace-normalized equality, not keyword presence), preserving the preparation-versus-authorized-execution distinction; test missing/negated conditions and required artifact omissions.
- New negative tests must exercise verify_project or focused guards as appropriate without relying on an unrelated earlier failure; do not weaken tests to pass.
- Success output distinguishes structural policy verification from actual model invocation, human review and deployment. No claims of actual fallback evidence verification.
- No broad English-check exemption or pinned factory modification; original model-key compatibility remains narrow and documented.

## Reader guidance
- Describe product-planner, router, ui-ux-designer, implementer, reviewer, release-manager and their exact responsibilities.
- Explain parent dispatch -> acknowledgement -> bounded execution -> evidence return -> independent review -> parent acceptance/correction.
- Include a compact role/phase table or Mermaid for the lifecycle and explicit references to assignment, increment and release templates.
- Clarify product planning vs screen design and frontend/backend implementation ownership, same-SHA CI and release observation/rollback boundaries.
- Show small-task reuse and planning-only/no-release completion without mandatory unrelated work.
- State that file-based instructions/rubrics and structural tests are not an autonomous scheduler or proof of native role invocation/production outcome.
- Preserve existing useful app operating instructions and source references. Update only relevant README/guide sections, not unrelated history.

## Acceptance and checks
1. Regression mutation of saved Spark -> Luna fails, while parent-recorded Luna fallback for execution does not require changing saved default.
2. Removed/negated UI prerequisite or missing UI evidence artifact fails.
3. Aligned spec/role/native reviewer or router workspace-write drift fails; valid six-role installation still passes.
4. All prior/new planning, release, assignment, parity, scope and permission regressions pass with raw red/green evidence for new fixes.
5. Reader guidance matches actual six-role files and delegation/release ownership and makes no unrun success claims.
6. No unauthorized file changes or historical evidence loss.
Run python -B scripts/verify-harness.py; python -B scripts/test_verify_harness.py; python -B scripts/smoke-ux-skills.py; git diff --check.
Before projecting the verify skill run successful provider preflight with the unchanged lifecycle delta-plan.
No commit/push/deploy/remote publication; parent handles final isolated-index tracking/preservation verification and Notion synchronization.
Return ready-for-review with actual dispatch/model/effort, acceptance mapping, exact paths and commands/exits/output; reviewer owns verdict.
