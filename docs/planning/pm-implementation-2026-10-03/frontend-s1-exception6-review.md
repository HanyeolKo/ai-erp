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
