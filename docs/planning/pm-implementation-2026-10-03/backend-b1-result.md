# Backend B1 implementation result

- Task: `ERP-PM-IMPLEMENT-20261003-B1`, repair attempt 3 of 3, under `backend-b1-repair3-contract.md`.
- Worktree/branch: `C:/Users/USER/.codex/worktrees/project-management-redesign/AI ERP` / `codex/project-management-redesign-20261003`; base `b6603269800352073e68c6e76fe4bedf516c5ba6`.
- Runtime: session-only JDK 25 at `.tools/jdk-25.0.4.1+1`, Gradle 9.2; authorized Luna/high execution. Status: `ready-for-review`; parent review remains authoritative.

## Attempt4 exception addendum

- Task/source: `ERP-PM-IMPLEMENT-20261003-B1`, attempt4/one human-authorized extra attempt, source `8d74efc37d34949053a974337128099ff8ce224`, native `ai-erp-implementer` Luna/high. External mode is `offline-contract-only`; JDK25 ready, local Docker absent, same-SHA PostgreSQL CI remains unchecked.
- `B1-CANONICAL-REQUEST-HASH`: shared `ManagementRequestCanonicalizer.normalize` now matches persistence `trim` + `isBlank`; service replay regression proves null then U+2003 uses one save/audit/receipt (`focused-final1.*`).
- `B1-READ-GRAPH-BOUNDS` / `B1-WORK-EMPTY-PAGE-COVERAGE`: exact-cap outgoing probe, visible-project-only batches, and known empty-page totals have focused regressions (`focused-after.*`, `focused-final1.*`).
- `B1-OPENAPI-CONTRACT`: REST Docs typed descriptors emit four `validationConstraints` records; existing normalizer reads generated `resource.json` and projects bounds. Unit metadata/idempotence/missing-record tests and generated-spec assertions pass (`openapi-focused9.*`; `backend/build/generated-snippets/*/resource.json`; `backend/build/api-spec/openapi3.yaml`).
- `B1-PG-REGRESSION-COVERAGE`: migration field preservation, rollback fixture, ACL/revocation, disabled replay, and Monday/Sunday boundaries were added to `ProjectManagementFoundationPostgresIntegrationTest`; `integration-compile.*` exit0 only. Live PG remains pending exact-SHA CI.
- `B1-DEPLOY-MIGRATION-CONTRACT`: V11 filename/count/checksum and missing/tamper guards added to `scripts/lib-deploy.sh` and `scripts/tests/deployment_contract.py`; Python AST syntax passed. Windows deployment harness was unavailable/stalled due WSL prerequisite; no Flyway/PG success claimed.

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

## Attempt4 checks

- Meaningful failure-before evidence: `tmp/pm-implementation-2026-10-03/b1/exception-repair4/failure-before.meta.txt` records exit `1` with four targeted regressions. Focused repairs then passed in `focused-after.*` and `focused-final1.*` (exit `0`).
- REST Docs metadata and normalizer follow r3: `openapi-focused9.meta.txt` records exit `0`; the generated `resource.json` files are the bounds source, and `OpenApiWorkspaceSchemaNormalizerTest` covers four records, missing metadata rejection, idempotence, unrelated-schema preservation, and conflict validation.
- Required final command: `backend/gradlew.bat test compileIntegrationTestJava openapi3 bootJar --no-daemon`, `final-suite.meta.txt`, exit `0` (2026-10-03 23:32:53–23:34:25 +09:00).
- Deployment Python AST check: `deployment-syntax.meta/stdout/stderr`, exit `0`; deployment contract execution remains unavailable/stalled because WSL is unavailable (`deployment-contract.*`, `deployment-contract-direct.meta.txt`).
- Scoped whitespace check and `git diff --check` both exit `0`; no old SQL migration, frontend, generated, harness/workflow, or config expansion was made by this attempt.
- Attempt4 status is `ready-for-review`; verdict ownership remains with the parent/reviewer. Actual PostgreSQL, same-SHA CI, Flyway, ACL, rollback, disabled replay, and WEEK-boundary execution remain unchecked locally.

- `integrationTest` was not run because Docker is unavailable locally. The V10/V11 migration, ACL, concurrency, FK, and rollback scenarios are compile-only locally and require the draft PR CI environment for live PostgreSQL evidence; no local PG success is claimed.


