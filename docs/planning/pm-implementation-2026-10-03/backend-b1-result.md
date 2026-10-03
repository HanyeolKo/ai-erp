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

## Attempt5 bounded correction (r3)

- Task/source: `ERP-PM-IMPLEMENT-20261003-B1`, authorized exception5, source `ac7ff1160c30d89d7aa41b2a5339f238af397eac`, native implementer Luna/high, worktree `C:/Users/USER/.codex/worktrees/project-management-redesign/AI ERP`. Scope was limited to Work search parameter typing, directly affected OpenAPI normalizer/docs tests, PostgreSQL regression fixtures, this result, and `tmp/pm-implementation-2026-10-03/b1/exception-repair5/`. Existing frontend and documentation edits were preserved.
- Source corrections: Work now normalizes missing/blank `q` to a typed empty string and all four Work JPQL queries use the empty-string guard; normalizer management bounds validate exact integral metadata/schema defaults and bounds before output replacement; ON_TRACK REST Docs request includes `healthReason=Reason` and `healthAsOf=2026-10-03`; direct PG fixtures cover real proxied rollback, disabled replay inside `TransactionTemplate`, nonempty saved-view/timestamp/Long preservation, and revoke-before-count/page boundaries.
- ACK/readiness evidence: `tmp/pm-implementation-2026-10-03/b1/exception-repair5/attempt5-start.meta.txt`, timestamp `2026-10-04T01:07:21.0807325+09:00`. Native collaboration transport returned `send_message_to_thread cannot send to your native ancestor`; this is recorded as a transport limitation and is not represented as a pre-edit delivery.
- Preserved environment-first attempt: the same targeted command initially exited `1` before compilation under `JAVA_HOME=C:\Program Files\Java\jdk-17` because Gradle could not find Java 25; raw evidence remains in `focused-after.meta.txt`, `focused-after.stdout.log`, and `focused-after.stderr.log`. The approved Java 25 runtime was then explicitly set per command to `D:/onedrive/Documents/ChatGPT/AI ERP/.tools/jdk-25.0.4.1+1`; version evidence is in `focused-final-jdk.stdout.log`/`focused-final-jdk.stderr.log`.
- Targeted unit/API tests with Java 25 exited `0`: `backend/gradlew.bat test --tests com.aierp.ProjectManagementFoundationTest --tests com.aierp.OpenApiWorkspaceSchemaNormalizerTest --tests com.aierp.ProjectManagementFoundationApiDocumentationTest --no-daemon`. Raw evidence: `focused-final.meta.txt`, `focused-final.stdout.log`, `focused-final.stderr.log`.
- `backend/gradlew.bat compileIntegrationTestJava --no-daemon` exited `0`; raw evidence: `integration-compile.meta.txt`, `integration-compile.stdout.log`, `integration-compile.stderr.log`.
- Required final `backend/gradlew.bat test compileIntegrationTestJava openapi3 bootJar --no-daemon` exited `0`, including `openapiContractTest`; raw evidence: `final-suite.meta.txt`, `final-suite.stdout.log`, `final-suite.stderr.log`.
- Scoped `git diff --check` exited `0`; final raw evidence: `diffcheck-final3.meta.txt`, `diffcheck-final3.stdout.log`, `diffcheck-final3.stderr.log`.
- Status: `ready-for-review`. Live PostgreSQL/Testcontainers integration was not run locally because Docker is unavailable; exact-SHA CI remains required for the corrected integration scenarios. No release, merge, or final acceptance is claimed.

## Attempt6 bounded correction (r2)

- Task/source: `ERP-PM-IMPLEMENT-20261003-B1`, authorized attempt6 at exact HEAD `961ef045090d0fc8f50785cbc671886048a2120f`, product baseline `a1eedf9f51109dba091999f213a0e36e464b382d`; actual native `ai-erp-implementer`, `gpt-5.6-luna/high`, worktree `C:/Users/USER/.codex/worktrees/project-management-redesign/AI ERP`. Scope stayed limited to the three assigned test files, this append, and `tmp/pm-implementation-2026-10-03/b1/exception-repair6/`; no production, migration, generated API, frontend, helper, config, Git, or provider changes were made by this attempt.
- Pre-edit acknowledgement and readiness: `tmp/pm-implementation-2026-10-03/b1/exception-repair6/ACK.md`; exact SHA/model/scope/JDK25 and offline-contract-only readiness recorded at `2026-10-04T02:28:49.3563712+09:00 +09:00`. Local Docker is unavailable; no PostgreSQL/Testcontainers execution is claimed.
- PG acceptance: `ProjectManagementFoundationPostgresIntegrationTest.java` now asserts the existing `Task` search returns total2 and both IDs while preserving description-only, omitted-q/range, revoke, paging, and completion assertions; migration timestamps use JDBC `OffsetDateTime` and exact UTC `Instant`s; rollback requires Spring `DataIntegrityViolationException`, deepest SQLSTATE `23514`, and the generated CHECK name. Existing CI37138282040 61/1 PG failure-before evidence remains parent-owned.
- OpenAPI acceptance: `OpenApiContractTest.java` reads `requestBody/content/application/json;charset=UTF-8/examples/{name}/value` for the management PATCH request and asserts `healthReason`/`healthAsOf` from that request example. `OpenApiWorkspaceSchemaNormalizerTest.java` adds independently named byte-preserving negative fixtures for metadata default/Max/fractional/duplicate cases, generated maximum/default/type/required cases, one missing metadata record, and one missing generated query parameter; prior Min, generated minimum/default, missing-file, idempotence, and unrelated-schema coverage remains.
- Targeted unit/API test: `backend/gradlew.bat test --tests com.aierp.OpenApiContractTest --tests com.aierp.OpenApiWorkspaceSchemaNormalizerTest --no-daemon`, exit `0`; raw `tmp/pm-implementation-2026-10-03/b1/exception-repair6/targeted.meta.txt`, `targeted.stdout.log`, `targeted.stderr.log`.
- Integration compile: first run exited `1` on an AssertJ overload ambiguity; raw `integration-compile.meta.txt`, `integration-compile.stdout.log`, `integration-compile.stderr.log`. After narrowing the SQL message assertion, `backend/gradlew.bat compileIntegrationTestJava --no-daemon` exited `0`; raw `integration-compile-rerun.meta.txt`, `integration-compile-rerun.stdout.log`, `integration-compile-rerun.stderr.log`.
- Required final command from `backend` — `backend/gradlew.bat test compileIntegrationTestJava openapi3 bootJar --no-daemon` — ran once, start `2026-10-04T02:36:48.6708595+09:00 +09:00`, end `2026-10-04T02:38:15.1027178+09:00 +09:00`, exit `0`; raw `final.meta.txt`, `final.stdout.log`, `final.stderr.log`. Scoped `git diff --check` also exited `0`; raw `diff-check.meta.txt`, `diff-check.stdout.log`, `diff-check.stderr.log`.
- Status: `ready-for-review`; independent review and parent acceptance remain required. Not run: live PostgreSQL/Testcontainers, same-SHA CI, browser/provider checks, deployment, merge, or release actions.


