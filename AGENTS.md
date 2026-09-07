# Project instructions

## Documentation ownership

Treat the relevant local workspace file as the canonical source for substantial architecture notes, design documents, reports, guides, and decision records.
Update the local source first, then archive a copy in the Notion database `AI 생성문서 관리` with the local source path and last synchronized date when practical.
Do not archive short conversation replies, temporary notes, raw command output, or intermediate working material unless requested.

<!-- harness-factory:start ai-erp -->
## AI ERP UI/UX and implementation routing

- Canonical contract and profile: `harness/harness-spec.json` (`core`).
- Cold start: `harness/HARNESS.md`, then `harness/state/state.json` and the relevant role.
- Entry: `$ai-erp`; task review: `$ai-erp-eval` using `task-review`; structure: `$ai-erp-verify`.
- For all non-UI implementation tasks (frontend/backend/scripts/infra/.github, root build/config, tests, and behavior-affecting configuration), route through `$ai-erp-implement` after the parent contract is explicit and complete.
- Every screen planning request MUST route to `ui-ux-designer` before drafting screen decisions or implementation. This includes pages, flows, layouts, navigation, forms, tables, dashboards, accessibility, states, and interaction changes.
- Graph:
  - `router -> ui-ux-designer -> reviewer` for UI planning.
  - `router -> implementer` for non-UI implementation tasks after a complete parent contract; the parent separately requests independent review.
  - `reviewer -> implementer` for UI implementation after passing `ui-plan-review` and completing the parent contract.
  - Every stage returns to the upper orchestrator; no screen shortcut or reverse implementation edge is allowed.
- Native Codex names are `ai-erp-ui-ux-designer`, `ai-erp-router`, `ai-erp-reviewer`, and `ai-erp-implementer`.
- Backend and frontend changes follow the same mandatory UI specialist gate before changing screen behavior.
- Project skills: `.agents/skills/ai-erp*/SKILL.md`; native role wrappers: `.codex/agents/ai-erp-*.toml`.
- Select pinned vendor guidance through `$ai-erp-frontend-design`, `$ai-erp-ui-ux-pro-max`, and `$ai-erp-web-design-guidelines`; load only relevant material.
- Run `python scripts/verify-harness.py` for harness changes and `python scripts/smoke-ux-skills.py` for UI/UX runtime smoke checks.
- Run `python -B scripts/test_verify_harness.py` after harness edits.
- Reporting follows `harness/policies/reporting.json`: local source first, then the configured Notion archive for substantial reader documents.
- Canonical contracts use English; reader reports use Korean and localized prose, preserving exact machine tokens.
- Respect spec approval gates and reuse authorization already granted.
- Core has no durable memory, self-evaluation, improvement, or Learning Gate. Recommend and obtain authorization before adding those layers.
- Core includes routing, planning, evaluation, verification, and reporting; it omits adaptive memory, self-evaluation, improvement, and governed learning controls.
- Native `router`, `ui-ux-designer`, and `reviewer` inherit the invoking model and effort.
- Implementer defaults to `gpt-5.3-codex-spark` with `model_reasoning_effort=high`; an explicit `gpt-5.6-luna`/`high` invocation is the only documented fallback when Spark is unavailable or quota-limited, and actual selection/reason must be recorded.
- Implementer is parent-assigned execution and verification only; it may not own verdicts, routing, or improvement actions.
<!-- harness-factory:end ai-erp -->
