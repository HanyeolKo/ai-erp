# Decisions

## D-001: Install bounded UI/UX routing

- Source: user request to create a UI/UX specialist, download and configure related skills, route all screen planning through the specialist, track the setup in Git, and merge its PR.
- Factory: https://github.com/HanyeolKo/harness-factory ; ref `0.3.0`; commit `537ef692042bf9f1895fbd6d3fac48c364307cf4`.
- Mode: `create`; no prior project harness or provider files were found.
- `core`, Codex-only projection, `balanced` abstract tiers, attended operation, and limits of 4 parallel tasks / depth 2 are bounded implementation choices inferred within the requested setup.
- No separate profile-selection answer was elicited. The receipt's `request` source records the setup authorization, not a fabricated response naming `core`.
- Core includes routing, planning, evaluation, verification, and reporting; it omits adaptive memory, self-evaluation, improvement, and governed learning controls.
- Canonical contracts use English; reader reports use Korean with localized prose, matching this conversation.
- All screen planning requires `router -> ui-ux-designer -> reviewer` before implementation; implementation-only backend tasks retain their existing workflow.
- Execution, evidence collection, and verdict ownership are explicitly separated across the three roles.
- Existing user authorization covers this setup, downloads, Git tracking, PR and merge, and the requested Notion archive. Further external writes outside that scope retain approval boundaries.

## D-002: Native model inheritance

Codex agent wrappers omit `model` and `model_reasoning_effort` to inherit the invoking user's configuration. Factory `0.3.0` requires these optional native keys; the project verifier documents this narrow compatibility difference without inserting a dummy model or altering deployed settings.
All other role metadata, instruction bytes, sandbox access, managed skills, path safety, and structural checks remain applicable.

## D-003: Vendor and archive ownership

Upstream UI/UX skills are pinned under `vendor/ux-skills/`; concise project skills select them progressively and adapt paths to the project.
Local files are canonical. Substantial reader documents are then archived to Notion database `AI 생성문서 관리` (`345de20d-f69b-4fe8-b79b-a204c819a7f8`) with their local source and synchronized date.

## D-004: Lightweight implementer and task-review split

- Source: explicit request to assign Spark or a comparable lightweight model to implementation while upper agents retain deep decisions, independent review, and final acceptance.
- Scope: add `ai-erp-implementer`, `task-review`, implementation contracts/results, and explicit model policy for all code, tests, and behavior-affecting configuration.
- Effect: D-004 supersedes D-001's three-role, balanced-tier, and backend ordinary-flow choices and supersedes D-002's blanket inheritance for implementation only. Router, designer, and reviewer retain inherited native model and effort; the native implementer defaults to `gpt-5.3-codex-spark` with `high`, and `gpt-5.6-luna` with `high` is the documented fallback when Spark cannot be invoked.
- Route: `router -> ui-ux-designer -> reviewer -> implementer` for UI implementation; `router -> implementer` for non-UI implementation after a complete parent contract. Each stage returns to the upper orchestrator, which requests independent review after the result.
- New artifacts: `harness/evaluation/TASK-REVIEW-RUBRIC.md`, `harness/templates/IMPLEMENTATION-CONTRACT.md`, `harness/templates/IMPLEMENTATION-RESULT.md`, and receipt evidence under `harness/maintenance/runs/add-spark-implementer`.

## D-005: Increment lifecycle and bounded model orchestration

- Source: user explicitly requested progressive planning/design, frontend/backend implementation, authorized deployment/recovery, separate planning responsibility, clear assignment flow, external prerequisite stops, and token-efficient orchestration.
- Six roles: router, product-planner, ui-ux-designer, implementer, reviewer, release-manager. Parent dispatches complete assignments, receives evidence, requests independent review and accepts results. UI specialist prerequisites remain mandatory; planning-only and no-release increments can complete within scope.
- This decision supersedes D-001 parallelism/depth and D-002/D-004 model defaults: root and final reviewer Astra/high; router/planner/UI Sol/medium; implementer/release Luna/high, Spark/high explicit implementation alternative. All native defaults are explicit; the old optional-model-key factory exception is removed.
- Parent-owned capability escalation is Luna/high -> Terra/medium -> Sol/medium; no Astra executor. Sol ceiling requires orchestrator assignment/decomposition/context/prerequisite/acceptance self-review, not automatic execution escalation or independent self-approval.
- At most three total affected-scope attempts include initial execution and escalation; renaming cannot reset the count. External/quota blockers do not justify model escalation. One worker by default, at most two disjoint workers, depth one, minimal context and honest unavailable usage records.
- Required external setup missing or unknown stops dependent work with owner, concrete resolution and evidence-based resume conditions. Goal continuation cannot override this; explicitly scoped independent offline tests never prove live integration.
- Model policy independently passed and was accepted; lifecycle stage3 verification status is recorded separately under maintenance/runs/lifecycle-management. Structural checks do not prove runtime invocation, deployment success or token savings.

## D-006: Proportionate verification

- User approved replacing universal independent Astra review with low-risk parent acceptance, standard changed-area checks and optional Sol/medium review, and high-risk independent Astra/high review. This supersedes D-005 universal final-review wording; worker self-approval remains prohibited, and external stops plus specialist/release gates remain required.
- Use one compact task record, optionally inline for trivial work; detailed artifacts remain for high-risk work. Escalation evidence applies only to real model escalation. Reuse valid outputs, run targeted iteration checks and one applicable full suite at the completed behavior-change batch, and skip unrelated smoke/provider/tracking checks.
- Implementation and independent verification evidence belong to `harness/maintenance/runs/verification-proportionality`; this decision records user authorization, not an unrun completion claim.
