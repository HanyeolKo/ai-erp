# PR #11 배포 전 독립 검토

- 검토일: 2026-09-11. 검토자: `/root/pr11_release_review`.
- 대상: `6b94445`의 Google Workspace 수리 소스. 검토 중 부모가 main을 병합하여 HEAD가 `a6924c5`로 바뀌었으며, 아래 Calendar 결함은 병합 후 실제 파일에서도 다시 확인했다.
- 기준: `2026-09-07-google-workspace-contract.md`, `2026-09-09-pr11-repair-contract.md`, `harness/evaluation/TASK-REVIEW-RUBRIC.md`.
- 판정: **FAIL**. 현재 소스 차단 항목 1개와 최종 실행 증거 대기가 남는다. 이전 보고서의 이미 수정된 결함을 다시 미해결로 계산하지 않았다.
- 최신 판정: 아래 R2 최종 재검토에서 **소스 검토 PASS, 확인된 미해결 소스 차단 항목 0개**. 최종 커밋의 필수 CI·통합·배포 준비 증거는 별도이며 아직 이 문서에서 PASS로 판정하지 않았다. 아래 FAIL 절은 수정 전 근거다.

## 남은 소스 차단 항목

**GW-I11 — 취소 audit 실패가 원래 종료 시각을 잃고 무기한 재시도로 바뀐다.**

`backend/src/main/java/com/aierp/calendarintegration/CalendarDispatchWorker.java:155`는 `SYNCED` 상태로만 예약 audit를 구분하지만, 같은 claim의 170행은 상태를 `PENDING`으로 바꾼다. 일시 실패 완료는 234행에서 `PENDING`을 유지하고 `ensureUncertainty`를 호출한다. 이 함수는 원래 `reconcileUntil`이 만료되면 현재 시각부터 6시간으로 다시 설정한다. `CalendarProjectionRepository.java:16`의 `PENDING` 분기는 audit 기간과 무관하게 재선택한다. 자격증명 획득 실패의 `release` 또한 audit의 원래 상태를 복원하지 않는다.

재현 순서는 이미 취소 반영이 완료된 `SYNCED` 행에 곧 만료되는 audit 기간을 설정하고, audit claim 후 원래 기간을 지난 시점에 `TransientFailure`로 완료하는 것이다. 이후 기간이 연장되고 PENDING 재시도가 계속 선택된다. 실패가 반복되면 계약상 6시간의 제한된 취소 재확인이 종료되지 않으며 매 dispatch 주기의 호출로 확대된다.

필요한 수정·증거: 새 일정 반영 작업과 기존 취소 audit의 식별을 claim·release·완료·lease 복구 동안 유지한다. audit 실패는 원래 종료 시각과 재확인 간격을 보존하고 만료 후 종료해야 한다. provider 일시 실패, 자격증명 획득 실패, lease 만료 복구의 세 경로를 시간 제어 테스트로 검증한다. 정상적인 신규 반영 작업의 retry를 막는 단순 PENDING 제외로 수정해서는 안 된다.

## 소스에서 해소를 확인한 이전 항목

| 항목 | 현재 근거 |
| --- | --- |
| GW-I06 Gmail 중복 claim·receipt 경쟁 | `MailSendRequestRepository.java:13`의 INSERT ON CONFLICT와 영향 행 수로 소유자를 구분한다. `GmailService.java:148,170`은 age-out/finish에 같은 쓰기 잠금을 사용하고 SENDING에서만 종료 상태로 전이한다. 180행의 grant commit barrier는 실제 identity의 account→grant 잠금 transaction에 참여한다. |
| GW-I05 Drive 권한·개인 목록·N+1 | `DriveService.java:33`에서 응답 후 generation을 검사한다. 73행 이후 project 잠금 안에서 역할과 grant를 재검사하고 저장한다. 페이지 첨부자 profile은 46행에서 묶어서 조회한다. |
| GW-I04/GW-I03 Workspace HTTP·오류 | `GoogleHttpClient.java:87,98,102`는 헤더와 body에 하나의 절대 deadline을 적용하고, 읽기 중 byte 상한을 검사한다. safe ExternalServiceFailure로 401, 권한 부족, API 미설정, quota를 구분한다. |
| GW-I02/GW-I17 OAuth 및 설정 | 저장된 빈 SecurityContext 반영, refresh 운영 생성자 주입, 기존 완전한 기능 권한을 축소하는 grant 거절, credential 진입의 설정 gate가 소스에 존재한다. |
| GW-I12 및 Calendar 일시 오류 | scalar project 후보→stable project 잠금→binding 최초 읽기 순서가 존재한다. 자격증명 오류는 category에 따라 TRANSIENT와 PERMISSION_REQUIRED를 구분한다. |

## 검증 범위와 미실행

- 실제 소스와 unit/integration 테스트를 읽었다. Gmail에는 실제 PostgreSQL native claim/replay/age-out 테스트, identity에는 실제 account/grant 잠금 테스트, Drive에는 실제 저장/역할 변경 잠금 테스트가 추가되어 있다. 테스트 소스의 존재를 실행 성공으로 간주하지 않았다.
- 이 reviewer는 Gradle, frontend, 배포 계약, Docker, PostgreSQL, 브라우저 또는 실제 Google 호출을 실행하지 않았다. 부모가 CI와 runtime 증거를 수집한다.
- `Phase1PersistenceIntegrationTest`의 CI 실패는 부모의 별도 수리 범위다. 해결 및 최종 커밋의 전체 CI 성공 전에는 배포 판정을 PASS로 바꾸지 않는다.
- 상위 계약과 기존 UI specialist/PASS 연결은 확인했다. 09-09 수리 계약에는 Luna/high fallback 기록이 있으나 이번 read-only 검토는 과거 실제 호출 로그를 새로 확인하지 않았다. 현재 구현 모델의 호출 증거와 최종 acceptance mapping은 부모 결과 문서로 확인해야 한다.
- Notion 동기화는 부모가 최신 읽기 대기열 정책에 따라 같은 맥락의 안읽음 문서를 확인한 뒤 통합한다. 이 검토자는 외부 문서를 생성하거나 갱신하지 않았다.

## R2 소스 재검토

완료 계약 Revision 2의 현재 변경은 기존 audit claim을 `SYNCED`로 유지하고 immutable Claim에 audit 여부를 담는다. 실패 완료·release는 원래 기간을 연장하지 않으며 기간 만료 시 audit 시각을 지운다. 만료 lease도 audit이면 기간을 연장하지 않는다. 따라서 위 최초 무한 연장 경로는 소스에서 해소됐다.

**FAIL 유지 — GW-I11의 지원되는 수동 재시도 경쟁이 남는다.** audit claim이 진행되는 동안 `CalendarService.retry`가 해당 행을 정상 작업 `PENDING`으로 바꾸어도 claim token은 유지된다. 이후 `CalendarDispatchWorker.release`는 token이 같다는 이유만으로 상태를 `SYNCED`로 되돌린다. audit 기간까지 만료됐다면 재확인 시각도 지워져 사용자가 요청한 정상 재시도가 사라진다. 실패 완료의 audit 분기도 같은 PENDING 덮어쓰기 가능성이 있다. 현재 persisted 상태·revision이 여전히 해당 audit를 나타내는 경우에만 audit 상태를 복원하고, 새로 큐에 들어간 정상 작업은 유지해야 한다. 실제 `retry`→credential 실패 release 및 provider 실패 완료 순서의 회귀 검증이 필요하다.

취소 이후 새 CONFIRMED revision이 생기는 시나리오는 현재 제품에서 지원되지 않는다. `ScheduleService`는 CANCELLED 수정·재취소를 막고 confirm은 DRAFT만 허용한다. 따라서 이 가상 전이를 별도 운영 결함으로 계산하지 않았다. 새 테스트의 `newRevisionWinsOverStaleCancellationAuditClaim`은 방어 코드 검증이며 위 실제 수동 retry 경쟁을 대신하지 않는다.

`Phase1PersistenceIntegrationTest`는 concrete `GoogleAuthorizationService` mock과 실제 worker 호출로 바뀌었고 기존 revision·acknowledgement·audit·publication 검증을 유지한다. reviewer는 새 패치 테스트를 실행하지 않았다. 부모가 보고한 이전 131개 unit/API·integration compile/frontend 209개 성공은 새 패치의 실행 성공 증거가 아니며, 최종 실행은 대기 중이다.

## R2 최종 소스 재검토

**소스 검토 PASS. GW-I11 종료; 현재 확인된 소스 차단 항목 0개.** 마지막 변경에서 audit의 release와 완료 모두 현재 행이 `PENDING`이면 새 정상 큐 상태를 보존하고 기존 claim/lease만 해제한다. 정상 claim의 release는 기존 오류 분류를 유지한다. 이에 따라 수동 재시도 덮어쓰기 경로가 해소됐다. repository의 audit 분기는 `SYNCED`, 원래 기간, next 시각, 공통 lease 제한을 모두 요구하며, audit 실패는 원래 기간을 다시 열지 않는다.

`explicitQueuedPendingDuringAuditClaimRemainsPendingOnReleaseAndCompletion`은 release·성공 완료·실패 완료의 큐 보존을 검사한다. 기존 기간 만료 및 credential release 테스트도 확인했다. 테스트는 mock repository 경계를 사용하므로 PostgreSQL 실행 증거를 대신하지 않는다. 이번 변경에서 추가 방어 구조 변경을 요구할 만한 새 소스 결함은 확인하지 않았다.

부모가 최종 focused 실행을 진행 중이다. reviewer는 실행하지 않았으며, 이전 패치의 성공 결과를 마지막 수정의 성공으로 간주하지 않았다. Phase1 fixture 정리·실제 PostgreSQL 실행 및 최종 커밋 CI는 부모 검증에서 확정한다. 이 소스 PASS는 전체 task/release PASS 또는 배포 완료를 뜻하지 않는다.

## Revision 3 fixture 수리 검토

**한정 소스 검토 PASS, 새 SHA CI 대기.** CI `34546087462`의 실제 `com.aierp.Phase1PersistenceIntegrationTest.html`에서 228행의 `user_account_pkey` 중복 실패를 확인했다. 변경은 해당 INSERT 한 줄을 기존 공통 fixture 사용자의 email/display_name UPDATE로 바꾸며 같은 id와 값을 사용한다. 공통 fixture의 검증된 identity 행·email_verified_at과 공유 초대/권한/만료/회전/폐기/profile 단언을 유지하고, production 소스나 다른 동작을 변경하지 않는다. completion contract Revision 3와 일치한다. reviewer는 추가 테스트를 실행하지 않았으며 실패 CI를 새 후보의 성공 증거로 사용하지 않는다.
