# F1 attempt5 독립 소스·증거 검토

- Task: `ERP-PM-IMPLEMENT-20261003-F1`, authorized attempt5, contract r2. Native `ai-erp-reviewer` / `gpt-6-astra` / `high`; usage null.
- **task-review FAIL / changes-requested.** 안정된 결함 키 2개가 OPEN이다. 소스에서 확인한 P1 한 건과 필수 회귀검사 누락 P2 한 건이다. 브라우저·같은 SHA CI의 미수집 증거는 pending이며 실행 실패로 집계하지 않는다.
- 검토 대상은 C:/Users/USER/.codex/worktrees/project-management-redesign/AI ERP의 `ac7ff1160c30d89d7aa41b2a5339f238af397eac` + 동결 F1 diff이다. 아래 소스 경로는 이 worktree 기준이다. B1/backend는 검토하지 않았다.
- 계약 r2, exception5 dispatch/preparation, exception4 review, 현재 frontend source/tests/result, 실제 `tmp/pm-implementation-2026-10-03/f1/exception-repair5/` 원본을 대조했다. 승인된 UI r3 / visual r1 / mapping r2는 그대로 적용한다.
- 부모의 native Luna/high 배정·직접 사용자 예외 승인·범위 제한을 확인했다. `ACK.md`는 사후 transport fallback임을 명시한다. pre-edit 전달 성공으로 해석하지 않는다. attempt6·모델 변경·추가 보수는 승인되지 않았다.

## Stable keys

| Key | Disposition | 소스·증거 판단 |
|---|---|---|
| F1-QUICK-CREATE-INPUT-LOSS | CLOSED at source | `ProjectPlan.tsx:234-245,292-305`는 제목·상태를 동기 React state에 반영하고 같은 값을 draft에 기록한다. delayed query notification만으로 controlled input을 구동하던 원인을 제거했다. PM test:149-153은 즉시 전체 ASCII 값을 검사한다. 실제 Chrome 차등 관찰은 pending이다. |
| F1-QUICK-CREATE-CONTINUITY | **OPEN P1** | `ProjectPlan.tsx:269-271`은 전역 mutation 전체를 순회하며 `variables.body.requestId`를 무조건 읽는다. task mutationKey 또는 body shape 검증이 없다. 아래의 다른 mutation 이력 반례에서 예외가 발생한다. |
| F1-OVERVIEW-REGION-INDEPENDENCE | CLOSED at source; affected runtime pending | `Schedules.tsx:56-65`는 accessRead denial 뒤 plan/member query를 제거하고 현재 project를 refetch한다. :69-72는 cached MANAGER에도 TASK 작성을 허용하지 않는다. draft namespace는 제거 대상이 아니다. :68,100-108은 plain500과 성공한 다른 영역을 분리한다. PM:201-204는 plan500 방향만 검사한다. |
| F1-TASK-EMPTY-STATE | CLOSED at source | `Schedules.tsx:74-76,103-108`은 TASK onboarding과 일정 섹션을 분리한다. PM:189-193 및 http.ts:24의 scheduleCount1 fixture가 TASK0+schedule1에서 예정 일정·일정 카드 제목을 실제 검사한다. |
| F1-BLOCKED-FACTS | CLOSED at source | `ProjectPlan.tsx:313-323`은 authoritative blockerIds/관측된 미완료 선행/명시적 BLOCKED를 보존하고 DONE/CANCELLED를 차단 집계에서 제외한다. incomplete+미관측 선행에는 `선행 상태 확인 필요`를 표시한다. PM:133-136,195-198이 두 off-page 경우를 구분한다. |
| F1-REPAIR-REGRESSION-COVERAGE | **OPEN P2** | 새 검사는 9개이며 PM 전체는21개다. session-boundary9개는 변경되지 않았고 TASK draft/session 종료/늦은 TASK callback을 검사하지 않는다. >5분 테스트와 current403 테스트도 제목이 주장한 전체 경계를 검사하지 않는다. 아래 matrix를 따른다. |
| F1-DEFAULT-TARGETS / F1-PARTIAL-COVERAGE | CLOSED at source 유지 | 이번 동결 diff는 PM test, ProjectPlan, Schedules 3개 파일뿐이다. 기존 scoped geometry와 partial label의 소스 종결 판단을 유지한다. 실제 영향 관찰은 부모 증거를 기다린다. |

`F1-COMPACT-FILTER-LABELS`, `F1-OVERVIEW-ACTION-AUTHORITY`, `F1-DEFAULT-MODE-PRECEDENCE`, `F1-DEFAULT-INITIAL-LOADING`의 기존 소스 종결도 유지한다. Calendar·legacy CSS에 새 요구사항을 추가하지 않는다.

## P1의 구체적 반례

1. 같은 App 세션에서 기본 TASK 목록의 기존 TASK 상태를 변경한다. `ProjectPlan.tsx:389,411`의 update mutation variables는 `{ item, field, value }`이며 `body`가 없다. 이 mutation은 캐시에 남는다.
2. 이어서 quick create에 제목을 입력하고 제출한다. :261에서 shared draft에 submitted와 attemptId가 생긴다.
3. :265-290의 reconcile은 current submitted가 있으므로 `getAll().filter`를 수행한다. 이전 update의 variables는 truthy이므로 :271에서 `undefined.requestId`를 읽어 TypeError가 난다. 렌더 후 effect 또는 mutation-cache notification 경로에서 발생한다.
4. 이전 일정 저장처럼 body가 없는 다른 mutation 이력도 같은 조건이다. TS `as TaskCreateMutation`은 런타임 shape를 검증하지 않는다. 새 PM 검사들은 quick-create 작업만 순차 실행하므로 이 반례를 포함하지 않는다.

이는 소스에서 도출한 반례이며 이번 검토자가 실행한 브라우저 재현은 아니다. 이전 첫 historical error를 선택하던 결함은 제거됐지만, 같은 continuity key의 완료를 막는 현재 결함으로 기록한다. 부모에게 mutation family를 먼저 한정하고 유효 variables의 session/operation/attempt를 비교해야 한다는 보수 방향을 반환한다. 수정은 수행하지 않았다.

## 계약별 의미 있는 검사 matrix

| 요구 | 현재 실제 증거 | 남은 범위 |
|---|---|---|
| 동기 전체 입력·빠른 입력 | PM:149-153의 fireEvent 전체 ASCII, 기존 userEvent.type 시나리오 | 연속 rapid ASCII 전용 assertion·Chrome 동일 입력 differential은 미확인이다. |
| reactive phase·per-draft Infinity GC | ProjectPlan:215-220,238-245; mode remount PM:111-124, 실제 route remount PM:99-108 | PM:175-179는 화면을 떠나기 **전** 시간을 이동하며 http.ts:14는 Date만 fake한다. inactive setTimeout GC를 전진시키지 않는다. 실제 >5분 absence 증거가 필요하다. |
| 이전500 오류가 pending retry를 풀지 않음 | PM:99-108의 route 왕복·deferred retry·disabled·완료 reset; 최종355 PASS | 다른 mutation 이력 P1, stale operation/attempt 강제 교차 응답은 검사하지 않는다. |
| unknown500 동일UUID/본문 | PM:75-96은 첫 body와 재시도 body 전체 동일성을 검사한다. | 현재 소스 :247-251의 session/operation/attempt guard와 :269-275의 remount 비교는 존재하나 위 P1을 해결하지 못한다. |
| 409 edited UUID / 성공 후 새UUID | PM:155-164,167-172에서 각각 다름을 검사한다. ProjectPlan:292-302와262가 구현 근거다. | 409 뒤 dirty/beforeunload 수명주기 assertion은 없다. |
| TASK session 종료·늦은 callback | App:127-148은 configuration 외 query 제거, ProjectPlan:247-251은 current generation 확인을 수행한다. | session-boundary:57-289는 일정·초대·그룹 생성 사례9개다. TASK namespace 제거 및 늦은 TASK callback의 새 draft 무영향 검사는 다른 test에서도 찾지 못했다. |
| current403 protected cache 제거·project 재확인·own draft | PM:144-146은 initial403의 작성 링크 부재, :182-186은 project GET≥2를 검사한다. | cached-success→403, 실제 plan/member 캐시 제거, seeded own draft 보존을 검사하지 않는다. 테스트 제목의 own draft 주장은 assertion과 다르다. |
| 독립500·empty·partial | PM:139-141 TASKpresent/schedule0, :189-193 TASK0/schedule1, :201-204 plan500, :133-136/:195-198 off-page 사실 | dashboard500 역방향, true/container/filter/partial-zero 및 overview nonempty partial의 계약 matrix가 완성되지 않았다. |
| BLOCKED/DONE/CANCELLED | ProjectPlan:313-323에서 소스 조건을 확인했다. | compact 집계에 대한 세 상태별 의미 있는 assertion은 없다. |
| default/legacy·loading/retained/error/retry | PM:10-38에서 기본·legacy view/kind/scope 경로를 검사한다. 기존 source-closed 상태는 유지한다. | explicit mode=default+valid selectors 및 compact initial pending/retained refetch/error/retry를 함께 검사하는 회귀 증거가 없다. |
| dirty route/beforeunload | PM:127-130은 실제 route 이탈 거부 후 초안 보존을 검사한다. | beforeunload 기존 검사는 schedule-ui-refinement의 일정 editor이며 TASK 전용 검사가 아니다. |

## 실제 실행 원본과 연결

- 최종 `root-test-final.meta.json` / stdout / stderr를 직접 읽었다. `pnpm frontend:test`, C worktree, 2026-10-04 01:22:50.341–01:23:30.180 KST, exit0, **30 files /355 tests PASS**다. stderr의 jsdom navigation/TimeoutOverflow 경고는 보존하며 실패로 바꾸지 않는다.
- 최종 `root-typecheck-final.*`: `pnpm frontend:typecheck`, 01:23:47.817–01:23:55.750 KST, exit0; stdout에서 `tsc -b --pretty false`를 확인했다. `root-build-final.*`: `pnpm frontend:build`, 01:24:09.127–01:24:17.374 KST, exit0; tsc와 Vite86 modules 및 최종 asset 이름을 확인했다. 이 두 stderr는 비어 있다.
- `targeted.*`/`targeted2.*`의 remount 실패, `targeted3.*`/`targeted4.*`의 실패, `pm-final2.*`의 pending retry 실패(exit1)를 보존했다. `targeted5`는 최종 source 이전의29 PASS다. 이를 최종21 PM+9 session 또는 새29개 검사로 해석하지 않는다. 최종 전체355 PASS가 더 늦은 유효 실행 증거다. 모든 ordered cause의 named failure-before-fix 증거가 별도로 완성됐다고 판단하지 않는다.
- `whitespace-final2.meta.json`은 지정7 paths의 scoped trailing-whitespace scan, 01:25:16.356–01:25:16.452 KST, exit0을 기록한다. 다만 command는 설명 문자열이고 실제 PowerShell 식이 없으며 참조한 `whitespace-final2.stdout.txt`가 존재하지 않는다. stderr는 비어 있다. 정확한 명령·완전한 raw transcript 요건은 아직 충족되지 않았다.
- SHA256을 직접 계산해 manifest와 일치함을 확인했다: diff `6EA2DEF5328674B051219A7FC3D79E4812B264903C7208E8739135A2A32D65D6`, frozen JS `1D711EBA8BA8D3A01AE3D8F33273785056339CA4FFBBD17FD2BAE2BC60348E63`, generated baseline `812AB73C0FB80E449862826585F6FCD4C60299BD93E2A3CD0435BB6A8B6ECE28`, unchanged helper `4E4EFDF6B4F0B75D4832495A3F1BC13EE5AFD9C6F30A1160B831C392C5DD7E1D`.
- offline-contract-only own-API fixture 검증이다. live ERP/Google 성공으로 해석하지 않는다. 같은 SHA CI와 실제 Chrome R5 관찰은 이 검토 시점에 pending이다. setup 누락을 추정하지 않았다.
- 검토자는 tests/build/Git/browser/server/CI/provider를 실행하지 않았고 소스·테스트·설정·helper를 수정하지 않았다. 지정 operational review만 저장했다. 중간 운영 기록이므로 별도 Notion 페이지를 만들지 않는다.
- 부모 disposition은 `changes-requested`다. 실행된 최종 suite의 성공은 인정하지만 P1과 명시적 필수 회귀검사 누락 때문에 F1 acceptance/F2 진입을 승인하지 않는다. 다음 조치는 부모가 정하며 attempt6·release·merge 권한을 부여하지 않는다.

## 추가 확인: 같은 P1의 실제 Chrome 재현

- 부모가 frozen R5 / Chrome page20 / port4230 / MANAGER / scenario=none에서 수행한 원본5개와 C worktree의 `tmp/pm-implementation-2026-10-03/browser/f1-candidate-r5/server-4230.stdout.log`를 직접 읽었다. 소스와 앞서 확인한 diff·JS SHA는 바뀌지 않았다. **FAIL, 기존 OPEN 키2개와 P1/P2 건수를 그대로 유지한다.** 새 결함 키를 만들지 않는다.
- 원본 위치는 original D의 `tmp/pm-implementation-2026-10-03/browser/f1-exception5-observations/`이다. `r5-ascii-manager-after-fill-geometry.json`은 2026-10-03T16:36:02.782Z,1280×900,DPR1에서 제목 전체 `R4 durable draft a12345` 보존을 확인한다. 따라서 INPUT-LOSS의 기존 소스 종결에 이번 affected Chrome 관찰이 추가된다. 이 관찰을 다른 입력 방식 전체에 대한 검증으로 확대하지 않는다.
- `r5-prior-task-state-mutation.json`에는 기존 TASK의 상태가 진행 중으로 바뀌고 저장 완료 문구가 표시된다. server 로그의 PATCH200 본문도 `state=IN_PROGRESS`이다. 이후 `r5-create-after-prior-task-mutation.json`의 TASK 생성 클릭 결과는 RootWebArea만 남는다. 같은 server 로그는 POST200, UUID `833f440f-3296-4615-877b-c6b5b34a37d2`, resource `task-created-1`, 전체 ASCII 제목을 기록한다.
- `r5-mixed-mutation-console-errors.json`은 `Uncaught TypeError: Cannot read properties of undefined (reading 'requestId')`와 `index-DIiefoBd.js:10:12820` stack을 기록한다. `r5-mixed-mutation-blank-dom.json`은 2026-10-03T16:37:41.255Z에 동일 URL·R5 assets, 빈 body text, `mainPresent=false`를 확인한다. 이는 위 P1 소스 반례를 실제로 확인한 **실행 FAIL**이다. synthetic API가 생성을 수락한 뒤 화면이 사라져 사용자가 결과를 확인할 수 없었다.
- 이 추가 증거는 offline own-API fixture를 사용한 실제 브라우저 결과이며 live ERP/Google 통합 결과가 아니다. 다른 pending 브라우저 항목·같은 SHA CI·P2 검사 누락은 변경하지 않는다. 검토자는 원본 읽기와 이 보고서 추가만 수행했으며 브라우저·검사·보수·provider 작업을 실행하지 않았다.
