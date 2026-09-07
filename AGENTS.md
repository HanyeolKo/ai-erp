# Project instructions

## Documentation ownership

Treat the relevant local workspace file as the canonical source for substantial architecture notes, design documents, reports, guides, and decision records.
Update the local source first, then archive a copy in the Notion database `AI 생성문서 관리` with the local source path and last synchronized date when practical.
Do not archive short conversation replies, temporary notes, raw command output, or intermediate working material unless requested.

### Notion reading queue

- Keep local files as the canonical sources and update them before synchronizing to Notion. Notion is a concise reading archive; do not create a separate page for every output or revision.
- Before creating a page in `AI 생성문서 관리`, search for existing documents in the same context (the same project, topic, purpose, and continuing decision or work; title similarity alone is insufficient) and inspect the actual reading-status property: `읽음`, `읽는중`, or `안읽음`.
- If a same-context document is `안읽음`, update that existing page instead of creating a new one. Consolidate overlapping content into a concise, current document, retaining relevant decisions and necessary context rather than appending repeated reports. Keep its reading status as `안읽음` and refresh the local source path(s) and synchronization date.
- Preserve same-context documents marked `읽음` or `읽는중`. If no suitable unread page exists, create a new page only for substantive new information the user needs to read; summarize the changes and link related documents rather than duplicating their full contents.
- On every initial document registration, explicitly set the reading-status property to `안읽음`; do not rely on a database default or leave it empty.
- If the reading-status property or its value cannot be verified, keep the local source updated and defer Notion synchronization rather than guessing the status or creating a duplicate.
- This rule governs future synchronization. Do not bulk merge, delete, or change the reading status of existing documents unless the user requests it.

<!-- harness-factory:start ai-erp -->
## AI ERP UI/UX and implementation routing

- Canonical contract and profile: `harness/harness-spec.json` (`core`).
- Cold start: `harness/HARNESS.md`, then `harness/state/state.json` and the relevant role.
- Entry: `$ai-erp`; planning: `$ai-erp-plan`; release: `$ai-erp-release`; task review: `$ai-erp-eval` using `task-review`, `increment-plan-review`, or `release-review`; structure: `$ai-erp-verify`.
- For all non-UI implementation tasks (frontend/backend/scripts/infra/.github, root build/config, tests, and behavior-affecting configuration), route through `$ai-erp-implement` after the parent contract is explicit and complete.
- Every screen planning request MUST route to `ui-ux-designer` before drafting screen decisions or implementation. This includes pages, flows, layouts, navigation, forms, tables, dashboards, accessibility, states, and interaction changes.
- General product planning routes to `product-planner` with `TASK-ASSIGNMENT.md`; accepted plans requiring screens then route through `ui-ux-designer` and `ui-plan-review`.
- Parent-controlled dispatch, acknowledgement, return, independent review and acceptance follow `harness/workflows/DELEGATION-PROTOCOL.md`.
- Accepted implementation evidence enters `release-manager` only through parent assignment; release readiness is distinct from deployment completion and unresolved recovery is `operator-action-required`.
- Graph:
  - `router -> product-planner -> reviewer` for product planning; `product-planner -> ui-ux-designer` is allowed only after accepted `increment-plan-review` when screens are required.
  - `reviewer -> release-manager` for accepted implementation release preparation; independent `release-review` precedes any authorized execution.
  - `router -> ui-ux-designer -> reviewer` for UI planning.
  - `router -> implementer` for non-UI implementation tasks after a complete parent contract; the parent applies the risk policy and requests only an applicable review.
  - `reviewer -> implementer` for UI implementation after passing `ui-plan-review` and completing the parent contract.
  - Every stage returns to the upper orchestrator; no screen shortcut or reverse implementation edge is allowed.
- Native Codex names are `ai-erp-ui-ux-designer`, `ai-erp-router`, `ai-erp-reviewer`, `ai-erp-implementer`, `ai-erp-product-planner`, and `ai-erp-release-manager`.
- Backend and frontend changes follow the same mandatory UI specialist gate before changing screen behavior.
- Project skills: `.agents/skills/ai-erp*/SKILL.md`; native role wrappers: `.codex/agents/ai-erp-*.toml`.
- Select pinned vendor guidance through `$ai-erp-frontend-design`, `$ai-erp-ui-ux-pro-max`, and `$ai-erp-web-design-guidelines`; load only relevant material.
- Run `python scripts/verify-harness.py` for harness changes and `python scripts/smoke-ux-skills.py` for UI/UX runtime smoke checks.
- Apply `harness/policies/VERIFICATION.json`: parent selects low/standard/high; low uses applicable quick checks, standard uses changed-area tests with optional Sol/medium review, and high requires relevant integration checks plus independent Astra/high review. Record low/standard work in `harness/templates/TASK-RECORD.md` and reuse valid evidence without repeating unchanged checks.
- Run `python -B scripts/test_verify_harness.py` after harness edits.
- Reporting follows `harness/policies/reporting.json`: local source first, then the configured Notion archive for substantial reader documents.
- Canonical contracts use English; reader reports use Korean and localized prose, preserving exact machine tokens.
- External dependencies use `harness/workflows/EXTERNAL-DEPENDENCIES.md`: declare mode and target-specific readiness. Missing or unknown required setup stops dependent work before edits or execution and returns `harness/templates/BLOCKER-REPORT.md` to the parent; goal-mode continuation cannot override the block.
- Explicitly scoped `offline-contract-only` work may proceed only when independent of missing external facts and must not be reported as live integration success. Do not invent credentials, OAuth settings, permissions, provider responses, or alternate providers.
- Respect spec approval gates and reuse authorization already granted.
- Core has no durable memory, self-evaluation, improvement, or Learning Gate. Recommend and obtain authorization before adding those layers.
- Core includes routing, planning, evaluation, verification, and reporting; it omits adaptive memory, self-evaluation, improvement, and governed learning controls.
- Native defaults are explicit: root/final reviewer Astra/high; router, product-planner, and ui-ux-designer Sol/medium; implementer and release-manager Luna/high. Spark/high is an explicitly selected implementation alternative with reason and availability evidence.
- Parent-owned executor escalation is Luna/high -> Terra/medium -> Sol/medium, never Astra execution. Sol-ceiling failures return to the Astra orchestrator for self-review; workers cannot self-escalate. Generic dispatch records model, effort, canonical role/contract, bounded context, and `MODEL-ESCALATION.md` when applicable. Limits are two active workers and delegation depth one.
- Implementer is parent-assigned execution and verification only; it may not own verdicts, routing, or improvement actions.
- Execution evidence and bounded retry guidance: `harness/loops/EXECUTION-LOOP.md`.
<!-- harness-factory:end ai-erp -->
