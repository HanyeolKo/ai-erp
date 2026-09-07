# Environment

AI ERP uses React and TypeScript in `frontend/`, Java in `backend/`, and pnpm workspace scripts.

- Structural task check: `python scripts/verify-harness.py` (Python 3.11 or newer).
- Frontend checks when UI code changes: `pnpm frontend:test`, `pnpm frontend:typecheck`, `pnpm frontend:build`.
- Optional design search: `python vendor/ux-skills/ui-ux-pro-max/scripts/search.py "B2B SaaS dashboard" --domain product --json`.
- Runtime: Codex; `router/ui-ux-designer/reviewer` inherit invoking settings. `implementer` defaults to `gpt-5.3-codex-spark` with `model_reasoning_effort=high`; `gpt-5.6-luna` with `high` is the explicit fallback when Spark cannot be invoked or is quota-limited, and evidence is required.
- Project instructions: `AGENTS.md`; canonical role and skill meaning: `harness/`.
- Reports and plans remain local canonical files; Notion is the archive copy.

Use repository-relative paths in commands and evidence. Do not store machine-specific credentials or local dependency locations in tracked files.
