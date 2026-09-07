# UI/UX specialist setup

Every screen planning task now routes through `ui-ux-designer` and independent `reviewer` assessment before implementation.

- `harness/harness-spec.json` owns the roles, routing, and evaluation contract. `AGENTS.md` and native Codex role/skill files expose the workflow.
- Plan and review templates cover user flows, permissions, tables, filters, forms, screen states, accessibility, and observable acceptance criteria.
- Three downloaded design skills remain pinned under `vendor/ux-skills/`; concise project skills load relevant guidance progressively and reject recommendations that do not fit internal ERP tasks.
- `core` is a bounded implementation choice within the requested setup. Native agents inherit the user's model and reasoning configuration.

`python -B scripts/verify-harness.py --require-tracked` passed the schema, permissions, DAG, provider parity, required routing, and pinned vendor integrity checks. All 10 mutation tests and the offline search smoke passed. Evidence is stored under `harness/maintenance/runs/create-ui-ux-agent/`.

Independent review identified and resolved two defects: private Codex files are excluded from tracking requirements, and explicit model overrides cannot bypass the inheritance policy. Tests also retain Python 3.11 cleanup compatibility. The latest main branch's workflow contract now checks the added harness job without relaxing deployment safeguards.

The original factory `0.3.0` validator requires two optional Codex model fields. This incompatibility is retained in the evidence; the project verifier makes only those keys optional while preserving all other checks. No actual screen implementation or browser validation was performed for this setup.

Local files remain canonical. The Korean reader guide is `docs/architecture/ui-ux-agent-guide.md`. The delivery owner finishes the authorized PR merge and synchronizes substantial reader documents to the requested Notion archive.
