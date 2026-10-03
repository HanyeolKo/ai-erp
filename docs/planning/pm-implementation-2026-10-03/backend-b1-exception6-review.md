# B1 attempt6 독립 검토

- **판정: FAIL / changes-requested.** `ERP-PM-IMPLEMENT-20261003-B1`, contract r2, high-risk task-review; native `ai-erp-reviewer`, `gpt-6-astra/high`, usage=null. 부모에게 native ACK를 전달했습니다. baseline `961ef045090d0fc8f50785cbc671886048a2120f` 및 동결된 세 테스트 파일을 검토했습니다.
- **B1-OPENAPI-CONTRACT — OPEN, P2.** `OpenApiContractTest.java:112`가 기존 response `example(...)`를 `requestExample(...)`로 교체했습니다. 요청 예제의 세 필드는 올바르게 검증하지만, 기존 응답의 `health/healthReason/healthAsOf` 단언은 사라졌습니다. 요청을 유지하고 응답 예제에서만 필드를 제거하는 반례를 더 이상 검출하지 못합니다. 계약4의 “Do not ... replace response checks”에 어긋납니다. 기존 응답 단언과 별도 요청 단언을 함께 유지해야 합니다. 새 결함 키는 추가하지 않습니다.
- **B1-PG-REGRESSION-COVERAGE — CLOSED-at-source.** PG `:89-90,207`은 typed `OffsetDateTime`을 정확한 UTC Instant와 비교합니다. `:118-131,208`는 실제 service 호출의 Spring data-integrity wrapper, deepest SQLSTATE `23514`, 고유 CHECK 이름을 확인한 뒤 resource/version/updated_at/audit/receipt 불변성과 실패 UUID 부재를 검증하고 별도 finally transaction으로 정리합니다. `:156-168`은 total2/두 ID, description-only, revoke/page/count/complete/observed 검사를 유지합니다. 실제 PG 성공을 뜻하지 않습니다.
- OpenAPI 요청 accessor `:195-197`의 경로는 정확합니다. normalizer 테스트 `:58-105`의 열 가지 독립 음성 fixture는 계약의 각 조건을 만들고 `readAllBytes/containsExactly`로 산출물 보존을 확인합니다. helper `:113-123`도 대조했습니다.
- 세 소스 SHA256과 saved patch SHA256은 모두 manifest와 일치했습니다. patch=`9DEE510992A10B344F06A4BD2D506891A8CC81CE7068BF4CCC0537C9D8477242`. 기존 네 CLOSED-at-source와 이전 다섯 FAIL은 유지합니다.
- raw ACK/meta/stdout/stderr를 직접 확인했습니다. targeted=0, 첫 integration compile=1(AssertJ overload), rerun=0, final=0, scoped diff-check=0입니다. final은 02:36:48–02:38:15 +09:00에 실행됐습니다. `test/openapi3/normalizeWorkspaceOpenApi/openapiContractTest`가 실행됐고 `bootJar/compileIntegrationTestJava`는 UP-TO-DATE였습니다. 초기 실패는 보존됩니다. 새 기능 red-before-green 로그와 별도 untracked whitespace 증거는 없습니다.
- **BLOCKER:** Docker 부재로 동일 변경의 실제 PostgreSQL/같은-SHA CI가 pending입니다. 부모가 실제 CI 증거를 확보하고 미충족 소스 조건을 해결하기 전에는 acceptance가 불가합니다. 새 시도는 승인하지 않습니다. reviewer는 테스트·빌드·Git·CI/provider·배포를 실행하지 않았습니다. 이 중간 검토는 Notion 보관 대상이 아닙니다.

증거 경로: worktree `C:/Users/USER/.codex/worktrees/project-management-redesign/AI ERP`; 소스는 `backend/src/integrationTest/java/com/aierp/ProjectManagementFoundationPostgresIntegrationTest.java`, `backend/src/test/java/com/aierp/{OpenApiContractTest,OpenApiWorkspaceSchemaNormalizerTest}.java`; raw는 `tmp/pm-implementation-2026-10-03/b1/exception-repair6/{ACK.md,targeted.*,integration-compile.*,integration-compile-rerun.*,final.*,diff-check*,diff-check-result*}`입니다. 동결 원본은 `D:/onedrive/Documents/ChatGPT/AI ERP/tmp/pm-implementation-2026-10-03/b1/exception-repair6-parent/{parent-reviewed-manifest.json,parent-reviewed-diff.patch}`입니다.

## 같은 attempt6의 실제 CI 증거 추가 — 2026-10-04

Native `ai-erp-reviewer` / `gpt-6-astra/high`가 부모에게 ACK를 전달한 뒤 저장된 원시 증거를 독립 검토했습니다. usage=null입니다. 아래 관측은 앞선 PG/CI pending 상태만 갱신하며, 원래 판정과 attempt1–6 이력을 보존합니다.

- `run.json`은 run `37142873458`이 정확한 SHA `a7ca69d63582923736d7951812632efcfa573d42`에서 completed/success임을 확인합니다. verify job `111260754683`은 `2026-10-03T18:22:20Z`에 완료됐고, harness도 성공했습니다. `watch.meta.json`의 exitCode는 0입니다. 현재 세 소스 SHA256을 다시 계산하여 기존 frozen manifest와 모두 일치함을 확인했습니다.
- 원본 HTML의 각 `index.html:22-36`에서 integrationTest **61/0 failures/0 ignored**, openapiContractTest **9/0/0**, test **194/0/0**을 확인했습니다. PG 클래스 자체는 **9/0/0**입니다. 클래스 보고서 `:101-123`은 populated V10 migration, CHECK 오류 뒤 source/ledger rollback, 검색·권한 회수·페이지 테스트의 실제 통과를 확인합니다. 따라서 `B1-PG-REGRESSION-COVERAGE`의 소스 종결에는 이제 같은 SHA의 실제 PostgreSQL 성공 증거도 있습니다.
- `verify-job.log:658,808-820`은 실제 integrationTest 및 OpenAPI 실행과 BUILD SUCCESSFUL을 기록합니다. `:1048-1049`의 frontend 결과는 30 files/358 passed입니다. API 생성, typecheck/build, Docker image build(`:5044-5048`), deployment-contract(`:460`, 156 scenarios/622 assertions), Compose 설정, Caddy(`:496`) 검사가 성공했습니다. 이는 CI 검사 관측이며 실제 ERP/Google 연동이나 운영 배포를 뜻하지 않습니다.
- artifact `11281641427`의 ZIP SHA256을 직접 계산한 결과 `88b5485f8ddc79697575b954466fab2e4160b1ca366a891cd44993b4663665e1`로 GitHub digest 및 download metadata와 일치합니다. metadata는 3,468,006 bytes와 100 members의 안전한 추출을 기록합니다. reviewer가 별도로 추출을 실행한 것은 아닙니다.

**판정은 FAIL / changes-requested로 유지합니다.** `B1-OPENAPI-CONTRACT` P2는 기존 응답 예제 단언 누락으로 OPEN입니다. 성공한 CI가 이 소스 조건을 면제하지 않습니다. PG/같은-SHA CI pending은 해소됐지만 acceptance는 여전히 불가합니다. 추가 시도7, 수정, Git, CI/provider, 브라우저/서버, 배포를 실행하거나 승인하지 않았습니다. 최종 acceptance와 publication은 부모 소관입니다.

추가 원시 증거의 루트: `D:/onedrive/Documents/ChatGPT/AI ERP/tmp/pm-implementation-2026-10-03/ci-attempt6/`. 검토 파일: `run.json`, `verify-job.log`, `watch.meta.json`, `artifacts.json`, `artifact-download.meta.json`, ZIP, `artifact/reports/tests/{integrationTest,openapiContractTest,test}/index.html`, `artifact/reports/tests/integrationTest/classes/com.aierp.ProjectManagementFoundationPostgresIntegrationTest.html`.
