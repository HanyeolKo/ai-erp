# Unified Project UX backend result r1

- Task: `consolidate-pr-17-18-22` / `unified-project-ux`; contract r1; API r2; backend owner `ai-erp-implementer`.
- Status: `ready-for-review`. Parent-selected executor: native Luna/high. No escalation, commit, push, PR, deploy, or release action performed.
- Scope: `backend/**`, `scripts/lib-deploy.sh`, and `scripts/tests/deployment_contract.py`. Frontend and preview files were not edited.

## Implemented

1. Ported PR18 schedule-workspace entities, repositories, service, controller, REST Docs/OpenAPI coverage, typed schema normalizer, query/audit integration, and prior SV-BE-001..012 repairs onto current main.
2. Preserved the project-plan domain and tests. No diff exists under `backend/src/main/java/com/aierp/projectplan`, project-plan tests, `ProjectPlanPostgresIntegrationTest`, or `ProjectAccess`.
3. Preserved deployed V9 byte identity (`V9__create_project_plan.sql` blob hash `eac36a5ac007e6af389fb93b5cb595467fa37180`) and added workspace storage as `V10__add_schedule_workspace.sql`.
4. Added V9-to-V10 PostgreSQL migration regression source, including populated project-plan/schedule data, V9 checksum stability, V10 history, preservation checks, and workspace constraints.
5. Extended deployment allowlist/checksum to exactly V1,V2,V4,V5,V6,V7,V8,V9,V10 (count 9), retaining independent V8/V9 guards and adding V10 missing/checksum/post-build tamper cases.

## Evidence

| Criterion | Evidence |
| --- | --- |
| Focused workspace unit/REST Docs/normalizer tests | 11 tests, failures/errors/skips 0; `backend/build/test-results/test/` |
| Full backend unit suite | 159 tests, failures/errors/skips 0; `backend/build/test-results/test/` |
| OpenAPI contract | 8 tests, failures/errors/skips 0; `backend/build/test-results/openapiContractTest/` |
| Generated API | `backend/build/api-spec/openapi3.yaml` (138013 bytes) |
| Integration-test compilation | exit 0; `backend/tmp-unified-backend-compile-integration.log` |
| Boot JAR | exit 0; `backend/build/libs/ai-erp-backend-0.0.1-SNAPSHOT.jar` |
| Deployment migration guards | 9 scenarios / 57 assertions, exit 0; `backend/tmp-unified-deployment-migration-final.log` |

## Commands

- `backend/.\gradlew.bat test --tests com.aierp.ScheduleWorkspaceServiceTest --tests com.aierp.ScheduleWorkspaceApiDocumentationTest --tests com.aierp.OpenApiWorkspaceSchemaNormalizerTest --console=plain` — exit 0; `backend/tmp-unified-backend-focused.log`.
- `backend/.\gradlew.bat compileIntegrationTestJava --console=plain` — exit 0; `backend/tmp-unified-backend-compile-integration.log`.
- `backend/.\gradlew.bat test compileIntegrationTestJava openapi3 bootJar --console=plain` with project JDK 25 — exit 0; `backend/tmp-unified-backend-full.log`.
- `$env:CONTRACT_FOCUS='migration'; python scripts/tests/deployment_contract.py .` — exit 0; `backend/tmp-unified-deployment-migration-final.log`.

## Checks not run / limitations

- `integrationTest` was not run locally because Docker is unavailable. Real PostgreSQL migration, authorization, concurrency, rollback, and query-plan evidence must come from the parent’s CI run; local compilation is not live integration proof.
- Frontend generation/typecheck/browser, provider, Docker image, deployment, and release checks are outside this backend lane. The parent owns the generated-client handoff and CI/release gates.

Source checkout: `C:/Users/USER/.codex/worktrees/unified-project-ux/AI ERP`; result recorded 2026-09-29 Asia/Seoul.
