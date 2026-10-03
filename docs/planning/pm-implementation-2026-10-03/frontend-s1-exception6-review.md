# F1 attempt6 독립 검토

- Task: ERP-PM-IMPLEMENT-20261003-F1; contract r2; native ai-erp-reviewer / gpt-6-astra / high; usage null.
- **task-review FAIL / changes-requested.** 기존 `F1-REPAIR-REGRESSION-COVERAGE` P2가 OPEN이다. `F1-QUICK-CREATE-CONTINUITY` P1은 아래 방어 코드로 source-closed이며 affected Chrome 확인은 pending이다. 현재 후보의 새로운 production FAIL은 확인하지 않았다.
- 앞선 attempt1–5 FAIL과 cap3/예외4·5·6 이력을 보존한다. 재시도나 모델 변경 권한을 부여하지 않는다. 최종 acceptance는 부모가 결정한다.
- 검토 기준 경로 C:/Users/USER/.codex/worktrees/project-management-redesign/AI ERP, baseline961ef045090d0fc8f50785cbc671886048a2120f + frozenR6. PM은 frontend/src/pm-default-workspace.test.tsx, SB는 frontend/src/session-boundary.test.tsx를 뜻한다.

## 계약 요구사항 7개

| 번호 | 판정과 근거 |
|---|---|
| 1 | source PASS. ProjectPlan.tsx:269–281은 exact operationKey를 먼저 비교하고 variables/body/requestId/operationId/attemptId/sessionGeneration 형태를 검증한 뒤 기존 식별자·세대 비교를 유지한다. 다른 mutation의 body 없는 변수를 역참조하지 않는다. |
| 2 | 부분 충족. PM:101–117은 실제 기존 TASK PATCH 성공을 기다린 뒤 같은 App에서 POST를 실행하고 두 요청 각각 1회와 성공 UI를 확인한다. 그러나 전체 입력/POST payload의 정확성 assertion 및 같은 operationKey의 malformed 후보 검사는 없다. :116의 DOM 문자열 부재만으로 uncaught effect 오류 전체 부재를 주장하지 않는다. |
| 3 | FAIL: P2 유지. PM:195–200은 이탈 전 Date를 전진시키고, 이탈 후 setTimeout/clearTimeout fake timer를 설치한다. 이탈 당시 이미 예약된 native GC timeout은 그 후의 fake timer 전진으로 검증되지 않는다. advance는 act 밖이며 hash만 확인하고 실제 inactive 전환을 확인하지 않는다. 제목만 검사하고 state/locked phase는 검사하지 않는다. http.ts:14는 기본적으로 Date만 fake하며 :3의 cleanup은 real timers를 복구한다. Infinity GC 구현 자체의 기존 source closure는 유지한다. |
| 4 | 부분 충족. SB:293–313은 deferred TASK POST, App401, 실제 old draft 데이터 부재(:308), 응답 resolve 뒤 old query 부재(:311), login UI를 검사한다. 다만 mutation의 최종 정착을 기다리는 monitorMutations assertion이 없고, 부재 predicate는 이미 true일 수 있다. 새 actor/새 draft 입력·reset·toast·cache 무영향 시나리오는 없다. 이를 완전한 actor isolation 회귀로 인정하지 않는다. |
| 5 | 부분 충족. PM:210–241은 성공 응답에서 own draft를 입력한 뒤 current403, project GET 반복, TASK 생성 링크 부재, 같은 generation title/state를 확인한다. :238은 removeQueries 호출 키만 검사한다. 실제 plan/member 데이터의 사전 존재와 사후 부재 assertion은 없으므로 보호 캐시 제거 완료를 증명하지 못한다. |
| 6 | FAIL: 아래 유효 재사용과 실제 누락을 구분한다. |
| 7 | source PASS 유지. ProjectPlan.tsx:215–251,262–263,278–308은 draft Infinity GC, 동기 입력·reactive phase, immutable retry payload 및 operation/attempt/session guard를 유지한다. 프로덕션 diff는 reconciliation에 한정된다. 새 actor 관련 회귀 증거 부족은 P2이며 새로운 production failure로 확대하지 않는다. |

## 유효 재사용과 누락

- 유효: PM:169–172 전체 ASCII 단일 change; :147–150 실제 route 거부; :175–184 409 edited title/newUUID; :77–98 500 전체 동일 body/UUID; :119–128 이전500 후 remount/deferred retry 잠금; :131–144 pending mode remount; :187–192 성공 후 newUUID.
- 유효: PM:159–161 TASKpresent/schedule0; :244–247 TASK0/schedule1(http.ts:24); :153–156 partial nonzero/authoritative off-page blocker; :250–253 미관측 선행 unknown; :256–258 plan500/성공한 일정 영역; :12–40 default/legacy 경로.
- 여전히 누락: 연속 rapid ASCII, TASK beforeunload, dashboard500 역방향과 독립 retry, true/container/filter empty 및 partial zero/overview nonzero 분리, active explicit BLOCKED/loaded blocker 대비 DONE·CANCELLED 제외, explicit mode=default+valid view/kind/scope, compact initial pending/retained refetch/error/retry. 변경되지 않은 legacy·일정 검사를 TASK compact assertion으로 대체하지 않는다.
- 기존 INPUT-LOSS, OVERVIEW-REGION-INDEPENDENCE, TASK-EMPTY-STATE, BLOCKED-FACTS, DEFAULT-TARGETS, PARTIAL-COVERAGE 및 geometry 관련 source closure를 유지한다. 위 누락은 계약상 검사 부족이며 해당 구현이 오작동한다는 판정은 아니다.

## 직접 읽은 원시 실행 증거

raw6 = tmp/pm-implementation-2026-10-03/f1/exception-repair6/. 아래 시각은 2026-10-04 KST이며 meta/stdout/stderr를 모두 읽었다.

| 원본 | 실행 시각 | 결과 |
|---|---|---|
| frontend-test-final.* | 02:43:29.913–02:44:11.970 | pnpm frontend:test; exit0; 30 files/358 PASS. stderr의 jsdom navigation 경고 2건을 보존한다. |
| frontend-typecheck-final.* | 02:44:30.379–02:44:39.586 | pnpm frontend:typecheck; tsc -b --pretty false; exit0; stderr empty. |
| frontend-build-final.* | 02:44:55.699–02:45:04.454 | pnpm frontend:build; tsc/Vite86 modules; exit0; stderr empty. |
| p1-update-quick-create-final.* | 02:46:09.929–02:46:15.712 | 실제 PATCH→create focused test; 1PASS/22skipped; exit0; stderr empty. |
| whitespace-final.* | 02:45:51.201–02:45:51.252 | exit0; stdout 존재. 그러나 command는 설명 문자열이며 정확한 PowerShell 식·scoped paths가 없어 exact-command 요건 미충족. |

- ACK.md는 local fallback이라고 명시한다. 문서 자체를 실제 invocation 또는 pre-edit 전달 성공 증거로 대체하지 않으며 native 배정은 부모 기록에 의존한다.
- mixed-cache-before-fix.md는 fresh baseline 미실행을 정직하게 명시한다. prior exception5-review가 검증한 originalD frozenR5 실제 Chrome PATCH200→POST200→TypeError/blank 원본을 역사 증거로 재사용한다. 이번 검토에서 그 브라우저 실행을 새로 수행하거나 fresh attempt6 baseline으로 집계하지 않았다.
- SHA256을 직접 계산했다. manifest의 source3과 HTML/CSS/JS3 모두 일치했다. diff=57DBE4D63DB4E2C3A77FE47CAA214D1A87B5C487E0665E043B386139D872BA76; JS=030AE6AD741718D187E4D00E938FCB3C685F725A44D28711B3CA750F75F3F068; helper=4E4EFDF6B4F0B75D4832495A3F1BC13EE5AFD9C6F30A1160B831C392C5DD7E1D. Source3 전체 값은 같은 manifest에 있다. generated baseline은 이번에 재계산하지 않았다.
- offline-contract-only이며 live ERP/Google 통합 증거가 아니다. 부모 R6 Chrome와 same-SHA CI는 pending으로 남긴다. 이는 실행 FAIL 건수에 합산하지 않는다.
- 검토자는 tests/build/browser/server/CI/provider/Git을 실행하거나 code/test/helper/config를 변경하지 않았다. 이 운영 검토 원문만 저장하며 Notion에 별도 등록하지 않는다. 추가 수정·새 시도는 부모 판단 사항이다.

## 추가 검토: frozenR6 실제 Chrome 및 부모 whitespace 원본

- 같은 attempt6의 증거 추가이며 기존 검토를 보존한다. native Astra/high, usage null이다. **전체 FAIL / changes-requested와 기존 P2 OPEN을 유지한다. F1-QUICK-CREATE-CONTINUITY P1은 source와 아래 실제 affected fixture 관찰에서 CLOSED다.** 신규 production FAIL은 확인하지 않았다. historical attempt1–5 FAIL은 그대로 남는다.
- 부모가 보고한 제품 commit은 a7ca69d63582923736d7951812632efcfa573d42다. 현재 source3 SHA256을 다시 계산해 frozenR6 manifest와 일치함을 확인했다. commit 객체 자체는 Git 금지 범위이므로 직접 조회하지 않았다. manifest는 여전히 dispatch baseline961ef045를 기록하며 이 사실을 새 commit과 혼동하지 않는다.
- 원본 O = D:/onedrive/Documents/ChatGPT/AI ERP/tmp/pm-implementation-2026-10-03/browser/f1-exception6-observations/. 원본 S = C worktree의 tmp/pm-implementation-2026-10-03/browser/f1-candidate-r6/. 아래 CallToolResult의 해당 text와 isError 필드를 직접 읽었으며 isError=true는 없었다. helper는 offline own-API fixture이며 live ERP/Google 증거가 아니다.
- O/r6-manager-input-and-existing-update.json, r6-manager-create-after-existing-update.json과 S/server-4250.stdout.log에서 기존 TASK PATCH200 이후 제목 R6 durable draft a12345, state READY의 POST200을 확인했다. UUID 6ed98a1a-ea05-406c-a9fb-87cc5e284466, task-created-1이 생성되었고 작업 화면·새 행·성공 문구가 유지되며 own form은 빈 제목/BACKLOG로 복구됐다. r6-manager-keyboard-submit.json은 Enter 실행 성공과 두 번째 행을 기록한다. 두 번째 POST의 UUID는 31c98948-bc7e-42ba-93d2-17c6179f2fd7로 다르다. post-console/final-console-21에는 404 한 건만 있으며 server의 favicon404와 대응한다. R5 body.requestId 예외·blank가 이번 흐름에서는 재현되지 않았다.
- O/r6-manager-390-geometry.json은 2026-10-03T18:02:26.446Z,390×844,DPR1, document client/scrollWidth375/375를 기록한다. r6-manager-640-dpr2-geometry.json은 18:07:24.028Z,640×450,DPR2,625/625다. 둘 다 overflow0이며 JS index-Dpxn5-9C.js/CSS index-Dgjof72h.css가 manifest와 연결된다. 390 자료에서 제목·상태·생성 버튼 높이44px와 폭303px를 확인했다. 이를 모든 viewport/zoom/접근성의 포괄적 검증으로 확대하지 않는다.
- O/r6-unknown-first-submit/departure-confirm/remount/result-replay.json 및 S/server-4251.stdout.log는 적용 후500 → 실제 이탈 확인 → 재진입 시 제목·READY 잠금 → 동일 전체 body/UUID 9f27f064-619e-4814-9398-b0ba877016d0 재전송200 → 원래 task-created-1 반환을 증명한다. R5의 장시간 부재 증거는 역사 자료로 유지하며 R6에서 새 5분 대기를 수행했다고 주장하지 않는다.
- O/r6-conflict-first-submit/edited/resubmit.json 및 S/server-4253.stdout.log는409 이후 editable 입력과 수정 제목 성공을 증명한다. UUID는 8ba84578-6b23-4529-b819-79ea7bfea791에서39eeb7e3-3951-4568-af60-916feb67303c로 바뀌었다.
- O/r6-pending-second-submit/second-mode-away/second-mode-back/settled.json과 S/server-4252.stdout.log에서 두 번째 요청 fe1dd181-0d2e-4a49-925c-58af5eb9ceea는18:09:12.868Z에 적용되고18:09:27.876Z에 응답했다. 그 사이 advanced mode 왕복 후에도 정확한 제목·READY와 disabled input/state/저장 중 버튼을 확인했다. settled snapshot은 빈 제목/BACKLOG와 생성 행을 보여준다. 이 delayed-create 관찰에 이전500이 있었다고 주장하지 않는다. 이전500+pending retry는 앞서 연결한 소스 테스트 범위다.
- O/r6-viewer-snapshot은 제목/상태/생성을 disabled로, r6-member-input은 own draft와 enabled 생성 버튼을 보여준다. r6-forbidden-overview는 TASK 접근 거부와 읽기 전용 안내를 보여준다. 이것으로 실제 보호 query cache 삭제나 세션 종료를 추론하지 않는다.
- O/r6-plan500-snapshot/retry, r6-dashboard500-snapshot/retry와 S/server-4258/4257.stdout.log는 각각 실패 영역 GET이 재실행되고 성공한 다른 영역이 유지됨을 확인한다. fixture는 계속500이므로 retry 성공 복구를 증명하지는 않는다. partial snapshot에는 관측 범위 문구가 있다. empty/container snapshot은 모두 TASK 없음·일정 없음으로 표시되므로 이 화면 둘만으로 container/true-empty 구분의 완전한 충족을 주장하지 않는다. 소스 assertion 누락은 기존 P2에 남긴다.
- 부모 whitespace 증거 G = originalD tmp/pm-implementation-2026-10-03/exception6-parent-git/. staged-diff-check.meta.json과 stdout은03:03:41.599–03:03:41.713 KST, exit2, review EOF blank를 기록한다. 이를 보존한다. staged-diff-check-rerun.meta.json은03:04:22.902–03:04:23.004, exit0, 정확한14 scopePaths와 git diff --cached --check를 기록하며 stdout/stderr .log는 실제 존재하고0bytes다. 부모가 extra EOF blank만 제거한 뒤의 scoped check 증거로 인정하므로 앞선 raw6 whitespace의 exact-command 증거 부족은 이 범위에서 보완됐다. 이번 addendum 이후의 새 검사 실행을 주장하지 않는다.
- **남은 필수 조건:** 기존 malformed mutation/실제 inactive timer/current403 보호 데이터 부재/세션 종료·늦은 callback·새 actor 관련 검사와 나머지 named P2 누락은 FAIL이다. 실제 helper 관찰로 해당 소스 누락을 대체하지 않는다. same-SHA CI run37142873458은 부모 배정 시점 기준 pending이며 이번 검토자는 조회하지 않았다. P2와 CI 때문에 F1 acceptance/S2/S3 진행은 승인하지 않는다. 추가 시도·attempt7·수정·배포 권한을 부여하지 않는다.
- 검토자는 원본 읽기·SHA 계산·이 원문 추가만 수행했다. tests/build/Git/browser/server/CI/provider 실행이나 code/test/config/helper 수정은 없으며 Notion 별도 보관도 하지 않았다.

## 추가 검토: source6 같은 SHA CI 완료

- originalD의 tmp/pm-implementation-2026-10-03/ci-attempt6/run.json, verify-job.log, artifact-download.meta.json과 내려받은 HTML 결과를 직접 읽었다. run37142873458은 headSha a7ca69d63582923736d7951812632efcfa573d42, completed/success이며 harness/verify의 모든 기록된 단계가 success다. 앞선 CI pending 상태를 이 완료 증거로 갱신한다.
- HTML 원본의 실제 결과는 integrationTest61/실패0, openapiContractTest9/실패0, 일반 test194/실패0이다. verify-job.log는2026-10-03T18:19:17Z에 frontend30 files/358 PASS를 기록하고, API 생성·typecheck·build·Docker image·deployment-contract 및 harness 단계 성공은 run.json 단계와 연결된다. deployment-contract 검사 성공은 실제 배포 완료를 의미하지 않는다.
- artifact11281641427의 다운로드는18:27:15.996–18:27:19.826Z에 기록됐다. 실제 ZIP의 SHA256을 독립 계산해88B5485F8DDC79697575B954466FAB2E4160B1CA366A891CD44993B4663665E1로 확인했으며 다운로드 metadata 및 upload 로그의 digest와 일치한다.
- **source6 task-review FAIL / changes-requested와 F1-REPAIR-REGRESSION-COVERAGE P2 OPEN은 유지한다.** 실행된 suite의 성공은 인정하지만 빠진 assertion을 보완하지 않는다. correction7 준비에서 찾은 Schedules 제거-key 문제는 현재 정적 반례이며 source7 실행 실패로 집계하지 않는다. 앞선6 FAIL 기록과 모든 cap/예외 이력은 보존한다.
- 검토자는 로컬 원본 읽기·ZIP 해시 계산·허용된 검토 문서 추가만 수행했다. CI/provider/Git/tests/build/browser/server 실행은 없었다. attempt7은 UNAUTHORIZED/UNASSIGNED이며 F1 acceptance, S2/S3, 새 시도나 배포를 승인하지 않는다. usage null이다.
