# Task 1 backend report

## Status

`DONE_WITH_CONCERNS`: the bounded persistence/principal foundation is committed and its unit/OpenAPI/bootJar checks pass using the supplied Java 25 toolchain. Docker remains unavailable, and the complete Task 1 domain/API inventory is not implemented in this partial handoff.

## RED evidence

1. Added `MeApiTest.returns_the_authenticated_principal_without_a_production_header_fallback` before adding `CurrentUserController`. The intended initial failure was that `/api/v1/me` was denied by the pre-existing security policy.
2. Command: `backend\\gradlew.bat test --tests com.aierp.MeApiTest`
3. Exit code: `1` before test discovery. Gradle reported: `Cannot find a Java installation ... matching: {languageVersion=25 ...}`.

The environment failure means this is not valid behavioral RED evidence. After Java 25 became available, the focused test passed (`exit 0`), but it was no longer possible to observe its pre-implementation behavioral failure without rewriting committed production code. Do not treat this as complete real TDD evidence.

## Implemented bounded changes

- Added EXPAND-only Flyway `V2__create_phase1_tables.sql` with all Phase 1 table names, PostgreSQL UUID columns, `TIMESTAMPTZ`, enum checks, and local indexes. Cross-schema relationships are represented by UUID values rather than cross-schema foreign keys.
- Added `GET /api/v1/me`, authenticated only through Spring Security's actual principal. There is no header or dummy-user fallback.
- Added a focused MockMvc behavior test and success REST Docs resource generation for `/api/v1/me`.

## Changed files

- `backend/src/main/resources/db/migration/V2__create_phase1_tables.sql`
- `backend/src/main/java/com/aierp/platform/web/SecurityConfiguration.java`
- `backend/src/main/java/com/aierp/identity/api/CurrentUserController.java`
- `backend/src/test/java/com/aierp/MeApiTest.java`

## Commands and evidence

| Command | Exit | Result |
| --- | ---: | --- |
| `java -version` | 0 | Oracle JDK 21.0.7 only |
| `gradlew.bat test --tests com.aierp.MeApiTest` | 1 | blocked before test discovery by missing Java 25 |
| `JAVA_HOME=<supplied JDK 25>; gradlew.bat test --tests com.aierp.MeApiTest` | 0 | focused principal endpoint test passed |
| `JAVA_HOME=<supplied JDK 25>; gradlew.bat test openapi3 bootJar` | 0 | all unit tests, generated OpenAPI, and boot jar passed |
| `docker version --format '{{.Server.Version}}'` | unavailable | Docker executable is not installed |
| `git diff --check` | 0 | no whitespace errors |

`integrationTest` was not run because Docker is unavailable.

## Self-review

- Authorization: `/api/v1/me` is authenticated; all remaining application paths remain denied by default. No production authentication fallback was added.
- Stale revision handling, schedule authorization, invitations, notifications, Calendar projection behavior, problem responses, and the remaining Phase 1 API inventory are **not implemented** in this partial handoff.
- Credentials: no secrets, credentials, OAuth client values, or tokens were added.
- Data exposure: current-user response is limited to principal name and granted authorities.
- Module boundary: identity API depends only on Spring Security/web types; migration avoids cross-schema foreign keys.

## Assumptions and concerns

1. `TIMESTAMPTZ` fulfills the UTC storage requirement; API serialization still needs integration coverage.
2. The supplied Java 25 path was used only for this validation shell; a durable local toolchain configuration may still be needed for later runs.
3. This commit is deliberately incomplete against the full Task 1 brief. A further approved implementation unit is required for the domain entities, repositories, services, all schedule/invitation/calendar workflows, error contract, OIDC provisioning, and their REST Docs coverage.
