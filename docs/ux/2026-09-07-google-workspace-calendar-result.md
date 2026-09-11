# Google Workspace Calendar 구현 결과

- 구현일: 2026-09-07 (Asia/Seoul)
- 범위: Calendar partition (`calendarintegration/**`, `schedule/api/ScheduleLookup.java`, 승인된 `ScheduleRepository.java`, `project/ProjectRepository.java`, `project/api/ProjectAccess.java`, `GoogleCalendar*Test.java`, 승인된 startup/supporting fixtures)
- 기준 계약: `docs/ux/2026-09-07-google-workspace-contract.md` revision 1
- 모델: gpt-5.6-luna/high fallback (Spark quota 제한으로 상위가 승인)

## 구현 내용

`GoogleCalendarAdapter`를 추가해 Calendar v3의 writable calendar 목록 조회, 선택 대상 권한 확인, 안정적인 base32hex 이벤트 ID, UTC event payload, private ERP 식별 속성, ETag 조건부 갱신, 취소 404/410 성공 처리를 구현했다. 운영 전송은 고정된 Google HTTPS 호스트와 10초 deadline, redirect 금지, 응답 크기 제한, 401/403/409/429/5xx 안전 분류를 사용한다. 테스트는 주입 가능한 localhost transport만 사용한다.

프로젝트 Calendar API는 목록, 바인딩, 조회, 로컬 해제와 legacy connection/reconnect 경로를 제공한다. 바인딩은 MANAGER와 provider writable 확인 후 저장하며, provider 반환 이름을 사용하고 backfill cursor/pending 상태를 기록한다. 해제는 외부 이벤트를 삭제하지 않고 binding generation과 claim을 무효화한다.

`ScheduleLookup`은 schedule 엔티티를 외부 모듈로 노출하지 않는 immutable snapshot과 project/status/businessRevision 조건의 UUID cursor page(max 100)를 제공한다. `CalendarDispatchWorker`는 짧은 claim 트랜잭션, 트랜잭션 밖 provider HTTP, claim token·revision·binding generation·credential generation·현재 MANAGER 검사를 거친 CAS 완료를 사용한다. 연결 해제나 늦은 schedule revision은 SYNCED로 덮어쓰지 않는다.

취소 요청이 provider HTTP 이후 응답을 잃으면 60초 claim lease 만료 뒤 동일한 deterministic ID를 GET하여 상태를 재확인한다. 로컬 projection의 CANCELLED snapshot이 권위이며 404/410에는 생성하지 않는다. 이미 SYNCED인 취소 projection을 무기한 Google tombstone으로 가정하지 않는다. 만료된 이전 claim을 새 token이 인수할 때는 현재 상태가 CONFIRMED여도 기존 외부 요청의 불확실성을 최대 6시간의 `reconcileUntil`로 먼저 보존하고, 이후 CANCELLED가 되면 due audit claim으로 다시 확인한다. token이 바뀐 늦은 완료는 이 marker를 지우지 않는다.

## 검증 증거

- `git diff --check`: exit 0.
- 이전 focused 실행에서 `GoogleCalendarAdapterTest`, `GoogleCalendarDispatchTest`, `CalendarStartupRegressionTest`, `IdentityAndDeliveryTest` 16건이 통과했다. 이후 G06 두 token race와 `SupportingApiTest` 의존성 fixture를 추가한 최신 실행은 Calendar 코드에 도달하기 전 shared core `GmailController`의 누락된 `GmailService`와 `DriveService`의 `Transactional` import 오류로 `compileJava` 단계에서 중단됐다. 따라서 최신 변경에 대해 테스트 통과를 주장하지 않는다.
- 최신 Calendar 변경의 정적 근거: `git diff --check`는 통과했고, source에는 project-row pessimistic lock, project/status/revision UUID-cursor export, token/revision/binding/credential/MANAGER barrier, max-6-hour uncertainty marker, due-audit claim branch, cancellation 404/410 no-insert가 반영돼 있다. Backfill candidate는 scalar `projectId`만 먼저 읽고 stable project lock 뒤 binding을 처음 materialize한다.
- `GoogleCalendarDispatchTest`에는 binding generation stale completion, confirmed timeout의 claim 해제·uncertainty 보존 후 cancellation, confirmed success의 no-hot-poll, stale managed backfill binding, A expired → B CANCELLED/404 → late A token mismatch → due audit 순서를 검증하는 테스트 source가 있다. `GoogleCalendarAdapterTest`에는 initial cancellation 404 → late confirmed insert → bounded cancellation audit DELETE 순서와 delayed/stalled body timeout source가 있다. 실제 실행 결과는 shared core compile blocker 해소 후 재확인해야 한다.
- JDK provider transport는 header 전송 시작부터 하나의 절대 deadline을 사용하고, 남은 시간만 body Future에 부여한다. `InputStream.read()` timeout 시 `cancel(true)`와 stream close를 호출하며, bounded reader가 2MiB 초과 전에 실패한다. localhost HTTP acceptance는 injectable JDK transport와 redirect/oversize/stalled-body 서버를 사용한다.
- PostgreSQL/Docker, 실제 Google 계정 동의·Calendar 쓰기, full integration test는 계약대로 실행하지 않음.
- 최신 appendix repair readiness: **FAIL/PENDING independent review**. Workspace가 다른 Google Workspace partition을 안정화하는 동안 Gradle 실행을 중지했으므로 새 테스트들의 실행 결과는 없다.

## 남은 상위 작업

상위 core는 V8에 `reconcile_until`과 `next_reconcile_at`을 추가해야 한다. PostgreSQL/Docker integration과 full regression은 상위가 실행하며, independent task-review에서 bounded cancellation audit의 제공자 외부 exactly-once 한계를 확인한다.

## 독립 리뷰 항목 추적

- `GW-I11`: 만료된 이전 insert claim을 새 cancellation claim이 token 교체하기 전에 audit marker로 보존하고, 404/410 취소 성공 뒤 due audit가 다시 동일 ID를 조회하도록 보완했다. two-token race test source를 추가했지만 shared core compile blocker 때문에 최신 실행 증거는 pending이다.
- `GW-I12`: binding/disconnect와 backfill에 stable project-row `PESSIMISTIC_WRITE` 잠금 및 locked binding 재조회 경로를 적용했다. 실제 PostgreSQL 경합 증거는 상위 full run에서 확인한다.
- `GW-I04`: Calendar JDK transport의 response body는 daemon Future의 bounded reader에서 읽으며 10초 timeout 시 future 취소와 stream close를 수행한다. Identity refresh transport의 동일 항목은 core 소유 범위로 남아 있다.

원본 경로: `C:/Users/USER/.codex/worktrees/60aa/AI ERP/docs/ux/2026-09-07-google-workspace-calendar-result.md`. Notion `AI 생성문서 관리` 동기화는 상위 오케스트레이터가 담당한다.
