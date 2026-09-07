# Project instructions

## Documentation ownership

Treat the relevant local workspace file as the canonical source for substantial architecture notes, design documents, reports, guides, and decision records.
Update the local source first, then archive a copy in the Notion database `AI 생성문서 관리` with the local source path and last synchronized date when practical.
Do not archive short conversation replies, temporary notes, raw command output, or intermediate working material unless requested.

<!-- harness-factory:start ai-erp -->
## AI ERP UI/UX routing

- Canonical contract and profile: `harness/harness-spec.json` (`core`).
- Cold start: `harness/HARNESS.md`, then `harness/state/state.json` and the relevant role.
- Entry: `$ai-erp`; task review: `$ai-erp-eval`; structure: `$ai-erp-verify`.
- Every screen planning request MUST route to `ui-ux-designer` before drafting screen decisions or implementation. This includes pages, flows, layouts, navigation, forms, tables, dashboards, accessibility, states, and interaction changes.
- Delegate the bounded task using `harness/team/agents/ui-ux-designer.md` and `$ai-erp-ui-ux`; collect the actual plan and handoff evidence, then request independent review from `reviewer`.
- Native Codex names are `ai-erp-ui-ux-designer`, `ai-erp-router`, and `ai-erp-reviewer`. If already executing as the designer, perform the assigned planning task without delegating to another designer.
- Require evaluator `ui-plan-review` to pass before implementation. Direct design-skill requests follow the same route. If delegation is unavailable, disclose the limitation without inventing specialist review.
- Backend-only work without screen decisions retains the ordinary project workflow.
- Project skills: `.agents/skills/ai-erp*/SKILL.md`; native role wrappers: `.codex/agents/ai-erp-*.toml`.
- Select pinned vendor guidance through `$ai-erp-frontend-design`, `$ai-erp-ui-ux-pro-max`, and `$ai-erp-web-design-guidelines`; load only relevant material.
- Run `python scripts/verify-harness.py` for harness changes and appropriate frontend/browser checks for implemented UI; preserve actual evidence and checks not run.
- Reporting follows `harness/policies/reporting.json`: local source first, then the configured Notion archive for substantial reader documents.
- Canonical contracts use English; reader reports use Korean and localized prose, preserving exact machine tokens.
- Core has no durable memory, self-evaluation, improvement, or Learning Gate. Recommend and obtain authorization before adding those layers.
- Respect spec approval gates and reuse authorization already granted. Native agents inherit the user's model and reasoning settings.
<!-- harness-factory:end ai-erp -->
