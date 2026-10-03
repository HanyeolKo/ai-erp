# Attempt 6 verification preparation (UNAUTHORIZED / UNASSIGNED)

- Purpose: parent-owned correction preparation after both attempt-5 source reviews returned `FAIL / changes-requested`.
- Status: proposed attempt 6 is **UNAUTHORIZED and UNASSIGNED**. This file is not a contract, dispatch, verdict, count reset, model escalation, or release approval.
- Frozen input: `C:/Users/USER/.codex/worktrees/project-management-redesign/AI ERP`, base `ac7ff1160c30d89d7aa41b2a5339f238af397eac` plus completed attempt-5 source.
- Authority retained by parent: design, scope, risk, dispatch, review, acceptance, CI, browser evidence, and release. Preserve all original five `FAIL` records.
- External mode: `offline-contract-only`; no live ERP/Google/provider claim is available or needed for this preparation.

## B1 minimal correction proposal

1. Keep the production null-`q` repair in `ProjectManagementService.java:87-101` and the four empty-String guards in `PlanItemRepository.java:16,27,35,44` unchanged.
2. In `ProjectManagementFoundationPostgresIntegrationTest.java:140-164`, `"Task"` matches the base title and the authorized second row's description. Change the assertion at `:155` to `totalCount=2` and both `item`/`otherItem`; retain `"other task description"` as the description-only one-row assertion. The omitted-`q` ALL/TODAY and post-revoke page/count assertions remain.
3. At PG `:87-88`, replace timestamp substring checks with exact instants. Read `starts_at`/`ends_at` as `OffsetDateTime` (or another JDBC typed instant) and compare `.toInstant()` with `Instant.parse("2030-01-01T10:00:00Z")` and `...11:00:00Z`; do not rely on JVM display zone or truncate fractions.
4. At PG `:119`, retain the unique fresh-UUID CHECK fixture but assert the intended receipt violation: Spring data-integrity wrapper plus deepest SQL exception SQLSTATE `23514` and the generated constraint name. Only then assert exact source map, audit/receipt counts, and no failed-request receipt. An arbitrary `Throwable` must not establish rollback provenance.
5. `ProjectManagementFoundationApiDocumentationTest.java:45` already sends `healthReason` and `healthAsOf`. In `OpenApiContractTest.java:112-115`, use a request-example helper that reads `requestBody/content/application/json;charset=UTF-8/examples/{name}/value`, rather than response `200`. The actual generated example is `backend/build/api-spec/openapi3.yaml:518-523`; its REST Docs source is `backend/build/generated-snippets/project-management-definition-patch/resource.json` under `request.example` (the rendered request is `http-request.adoc`).
6. Keep `OpenApiWorkspaceSchemaNormalizer.java:54-104`; production validation already uses exact integer conversion and validates the four records before replacement. Add byte-preservation negative fixtures in `OpenApiWorkspaceSchemaNormalizerTest.java` for: limit metadata default `51` versus `50`; fractional metadata `Max`; fractional metadata default; conflicting duplicate `Max`; generated maximum `100.5`; fractional generated default; generated type other than `integer`; generated `required=true`; one missing metadata record; and one missing generated query parameter. Retain the existing fractional `Min`, duplicate `Min`, generated minimum, missing-file, and idempotence cases.

## F1 minimal runtime guard proposal

1. Limit changes to `TaskQuickCreate` reconciliation in `ProjectPlan.tsx:265-290`. Before reading variables, require `candidate.options.mutationKey` to equal the existing `operationKey` (`["project-task-create", ...draftKey]`). Then use a runtime type guard requiring an object variables value, object `body`, string `body.requestId`, string `operationId`/`attemptId`, and numeric `sessionGeneration`. Only guarded candidates may enter the existing request/operation/attempt/session comparisons.
2. Do not add a global mutation helper or change other mutation families. The counterexample is the existing update mutation at `ProjectPlan.tsx:389`, whose variables are `{item,field,value}` and have no `body`; it must coexist in the mutation cache without throwing when quick-create reconciliation runs.
3. Add a PM regression that completes a real existing TASK update through `PATCH /api/v1/projects/p1/plan/items/task-1`, then submits quick-create. Assert no render/effect exception and one correct create POST. This exercises the current `http().on(...)` route registry and `server.calls`; no invented transport helper is needed.

## F1 required lifecycle/cache fixtures

1. `pm-default-workspace.test.tsx` currently has 21 tests. Replace the five-minute case at `:175-179`: enter draft, navigate away so the draft query is inactive, then enable fake `setTimeout`/`clearTimeout` as well as `Date`, advance beyond five minutes inside `act`, and return. Advancing only fake `Date` before leaving does not exercise inactive GC scheduling. Preserve `http.ts:3,14-15` cleanup/system-time behavior.
2. `session-boundary.test.tsx` currently has 9 tests and already provides `beginSessionBoundary`, `monitorSessionCleanup`, `monitorMutations`, old/new mounts, and deferred responses. Add one TASK case: hold `POST .../plan/items`, terminate the current session through the existing App 401 path, assert the old-generation `["project-task-draft", generation, "p1"]` query is removed, resolve the old POST, and assert it neither recreates draft data nor changes the clean/login UI.
3. Strengthen current-403 coverage with cached success first: load `/projects/p1/plan` so plan and members caches exist, enter an own quick-create draft, then make the overview TASK read return 403. Capture the App `QueryClient` using the existing `QueryClient.prototype.removeQueries` spy pattern. Assert `keys.plan("p1")` and `keys.members("p1")` data are absent, project GET was repeated, TASK actions stay frozen, and the same-generation draft query still contains the exact title/state.
4. Keep unknown retry body/UUID, 409 edited/new UUID, success/new UUID, TASK0+schedule1, partial predecessor, and plan500 assertions. Baseline `21` PM tests, `9` session tests, and `355` total PASS are historical attempt-5 counts; they do not substitute for these named fixtures or predict the later total.
5. Existing synthetic browser fixture routes are `current-plan403`, `plan-500`, `task-empty`, `delayed-create`, `unknown-response`, and `conflict` in `tmp/pm-implementation-2026-10-03/browser-fixtures-s1.mjs`. They may support a later parent browser checklist but cannot replace source regressions or live evidence.

## Verification status

- No tests, builds, PostgreSQL containers, Git commands, browser/server runs, CI, provider calls, or helper/config/source edits were executed. Only this English intermediate preparation document was written.
- A future parent-authorized implementer must capture each named failure first, run affected checks, then the parent-selected final suites; corrected real PostgreSQL and same-SHA CI remain pending.
