# Backend B1 implementation result

- Task: `ERP-PM-IMPLEMENT-20261003-B1`, repair attempt 3 of 3, under `backend-b1-repair3-contract.md`.
- Worktree/branch: `C:/Users/USER/.codex/worktrees/project-management-redesign/AI ERP` / `codex/project-management-redesign-20261003`; base `b6603269800352073e68c6e76fe4bedf516c5ba6`.
- Runtime: session-only JDK 25 at `.tools/jdk-25.0.4.1+1`, Gradle 9.2; authorized Luna/high execution. Status: `ready-for-review`; parent review remains authoritative.

## Implemented repair3 evidence

- `B1-CANONICAL-REQUEST-HASH`: project-owned structured canonical fields now trim text and normalize blank to null; operation/resource identity and named fields remain unambiguous. Unit coverage includes delimiter collision and blank/null plus surrounding-space equivalence.
- `B1-DISABLED-CAPABILITIES`: definition, bulk TASK, single TASK, and Work `canEdit` require enabled configuration and current rights.
- `B1-WORK-DEPENDENCY-ORDER`: unfinished predecessor attention is part of the query `ORDER BY` before paging; the unit and CI-only PG scenarios cover visible blocking facts.
- `B1-WORK-DATE-WINDOW`: query and count share inclusive coalesced point/interval overlap plus independent overdue inclusion; today/week endpoint fixtures are represented in the PG source.
- `B1-WORK-ITEM-FIDELITY`: repository-free `PlanItemReadModel` reuses the loaded snapshot context; bounded Work loads direct incoming facts before outgoing coverage, batches same-project neighbors in groups of 100, and reports incomplete/null totals on overflow.
- `B1-IMMUTABLE-AUDIT-PORT`: project-owned mutation/audit API defensively copies persistence maps and recursively exposes unmodifiable maps/lists; mutation attempts are tested.
- `B1-OPENAPI-CONTRACT`: generated REST Docs/OpenAPI checks assert Work/TASK/history query parameters, Work/assignee/priority/health enums, nonempty nested payloads, valid ON_TRACK health example, TASK and definition PATCH 400/403/404/409, and receipt 404.
- `B1-PG-REGRESSION-COVERAGE`: integration sources include isolated populated V10→V11 migration, distinct-request stale competition with ledger assertions, identical-request concurrency, revoked replay, composite FK, authority-scoped Work, and date/dependency fixtures. Docker/Testcontainers execution is pending CI.

The original Flyway namespace and semantics remain intact. The authorized project-management flag is isolated in `application.yml`, production Compose, and the example environment file. No frontend, generated artifact, provider, workflow, harness, or legacy migration edits were made.

## Checks

- Focused unit and API documentation tests: exit 0.
- `backend/gradlew.bat compileIntegrationTestJava --no-daemon`: exit 0; source compilation only.
- Required final command from `backend`: `backend/gradlew.bat test compileIntegrationTestJava openapi3 bootJar --no-daemon`; start `2026-10-03T18:47:14.7424862+09:00`, end `2026-10-03T18:48:31.2256593+09:00`, exit `0`. Exact metadata/stdout/stderr are recorded in `tmp/pm-implementation-2026-10-03/b1/repair3-final.meta.txt`, `repair3-final.stdout.log`, and `repair3-final.stderr.log`.
- Final `git diff --check` ran from the worktree at `2026-10-03T18:49:48.3838061+09:00`–`2026-10-03T18:49:48.5296101+09:00`, exit `0`; evidence is recorded in `repair3-diffcheck-final.meta.txt`, `repair3-diffcheck-final.stdout.log`, and `repair3-diffcheck-final.stderr.log`.

## Not run / limitations

- `integrationTest` was not run because Docker is unavailable locally. The V10/V11 migration, ACL, concurrency, FK, and rollback scenarios are compile-only locally and require the draft PR CI environment for live PostgreSQL evidence; no local PG success is claimed.


