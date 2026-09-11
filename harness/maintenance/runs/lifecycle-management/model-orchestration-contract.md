<!-- document-budget exception: required-sequential-instruction | User-priority model allocation, context economy and acceptance form one bounded contract. -->
# Model orchestration priority contract

Task: lifecycle-management-model-orchestration. Revision: 1. Parent: /root. Status: ready-to-implement.
User explicitly prioritizes token-efficient orchestration: Astra planning/supervision/review, Luna implementation with Spark as an explicit alternative; defaults must be enforced for subagent dispatch.
This contract supersedes previous inherited-model and Spark-primary requirements in stage-3 contracts and earlier decisions. Preserve all unrelated lifecycle and external-blocker work.
Base: current frozen working tree after interrupted stage 3; stages 1/2 accepted, stage 3 not accepted. Parent owns design and final acceptance.
Executor: explicitly invoked gpt-5.6-luna/high, no re-delegation. No UI/application change or external operation.

## Approved model and dispatch design
- Root orchestrator: gpt-6-astra/high; router: gpt-6-astra/medium; product-planner, ui-ux-designer, reviewer: gpt-6-astra/high.
- Implementer: gpt-5.6-luna/high by default. gpt-5.3-codex-spark/high is an explicitly selected alternative for a bounded implementation task, with reason/availability evidence. Saved default remains Luna.
- Release-manager: gpt-5.6-luna/high for contract preparation and authorized existing-command execution; Astra parent/reviewer retain release decisions and independent verdicts. Set its abstract model_tier to fast; permissions/capabilities remain unchanged.
- Every native role wrapper explicitly declares its approved model and effort; do not rely on parent inheritance. Root config declares Astra/high. Do not add undocumented global subagent-model settings.
- Every generic subagent dispatch must explicitly select the model and effort and assign the canonical role/contract. If a native tool pins a stale model and cannot override it, use an explicit approved-model generic worker with the same canonical role/boundaries and record why; never claim it was the native role.
- Verify returned invocation metadata when available; otherwise distinguish selected tool arguments from unobservable actual provider execution. Missing/mismatched required selection evidence blocks execution/review.
- No automatic upgrade from Luna/Spark to Astra for implementation and no automatic downgrade of an Astra verdict role. Unavailable/quota-limited required capacity returns blocked with evidence and resolution options; no blind alternate-model sweep or credit purchase/reset.
- Parent may perform obvious routing locally as Astra and record it; do not spawn a router only to restate an already complete assignment. Required UI specialist planning and independent review remain.
- Default one active worker; at most two subagents for explicitly disjoint useful work. Set spec max_parallelism and Codex max_threads to 2, max_delegation_depth/max_depth to 1. Workers cannot spawn workers.
- New subagents default to fork_turns none or equivalent minimal context; provide the task contract, current source references and the relevant role only. Full-history fork requires an explicit parent justification and cannot be accidental.
- Reuse a worker only for the same bounded context; send corrections as defect deltas, not repeated full history. After scope/context changes, use a fresh minimal assignment.
- Parent declares input/context budget, exact source manifest and output budget. Preserve required instructions and acceptance; split scope when context cannot fit instead of silently dropping safety requirements.
- Summaries contain acceptance mapping, changed paths, actual check exits, blockers and evidence pointers; raw logs stay in files. Default return target is at most 250 words plus evidence links, unless the contract requires more.
- Review only changed or invalidated criteria after a bounded correction; do not repeat passed checks without a change or unresolved concern. No duplicate reviewer agents for the same criteria.
- Record selected role/model/effort, reason, context manifest, reuse/fork choice, round count, elapsed time and provider token/cache usage only when actually supplied. Missing per-call usage is null/unavailable, not zero. Account-wide percent is not task token usage; do not claim measured savings without a comparable measured baseline.

## Exact permitted files
- Create harness/policies/MODEL-ORCHESTRATION.md and harness/templates/MODEL-USAGE-RECORD.md.
- Modify .codex/config.toml and all six .codex/agents/ai-erp-*.toml wrappers.
- Modify harness/harness-spec.json only for approved tiers/limits and descriptive policy references; preserve six-role DAG, domains, capabilities, permissions, core and gates.
- Modify harness/HARNESS.md, ENVIRONMENT.md, team/TEAM-ARCHITECTURE.md and all six harness/team/agents role documents.
- Modify harness/skills/ai-erp, ai-erp-implement, ai-erp-eval, ai-erp-plan, ai-erp-release, ai-erp-ui-ux, ai-erp-verify SKILL.md and their exact .agents projections.
- Modify harness/templates/TASK-ASSIGNMENT.md, IMPLEMENTATION-CONTRACT.md, IMPLEMENTATION-RESULT.md, RELEASE-CONTRACT.md, RELEASE-RESULT.md.
- Modify harness/workflows/DELEGATION-PROTOCOL.md, harness/evaluation/TASK-REVIEW-RUBRIC.md, harness/recovery/CHECKPOINT.md, RECOVERY-PLAYBOOK.md and harness/ledger/JOURNAL-FORMAT.md for model/context evidence references only.
- Modify scripts/verify-harness.py, scripts/test_verify_harness.py for the new explicit model policy, limits and context/usage artifact guards.
- Modify AGENTS.md managed block only; update relevant README.md and docs/architecture/ui-ux-agent-guide.md model/orchestration sections and resolve their outdated role-count/general-planning table wording.
- Add model-orchestration-result.md and model-orchestration-checks.json in this run directory.
- No other source files. Parent owns this contract, design doc, delta, state/journal/decisions, preservation, final reader report and Notion archive.

## Required verification
1. All six wrappers and root config enforce the approved defaults/efforts; missing keys, wrong role model, automatic expensive implementation default and saved Spark alternative fail meaningful negative mutations.
2. Release-manager fast tier and fixed limits 2/1 match common/native metadata. Existing role access, UI/release prerequisites and external stop guards remain intact.
3. Remove the obsolete optional-model-key relaxation in ProjectValidator; use the unmodified pinned factory required-key checks. Both project and standalone factory validation must pass after explicit keys are installed.
4. Assignment and usage templates require model reason/evidence, context manifest/budget, output budget, fork/reuse and usage availability; missing essential fields/artifacts fail focused tests.
5. Active canonical guidance contains no conflicting inherited-model or Spark-primary/Luna-only-fallback rules. Preserve historical reports/contracts/decisions; they remain historical evidence.
6. No unsupported native keys, increased permissions, new roles/evaluators/scheduler, external services, Git publication or vendor edits.
- Common metadata first; provider-path preflight against delta-plan.json before synchronizing wrappers/skills. No validator bypass.
- Capture raw current command output directly via subprocess into JSON; do not reconstruct earlier stage-3 red logs if unavailable. Add new model-policy negative tests before the corresponding guard changes and retain actual red/green outputs.
- Use exec_command login:false to avoid the observed default-login shell completion problem. Run project verifier, full regressions, UX smoke, standalone factory verifier and git diff --check.
- Return ready-for-review with concise numbered acceptance mapping. Independent Astra reviewer owns verdict; parent owns acceptance. Do not resume other stage-3 completion work without parent dispatch.
