# B1 repair3 독립 소스 재검토

- 대상: ERP-PM-IMPLEMENT-20261003-B1, repair attempt 3/3. Native ai-erp-reviewer, Astra/high. usage: null.
- 기준 작업 트리: C:/Users/USER/.codex/worktrees/project-management-redesign/AI ERP, base b6603269800352073e68c6e76fe4bedf516c5ba6. 현재 미커밋 B1 후보를 검토했다. 프런트엔드는 이 판정의 범위 밖이다.
- 기준 계약: backend-b1-contract.md, architecture-contract.md, 승인된 UI r3, backend-b1-repair3-contract.md, 이전 backend-b1-repair2-review.md. 기존 실패 근거는 보존한다.
- **소스 게이트: FAIL / changes-requested.** 기존 9개 중 5개 CLOSED, 4개 OPEN이며, 별도 신규 결함 1개가 있다. PostgreSQL 실행 근거의 단순 대기와 아래 실제 소스·검사 누락을 구분한다. 동일 SHA CI가 성공하더라도 이 소스 결함을 해소한 것으로 보지 않는다.

## Stable key별 판정

| Stable key | 판정 | 근거 |
|---|---|---|
| B1-CANONICAL-REQUEST-HASH | OPEN, P2 | 구조화된 필드와 ASCII blank/null 정규화는 보완됐다. 그러나 ManagementRequestCanonicalizer.java:56-59는 trim 후 isEmpty를 쓰고, 실제 저장 경로 ProjectManagementService.java:161 및 ProjectManagementDefinitionAccess.java:20은 isBlank를 쓴다. U+2003 EM SPACE만 있는 문자열은 trim 이후에도 남으며 isBlank는 참, isEmpty는 거짓이다. 따라서 completionCriterion=null로 저장한 요청과 같은 requestId/version으로 completionCriterion="\u2003"을 재전송하면 저장 의미는 둘 다 null인데 해시가 달라 REQUEST_PAYLOAD_MISMATCH가 발생한다. 정의의 purpose/successCriteria도 같은 차이가 있다. 저장과 동일한 정규화를 공유하고 실제 요청의 blank/null·주변 공백 재전송이 한 번만 저장됨을 검사해야 한다. 현재 새 검사는 ASCII 정규화 문자열 비교에 머문다. 원래 구분자 충돌은 해소됐으며 중복 집계하지 않는다. |
| B1-DISABLED-CAPABILITIES | CLOSED at source | definition/bulk/work의 enabled 및 현재 role 기반 canEdit, 쓰기와 쓰기 재전송 이전 disabled guard가 보존됐다. disabled 재전송의 실제 PG 시나리오 누락은 PG-REGRESSION 키에서 다룬다. |
| B1-WORK-DEPENDENCY-ORDER | CLOSED at source | PlanItemRepository의 unfinished predecessor EXISTS가 attention 정렬에 남아 있고 priority/deadline/title/id보다 먼저 적용된다. 새 PG 소스는 날짜가 없는 predecessor를 포함하되 ALL을 사용해 기존의 부적격 TODAY fixture 문제를 피한다. 실행 성공을 주장하지 않는다. |
| B1-WORK-DATE-WINDOW | CLOSED at source | query/count 모두 coalesce(start,end) <= to, coalesce(end,start) >= from의 포괄 경계와 별도 overdue 조건을 사용한다. 단일 날짜는 점으로 취급된다. today 양 끝점·지난 점·포함 구간·overdue와 matching count 검사가 추가됐다. WEEK 양 끝점의 명시적인 양성 검사는 아직 부족하며 PG-REGRESSION의 검사 범위에서 다룬다. |
| B1-WORK-ITEM-FIDELITY | CLOSED at source, cap 결함 별도 | cap 미만에서는 실제 페이지 TASK와 직접 predecessor/successor를 shared PlanItemReadModel에 전달한다. 기존 관계·blocker·summary·forecast 계산을 사용하고 legacy snapshot의 hierarchy context는 유지한다. TASK 자손은 승인된 기존 계층 규칙상 N/A다. cap 경계의 누락은 READ-GRAPH-BOUNDS 한 건으로 집계한다. |
| B1-IMMUTABLE-AUDIT-PORT | CLOSED at source | 입력의 방어적 복사와 null을 보존하는 중첩 map/list 불변 반환이 유지된다. 관련 기존 unit 검사는 보존됐다. |
| B1-OPENAPI-CONTRACT | OPEN, P2 | 실제 생성물 backend/build/api-spec/openapi3.yaml:207-241은 range/timeZone/assignee/page/limit을 required:true로 게시한다. controller ProjectManagementController.java:16은 모두 기본값이 있어 생략 가능하다. page/limit 스키마도 실제 int와 달리 string이다. history의 page/limit도 같은 문제(yaml:879-888, controller:24)다. 파라미터 이름·enum·PATCH 오류·유효 ON_TRACK 예제는 보완됐지만 OpenApiContractTest.java:92-116은 이 optional/default/type 일치를 검증하지 않는다. 실제 파라미터 기본값·필수 여부·숫자 타입 및 범위를 문서화하고 생성물에서 검증해야 한다. cap 10000 및 incomplete 반환 의미도 계약대로 API 문서에 명시해야 한다. |
| B1-PG-REGRESSION-COVERAGE | OPEN, P2 | ProjectManagementFoundationPostgresIntegrationTest.java:55-70에 별도 DB에서 실제 target10→11을 거치는 구조가 생겼다. 다만 이후 비교는 TASK title, plan target_start, saved view 존재 수와 새 테이블 비어 있음에 국한된다. 계약의 기존 TASK state/version/date, schedule identity/fields, custom workspace 값 보존을 비교하지 않는다. :86-104의 distinct-request 동일 버전 경쟁은 승자 1/낙관적 잠금 실패 1, version/audit/receipt 각 1을 확인하는 유효한 추가다. 그러나 전체 6개 테스트에 source/ledger 실패 후 rollback, unrelated/revoked membership의 work page/count 제외, disabled replay가 없다. WEEK Monday/Sunday 포함 및 count도 명시적으로 검증하지 않는다(:112). 계약에서 요구한 실제 시나리오를 추가해야 하며, 현재 결과 문서의 포괄적인 ACL/rollback 시나리오 설명은 소스가 뒷받침하지 않는다. |
| B1-READ-GRAPH-BOUNDS | OPEN, P2 | 전체 프로젝트 행 materialization과 legacy mapper의 반복 edge 조회는 제거됐고, pure context를 snapshot/page마다 재사용한다. 그러나 ProjectManagementService.java:107-116에서 incoming이 정확히 10000개이고 hasNext=false이면 remaining=0이 되어 outgoing 조회·overflow probe를 아예 건너뛴다. 이 TASK들에 추가 successor가 있어도 complete=true와 정확한 전체 관계인 것처럼 반환한다. exact-cap에서도 아직 관측하지 않은 방향에 대한 probe가 필요하다. 또한 :120-126은 페이지의 프로젝트가 아니라 모든 readable project마다 같은 neighbor ID 전체를 100개씩 반복 조회한다. 페이지가 프로젝트 A만 포함해도 B…N에 불필요한 조회를 하고 비용이 전체 권한 프로젝트 수에 비례한다. 보이는 TASK의 project별 관계로 필요한 조회를 제한해야 한다. 현재 테스트는 old full-project query 미호출과 한 predecessor 조회만 검사하며 exact-cap/overflow/empty-page/batch-scope/query-count 계약을 충분히 검사하지 않는다. |

## 신규 결함

**B1-WORK-EMPTY-PAGE-COVERAGE — P2, OPEN.** ProjectManagementService.java:106은 빈 페이지에서 `total==0 ? total : null`을 반환하면서 `complete=true`로 고정한다. 예를 들어 대상 TASK가 1개이고 page=1, limit=1이면 exact count=1을 이미 계산했는데 totalCount=null, complete=true가 된다. 이는 complete일 때의 알려진 전체 개수 계약과 맞지 않고, 정상적인 마지막 페이지 이후 조회에서 발생한다. graph 읽기 없이 실제 total을 그대로 보존하고, total=0인 첫 페이지와 total>0인 범위 밖 페이지를 각각 검증해야 한다. 이는 관계 cap의 부분 관측과 별도 분기이므로 독립 키로 기록한다.

## 재사용한 실행 근거와 한계

- tmp/pm-implementation-2026-10-03/b1/repair3-final.meta.txt: `gradlew.bat test compileIntegrationTestJava openapi3 bootJar --no-daemon`, backend cwd, JDK 25, 2026-10-03 18:47:14.7424862~18:48:31.2256593 +09:00, exit 0. stdout의 BUILD SUCCESSFUL 및 생성된 실제 OpenAPI를 확인했다. compileIntegrationTestJava는 컴파일 근거이며 실제 PostgreSQL 실행이 아니다.
- 기존 repair3 diff metadata의 exit 0 근거를 재사용했다. 검토 중 테스트·빌드·Git 검사·브라우저·외부 작업을 추가 실행하지 않았다. 소스/테스트/설정은 수정하지 않았다.
- 실제 PostgreSQL/Compose 검사는 로컬 Docker 부재로 아직 수행 근거가 없으며, backend-only 후보의 같은 SHA CI가 별도로 필요하다. 이 문서는 high-risk 최종 수락이나 PR/병합/배포 권한을 부여하지 않는다.
- 이번이 승인된 세 번째 시도다. 실패 후 다음 조치는 부모의 self-review 및 새 명시적 판단 대상이며, 검토자는 attempt4나 모델 승격을 배정하지 않는다.
- 이 파일은 부모에게 반환하는 중간 운영 검토 기록이다. Notion 게시 또는 별도 사용자용 독서 문서 생성은 수행하지 않았다.
