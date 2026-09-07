# Environment

AI ERP uses React and TypeScript in `frontend/`, Java in `backend/`, and pnpm workspace scripts.

- Structural task check: `python scripts/verify-harness.py` (Python 3.11 or newer).
- Frontend checks when UI code changes: `pnpm frontend:test`, `pnpm frontend:typecheck`, `pnpm frontend:build`.
- Optional design search: `python vendor/ux-skills/ui-ux-pro-max/scripts/search.py "B2B SaaS dashboard" --domain product --json`.
- Runtime: Codex; root config and final reviewer use `gpt-6-astra`/high. Native lower-role defaults are explicit: router/product-planner/ui-ux-designer `gpt-5.6-sol`/medium; implementer/release-manager `gpt-5.6-luna`/high. Parent-owned executor escalation is Luna/high -> Terra/medium -> Sol/medium, never Astra; Spark/high is an explicitly selected implementation alternative with reason and availability evidence.
- Project instructions: `AGENTS.md`; canonical role and skill meaning: `harness/`.
- Reports and plans remain local canonical files; Notion is the archive copy.
- External API, OAuth, account, permission, and managed-setting readiness is target-specific. Missing or unknown required setup is a blocker; use `harness/workflows/EXTERNAL-DEPENDENCIES.md` and `harness/templates/BLOCKER-REPORT.md` for owner, options, and resume evidence. Local tests or environment-variable names do not prove live readiness.
- Release evidence records cwd, exact SHA, same-SHA CI, authorization, DB compatibility, backup/restore boundaries, rollback, smoke limits, observation, and recovery pointers using existing project commands. This harness task does not execute release operations.

Use repository-relative paths in commands and evidence. Do not store machine-specific credentials or local dependency locations in tracked files.
