# Verification matrix

Select checks from the actual CI and deployment contract for the changed increment, using `harness/policies/VERIFICATION.json`: targeted checks during iteration and one full applicable suite at the completed behavior-affecting batch boundary. Reuse valid evidence and record unavailable commands as not run; UX smoke, provider preflight, and Git tracking run only on their policy triggers.

| Area | Existing evidence/command | Required release link |
| --- | --- | --- |
| Frontend | Commands: `pnpm frontend:test`, `pnpm frontend:typecheck`, `pnpm frontend:build`; cwd: repository root; source: `package.json` and `.github/workflows/ci.yml` | Same reviewed SHA |
| Backend | Command: `./gradlew clean test integrationTest openapi3 bootJar`; cwd: `backend/`. Source: `.github/workflows/ci.yml` | Same reviewed SHA and generated artifact identity |
| API | Commands: `pnpm api:validate` and `pnpm api:generate`; cwd: repository root; source: `package.json` and `.github/workflows/ci.yml` | Generated artifact identity |
| Database | Functions `verify_migrations` and `migration_checksum` in `scripts/lib-deploy.sh`, plus command `bash scripts/tests/deployment-contract.sh`; cwd: repository root; source: `.github/workflows/ci.yml` and `scripts/tests/deployment-contract.sh`. A new migration requires inventory, checksum, compatibility, and migration-test review; old-app compatibility is a manual review item, not claimed as an executable command. | Backup/restore and rollback boundary |
| Deployment | Command: `bash scripts/tests/deployment-contract.sh`; cwd: repository root; source: `.github/workflows/ci.yml`, `.github/workflows/deploy-production.yml`, and `scripts/tests/deployment-contract.sh` | Authorized command and observation pointers |

Unavailable commands are recorded as not run and cannot satisfy a required criterion. This matrix references existing commands; it does not execute or replace CI.
