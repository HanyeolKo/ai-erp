# Environment

AI ERP uses React and TypeScript in `frontend/`, Java in `backend/`, and pnpm workspace scripts.

- Structural task check: `python scripts/verify-harness.py` (Python 3.11 or newer).
- Frontend checks when code changes: `pnpm frontend:test`, `pnpm frontend:typecheck`, `pnpm frontend:build`.
- Optional design search: `python vendor/ux-skills/ui-ux-pro-max/scripts/search.py "B2B SaaS dashboard" --design-system -p "AI ERP"`.
- Runtime: Codex; wrappers inherit the invoking model and reasoning settings.
- Project instructions: `AGENTS.md`; canonical role and skill meaning: `harness/`.
- Reports and plans remain local canonical files; Notion is the archive copy.

Use repository-relative paths in commands and evidence. Do not store machine-specific credentials or local dependency locations in tracked files.
