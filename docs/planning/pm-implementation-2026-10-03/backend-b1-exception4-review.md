# B1 attempt4 독립 소스·근거 검토

- 대상: `ERP-PM-IMPLEMENT-20261003-B1`, 승인된 예외 attempt4, exception contract r3. Native `ai-erp-reviewer`, Astra/high, usage `null`.
- 작업 트리: `C:/Users/USER/.codex/worktrees/project-management-redesign/AI ERP`. 부모가 제시한 커밋 `8d74efc37d34949053a974337128099ff8ce224b`와 현재 미커밋 B1 후보를 검토했다. Git을 실행하지 않았으므로 커밋 식별자는 부모 근거를 사용한다. F1은 범위 밖이다.
- **소스 게이트: FAIL / changes-requested.** 기존 키 2개 OPEN, 4개 CLOSED at source다. CLOSED는 아래 필수 회귀검사까지 모두 충족했다는 뜻이 아니다. 실제 PostgreSQL 및 배포 계약 CI는 PENDING이며 high-risk 최종 수락은 불가하다. 이전 attempt1/2/3 FAIL을 보존한다.

| Stable key | 판정 | 독립 근거 및 남은 조건 |
|---|---|---|
| B1-CANONICAL-REQUEST-HASH | CLOSED at source | `ManagementRequestCanonicalizer.java:56`의 trim/isBlank를 정의 저장 `ProjectManagementDefinitionAccess.java:20`과 TASK 저장의 trimOrNull이 공유한다. `ProjectManagementFoundationTest.java:46`은 실제 서비스의 null→U+2003 재전송에서 save/audit/receipt 호출이 각각 한 번임을 검사한다. ASCII 재전송, 주변 공백 재전송, 반환 버전 1 검사는 아직 부족하다. |
| B1-OPENAPI-CONTRACT | OPEN, P2 | 생성 YAML의 optional/default/integer/min/max는 수정됐다. 그러나 `ProjectManagementFoundationApiDocumentationTest.java:45`와 실제 `build/generated-snippets/project-management-definition-patch/http-request.adoc`는 ON_TRACK 요청에 healthReason/healthAsOf를 여전히 누락한다. 실제 정의 서비스는 이를 거절한다. `OpenApiWorkspaceSchemaNormalizer.java:69-73`은 metadata와 schema의 default를 비교하지 않는다. metadata default만 51로 바꾸어도 거부하지 않는 경로다. 기존 minimum=0.5도 intValue로 0으로 취급한다. `OpenApiWorkspaceSchemaNormalizerTest.java:13-41`에는 결과 문서가 주장하는 conflict 검사와 default/noninteger 거부 검사가 없다. |
| B1-PG-REGRESSION-COVERAGE | OPEN, P2 | 아래 실제 PG 소스 결함과 필수 검사 누락이 남았다. 컴파일 성공으로 닫을 수 없다. |
| B1-READ-GRAPH-BOUNDS | CLOSED at source | `ProjectManagementService.java:107-143`은 exact10000에서 outgoing을 probe하고 visible project별 100개 이하 이웃 배치를 조회한다. pure mapper와 legacy context 재사용도 유지된다. `FoundationTest.java:60-88`은 cap+extra와 프로젝트 A 범위를 검사한다. exact-cap/no-extra, incoming overflow, 배치 크기, 아래 cap의 양방향 사실에 대한 지정 회귀검사는 없다. |
| B1-WORK-EMPTY-PAGE-COVERAGE | CLOSED at source | `ProjectManagementService.java:106`은 이미 계산한 total을 보존한다. `FoundationTest.java:90-97`은 total1/page1의 빈 결과와 graph 미호출을 검사한다. genuine total0 및 observedCount/hasNext 명시 검사는 없다. |
| B1-DEPLOY-MIGRATION-CONTRACT | CLOSED at source | `scripts/lib-deploy.sh:123-132`는 정확한 V11 파일과 총 10개를 검증하고 checksum에 포함한다. `scripts/tests/deployment_contract.py:505-579`는 missing-V11, 내용 변경, build 이후 변경과 Flyway 미호출을 검사한다. 기존 V8/V9/V10 검사를 유지했다. 실행 성공은 별도 CI 대기다. |

PG 근거는 `backend/src/integrationTest/java/com/aierp/ProjectManagementFoundationPostgresIntegrationTest.java`에 있다.

1. `:122-127`은 disabled 서비스를 직접 생성한다. 테스트에 트랜잭션이 없고 `ProjectAccess.java:17`의 lockProject는 MANDATORY이므로, 실제 실행에서는 disabled guard 이전에 IllegalTransactionStateException이 발생한다. 기대 메시지 PROJECT_MANAGEMENT_DISABLED에 도달하지 못한다. 이는 소스에서 확인한 예상 실패이며 실제 PG 실행 결과로 제시하지 않는다.
2. `:108-120`은 plan_item 제목을 JDBC로 바꾸고 감사 INSERT 성공 후 직접 예외를 던진다. 실제 관리 서비스의 source/version 변경 뒤 audit/receipt 실패를 유발하지 않으며 version 복구도 비교하지 않는다. 서비스의 트랜잭션 경계가 깨져도 이 테스트는 통과할 수 있다.
3. `:69-90`은 saved-view config를 여전히 `{}`로 만들고 schedule 시작/종료 시각을 비교하지 않는다. TASK labels도 정확한 값 대신 부분 문자열로 검사한다. `:133-148`은 revoked 이후 count와 페이지 경계를 검증하지 않는다. 두 경쟁 시나리오 및 WEEK 월·일 양성/바깥 경계/count 검사는 유지·추가됐다.

재사용한 원시 근거의 루트는 `tmp/pm-implementation-2026-10-03/b1/exception-repair4/`다. `failure-before.*`는 네 회귀검사 실패와 exit1을 보존한다. `focused-final1.meta.txt`, `openapi-focused9.meta.txt`, `final-suite.meta.txt`는 exit0이며 마지막 stdout은 BUILD SUCCESSFUL이다. 마지막 실행은 2026-10-03 23:32:53~23:34:25, backend/JDK25에서 수행한 `test compileIntegrationTestJava openapi3 bootJar --no-daemon`이다. metadata 자체에는 command 배열이 없으므로 명령은 결과 문서와 stdout 작업 목록을 함께 참조했다.

`deployment-contract-direct.meta.txt`는 INTERRUPTED이며 Windows/WSL 제약을 기록한다. 실제 PostgreSQL, deployment-contract, 같은 SHA CI, 브라우저, 서버, 외부 제공자 검증은 이번 검토에서 실행하지 않았다. 소스·테스트·설정도 변경하지 않았다. 결과 문서의 conflict 검사 및 포괄적인 PG 완료 설명은 위 한계로 정정되어야 한다.

부모는 이 실패를 보존하고 중단 조건을 적용해야 한다. 이 검토는 attempt5, 모델 승격, 수락, Git 또는 배포를 승인하지 않는다. CI 성공만으로 OPEN 소스 키나 누락된 필수 검사를 해소할 수 없다. 이 파일은 중간 운영 판정이므로 Notion 게시를 수행하지 않았다.
