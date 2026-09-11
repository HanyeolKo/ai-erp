# Google Workspace 백엔드 구현 결과

- 구현자: `gpt-5.6-luna/high` (Spark quota 제한으로 승인된 fallback)
- 기준: `docs/ux/2026-09-07-google-workspace-contract.md` revision 1
- 상태: `in-progress-ready-for-review`; 최종 verdict는 `/root`와 독립 reviewer가 소유한다.

## 변경 내용

Identity에 암호화된 AES-256-GCM Google grant vault와 `identity::api GoogleAccess` named interface를 추가했다. `Feature {DRIVE, GMAIL, CALENDAR}`, `Credential(accessToken,generation)`의 redacted `toString`, 연결 상태, generation 검사를 제공한다. OAuth connect intent는 현재 ERP principal, Google subject, Spring OAuth state, generation, 10분 만료를 세션에 묶고, callback 성공 시에만 grant를 저장하며 실패·다른 계정·부족한 scope에서는 기존 ERP 로그인과 vault를 유지한다. state 문맥은 callback마다 원자적으로 한 번만 소비되며 만료·재생·알 수 없는 callback은 정상 provisioning으로 전환되지 않는다. disconnect는 ciphertext를 제거하고 generation tombstone을 남긴다. Refresh claim/lease는 짧은 DB transaction으로 획득·완료하고 provider I/O는 transaction 밖에서 수행한다.

Google Workspace 모듈은 고정 HTTPS host와 10초 deadline, 2MB 응답 상한을 가진 injectable HTTP transport를 사용한다. Drive metadata 조회·프로젝트 reference attach/remove, Gmail 목록·상세 MIME 텍스트 추출, 명시적 send receipt를 제공한다. Drive attach는 VIEWER를 차단하고 provider file identity/trashed를 재검증한다. Gmail send는 To 수·수신자/제목 검증, normalized payload hash, `SENDING` claim 후 단일 provider 시도, `SENT`/`UNKNOWN`/`FAILED` receipt와 request replay/mismatch를 적용한다. Identity refresh transport는 body read도 전체 deadline 안에서 취소하며 localhost synthetic transport 테스트로 응답 상한과 정지 body를 검증한다.

이번 보완에서는 `googleworkspace`를 CLOSED Spring Modulith module로 명시하고 `api` named interface를 추가했다. Drive provider 응답은 페이지당 20개로 제한하고 attach 전후의 credential generation과 project role을 재검사한다. Gmail DTO의 수신자는 배열로 반환하고 `internalDate`/`date`는 ISO-8601로 정규화한다. MIME은 Jakarta Mail/Angus로 RFC 인코딩하며 plain text를 우선하고 HTML은 inert text로 변환한다. 발송 receipt persistence 실패는 UNKNOWN으로 보존하고 동일 requestId+payload 재호출은 기존 receipt를 재사용한다.

V8 migration은 `identity.google_authorization`, `google_workspace` schema의 Drive reference/mail receipt, Calendar binding/claim/revision columns를 추가한다. 운영 compose와 deploy env validation에 workspace flag와 encryption key를 등록했다.

## 검증

```text
Java: D:/onedrive/Documents/ChatGPT/AI ERP/.tools/jdk-25.0.4.1+1
prior gradle compileJava --no-daemon '-Dorg.gradle.native=false'       PASS
prior gradle test --tests com.aierp.GoogleWorkspaceCoreTest --no-daemon '-Dorg.gradle.native=false'  PASS (4/4)
prior gradle test --no-daemon '-Dorg.gradle.native=false'              FAIL (88 total, 8 shared Calendar failures)
current Java25 compileJava                                               PASS (`tmp/google-workspace-backend-compile-latest.log` after core handoff)
current GoogleWorkspaceCoreTest                                          PASS (11 tests; `tmp/google-workspace-backend-focused-java25-final.log`)
git diff --check, python -m py_compile scripts/tests/deployment_contract.py PASS
```

로그:

- `tmp-google-backend-compile7.log`
- `tmp-google-backend-focused2.log`
- `tmp-google-backend-normal.log`
- `tmp-google-deployment-migration.log`
- `tmp-google-deployment-migration2.log`
- `tmp/google-workspace-backend-compile-latest.log`
- `tmp/google-workspace-backend-focused-java25-final.log`

현재 focused Java25 실행은 11개 테스트로 통과했다. 테스트에는 localhost HTTP provider를 통한 Drive 20개 상한, MockMvc Gmail 배열/ISO DTO, plain 우선 MIME 파싱, 동일 requestId receipt replay, DB uniqueness를 모사한 concurrent claim의 단일 provider POST가 포함된다. Docker/PostgreSQL integrationTest와 실제 Google API 호출은 실행하지 않았다.

## 최종 core 보완 인계

`gpt-5.6-luna/high` 승인 fallback으로 Spark quota 제한에 대응해 다음 bounded repair를 적용했다. 알 수 없거나 만료된 LOGIN callback에서 OAuth filter가 세션에 저장한 OIDC 인증을 지우도록 `restoreSnapshotOrClear`가 빈 `SecurityContext`를 `HttpSessionSecurityContextRepository`에 저장한다. 실제 세션 저장 context를 확인하는 unknown/expired callback 회귀 테스트를 추가했다. 운영용 `GoogleTokenRefreshTransportClient(Environment)` 생성자에는 `@Autowired`를 명시했다.

`GoogleAccessException`의 safe-code/category 생성자는 `TEMPORARY` 오류를 `PERMISSION_REQUIRED` status로 노출하지 않고 category를 보존하며 status는 null로 둔다. 새 incremental grant가 기존에 완전히 연결된 Drive/Gmail/Calendar capability를 provider scope에서 제거하면 `GOOGLE_SCOPE_REGRESSION`으로 저장 전에 거절한다. 이전 ciphertext, refresh token, scope, generation 보존과 encrypt/save 미호출을 각 capability에 대해 회귀 검증한다. OAuth resolver 회귀 fixture에는 실제 `/oauth2/authorization/google` request URI를 설정해 독립 state 검증을 유지했다.

이번 인계에서 실행한 source check는 `git diff --check` PASS다. focused Gradle 재실행은 기존 Gradle wrapper `gradle-9.2.0-bin.zip.lck` 잠금으로 실행하지 못했으며, 따라서 위 신규 테스트의 실행 결과는 pending이다. 전체 backend, PostgreSQL integrationTest, 실제 Google API 호출, push/deploy는 수행하지 않았다. Calendar worker의 temporary category 소비부는 승인 core path 밖에 있어 parent handoff 대상으로 남긴다.
