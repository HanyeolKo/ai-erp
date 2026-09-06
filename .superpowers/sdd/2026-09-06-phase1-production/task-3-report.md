# Task 3 report: production packaging

## Scope

Implemented the approved deployment package only: immutable multi-stage application image, production Compose, Caddy configuration, bounded deployment scripts, and a fake-runtime deployment contract test.

## TDD evidence

`scripts/tests/deployment-contract.sh` was written before the scripts. Its first attempted execution was blocked because the native Windows `bash` command routes to uninstalled WSL; final checks use the installed Git Bash at `D:/Git/bin/bash.exe`. The contract test was then expanded while implementing the scripts and is retained as the regression suite.

The green contract test exercises first deployment, blue-to-green promotion, inactive-slot health failure before the switch, public-smoke failure after the switch with rollback, invalid SHA rejection, lock contention, and static safeguards against volume deletion, arbitrary removal, secret echo, and non-project container stops.

## Changed paths

- `Dockerfile`, `.dockerignore`
- `infra/Caddyfile`, `infra/compose.prod.yml`, `infra/.env.prod.example`
- `scripts/{preflight,deploy,smoke,rollback,backup}.sh`
- `scripts/tests/deployment-contract.sh`
- `README.md` deployment section

## Validation

- `D:/Git/bin/bash.exe -lc 'for f in scripts/*.sh scripts/tests/*.sh; do bash -n "$f"; done'` passed.
- `D:/Git/bin/bash.exe -lc 'bash scripts/tests/deployment-contract.sh'` passed.
- `git diff --check` passed.

## Review fix round 1

- Release IDs now require a clean tracked worktree and exact repository `HEAD`; image builds carry and verify `org.opencontainers.image.revision`.
- DB and Redis URLs are pinned to the project-only Compose services and matching credentials. OIDC uses `APP_OIDC_ENABLED`; app-owned Flyway is disabled after external one-shot migration.
- `active-state.json` is the atomic traffic state. Caddy validates a complete candidate configuration before an atomic upstream/state switch and restores the prior state on reload/write failure.
- Rollback uses a validated local immutable image identity and starts the requested release in the currently inactive slot.
- The contract fake is stateful and covers provenance, first/subsequent deploys, failure recovery, rollback modes, secret suppression, lock, dirty/wrong HEAD, and unsafe operation absence.

## Not run locally

Docker CLI/daemon is unavailable in this Windows workspace. Therefore `docker compose ... config --quiet` and the multi-stage image build must run on the GitHub-hosted CI or the approved Linux deployment host. No SSH, GitHub access, image pull, or deployment was performed by this task.

## Review fix round 4

Final commit: `ecc29575ca275eb313f5d45ab2dfcc3c299e8925` (16 owned packaging paths only). The initial exact-path `git commit --only` was amended using an isolated temporary index containing only the new shell test's 100755 mode correction, because Git for Windows dropped the staged executable bit during `--only`. All eight shell files are verified as 100755 in the final commit tree. The report and concurrent workflow changes were excluded.

Replaced the deployment state machine, preserving frontend/backend/workflow ownership. The immutable image tag is derived after literal `.env` parsing, the clean tracked/untracked checkout and migration checksums are checked immediately before the external Flyway invocation, and all existing named resources/port publishers require exact project ownership. Paths, modes, owners and symlink components are checked before release writes. Inherited fd 9 is accepted only for the exact lock path and is re-flocked; caller environment flags do not grant lock ownership.

State is verified against checksummed immutable manifests, OCI revision labels, local image IDs/tags, the active Caddy upstream and the actual healthy app container. Rollback always uses the current inactive color, preserves the target manifest and records the former active release in atomic state. Same-active deployment performs verified public smoke without rebuilding; completed non-active deployment is rejected.

Promotion keeps exact upstream/state snapshots through both bounded public smoke rounds and all manifest/checksum/state writes. Immutable manifests use atomic no-clobber hard links. Abort restores absence or exact prior content, reloads Caddy, stops the candidate and reports recovery failure. Command-substitution ERR traps compensate only in the entrypoint process. Metadata is committed before the old slot is stopped so a metadata failure can restore traffic to an app that remains running. This ordering deliberately resolves the brief's conflicting old-stop/manifest order in favor of recoverability.

Every deployment, including the initial empty DB, produces an atomically installed, `pg_restore -l` validated custom-format backup plus dump/config/release/migration/history metadata. Per-release event logs contain stage names and timestamps only. Internal and public smoke cover readiness, SPA, configuration, info and Swagger; each public response must carry the exact release header. Both operational runbooks now describe preparation, provenance, rollback lineage, backup retention, first-deploy recovery and Linux prerequisites.

The Dockerfile's nested `backend/` COPY destinations were corrected to match `/workspace/backend`; stale static content is removed before copying the exact frontend and Swagger outputs. The runtime includes curl for bounded smoke and exposes only non-root port 8080. App Compose pull policy remains `never` and Spring Flyway remains disabled.

### Round 4 evidence

- RED: original empty-database backup returned success without `pg_dump`; the new effect assertion failed after 1 scenario / 2 assertions. The Python test process failed as expected; the initial PowerShell cleanup wrapper itself returned 0 after removing its temporary environment setting.
- RED: `D:/Git/bin/bash.exe -lc 'bash scripts/tests/error-trap-contract.sh'` exited 1 with `abort must execute exactly once`, demonstrating duplicate compensation from inherited ERR traps before the process guard.
- GREEN: bundled Python running `scripts/tests/deployment_contract.py D:/workspace/AI ERP/ai-erp-cicd` exited 0: **83 scenarios / 310 assertions**. Every actual deployment/preflight/rollback/backup/smoke entrypoint was executed by `D:/Git/bin/bash.exe`. This includes 18 injected failure boundaries, three required-call mutation probes, sequential rollback lineage, metadata/image/container tampering, strict unknown-command rejection and safe-operation/secret checks.
- GREEN: `CONTRACT_RED_PROBE=1`, `TEST_PYTHON=<bundled Python>`, `D:/Git/bin/bash.exe -lc 'bash scripts/tests/deployment-contract.sh'` exited 0: **9 scenarios / 39 assertions** for the original empty-DB RED case plus first-deploy reload/public/manifest/state recovery and successful retry.
- GREEN: `D:/Git/bin/bash.exe -lc 'bash scripts/tests/error-trap-contract.sh'` exited 0: **1 scenario / 2 assertions**. The public shell contract launcher now includes this regression check.
- Local total across these runs: **93 scenarios / 351 assertions**. This is executed coverage, not a claim that the fake environment substitutes for Linux or Docker.
- `D:/Git/bin/bash.exe -lc 'for f in scripts/*.sh scripts/tests/*.sh; do bash -n "$f" || exit; done'` exited 0.
- `git diff --check` exited 0. Operational shell files and both shell contract entrypoints use Git mode 100755.

### Remaining execution gates

No local Docker CLI/daemon, image build, real Compose config, PostgreSQL/Flyway process, container networking, or Caddy process was exercised. No installed WSL distribution or Linux flock executable is available. The suite contains real Linux competing-flock, inherited-descriptor, ownership/mode and symlink checks, but Windows explicitly skips those Linux-only checks and uses strict command doubles for filesystem metadata/locking. These gates must execute on Linux CI or the approved host before deployment acceptance. CI's Compose config check must supply `APP_IMAGE=ai-erp:config-check` because `.env.prod.example` intentionally no longer supplies an application image.

No SSH, GitHub or external network calls were made. Source changes are restricted to the owned packaging paths. This local report is excluded from the commit; it is tracked by the shared checkout despite the brief describing it as ignored.

## Review fix round 5

Final closure commit: `9927713ccb2b8c80c8676c47de20d6b45ea890e4` (five production-packaging files only).

Independent round-4 review identified three Important defects and one Minor harness gap. The closure now:

- lets rollback recover a stopped, unhealthy, or absent active application while still rejecting state, upstream, manifest, ownership, and image-identity disagreement;
- keeps a candidate running and preserves transaction evidence whenever compensating Caddy reload is not confirmed, while normal compensation still stops the unused candidate and cleans temporary files;
- sets `SERVER_FORWARD_HEADERS_STRATEGY=framework` on the shared app service so TLS-terminating Caddy supplies the public HTTPS scheme used by Spring Security and OIDC callbacks; and
- replaces `rm` in the deployment harness with an exact path/options boundary double and proves an injected foreign-sentinel deletion is rejected without deleting the sentinel.

### Round 5 evidence

- Focused RED to GREEN: compound Caddy recovery changed from a stopped live-routed candidate to **3 scenarios / 26 assertions** passing.
- Focused RED to GREEN: stopped and unhealthy active applications initially blocked rollback; stopped, unhealthy, and absent-active recovery plus strict deployment and corrupt-image rejection now pass **12 scenarios / 61 assertions**.
- Forwarded-header contract changed from **1 / 1 failing** to **1 scenario / 2 assertions passing**.
- Unsafe-delete mutation changed from deleting the sentinel after **2 / 4** to an explicit exit 97 with the sentinel preserved, **2 scenarios / 8 assertions passing**.
- Final public Git Bash launcher: Python state suite **97 scenarios / 399 assertions**, plus nonrecursive ERR compensation **1 scenario / 2 assertions**, all exit 0.
- Git Bash syntax checks, owned diff checks, and executable-mode checks passed; all eight shell files remain mode 100755.

Fresh independent round-5 review and hosted Linux/Docker execution remain the acceptance gates. The server environment whitelist was aligned separately without disclosing values, and no existing container was changed during this source fix.
