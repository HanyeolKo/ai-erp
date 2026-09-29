# Schedule views backend final review — active attempt3 precheck

- Task `schedule-views-delivery`; reviewer `/root/views_backend_review`, native Astra/high; 2026-09-22.
- ACK implementation r1/API r2 and parent amendment authorizing exact V9 deployment-input allowlist/count/checksum plus regressions. Existing accepted UI prerequisite reused; frontend excluded.
- Reviewed backend snapshot `974e912` against explicit base `49d79fe553bb4048a72671b503609e2d2c38ce58`, current source/tests/generated API, final executor result and prior r2 findings. Sol attempt3 is still active; this is early feedback, not a terminal executor handoff.
- **Final independent BACKEND-scope verdict: PASS at `78821a726487b3f5bec38d2322acd73eaf620f12`, based on CI `35634074607` and the reviewed source.** SV-BE-001–012 are closed in the reviewed backend scope. Overall task, whole-CI and release verdicts are **not PASS**: frontend tests failed and frontend type/build plus Docker image build were skipped. Parent retains final task acceptance, Git and release ownership.

## Exact-SHA backend disposition, 2026-09-22

- Immutable source: `78821a726487b3f5bec38d2322acd73eaf620f12`; implementation r1/API r2, accepted UI prerequisite and parent-approved V9/OpenAPI/benchmark amendments unchanged. Parent supplied exact run/SHA linkage; inspected actual downloaded run status and reports.
- [CI run35634074607](https://github.com/HanyeolKo/ai-erp/actions/runs/35634074607) status JSON confirms `./gradlew clean test integrationTest openapi3 bootJar` **success** and `pnpm api:generate` **success**. Deployment-contract and Caddy checks also succeeded. The workflow's overall conclusion is **failure**, caused by frontend:test; frontend:typecheck, frontend:build and docker build are explicitly skipped.
- Independently read report counters: **146 unit/REST Docs tests, 8 OpenAPI contract tests, 47 PostgreSQL integration tests; zero failures and zero ignored tests in each suite**. This is actual PostgreSQL execution, not integration compilation. Boot JAR generation and normalized API generation completed in the successful backend Gradle step.
- Actual current-statistics application plans pass unchanged limits: typed page **424 inclusive root blocks / 0.422ms / 100 rows**; typed group **106 blocks / 0.285ms / 1 row**. Limits remain5,000 blocks, under1,000ms and at most100/10 rows. Raw before-ANALYZE diagnostics are also retained: page5,606 blocks and group10,829. Performance evidence is bounded to the representative fixture with refreshed statistics; no general production-scale or stale-statistics guarantee is inferred.
- Downloaded evidence root: `C:/Users/USER/AppData/Local/Temp/schedule-views-ci-35634074607-artifacts/`. Counter sources: `reports/tests/test/index.html`, `reports/tests/openapiContractTest/index.html`, `reports/tests/integrationTest/index.html`. Plan/SQL/bind source: `reports/tests/integrationTest/classes/com.aierp.ScheduleWorkspacePostgresIntegrationTest.html`. Generated artifact `api-spec/openapi3.yaml` is116,528 bytes. Workflow evidence: sibling `schedule-views-ci-35634074607-status.json`.

| Backend criterion | Final evidence disposition |
| --- | --- |
| Contract, ownership, revisions and scope | PASS; detailed parent contract and amendments, native assigned executor escalation, independent Astra/high review; source scope and authorized build/script additions inspected. |
| Permissions, typed values, archive/no-op and optimistic concurrency | PASS; reviewed repairs and executed unit/PostgreSQL cases close SV-BE-001–005/012. |
| Additive migration/data preservation/constraints | PASS; actual PostgreSQL migration and independent named CHECK/FK cases close SV-BE-006; exact eight-file deployment-input contract passed. |
| Query correctness, isolation, grouping and bounded plan evidence | PASS; actual application query/snapshot/typed SQL and measured plan cases close SV-BE-007/008/010 under the recorded statistics precondition. |
| API generation/schema fidelity | PASS; exact normalized nullable scalar/map semantics and operation requiredness checks executed, API generation succeeded; SV-BE-009 closed. |
| Verification attribution and preserved failure evidence | PASS; baseline output-path diagnosis and subsequent real failed/repaired runs retained; SV-BE-011 closed. |
| Overall UI/task/release/Docker image | NOT ACCEPTED by this backend review; failed frontend gate and skipped later gates remain with the parent and frontend/release reviewers. |

- Parent disposition: backend evidence may be accepted/reused for this exact SHA or demonstrably unchanged backend artifacts. Continue resolving the separate frontend/task gates before any merge/release readiness decision. The known frontend review findings and pending retry exception are not waived.
- Reviewer reran no tests. Docker image execution/build, frontend interaction/browser and deployment were not verified here; full Docker stage is specifically **not run in this CI**. This PASS is backend-only and does not claim deployment, whole-CI success or release readiness.

## Historical source and runtime reconciliation (superseded by final disposition above)

### Benchmark-statistics fixture review at 78821a7

- Narrow independent diff `e0e35c0..78821a7`: one integration-test line and two evidence-document lines changed. No production query, index, dependency, configuration or deployment change.
- Parent reports real e0e35c0 CI `35632110988`: page6,307 root blocks/3.185ms and group11,933/5.384ms; both still exceed5,000 blocks. Plans use the project-leading index but show estimates1 versus actual120 and repeated range scans. Earlier failures remain preserved rather than relabeled as passes.
- The repaired benchmark emits the actual captured page/group SQL, binds and EXPLAIN ANALYZE/BUFFERS JSON before statistics refresh, runs ANALYZE on project_schedule, schedule_property, schedule_property_option and schedule_property_value, then emits and gates the **same captured SQL and arguments** afterward. Fixture records, query results and predicates are unchanged.
- Acceptance bounds remain execution under1,000ms, at most100 page rows/10 group rows, and at most5,000 inclusive root shared hit+read blocks. No planner forcing, cost adjustment, threshold increase or data reduction. The real application query still executes before measurement; current-statistics EXPLAIN executes its captured SQL afterward.
- Rationale is supported by PostgreSQL's requirement for current relation statistics when assessing plans, with manual ANALYZE appropriate after substantial table-content changes: [PostgreSQL 17 EXPLAIN notes](https://www.postgresql.org/docs/17/sql-explain.html). This is an explicit benchmark precondition; it does not establish production statistics freshness or a performance pass by itself.
- Source disposition: bounded fixture correction is consistent with the parent authorization and preserves intended assertions. Reused reported targeted unit/integration compilation success; no tests rerun. Final runtime verdict remains pending real CI of this exact SHA, including the emitted pre/post plans and unchanged bounds.

### Bounded query-performance repair over b5ceac6

- Actual CI `35629948711` executed47 integration tests, with46 passing and1 failing. Independently inspected its downloaded `ScheduleWorkspacePostgresIntegrationTest.html`: typed page root shared blocks **9,823**, group **10,063**, both above the unchanged5,000 bound. Plans show property-value sequential scans repeated120 times. These are runtime production-query findings under SV-BE-010, not fixture failures.
- Raw evidence: `C:/Users/USER/AppData/Local/Temp/schedule-views-ci-35629948711-artifacts/reports/tests/integrationTest/classes/com.aierp.ScheduleWorkspacePostgresIntegrationTest.html`. Captured application SQL omits value-row project predicates while existing composite indexes lead with project_id.
- Inspected exactly three production line changes in `ScheduleWorkspaceService`: `appendFilters` now correlates `v.project_id=s.project_id` in every property branch (including both NE subqueries and empty/not-empty); `appendSort` does so for scalar and SINGLE_SELECT option-order subqueries; `groups` adds it to the value LEFT JOIN's ON clause.
- Scope/semantics: predicates are redundant with the existing composite project/schedule FK for valid data and expose the index's leading project key. Placement in the LEFT JOIN ON clause preserves unset groups; NE/unset truth, option order, nulls-last and UUID tie-break remain unchanged. No placeholders or argument order changed. No schema/index, threshold, authorization, time-window or pagination changes observed.
- New focused unit regression checks all compiler branches and matches every property-value scan with its project correlation across count/page/group SQL. It is a structural regression; the existing real typed-query, isolation and captured-plan tests remain the runtime acceptance evidence.
- Source disposition: bounded repair consistent with parent authorization, no defects found in this delta. Performance improvement is a hypothesis until new real CI executes the repaired query under the original bounds. Reviewer ran no broad or duplicate tests; stable commit and new CI evidence remain required.

### Narrow real-CI fixture repair review at 42ed0c1

- Independently inspected diff `fd40c89..42ed0c199b028a23f9e610cee169d645a2e9dbe0`. Only executable change is eight changed lines in `ScheduleWorkspacePostgresIntegrationTest.java`; three other changes are reporting documents. No production, migration, build/configuration, deployment-script or frontend changes in this delta.
- EXPLAIN capture now uses Mockito `getRawArguments()[2]` / `[1]` for the original Object[] vararg array instead of an expanded individual UUID argument. It retains real JdbcTemplate calls and the original application SQL, 120-row assertion and EXPLAIN row/buffer/time bounds. Intended plan assertions are preserved.
- Atomic-update fixture now locates the projection actually created by the legacy lifecycle and sets its baseline SYNCED/revision before invoking the operation, avoiding a duplicate insertion. It retains rollback title/participant/ACK assertions and legacy stale-write rejection, and strengthens both rollback and custom-only assertions to check projection status **and** business_revision. No behavior assertion was removed or weakened.
- Parent evidence: prior real CI `35626604956` ran47 integration tests, with45 passing and the two described fixture failures. This historical run is not an exact-source pass. New run `35628221032` is pending; reviewer did not rerun tests or poll CI. Source check of this bounded repair has no findings; final verdict still requires current run evidence.

### Normalizer safeguard reconciliation for CI checkpoint

- Narrow follow-up inspection confirms SV-BE-009's remaining safeguard repaired: raw scalar key set must equal type/description/nullable with object type and nullable=true; normalized scalar key set must equal oneOf/description and its alternatives must exactly equal the three branch maps (only string permits null). Duplicate alternatives and unexpected properties/items/required now reject.
- Raw/normalized value-map containers also enforce exact key sets; transformations remain scoped to workspace component names and recognized scalar markers/counts. Validation precedes writing the temporary artifact, so rejection leaves the input unchanged.
- Tests now cover idempotent repeated normalization, wrong count, unexpected raw keys, unchanged file on rejection, and preservation of an unrelated schema's explicit null. No broad rereview or unchanged checks rerun.
- Parent reports full local `clean test compileIntegrationTestJava openapi3 bootJar` exit0, 152 tests with no failures; uncached contract and targeted normalizer checks pass. Parent also reports `pnpm api:generate` and frontend typecheck exit0 against the normalized artifact; those are downstream integration evidence, not a frontend review by this reviewer.
- All previously reported source findings SV-BE-001–012 are reconciled at source/precheck level. Required database/runtime evidence, including actual service snapshot/EXPLAIN/named constraints and numeric no-op assertions, must still execute successfully on the exact committed source. Parent must provide stable SHA and PostgreSQL/Docker CI before the independent final verdict can be PASS.

## Resumed review of repairs after 974e912

- Parent explicitly amended implementation r1 to include `backend/build.gradle`, workspace-only JVM OpenAPI normalizer and tests, with no new dependencies or Docker changes. Scope/revision/accepted UI gate remain valid. Reviewed current working source during the executor's full local command; final immutable SHA has not yet been supplied.
- **SV-BE-002 source repaired:** DATE value writes now enforce exact four-digit ISO format and LocalDate validation; previous strict filter/status corrections retained.
- **SV-BE-012 source repaired:** `sameValue` parses typed inputs and compares NUMBER using BigDecimal.compareTo. The parsed decimal uses the numeric node's textual decimal representation; strings remain rejected. Unit coverage and PostgreSQL cases now include normally parsed integers/decimals, equivalent scale and unchanged archived numbers, asserting stable rowVersion and custom-event count. Actual PostgreSQL execution remains pending.
- **SV-BE-010 authoring defects repaired:** snapshot test calls the real proxied workspace.query, pauses after its actual count and commits a separate concurrent insertion, then asserts returned count/page/groups. Plan test captures actual application typed page/group SQL and arguments, executes EXPLAIN ANALYZE/BUFFERS JSON and asserts row, buffer and elapsed bounds over the representative fixture. Migration negative cases use fresh valid property tuples and assert SQLState plus exact named CHECK/FK, including wrong-option ownership. These now verify the intended boundaries rather than database behavior alone or masked duplicate errors; execution remains required.
- **SV-BE-009 output semantics repaired:** actual normalized `backend/build/api-spec/openapi3.yaml` inspected at 116,267 bytes. Optional request fields, create-versus-update rowVersion, nullable dashboard/groups, scalar unions and additionalProperties now match the reviewed requirements. OAS3 scalar union is one nullable string branch plus number and boolean branches, so null matches exactly one branch. Expanded OpenApiContractTest checks these semantics.
- Generation wiring: openapi3 finalizes openapiContractTest; contract test depends on normalizeWorkspaceOpenApi; normalizer depends on raw openapi3. The existing Docker api-contract stage runs openapi3 and completes finalizers before copying the artifact. Helper uses the existing JVM YAML library and restricts transformations to schedule-workspace component names and exact description markers/counts. No dependency or Docker changes observed.
- **Historical SV-BE-009 safeguard gap, now repaired as reconciled above:** the earlier normalizer permitted extra raw keys and duplicate normalized alternatives. This was reported while attempt3 remained active and corrected before the CI checkpoint.
- Reused parent/executor targeted evidence: numeric6, RESTDocs1, normalizer2 and clean generation/contract PASS. Full local clean test/compileIntegrationTestJava/openapi3/bootJar was still running when review resumed. These numbers are provided evidence, not reviewer reruns. Real PostgreSQL/Docker CI and exact stable SHA remain pending; no compilation-only acceptance.

## Earlier source-precheck evidence retained below

## Findings requiring action during the active attempt

### SV-BE-012 — HIGH — NUMBER no-op comparison uses JSON representation identity

- Evidence: `backend/src/main/java/com/aierp/schedule/ScheduleWorkspaceService.java:44,71`. Existing NUMBER values become `DecimalNode(BigDecimal)`; `applyValues` compares that node directly to the incoming JsonNode before archive validation.
- Independent dependency inspection: `javap -c tools.jackson.databind.node.DecimalNode` from the actual cached Jackson **3.1.5** JAR shows `equals` accepts only DecimalNode and delegates to scale-sensitive `BigDecimal.equals`. Normal HTTP JSON numbers can be IntNode/DoubleNode; even two DecimalNodes with equal numerical value but different scale compare unequal.
- Trigger: save NUMBER `12.5`, then submit the unchanged JSON number again. Actual: treated as a change, advances schedule rowVersion and emits a custom-value event. If the property was archived, the unchanged assignment is rejected. Expected: same typed numeric value is a no-op, including the contract's preserved archived value case.
- Existing PG no-op test (`ScheduleWorkspacePostgresIntegrationTest.java:23-25`) constructs the same BigDecimalNode `12.50` directly, bypassing normal wire deserialization and missing this defect.
- Repair: validate incoming numeric type, compare canonical decimal values numerically (`compareTo == 0`), then apply archive/new-assignment rules. Add parsed-JSON or HTTP coverage for integer/decimal/exponent/scale-equivalent numbers, no revision/event change, and unchanged archived NUMBER preservation. Keep strings distinct from numbers.

### SV-BE-009 — HIGH — generated schemas still contradict API r2

- Populated 11-operation REST Docs and the actual 134,615-byte generated artifact are now present; the route-only/stale-document defect is repaired. There are **10 paths / 11 operations**, not the 9 paths stated in the executor result.
- Remaining concrete mismatches in `backend/build/api-spec/openapi3.yaml`: query `from/page/size/to` incorrectly required at lines2041-2044; filter scalar `value` restricted to string at2092-2095; dashboard `viewId` lacks nullable:true at3333-3335; property creation incorrectly requires optional archived/options/position at3594-3599.
- Causes: `ScheduleWorkspaceApiDocumentationTest.java` helpers use required descriptors for optional wire fields; a select-only filter sample infers STRING; dashboard/group nulls are not represented. Record create also documents schedule.rowVersion as mandatory through the shared write descriptor, although only updates require it. Properties PATCH similarly requires type/options/position/name instead of their partial-update semantics.
- Repair descriptors/generated schemas to encode actual optional fields and explicit nulls, polymorphic Scalar, and create-versus-PATCH differences. Assert these semantics in OpenApiContractTest, not only operation existence/properties. Regenerate via the approved workflow and verify the consumed artifact. Do not hand-edit generated client files.

### SV-BE-010 — HIGH — expanded integration coverage still has non-probative assertions

- The suite now contains 11 workspace tests and expanded migration assertions, a substantial improvement. Atomic create/update rollback, typed SQL, option concurrency, permission revocation, dashboard persistence and event isolation have meaningful application calls.
- **Snapshot test**, `ScheduleWorkspacePostgresIntegrationTest.java:55-57`: sets REPEATABLE_READ on an independent raw connection and runs its own count/page/group SQL; workspace.query is never invoked. Reflecting an annotation plus testing PostgreSQL isolation does not verify the application's proxied transaction and mixed JPA/JDBC response. Exercise the actual service query with a deterministic concurrency barrier after its first read and assert its returned total/records/groups.
- **Query plans**, line46: EXPLAIN of a simplified fixed-field query is merely asserted nonempty. Every valid EXPLAIN satisfies this, and it captures none of the application's typed filter/group/select-order SQL. Capture actual representative application plans with an explicit acceptable plan/work bound and reproducible data, preserving evidence for review.
- **Migration constraints**, `V9ScheduleWorkspaceMigrationIntegrationTest.java:27`: wrong-type/multiple-column/type-mismatch cases reuse already-populated project/schedule/property tuples and accept any SQLException. A missing intended check can still pass through duplicate-key rejection; the zero-column case also references a nonexistent property. Use otherwise-valid fresh rows and assert the specific intended constraint/SQLState; separately test wrong-option ownership with matching SINGLE_SELECT type.
- **Wire no-op**: add the SV-BE-012 cases. A passing current suite cannot close a missing assertion.

## Stable finding reconciliation

| ID | Current disposition |
| --- | --- |
| SV-BE-001 | Source repaired: create scope helper requires writer before validation; update checks membership before view lookup and personal owner before version. New role/revocation tests authored; exact-source CI pending. |
| SV-BE-002 | Previous strict filter DATE/status issues repaired with regex+LocalDate and enum validation. Tests cover malformed dates/status. Source DATE writes still use LocalDate.parse alone rather than the exact four-digit wire restriction; align with the shared strict validator while repairing validation. |
| SV-BE-003 | Typed Timestamp/UUID binding retained; combined successful fixed/custom PostgreSQL query authored. |
| SV-BE-004 | Provider-safe conditional repository version update with flush/clear/reload replaces manual @Version assignment. Concurrent one-success/one-stale and persisted/no-op checks authored. |
| SV-BE-005 | Explicit native initial dashboard insert stores1; reload returns managed version and later updates use @Version. First/subsequent/stale/builtin/null/shared/archive tests authored. |
| SV-BE-006 | Correct typed-column and definition-type constraints retained; targeted negative-test independence still part of SV-BE-010. |
| SV-BE-007 | Source repaired with single `!` SQL escape and matching literal escaping; PostgreSQL literal %, _, ! and SQL-looking text cases authored. |
| SV-BE-008 | Non-null Unassigned label retained. |
| SV-BE-009 | Still open for schema semantics above, despite full populated route coverage. |
| SV-BE-010 | Still open for application-level snapshot, meaningful plan evidence and unmasked constraint assertions above. |
| SV-BE-011 | Remains resolved: implementation r1/API r2 and controlled build-path attribution accepted. |
| SV-BE-012 | New concrete numeric no-op/archive bug, above. |

## Deployment-input amendment and regression scope

- `scripts/lib-deploy.sh:123-132` now explicitly allowlists V1,V2,V4,V5,V6,V7,V8,V9; count exactly8; checksum explicitly includes each filename. Required regular/nonempty file, symlink and unexpected-entry checks remain. No wildcard bypass or production deployment occurred in this review.
- `scripts/tests/deployment_contract.py:497-540` adds missing-V9 rejection, moves content-checksum and post-build tamper fixtures to V9, and asserts tampered inputs never reach Flyway. Existing missing V7/V8 and unexpected/symlink cases remain. The production checksum still names V8; its separate checksum-content fixture was replaced by V9, not the production guard.
- Bounded script/source diff is consistent with parent amendment. Reuse reported syntax/migration-contract success and parent run35618634717 deployment-contract PASS; final exact-source CI still required.
- Existing schedule service diff only exposes batched summaries package-locally; repository adds scoped ID-list read; AuditEventConsumer adds WORKSPACE aggregate classification. No other legacy lifecycle implementation change observed.

## Evidence and checks not run

- Reused executor local evidence: 141 unit/REST Docs tests and 8 OpenAPI checks pass; integration compiles. Artifact inspected directly, including requiredness/nullability/scalar mismatches above.
- Parent reports earlier fef6267 run35620478790: 42 integration tests, 41 passed, one TEXT property fixture failure; the new snapshot corrects options to null. Those results are historical, not certification of974e912 or subsequent repairs.
- Reviewer performed source/artifact inspection and dependency bytecode reading only; no test reruns, PostgreSQL execution, CI polling, frontend/browser, provider calls, Git mutation, deployment or Notion writes. Pending current CI must be supplied by parent. New code/tests invalidate affected evidence and require relevant execution before final acceptance.
