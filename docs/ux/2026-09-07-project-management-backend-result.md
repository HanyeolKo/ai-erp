# Project management redesign backend execution receipt

- Task: project-management-redesign / backend r3 plus bounded participant identity revision2
- Contract: `docs/ux/2026-09-07-project-management-backend-contract.md`
- Plan/review: `docs/ux/2026-09-07-project-management-redesign-plan.md`, `docs/ux/2026-09-07-project-management-redesign-review.md` (`ui-plan-review: PASS`)
- Execution model: `gpt-5.6-luna`, `high` reasoning, current implementer invocation; fallback because the approved default `gpt-5.3-codex-spark` five-hour usage was exhausted (`usedPercent=100%`).
- Status: `ready-for-review`; final verdict remains parent reviewer-owned. This receipt records the canonical `gpt-5.6-luna`/`high` implementer invocation and the approved Spark quota fallback.

## Delivered

- Direct `POST /api/v1/projects` accepts a trimmed 1–200 character name with optional `groupId` and `requestId`. Missing `groupId` bootstraps a private group, creator `OWNER`, project, and creator `MANAGER` in the caller transaction. Existing group creation authorization remains enforced.
- Request replay uses a user/request UUID mapping and PostgreSQL transaction advisory lock. Matching payload returns the existing project and role; mismatched payload conflicts. The mapping is persisted with the creation records.
- Added reusable project share invitation persistence and API: manager-only GET/POST/DELETE lifecycle, secure 16-character Crockford code generation, seven-day expiry, rotation and revocation, verified-user preview, and idempotent MEMBER join. Join locks the project first, reloads the invitation afterward, preserves existing roles, and records one acceptance event only for a new member.
- Added bounded internal `IdentityProfiles` reads. Member and current-user responses include display name and email, with neutral/fallback display handling. Role mutation returns the same profile shape.
- Schedule detail now enriches only the authorized, project-scoped schedule response with one bounded profile lookup for the stored internal participant IDs (maximum 200). List, dashboard, and mutation responses keep nullable identity fields without profile fanout. Missing profiles and external participants retain null identity fields.
- Project role mutation now locks the project and prevents concurrent last-manager demotions. Legacy email invitation creation no longer requires unrelated group membership while retaining project/group matching and project-manager authorization.
- Added V7 additive migration and registered it in the strict deployment migration whitelist/checksum.
- REST Docs/OpenAPI covers new routes, nullable safe states, DELETE 204, direct creation request fields, and generated `frontend/src/api/generated.ts` through `pnpm api:generate`.
- The actor demotion lock race now releases the holder before bounded executor termination via `stopExecutor`; V6 migration assertions no longer query V7-only tables. The identity revision adds exact 200-ID, missing-profile, authorized-before-lookup, and no-list/dashboard-profile-call tests.

## Verification evidence

- `D:\onedrive\Documents\ChatGPT\AI ERP\.tools\jdk-25.0.4.1+1\bin\java.exe -version` — exit 0; Temurin 25.0.4.1.
- `backend/gradlew.bat test --tests com.aierp.BoundedReadRegressionTest --no-daemon` with `JAVA_HOME` set to the project JDK 25 — exit 0; bounded schedule identity tests passed.
- Root follow-up `openapi3` and `compileIntegrationTestJava` with project JDK 25 — exit 0; 84 tests, 0 failures/errors across 20 suites, including the identity and r3 repair sources (`tmp/project-redesign-backend-identity-check.log`).
- `pnpm api:generate` after the identity DTO change — exit 0; OpenAPI validation and TypeScript generation passed (`tmp/project-redesign-api-identity-generation.log`).
- `backend/gradlew.bat integrationTest` with project JDK 25 — exit 1 at Testcontainers initialization because Docker is unavailable on this host. No PostgreSQL/Redis integration behavior is claimed as executed.
- `python scripts/tests/deployment_contract.py .` — migration focus passed 4 scenarios / 23 assertions (`tmp/project-redesign-migration-final.log`); the full deployment contract passed 145 scenarios / 560 assertions (`tmp/project-redesign-deployment-tests.log`).
- Integration test sources compile under Java 25 and now include: same-user concurrent request replay with one group/project/request, same-key different-actor independence, injected post-write direct-creation rollback with final group/OWNER/project/MANAGER/request counts, actual-service manager/member/viewer/outsider and no-group-manager permission checks, seven-day/rotation/revocation/expiry and inactive metadata safety, ASCII grouping with Unicode `ß` and tab rejection, bounded 200-ID profile reads, simultaneous same-user join membership/event idempotency, deterministic project-lock rotation/revocation/expiry invalidation races, concurrent self-demotion with one manager retained and post-demotion invite denial, and V6→V7 legacy preservation with empty new tables. `integrationTestClasses` passed under Java 25; Docker CI must execute the PostgreSQL assertions. No PostgreSQL integration behavior is claimed as locally executed.

## Changed paths

- Backend: `backend/src/main/java/com/aierp/group/api/GroupAccess.java`, `backend/src/main/java/com/aierp/identity/api/CurrentUserController.java`, `backend/src/main/java/com/aierp/identity/api/IdentityProfiles.java`, project entities/repositories/services/controllers, and `backend/src/main/resources/db/migration/V7__add_project_share_invitations.sql`.
- Verification: affected REST Docs/unit tests, `backend/src/integrationTest/java/com/aierp/Phase1PersistenceIntegrationTest.java`, and `scripts/lib-deploy.sh`.
- Generated interface: `frontend/src/api/generated.ts` from the existing command; no handwritten generated code.

## Acceptance mapping

- B01/B02: direct bootstrap, request replay/mismatch, same-key actor isolation, existing-group authorization, and injected transaction rollback oracles are compiled; PostgreSQL execution is pending Docker CI and must be treated as pending, not passed.
- B03/B04/B05: actual service permission checks, secure code/expiry/rotation/revocation, metadata-safe preview, reusable cross-account join, same-user idempotency, and lock invalidation race oracles are compiled; PostgreSQL execution is pending Docker CI and must be treated as pending, not passed.
- B06/B07: self-demotion/authority-loss, profile bounds, current-user fields, and auth/CSRF paths are compiled or covered by Java 25 unit/OpenAPI checks; concurrent PostgreSQL execution is pending Docker CI.
- B08/B09: the V7 migration preservation oracle and strict V7 whitelist/checksum mutation coverage are compiled/updated and deployment-contract evidence passed locally. Additive REST Docs/OpenAPI and generated client passed locally. PostgreSQL integration execution remains pending Docker CI.
