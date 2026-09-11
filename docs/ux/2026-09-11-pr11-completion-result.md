# PR #11 마무리 검증

사용자가 추가한 `66acc79`, `6b94445`를 보존해 작업을 재개했다. 최종 요청은 남은 구현을 마치고 검증 후 병합·배포하는 것이다. 현재 문서는 진행 기록이며 완료 판정이 아니다.

## 확인한 결과

- 사용자 추가분의 Gmail 원자적 INSERT claim, receipt 잠금·grant barrier, Drive 저장 권한·세대 확인, Workspace HTTP 전체 deadline·오류 분류를 독립 검토했다. 이전 관련 소스 결함은 해소됐다.
- CI `34309690131`은 PostgreSQL 통합 34개 중 기존 Calendar 테스트 1개에서 실패했다. 실제 결과는 PENDING이고 과거 동기식 FAILED 기대가 남아 있었다. 재시도 선행 커밋과 worker 실패 격리를 확인하도록 테스트를 보완한다.
- 로컬 Java 25 `test openapi3 compileIntegrationTestJava`: 성공, 단위 테스트 131개 실패·오류·skip 0. 증거 `tmp/pr11-local-backend-20260911.log` 및 JUnit XML. 이후 Calendar 최종 보완은 별도 재검증 대상이다.
- frontend 209/209 통과. API 생성·typecheck·build 성공. 증거 `tmp/pr11-frontend-tests-20260911.log`, `tmp/pr11-api-20260911.log`, `tmp/pr11-typecheck-20260911.log`, `tmp/pr11-build-20260911.log`.
- 최신 main `310b374`의 하네스 변경을 병합했다. 외부 Google 검증은 실제 계정의 발송·일정 수정 없이 진행하며, synthetic 검사를 실제 연동 성공으로 간주하지 않는다.

## 남은 조건

독립 검토에서 취소 Calendar audit의 실패·lease 복구가 원래 종료 시각을 다시 연장하는 GW-I11을 확인했다. 한정 수정 및 회귀 검사 후 현재 head CI와 최종 독립 검토가 필요하다. 로컬 Docker가 없어 실제 PostgreSQL 및 배포 이미지 검증은 GitHub CI를 사용한다.

운영 도메인은 HTTP 200, release `310b37457c89e3bbc21fc0f0c59ea820f2b6d3be`, readiness UP, 기존 로그인 READY를 반환했다. 확인은 `--insecure`를 사용했으므로 공인 TLS 신뢰 검증은 아니다. GitHub self-hosted 운영 runner는 online이다. Google 활성화 플래그 및 서버 전용 암호화 키는 사용자에게 설정 여부를 확인 중이다. 코드 배포와 실제 Google 활성화는 구분한다.

## 문서 보관

로컬 문서가 기준 원본이다. Notion 기존 구현 검토 페이지에는 체크박스 `읽음=false`만 있고 현재 select `상태` 값이 비어 있어 새 읽기 대기열 정책의 `안읽음` 여부를 확정하지 못했다. 상태를 추정하거나 중복 페이지를 만들지 않고 동기화를 보류한다. 이전 갱신은 무료 블록 한도(403)로 실패했었다.

## 최종 수정 인계

구현자는 실제 Spark/high 실행으로 Calendar audit 식별을 claim·release·완료에 유지하고, 진행 중 사용자가 새로 요청한 PENDING 재시도를 보존했다. 정상 동기화의 일시 실패 재시도는 유지한다. 기존 CI 테스트는 durable PENDING 후 실제 worker의 합성 제공자 실패를 검증하며, 전용 Testcontainers DB의 이전 projection을 fixture에서 정리해 전역 worker 선택을 격리했다.

최종 Calendar 집중 테스트와 통합 테스트 컴파일은 성공했다(`tmp/pr11-calendar-final-20260911.log`). fixture 정리 한 줄은 이 실행 뒤 반영되어 최종 PR CI에서 실제 실행한다. 독립 최종 R2 소스 검토 PASS, 확인된 차단 결함 0개다. 실제 PostgreSQL·전체 CI 및 배포는 아직 대기한다.

## CI 후속 보완

후보 `9cf04135`의 CI `34546087462`에서 하네스·Linux 배포 계약·Caddy·백엔드 단위 검사는 통과했다. PostgreSQL 통합 34개 중 Calendar 격리 검사는 통과했지만, 공유 초대 테스트가 공통 fixture에서 이미 만든 사용자 행을 다시 INSERT하여 1개가 실패했다. 원본 HTML 증거는 `tmp/pr11-ci-final-artifacts-20260911/verification-artifacts/reports/tests/integrationTest/classes/com.aierp.Phase1PersistenceIntegrationTest.html`이다.

revision 3은 그 중복 INSERT 한 줄을 기존 사용자 행 UPDATE로 변경한다. Spark/high가 실행했으며 기존 이름·권한·만료·회전·폐기 단언은 모두 유지했다. 독립 한정 소스 검토 PASS이고 새 SHA 전체 CI가 필요하다. 운영 Google 설정 미확인으로 배포 준비는 별도 대기 중이다.
