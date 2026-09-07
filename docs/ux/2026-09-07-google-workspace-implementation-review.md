# Google Workspace 구현 독립 검토

- 검토자: `/root/google_workspace_review`, `ai-erp-reviewer` 역할. 실행자는 검토 판정을 소유하지 않는다.
- 기준: `58bc6d6` 위 미커밋 변경, Google Workspace 계약 revision 1, 전문 계획 revision 4 및 독립 UI 계획 PASS.
- 관찰 시점: 2026-09-07 17:16~17:22 KST. 아래 행 번호는 이때 읽은 소스 기준이다. 구현자가 동시에 수정 중이므로 이 보고서는 해당 스냅샷의 판정이며 수정본의 자동 통과를 뜻하지 않는다.
- **task-review: FAIL.** 실행 가능한 결함과 필수 검증 미완료가 남아 있다. 공개 push·배포·실제 Google 발송은 검토 범위에서 실행하지 않았다.

## 판정 근거

부모의 READY 계약, UI 전문 기획→독립 계획 PASS→구현 인계는 확인했다. 실제 구현 세 작업은 부모의 호출·완료 메시지와 결과 문서상 `gpt-5.6-luna/high`이며 Spark quota 소진에 대한 승인된 fallback이다. 정적 wrapper만을 모델 호출 증거로 사용하지 않았다. 구현 결과 문서가 열거한 동작을 코드 및 검증 증거와 대조했으며, 결과 문서의 주장만으로 통과시키지 않았다.

아래 안정 ID는 수정·재검토 때 유지한다. 같은 원인의 여러 파일은 한 항목으로 묶었다.

### GW-I01 — P1: vault 조회의 쓰기 잠금과 트랜잭션 경계가 실제 DB에서 충돌한다

`backend/src/main/java/com/aierp/identity/GoogleAuthorizationRepository.java:7`은 일반 `findByUserAccountId`에 `PESSIMISTIC_WRITE`를 붙였다. `identity/api/GoogleAuthorizationService.java:21`, `:46`, `:51`은 이를 read-only 트랜잭션에서 호출하고, `:30`의 `credential`은 트랜잭션 없이 호출한다. 잠금 조회는 활성 쓰기 트랜잭션이 필요하므로 실제 PostgreSQL의 연결 상태·grant 조회·refresh 진입이 실패할 수 있다. Mockito 기반 네 개 core 테스트는 이 경계를 실행하지 않는다. 일반 조회와 명시적 잠금 조회를 분리하고 refresh/connect/disconnect의 짧은 쓰기 구간에만 잠금을 적용해야 한다. 최초 행 생성의 disconnect/connect 경쟁도 함께 검증한다.

### GW-I02 — P1: OAuth connect intent가 해당 OAuth state와 원자적으로 결합되지 않는다

`identity/OidcConfiguration.java:48`, `:81`, `:92`는 현재 세션에 intent/feature가 존재하는지만 보고 callback을 분류한다. resolver는 생성한 OAuth state와 intent ID의 연관을 저장하지 않으며 callback은 이를 검증·원자적 소비하지 않는다. `googleFailure`는 어느 state에서 난 실패인지 확인하지 않고 현재 intent를 제거한다. 겹친 연결 시도에서 이전 실패가 새 intent를 지우면 새 성공 callback이 `:53`의 일반 로그인 provisioning으로 진입할 수 있다. 실패·재생·다른 계정에서도 원래 ERP 로그인을 유지한다는 G02를 보장하지 않는다. state별 불변 연결 문맥, 단일 소비, 실패와 일반 로그인 경로 분리, 임시 authorized-client 제거를 실제 callback 테스트로 입증해야 한다.

### GW-I03 — P1: 제공자 거절·refresh 실패 상태가 저장 및 HTTP 응답으로 전달되지 않는다

`identity/api/GoogleAuthorizationService.java:38`은 refresh 400/401에 예외만 던지고 grant를 REAUTH_REQUIRED로 바꾸거나 claim을 종료하지 않는다. `platform/web/ApiExceptionHandler.java:19`에는 Google/Calendar 안전 예외용 처리기가 없어서 이 예외들이 500 INTERNAL_ERROR가 된다. Drive/Gmail/Calendar는 모든 403을 권한 부족으로 취급하여 API 비활성화·quota와도 구별하지 않는다. UI가 기존 CONNECTED 상태를 계속 표시하고 재연결 동작을 안내하지 못한다. generation 조건부 상태 전이와 안전한 오류 코드 매핑, 서비스별 실제 grant 상태를 검증해야 한다.

### GW-I04 — P1: HTTP 본문 수신 전체에 10초 제한이 적용되지 않는다

`googleworkspace/GoogleHttpClient.java:25` 이후 InputStream 읽기는 크기만 제한하고 읽기 중단 deadline이 없다. `calendarintegration/GoogleCalendarAdapter.java:245`는 `stream.read`가 반환한 뒤에만 시간을 확인하므로 멈춘 body를 중단하지 못한다. `identity/GoogleTokenRefreshTransportClient.java:22`의 `ofString`은 본문 크기 제한도 없다. 헤더를 받은 뒤 응답이 정지하면 요청이 계약의 10초/lease보다 오래 남을 수 있다. 실제 localhost HTTP server가 헤더 이후 body를 멈추는 경우와 2MB 초과를 모든 실 transport에서 검증해야 한다.

### GW-I05 — P1: Drive 생성자·권한 재확인·조회 상한을 완성해야 한다

`googleworkspace/DriveService.java:19`와 `:20`의 두 생성자에는 Spring 선택용 annotation이 없다. 단일 애플리케이션 context에서 주입 성공을 확인해야 한다. `:35`는 attach 전체를 트랜잭션으로 감싸 provider HTTP까지 DB 문맥을 유지하며, 같은 persistence context의 역할 재조회가 최신 권한을 보장하지 않는다. `:26`은 provider가 준 모든 rows를 반환하고, 입력 pageToken 제한 및 provider 응답 후 credential generation 검사도 없다. 짧은 저장 트랜잭션에서 최신 역할과 grant를 재검사하고 20개/opaque token 상한을 적용한다. 중복 attach와 역할 강등 경쟁 테스트가 필요하다.

### GW-I06 — P1: Gmail send의 동시 요청·불확실한 receipt 경계가 미완성이다

`googleworkspace/GmailService.java:33`은 find-then-insert만 사용하므로 같은 key의 동시 최초 요청은 한쪽이 unique 충돌로 끝나고 동일 payload receipt 재생을 하지 않는다. `:30`은 credential 취득과 provider POST 사이에 fresh generation 검사를 하지 않는다. `:34`는 완료 저장 때 account/generation CAS가 없고 저장 실패를 UNKNOWN receipt로 처리하지 못한다. receipt age-out과 finish도 서로 잠금/CAS 없이 덮어쓸 수 있다. SENDING 선행 커밋 구조 자체는 있으나 그것만으로 계약 전체가 성립하지 않는다. 동시 claim, key/payload 충돌, disconnect, provider 성공 후 DB 실패, 60초 age-out 및 재조회가 재발송하지 않는 테스트가 필요하다.

### GW-I07 — P1: Gmail MIME 생성·읽기가 승인된 계약을 충족하지 않는다

`googleworkspace/GmailService.java:41`은 승인된 MIME 라이브러리 대신 문자열 연결로 메일을 만든다. 긴 Unicode Subject의 encoded-word folding 및 UTF-8 본문의 Content-Transfer-Encoding이 없다. `:40`의 주소 regex는 실제 단일 mailbox 구문을 검증하지 않는다. `:38`은 multipart/alternative의 plain과 HTML을 모두 이어 붙이고 MIME part 개수 상한 없이 순회하며 깊이 초과를 truncated로 표시하지 않는다. MIME 라이브러리로 작성하고 plain 우선·부분/깊이/본문 상한 및 inert HTML fallback을 실제 provider 응답으로 검증해야 한다.

### GW-I08 — P1: 실제 Gmail DTO와 프런트엔드 타입이 달라 상세 화면이 실패한다

`googleworkspace/GmailService.java:51`의 MessageSummary.to 및 MessageDetail.to/cc는 String이다. `frontend/src/api/client.ts:118`~`:120`은 string[]을 선언하며 `frontend/src/screens/GoogleWorkspace.tsx:289`의 MailDetailView는 `detail.to.join`과 `detail.cc.join`을 호출한다. 실제 backend 상세 응답에서 TypeError가 발생한다. fixture의 배열 응답으로는 발견할 수 없다. `internalDate`도 Gmail epoch-milliseconds 문자열을 그대로 보내는데 화면은 Date 문자열로 읽으므로 날짜가 올바르지 않을 수 있다. 한 DTO 표현을 확정하고 실제 직렬화 응답을 프런트 테스트에 사용한다.

### GW-I09 — P1: Gmail의 연결 거절 상태에서 무한 재렌더 및 개인 Dialog 노출이 가능하다

`frontend/src/screens/GoogleWorkspace.tsx:236`의 effect는 매 render 달라지는 `send` 객체를 dependency로 포함하고, 연결되지 않은 성공 상태마다 `clearCompose`의 새 객체와 `send.reset`을 설정한다. 이 경로는 반복 렌더를 만들 수 있다. 반대로 connection error이면 `!connectionReady`로 즉시 return하여 기존 compose를 닫지 않는다. `:278`, `:281`의 Dialog는 connected guard 밖에 있고 detail 제목도 캐시된 subject를 그대로 사용하므로 403 후 작성 내용·메일 제목이 계속 노출된다. 객체 dependency를 안정화하고 private modal 전체를 권한 거절 이후 fresh success 전까지 차단하는 테스트가 필요하다.

### GW-I10 — P2: 발송 완료·OAuth 복귀 결과가 화면에서 뒤처진다

`GoogleWorkspace.tsx:231`의 receipt query는 POST 완료 전에 SENDING을 받을 수 있다. `:245`의 `receipt.data ?? send.data`는 그 SENDING을 반환된 SENT보다 우선하고 send 성공 때 receipt를 갱신하지 않는다. 실제로 보낸 메일이 확인 중으로 남는다. 또 `:121`의 outcomeLabels는 대문자지만 backend `OidcConfiguration.java:115`는 connected/denied 소문자를 반환하여 callback 결과가 표시되지 않는다. 종료 receipt 우선순위와 안전한 callback 코드 표현을 맞춘다.

부모의 후속 Chrome 관찰에서는 fixture가 아직 없는 requestId의 선행 GET에 SENT를 생성해 주어, sendStatus UNKNOWN 설정에도 “메일을 보냈습니다.”가 표시됐다. 이는 실제 Gmail 발송 성공의 증거가 아니며 fixture 자체를 수정해야 한다. 선행 receipt/POST 완료 순서의 UI 검증은 없는 key의 404와 저장된 claim 상태를 정확히 재현한 fixture로 다시 수행한다.

### GW-I11 — P1: Calendar 취소의 늦은 외부 insert를 다시 정리하지 않는다

`calendarintegration/CalendarDispatchWorker.java:176`은 claim token이 바뀌면 이전 완료를 무시한다. `:191`은 CANCELLED GET404도 SYNCED로 완료하며 `CalendarProjectionRepository.java:15`는 PENDING만 다시 선택한다. 이미 전송한 이전 insert가 응답/처리 지연으로 lease 뒤에 완료되고, 그 전에 새 취소 claim이 GET404를 받은 경우 원격 일정이 나중에 생겨도 재검사하지 않는다. HTTP client deadline이나 결정적 ID는 제공자가 나중에 처리하는 것을 취소하지 못한다. 계약은 이 상황의 로컬 불확실성 보관과 취소 projection의 bounded periodic reconciliation을 명시했다. 단순 lease 재시도가 충분하다는 worker 주장은 기각한다. 늦은 provider insert 후 취소 재조정이 수행되는 경쟁 시나리오를 테스트해야 한다.

### GW-I12 — P1: Calendar binding/완료의 원자성이 입증되지 않았다

17:16에 읽은 `CalendarService.java:107`의 saveBinding은 TransactionTemplate 안에서 일반 findByProjectId를 호출했고 ProjectCalendarEntity에 version도 없어 기존 빈 binding의 두 동시 저장이 last-write-wins가 될 수 있었다. 17:21 수정본에는 잠금 조회가 추가됐지만 최초 미존재 행, backfill 저장과 disconnect, projection 완료의 calendar generation 검사·저장 사이 경쟁은 아직 재검토 및 DB 테스트가 필요하다. 트랜잭션이 있다는 것과 관련 행을 원자적으로 보호한다는 것은 별개다. 해당 안정 ID는 수정 검증 전까지 열어 둔다. 초기 중복 @Autowired 두 개도 확인했으며 17:21에는 하나로 수정됐으나 전체 context 재검증은 pending이다.

### GW-I13 — P1: V8 배포 allowlist와 checksum이 갱신되지 않았다

17:20의 `scripts/lib-deploy.sh:125`, `:129`, `:132`는 여전히 V1,V2,V4,V5,V6,V7 여섯 파일만 허용하고 개수가 정확히 6이어야 한다. V8 추가 후 배포 검사가 `unexpected migration set`으로 실패하며 checksum에서도 V8이 빠진다. V8·개수·checksum 및 deployment contract fixture를 함께 수정하고 실행해야 한다.

### GW-I14 — P2: 새 Google Workspace 모듈의 CLOSED 선언과 named API가 없다

17:20 파일 목록에는 `backend/src/main/java/com/aierp/googleworkspace/package-info.java`와 `googleworkspace/api/package-info.java`가 없다. 새 모듈은 계약의 CLOSED·api NamedInterface 경계를 선언하지 않았다. 명시적인 모듈 경계와 필요한 의존성만 추가하고 기존 Modulith 검증을 실행한다.

### GW-I15 — 필수 검증 부족

17:22 검색에서 Google 관련 테스트는 GoogleWorkspaceCoreTest, GoogleCalendarAdapterTest, GoogleCalendarDispatchTest 세 파일뿐이다. core 4개는 redaction/vault AAD/link/빈 To 검증이며 실제 OAuth callback·refresh 회전·동시 send claim을 실행하지 않는다. 필수 acceptance를 증명할 근거가 없다. 기존 unit fixture만 맞추기 위한 CalendarService의 `google == null` 전용 직접 전송 분기는 실제 운영 worker 경로 검증을 대체할 수 없다.

### GW-I16 — P2: 정상 발송으로 닫는 Dialog가 작성 버리기 확인을 다시 띄운다

부모의 실제 Chrome 실행에서 메일 보내기 이후 native 확인창 때문에 자동화가 정지했으며, `handle_dialog`로 수락하자 해소됐다. `frontend/src/ui.tsx:392`~`:433`의 programmatic dialog.close가 onClose를 호출하고 Gmail의 submitSend는 dirty form을 유지한 채 composeOpen만 false로 바꾼다. 따라서 `GoogleWorkspace.tsx:258`의 closeCompose가 정상 발송 뒤에도 “작성 중인 메일을 버릴까요?”를 묻는다. programmatic 성공 종료와 사용자 취소를 분리해야 한다. 동일 Dialog의 native cancel 이벤트는 KeyboardEvent.key를 보장하지 않으므로 Escape·취소·포커스 복원을 실제 브라우저에서 함께 재검증한다. 이 관찰은 부모가 전달한 실제 브라우저 증거이며 reviewer가 직접 재실행한 것은 아니다.

## G01~G10 매핑

| 기준 | 판정 | 근거/남은 실행 |
| --- | --- | --- |
| G01 OAuth·암호화·refresh | FAIL | GW-I01~04. vault AAD/redaction 소규모 PASS만 확인. callback/refresh HTTP 및 DB 경쟁 미실행. |
| G02 실패·다른 계정·재생·부분 승인 | FAIL | GW-I02~03. 실제 state/세션 보존 시험 없음. |
| G03 Drive 조회·첨부 권한 | FAIL | GW-I05. canonical URL 검증은 있음. cross-role/cross-account 경쟁 증거 없음. |
| G04 Gmail 읽기 안전성 | FAIL | GW-I07~08. 실제 DTO 상세 렌더가 불일치. |
| G05 명시적 발송·receipt | FAIL | GW-I06~10. 선행 SENDING 구조와 UI review는 있으나 동시/불확실성 검증 부족. |
| G06 Calendar 실제 동기화 | FAIL | GW-I11~12. ETag·결정적 ID·revision/guard 구조는 확인했으나 취소 재조정 누락. |
| G07 사람 이름 및 비밀 비노출 | PENDING | 기존 displayName 경로와 Credential.toString redaction 확인. OIDC별 access/refresh/ID/code sentinel 공개 경계 테스트 미실행. 실제 사용자 보고가 진짜 token인지 확정하지 않음. |
| G08 상태·모바일·키보드 | FAIL/PENDING | GW-I03,08~10. 부모 브라우저 실행 진행 중, 최종 viewport/keyboard 증거 대기. |
| G09 기존 기능 회귀 | PENDING | 부모 전달 frontend 207/207 PASS. backend 앞선 88개 중 8개 실패, 수정 후 전체 검사 필요. |
| G10 V8·배포 호환 | FAIL/PENDING | GW-I13~14. 실제 PostgreSQL/Testcontainers 미실행. |

## 검증 기록과 재검토 조건

이 reviewer는 공유 Gradle 출력 잠금 충돌을 피하기 위해 Gradle을 새로 실행하지 않았다. 파일 읽기·검색·diff 범위 검토만 실행했다. 부모 전달 증거는 `tmp/google-workspace-root-full-frontend.log`의 207/207, 프런트 결과 문서의 focused 8/8·typecheck/build PASS, backend 결과 문서의 core 4/4 및 전체 88 중 8 실패다. 브라우저는 부모가 실행 중이며 이 보고서에서 통과시키지 않는다. Docker가 없어 PostgreSQL integrationTest를 실행하지 못한 사실도 유지한다.

부모가 계약을 조정할 필요가 있는 schema/DTO 선택은 구현자에게 판정을 넘기지 않고 먼저 확정한다. 위 안정 ID 수정 후 실제 context/OAuth/HTTP/DB 경쟁·전체 회귀·OpenAPI 생성·배포 검증 증거를 수집하여 독립 재검토를 요청해야 한다. required pending을 PASS로 바꾸지 않는다. 공개 저장소 반영·운영 배포 판단은 부모와 사용자가 소유한다.

로컬 원본: `C:/Users/USER/.codex/worktrees/60aa/AI ERP/docs/ux/2026-09-07-google-workspace-implementation-review.md`. Notion 보관 동기화는 상위 오케스트레이터가 담당하며 이 검토 작업에서는 외부 쓰기를 실행하지 않았다.

## Core 한정 재검토 — 2026-09-07 17:25~17:28 KST

대상은 identity/OAuth/refresh/platform/V8/deploy 수정뿐이다. Workspace·Calendar·프런트 수정은 진행 중이므로 이 절에서 재판정하지 않았다. **전체 task-review는 FAIL 유지**이며 Gradle·외부 서비스 호출은 실행하지 않았다.

| 안정 ID | 현재 판단 | 확인한 수정과 남은 문제 |
| --- | --- | --- |
| GW-I01 | 소스 수정 확인 / DB 검증 PENDING | 일반 조회의 잠금이 제거됐고 별도 lockByUserAccountId가 생겼다. connect/disconnect는 UserAccountRepository.lockById 뒤 grant 잠금 순서로 최초 grant 행 경쟁을 직렬화한다. refresh 완료와 실패는 grant 잠금 및 claim+generation 비교를 한다. 기존 읽기 잠금 오류는 코드상 해소됐으나 실제 PG 첫 연결/해제/refresh 경합은 아직 실행하지 않았다. |
| GW-I02 | FAIL 유지 | resolver가 단일 세션 state 문자열을 저장하고 success가 비교하도록 바뀌었다. 그러나 state별 intent 및 원자적 소비는 없고, failure는 여전히 어느 요청의 실패인지 확인하지 않고 최신 intent를 전부 지운다. 아래 재현 순서를 테스트해야 한다. |
| GW-I03 | 부분 수정 / FAIL 유지 | failRefresh가 일치하는 claim/generation에서 lease를 지우고 reauth 상태를 저장한다. GoogleAccessException이 ExternalServiceFailure로 바뀌었으며 advice가 안전한 코드를 담은 502를 낸다. 실제 provider401/403의 저장 상태·API 비활성화/속도 제한 분류와 UI 연동은 입증되지 않았다. String-code 생성자는 TEMPORARY category인데 status()는 PERMISSION_REQUIRED를 반환하여 Calendar의 임시 refresh 실패를 권한 부족으로 분류할 수 있다. |
| GW-I04 | FAIL 유지 | refresh 본문은 1MB InputStream 상한이 생겼으나 GoogleTokenRefreshTransportClient.java:22의 input.read를 중단할 전체 deadline은 없다. refresh lease15초보다 오래 provider 수신이 남을 수 있다. |
| GW-I13 / G10 | FAIL 유지 | 17:25의 lib-deploy.sh:129는 여전히 파일 수6, :132의 checksum은 V8 제외다. V8 파일은 존재한다. |
| GW-I14 | FAIL 유지 | 17:27 확인에서도 googleworkspace/package-info.java와 api/package-info.java는 존재하지 않는다. 수정 담당 파티션의 후속 검토 필요. |
| GW-I15 | PENDING 유지 | core 테스트가 4개에서 6개로 늘었지만 새 두 테스트도 mock 조회 경로와 Calendar scope 판정이다. OAuth callback/refresh 실제 HTTP/DB 동시성은 여전히 시험하지 않는다. 새 6개 실행 결과를 이 reviewer가 받거나 실행하지 않았다. |
| GW-I16 | 재검토 제외 | 이 인계 범위는 core만이다. 실제 UI Dialog 수정·브라우저 결과가 와야 닫을 수 있다. |

GW-I02의 구체적 실패 순서는 연결 A를 시작한 뒤 연결 B를 시작하고, A의 stale/invalid callback이 B의 OAuth callback보다 먼저 failure handler에 들어오는 것이다. `OidcConfiguration.java:69`~`:75`는 state 확인 없이 B의 original/feature/state를 지운다. B의 유효 OAuth state가 Spring authorization-request 저장소에 남아 성공 처리되면 `:48`의 connect 분류 조건은 모두 false가 되고 일반 provisioning으로 들어간다. B에서 사용자가 다른 Google 계정을 선택한 경우에도 기존 ERP 계정 유지 계약을 우회한다. 단일 `google.connect.state` 추가만으로 이 경쟁을 해결하지 못한다. 실패 시 현재 intent를 지우기 전에 해당 state의 불변 문맥을 확인하고, 소비된/실패한 connect callback이 일반 로그인으로 변환되지 않게 해야 한다.

### GW-I17 — P1: 런타임 비활성화가 기존 Drive/Gmail credential 사용을 차단하지 않는다

`identity/api/GoogleAuthorizationService.java:31`의 credential은 `configured()`를 확인하지 않는다. 이 메서드의 status 판정도 저장된 grant만 본다. 서버가 기존 vault/key를 유지한 채 APP_GOOGLE_WORKSPACE_ENABLED=false 또는 OIDC 비활성으로 재시작해도 Drive/Gmail은 저장된 access token을 받아 외부 호출을 할 수 있다. `GoogleConnectionController.java:26`의 configurationRequired 및 connect POST 차단은 이미 있는 grant의 API 호출을 막지 않는다. credential의 공통 진입에서 실제 flag·OIDC·key를 강제하고 안전한 설정 필요 오류로 종료해야 한다. flag=false/OIDC 누락/key 누락 및 기존 grant가 있는 테스트가 필요하다.

추가로 grant 갱신의 독립 서비스 보존은 아직 검증되지 않았다. `GoogleAuthorizationService.java:107`은 새 scope 집합으로 기존 집합을 교체한다. 요청 서비스 권한만 만족한 새 grant가 기존 다른 서비스 권한을 제외했을 때 이를 어떻게 처리할지 테스트가 필요하다. 과거 scope를 새 token의 권한으로 임의 합치는 수정은 허용하지 않는다. 실제 provider 승인 범위에 근거하면서 계약의 원래 grant 보존 조건을 지켜야 한다.

이 재검토에서 실제 비밀 문자열이 DTO 또는 로그로 노출되는 경로를 추가로 확정하지는 않았다. token vault의 random nonce/AAD 및 credential redaction은 그대로 존재한다. 계정 표시 문제를 실제 access-token 누출로 단정하지 않는다.

## Calendar 한정 재검토 — 2026-09-07 17:28~17:31 KST

**G06은 FAIL 유지**다. 부모 및 worker 결과 문서의 focused 16개 성공은 인정하되, 새 테스트가 실제 결함 순서를 실행하는지 따로 확인했다. 이 reviewer는 Gradle이나 실제 Google 호출을 실행하지 않았다. 다른 파티션은 재검토하지 않았다.

### GW-I11 재검토: audit 필드 추가만으로 기존 취소 경쟁이 해소되지 않았다

V8과 projection에 `reconcile_until`, `next_reconcile_at`이 추가됐고 정상적으로 생성된 audit는 6시간 동안 30초 간격으로 재조회하는 코드가 있다. 그러나 `CalendarDispatchWorker.java:177`은 다른 claim token이면 즉시 return하고 audit를 만들지 않는다. audit 생성은 `:190`의 동일 token 완료 경로에만 있다. 다음 필수 순서는 여전히 누락된다.

1. 확정 revision의 insert를 이미 Google에 보냈으나 실행 프로세스가 종료되거나 응답이 불확실하다.
2. lease가 만료된 뒤 새 취소 claim이 token을 교체한다. claim 획득(`:141`) 및 취소 outbox consumer에는 이 이전 불확실성을 audit로 저장하는 코드가 없다.
3. 취소 GET404가 먼저 완료되어 SYNCED가 되고, reconcileUntil은 여전히 null이다.
4. Google의 이전 insert가 나중에 완료된다. 이전 로컬 완료가 돌아와도 token 불일치로 return한다. 이미 종료된 프로세스라면 완료 callback 자체도 없다.
5. audit 선택 대상이 아니므로 취소된 원격 일정이 남는다.

`GoogleCalendarDispatchTest.java:45`는 row의 **현재 token을 그대로** 이전 claim에 넣고 완료를 호출한다. 이는 같은 token의 desired revision 변경만 시험하며, 위 expired claim→replacement→404→late insert 순서의 회귀 테스트가 아니다. 불확실한 외부 시도가 사라지기 전에 로컬 audit 근거를 저장하고 token 불일치·프로세스 종료에서도 유지되도록 수정해야 한다.

같은 ID의 추가 결함으로 `CalendarProjectionRepository.java:15`는 audit OR 분기에 lease 조건이 없다. audit가 due인 행은 첫 worker가 새 60초 lease를 받은 뒤에도 두 번째 worker가 즉시 다시 선택할 수 있다. nextReconcileAt도 claim 시 미루지 않으므로 동시 worker가 token을 계속 교체한다. PENDING 및 audit 선택 모두 활성 lease를 배제해야 하며, DB에서 두 worker의 동시 claim 횟수와 실제 provider 호출을 검증해야 한다.

### GW-I12 재검토: bind 간 직렬화는 개선됐지만 backfill이 해제된 binding을 되살릴 수 있다

`CalendarService.java:110`, `:127`의 project 잠금과 ProjectCalendarRepository의 쓰기 잠금은 bind/disconnect 간 최초 행 경쟁과 역할 변경과의 정합성을 개선했다. 기존 프로젝트 역할 변경도 동일한 project 행을 잠근다. 하지만 `CalendarDispatchWorker.java:100`의 backfill은 ProjectCalendar를 잠금 없이 읽고 `:111`에서 cursor/pending을 변경하여 저장한다. ProjectCalendarEntity에는 @Version 또는 동적 갱신 제한이 없다. 그 사이 disconnect/rebind가 완료되면 Hibernate의 오래된 엔티티 전체 update가 이전 bindingOwner/externalCalendarId/generation까지 다시 저장할 수 있다. project/calendar에 일관된 잠금 순서를 사용하거나 version/CAS로 오래된 backfill 쓰기를 거절해야 한다.

CalendarProjection에는 기존 @Version이 있어 일반 outbox·backfill 저장의 충돌은 감지한다. 다만 `CalendarProjectionRepository.java:19`의 bulk invalidateClaims는 rowVersion을 증가시키지 않으므로 이 변경을 JPA 낙관적 잠금이 감지하지 못한다. 오래된 managed projection이 뒤늦게 저장되어 이미 무효화한 claim/lease를 되살리지 않는 검증과 수정이 필요하다. 완료 시 binding generation 검사와 commit 사이의 동시 해제도 일관된 잠금/CAS로 검증한다.

현재 읽은 `CalendarService.java:45`~`:47`에는 GoogleAccess·IdentityProfiles·PlatformTransactionManager의 @Nullable이 여전히 남았다. TM가 없으면 `saveBinding`은 self-invocation의 @Transactional이 적용되지 않는 직접 실행으로 내려가고, GoogleAccess가 없으면 retry가 provider 직접 전송 경로에 들어간다. 부모가 요청한 필수 운영 의존성 복원과 실제 startup fixture 수정이 디스크에 반영된 뒤 다시 확인해야 한다. worker 완료 메시지만으로 복원됐다고 판단하지 않았다.

### GW-I04의 Calendar 부분: 전체 HTTP deadline 미수정

`GoogleCalendarAdapter.java:246`은 여전히 `stream.read` 뒤에 시간을 확인한다. 헤더 이후 body가 멈추는 테스트가 없고 실제 읽기 취소가 없다. ETag 조건부 쓰기·결정적 ID·소유권/revision 및 매 요청 guard는 코드에서 확인했지만, 이 구조가 timeout과 이미 전송한 Google 요청의 외부 완료를 취소한다고 주장할 수 없다.

현재 G06 재검토 결론은 단순 PostgreSQL 증거 대기만이 아니다. **확인된 소스 결함 GW-I11/GW-I12/GW-I04가 남아 있어 FAIL**이며, 해당 수정과 실제 경쟁·timeout 시나리오 테스트 후 다시 검토해야 한다. focused 16개에는 legacy/startup 테스트가 포함되며, 16개의 독립 Calendar 동시성 시나리오를 통과했다는 뜻은 아니다.

## Calendar 두 번째 재검토 — 2026-09-07 17:43~17:46 KST

현재 변경의 개선점은 소스에서 확인했다. expired claim token을 교체하기 전에 최대 6시간의 불확실성을 보관하고, audit와 PENDING 양쪽에 공통 lease 배제 조건을 적용했다. invalidateClaims는 rowVersion도 증가시킨다. CalendarService의 운영 생성자에서 @Nullable이 제거됐다. InputStream은 Future의 제한 시간 뒤 close/cancel하는 방식으로 바뀌어 무한 body 대기를 끊을 수 있다. 새 테스트는 서로 다른 token A/B, B의 취소 완료, A의 늦은 완료 이후 audit 보존을 실제로 구분한다. 이 테스트는 이전의 동일-token 테스트보다 정확하지만 repository 자체는 mock이므로 PostgreSQL 동시 선택을 증명하지는 않는다.

**G06 소스 판정은 아직 FAIL**이다. 다음 세 경계가 남았다.

- **GW-I11 / 확인된 불확실성 누락:** CalendarDispatchWorker의 transient 완료는 claimToken/lease를 지운 뒤 `claim.schedule().cancelled()`인 경우에만 preserveUncertainty를 호출한다. CONFIRMED insert가 timeout/5xx를 반환하고 현재 일정도 아직 CONFIRMED이면 marker가 저장되지 않는다. 그 직후 다음 retry 전에 취소되면 새 claim은 만료 token을 발견할 수도 없다. 취소 GET404→SYNCED 이후 앞선 insert가 Google에서 늦게 완료되는 경우를 다시 놓친다. 만료 takeover뿐 아니라 provider 쓰기 결과가 불확실한 CONFIRMED 완료에서도 marker를 유지해야 한다. 이 순서의 테스트가 필요하다.
- **GW-I11 / audit 반복 실행:** 만료된 CONFIRMED claim takeover는 nextReconcileAt=now를 설정한다. 이어지는 CONFIRMED 성공 완료는 이 시각을 변경하지 않는다. repository는 schedule의 취소 여부와 무관하게 due audit를 선택하므로 정상 확정 일정에 대해 매 dispatch(현재 1초) GET/PATCH를 반복할 수 있다. 미래 취소에 필요한 불확실성은 보존하되 확정 상태에서 audit를 계속 dispatch하지 않도록 정리하고, 취소 outbox가 재조정을 깨우는 경계를 검증해야 한다.
- **GW-I12 / first-level cache:** backfillOnceInTransaction은 `findByBackfillPendingTrue`로 ProjectCalendar 엔티티를 먼저 managed 상태로 읽은 뒤 project lock과 calendar lock query를 수행한다. 같은 persistence context의 잠금 query는 이미 로드된 엔티티의 상태를 자동 refresh한다고 보장하지 않는다. 잠금을 기다리는 동안 disconnect/rebind가 완료돼도 오래된 binding 값이 남아 있을 수 있다. scalar candidate projectId만 먼저 읽고 잠금 후 엔티티를 최초 로드하거나, 잠금 아래 명시적 refresh/CAS를 사용해야 한다. 현재의 두 번 repository 조회를 fresh reread라고 판정하지 않는다.

**GW-I04 Calendar 부분은 부분 해소**다. 새로운 실제 stalled-body 테스트와 Future 취소 구조는 의미가 있다. 다만 JdkHttpTransport.send에서 header 응답을 기다린 다음 별도의 10초 body deadline을 시작하므로 request 전체는 약 20초까지 늘어난다. 계약의 총 10초 제한을 지키려면 HTTP 시작 전 monotonic deadline을 만들고 body에는 남은 시간만 전달한다. 이번 reviewer는 새 테스트를 실행하지 않았으며 parent가 compileTest의 다른 파티션 오류를 정리한 뒤 실행 결과를 제공해야 한다.

기존 전체 판정은 FAIL 유지다. 이 절은 Calendar 소스·테스트 적절성만 재검토한 결과이며, 프런트 브라우저 개선 및 다른 파티션의 결과를 대신 판정하지 않는다.

## Core 두 번째 재검토 — 2026-09-07 17:47~17:50 KST

identity/platform/infra만 확인했다. **전체 FAIL 유지**, core에도 아래 source blocker가 남아 있다. Gradle을 실행하지 않았다.

확인된 개선: state별 불변 복사본과 synchronized consume으로 이전 callback이 최신 flow를 제거하던 경로를 고쳤다. 명시적인 LOGIN/CONNECT 분류와 expired·replayed CONNECT의 ApplicationPrincipal 복원은 SecurityContextHolder뿐 아니라 HttpSessionSecurityContextRepository.saveContext를 사용한다. GW-I17의 configured fail-closed gate와 파라미터화된 설정 누락 테스트가 추가됐다. refresh는 HTTP 시작 전 단일 deadline을 만들고 헤더 Future와 본문 Future에 남은 시간을 사용하며 1MB 초과를 파싱 전에 거절한다. 실제 localhost oversized/stalled-body 테스트 소스를 확인했다. GW-I13의 V8 allowlist, 파일 수7, checksum 및 tampering scenario도 수정됐다. 이 부분들은 소스 수정 확인이며 실행·PG 통과로 판정한 것은 아니다.

남은 항목:

- **GW-I02:** `OidcConfiguration.java:164`의 restoreSnapshotOrClear는 snapshot이 없는 경우 `SecurityContextHolder.clearContext()`만 호출한다. OAuth 인증 필터가 success handler 전에 저장한 HttpSession의 SecurityContext를 빈 context로 저장/제거하지 않는다. unknown 또는 만료 LOGIN success callback에서 현재 요청 holder만 지워지고 다음 요청이 저장된 OIDC Authentication을 다시 읽을 수 있다. CONNECT snapshot 복원과 별개로 no-snapshot 경로도 session repository까지 처리하고, 테스트에서 실제 SPRING_SECURITY_CONTEXT와 다음 요청의 인증을 검사해야 한다. 현재 테스트는 주로 holder 및 flow map을 확인한다.
- **GW-I02 / 서비스 보존:** 새 grant의 requested feature만 required()로 검증하고 `GoogleAuthorizationService.connect`는 scope 집합 전체를 교체한다. 이미 완전히 연결된 Drive에 대해 Gmail 권한만 담긴 새 grant가 돌아오면 기존 Drive capability를 잃는다. 계약의 독립 서비스 보존을 위해 이전의 완전한 capability가 회귀하는 grant를 거절하거나 부모 승인된 별도 저장 설계를 적용해야 한다. 과거 scope를 새 token에 임의 합쳐서는 안 된다. 이 부정 경로 테스트는 아직 없다.
- **GW-I03:** GoogleAccessException의 String-code 생성자는 TEMPORARY category지만 status()는 PERMISSION_REQUIRED다. Calendar의 credential 실패 처리처럼 status를 소비하는 코드가 refresh 일시 실패를 권한 거절로 오분류한다. category/status 의미를 일치시키고 소비부의 연결 상태 안내·retry를 시험해야 한다.
- **GW-I15 / startup blocker:** `GoogleTokenRefreshTransportClient.java:25`의 운영 생성자와 `:28`의 package-private 테스트 생성자가 공존하지만 둘 다 @Autowired가 없고 기본 생성자도 없다. Spring의 생성자 선택은 declared constructors를 보므로 기존 단일 생성자 자동 주입에 의존할 수 없다. 운영 생성자를 명시하고 실제 context 시작을 검증한다. 최초 읽은 OAuth 테스트의 RuntimeException 인자 컴파일 문제는 17:49 최신 파일에서 수정되어 별도 미해결 결함으로 남기지 않았다.

ExternalServiceFailure는 현재 allowlist로 안전한 code만 외부 응답에 내보낸다. provider raw body를 API에 반환하는 새 경로는 이번 범위에서 확인하지 않았다. 새 localhost 테스트와 설정 gate 테스트는 적절한 개선이지만 callback의 저장된 Spring session, 실제 DB refresh 회전/동시성 및 전체 context 실행 증거는 여전히 필요하다.

## Calendar·Workspace 재검토 — 2026-09-07 17:53~17:58 KST

Core의 마지막 수정 네 항목은 다른 구현자가 진행 중이므로 제외했다. **전체 FAIL 유지**다. 부모가 공유 테스트를 소유하므로 reviewer는 Gradle을 실행하지 않았다. Workspace focused 11/11은 결과 문서와 테스트 소스를 대조했다.

소스에서 해소를 확인한 항목: GW-I08의 Gmail 수신자 배열/ISO 시각은 DTO·MockMvc 검증과 일치한다. GW-I14의 CLOSED module 및 api NamedInterface가 생겼다. GW-I07의 MIME 생성은 Jakarta Mail로 전환했고 plain 본문을 우선한다. Calendar는 scalar projectId 후보→project lock→첫 calendar 엔티티 읽기로 backfill의 first-level cache 문제를 고쳤다. provider dispatch 전에 uncertainty를 저장하므로 확정 timeout 직후 취소가 이전 시도 근거를 잃던 경로도 개선했다. Calendar HTTP는 이제 시작 전 하나의 절대 deadline과 남은 body 시간을 사용한다. 이러한 소스 수정의 실제 전체/PG 검증은 별도로 필요하다.

### GW-I11: audit 대상과 종료 시점의 두 결함이 남는다

CalendarDispatchWorker가 확정 성공 때 nextReconcileAt=null로 바꾸지만 CalendarProjectionRepository.claimable은 **nextReconcileAt is null도 due로 선택**한다. 따라서 확정 일정의 audit 반복 실행은 아직 멈추지 않는다. due audit는 명시적 nextReconcileAt이 있는 경우만 선택하도록 계약과 의미를 맞춰야 한다.

또한 모든 claim이 preserveUncertainty(now+6h)를 무조건 호출한다. 정상적인 취소 audit claim도 30초마다 horizon을 새로 6시간 연장하므로 audit는 종료되지 않는다. 취소 확인 자체와 새로 불확실해진 외부 쓰기를 구분하여, 기존 취소 audit가 성공했을 때 기존 horizon을 유지해야 한다. 고정된 horizon을 지나면 선택이 종료되는 Clock 기반 테스트가 필요하다. 두 항목 모두 기존 GW-I11이며 단순 실행 증거 대기가 아니다.

### GW-I06: assigned-ID JPA merge가 같은 requestId의 중복 발송을 허용할 수 있다

GmailService.claim은 initial findById 뒤 새 MailSendRequestEntity에 이미 non-null EmbeddedId를 지정하고 saveAndFlush를 호출한다. 엔티티에는 nullable @Version이나 Persistable.isNew가 없으므로 Spring Data JPA의 save는 INSERT 전용 persist가 아니라 **merge**를 사용한다. 요청 A/B가 둘 다 initial find에서 미존재를 읽은 후, A가 commit하고 B가 merge하면 B의 merge 조회는 A 행을 발견해 새 SENDING 상태로 갱신할 수 있다. unique insert 예외가 발생하지 않고 B도 자기 claim 성공으로 판단하여 두 번째 POST를 한다.

새 동시성 테스트는 ConcurrentHashMap.putIfAbsent를 saveAndFlush 대신 사용하므로 JPA merge 동작을 재현하지 않는다. 실제 DB의 INSERT ON CONFLICT DO NOTHING 같은 원자적 삽입 결과 또는 명시적 EntityManager.persist와 commit된 unique 충돌 재조회로 claim 소유권을 결정해야 한다. 단일 provider POST 요구의 핵심 source blocker다.

동일 ID의 추가 미해결 항목으로 finish와 60초 receipt age-out은 여전히 일반 find/update이며 row version·잠금·조건부 update가 없다. 오래된 SENDING을 읽은 age-out이 새 SENT를 UNKNOWN으로 덮거나, finish가 DB 상태를 변경하지 못했는데 로컬 row의 SENT를 반환할 수 있다. finish의 account/generation guard도 저장 transaction 안에 없다. 종료 상태의 단조로운 전이와 실제 DB 동시성 테스트가 필요하다. provider 성공 뒤 로컬 저장 예외를 UNKNOWN으로 반환하는 새 처리 자체는 개선으로 확인했다.

### GW-I04/GW-I03: Workspace transport와 제공자 오류 분류는 아직 미수정

googleworkspace/GoogleHttpClient.Default는 여전히 헤더 수신 뒤 InputStream.read를 같은 스레드에서 무기한 기다린다. Calendar·identity에 넣은 전체 deadline 수정이 Workspace에는 적용되지 않았다. 새 Drive localhost 테스트는 별도로 만든 anonymous transport를 사용하므로 이 운영 transport를 검증하지 않는다.

GoogleServiceException은 여전히 일반 RuntimeException이며 safe ExternalServiceFailure 처리기로 연결되지 않아 500으로 떨어진다. Drive/Gmail의 check는 모든 403을 PERMISSION_REQUIRED로 취급하고 API-disabled·quota를 구분하지 않는다. 결과 문서의 공통 오류/10초 제한 주장을 그대로 수용할 수 없다.

### GW-I05: Drive의 저장 권한·개인 목록 반환 경계가 남는다

주입 생성자와 20개 상한, pageToken 길이 제한, attach 전후 generation 검사는 추가됐다. 그러나 files는 provider 응답 후 generation 확인 없이 개인 목록을 반환한다. attach는 HTTP를 긴 transaction 밖으로 옮겼지만 마지막 role 검사와 실제 insert가 같은 짧은 project-lock transaction으로 보호되지 않아 강등 뒤 저장되는 경쟁이 남는다. 동일 파일 중복 첨부도 원자적 재생 검증이 필요하다. 프로젝트 목록의 profile 조회는 여전히 참조마다 호출하는 N+1이며 계약의 bounded batch 조회와 다르다.

GW-I07의 읽기 상한에서는 depth/parts 초과를 truncated로 표시하지 않는 부분이 남았다. 이 항목은 입력이 일부 생략됐다는 사용자 표시를 정확히 해야 하는 작은 수정이다.

상위 검증 문서의 B03/B04는 실제 Chrome에서 정상 발송 뒤 불필요한 native confirm이 사라지고 UNKNOWN→SENT 확인이 POST 추가 없이 수행됐다는 근거다. GW-I16의 해당 브라우저 결함 해소 증거로 인정한다. 전체 UI 거절·mobile·keyboard 및 backend DTO와의 통합 검증까지 대신하는 것은 아니다.
