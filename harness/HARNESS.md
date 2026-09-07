# AI ERP harness

Read in order: `harness-spec.json`, `state/state.json`, the entry skill, and the relevant role.

## Required routing

Every screen planning request MUST route through `ui-ux-designer` before planning output or implementation. This includes pages, flows, layouts, forms, navigation, tables, dashboards, accessibility, and interaction/state changes.
The router delegates a bounded task and collects the specialist's plan; an independent reviewer checks it before implementation starts. A direct design-skill invocation follows the same specialist and review route.
For code-only backend or infrastructure work without a screen decision, retain the ordinary project workflow.
If native delegation is unavailable, report the limitation; do not pretend a separate specialist reviewed the work.

## Scope and evidence

The `core` profile installs routing, execution, task evaluation, structural verification, and short reporting. It does not install memory, harness-effect evaluation, automated improvement, or a Learning Gate.
Use `templates/SCREEN-PLAN.md` and `evaluation/UI-PLAN-RUBRIC.md`; save actual plans under `docs/ux/`.
Read vendor material only as needed through the project domain skills. `vendor/ux-skills/` contains pinned reference snapshots, not additional root instructions.
Run `python scripts/verify-harness.py` for structural changes. Browser and frontend checks apply when screen code changes; never report a plan-only review as browser validation.
Record evidence and defects in `ledger/journal.jsonl`; keep `state/state.json.next_action` explicit.

## Reporting and permissions

Follow `policies/reporting.json`: update the local source first, then synchronize substantial reader documents to the user's Notion archive. Preserve the local source path and synchronization date.
Use Korean for reader reports, concise English for canonical contracts, and exact machine identifiers throughout.
Respect spec approval gates and reuse authorization already granted. The requested setup, skill downloads, Git tracking, PR, and merge are authorized for this installation.
