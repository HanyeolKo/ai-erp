# 통합 프로젝트 UX 독립 재검토 r3

- 판정: **FAIL / CHANGES_REQUESTED**. 작업 `consolidate-pr-17-18-22`, increment `unified-project-ux`, 검토일2026-09-30.
- 정확한 검토 SHA `bd32ea71aefd0f3d5ea748eabbeb850dee530a10`; 이전r2 `5d725fa1393f1c76111c1f0a712c7da1e30b670e`; main `8991a0947d41580454da5fbcf23dcd247c852b45`.
- 실제 `/root/unified_final_review`, native reviewer Astra/high, read-only. accepted product/UI/visualr1 및 implementationr1+correction-contract-r3 ACK. bounded reused context, usage null, depth1, 재위임 없음. 이 문서만 작성; r1/r2 FAIL 보존.
- 실제 attempt5/Sol-medium은 부모의 기록된 한정 예외이며 reviewer 승인이나 시도 초기화가 아니다. 부모는 아래 Calendar override 회귀를 수락했고 r3 PASS를 주장하지 않는다. 추가 구현/재시도/merge/release 승인은 부모 소유다.

## 남은 안정 결함: 3개 P1, 신규 ID 없음

소스 위치는 `frontend/src/` 기준이다. 이번 두 실행 경로 결함은 소스와 실제 호출 연결로 입증했으며 별도 browser/test 재현을 실행했다고 주장하지 않는다.

| ID | 재현, 실제 결과, 요구 수정 |
| --- | --- |
| `UPUX-FE-003` | Calendar에서409 또는 응답 유실 뒤 최신 schedule version4를 회복한다. `screens/Schedules.tsx:497` 및 명시적 recovery `448`이 `overrides[id]=latest`를 기록한다. 이후 다른 작성자가 version5/다른 시간으로 변경하고 workspace query가 새 값을 반환해도 `390`은 override4를 우선한다. override 제거 effect `410–418`은 `list.data`만 검사하지만 이 legacy list는 workspace mode에서 disabled(`378`)이고, 새 `refreshActiveTarget:422–427`은 workspace cache만 갱신한다. 따라서 표시·다음 수정의 rowVersion이4에 고정되어 다시409가 난다. `saved` 분기의 명시적 삭제(`486`)는 recovered 분기에 없다. 활성 workspace records의 최신 버전과 reconcile하여 stale override를 제거하고, recovery→외부 update→다음 edit의 실제 값/version을 검증해야 한다. |
| `SV-FE-006` | Calendar 시간 PATCH가500/409를 반환한 뒤 mutation recovery의 exact target 읽기만403/404로 거부되는 경우다. `Schedules.tsx:422–427`은 `readWorkspaceCalendar`를 직접 호출하므로 실패해도 React Query의 `workspaceList` 성공 상태/기존 cache가 그대로 남는다. common helper `schedule-time-mutation.ts:34–35`는 original write error와 `locked`만 반환하고 target denial을 전파하지 않는다. caller `Schedules.tsx:493–495`는 해당 일정만 잠그며 `onWorkspaceDenied`를 호출하지 않는다. global denial callback은 `activeQuery.isError`를 보는 `389`뿐이다. 따라서 기존 보호 rows와 전역 create/settings/properties 쓰기 권한이 계속 보인다. 명시적 recovery `445`도 같은 직접-read 경로다. target access denial을 전역 workspace lock과 보호 결과 숨김으로 전파하고, fresh role+metadata+exact target 전체 성공 전까지 유지해야 한다. |
| `SV-FE-013` | 위 두 상태 전이를 입증하는 회귀가 없다. r3 exact-target 테스트는 일반 background query 거부를 다루며 mutation recovery 내부의 직접 target 거부는 다루지 않는다. 시간 테스트는 List subminute/500 committed/uncommitted/role loss를 검증하나 Calendar recovered override→더 최신 workspace refetch를 검증하지 않는다. 요구된 버전·보호 결과·모든 열린 쓰기 잠금 assertions를 각각 추가해야 한다. 추가되지 않은 일반적 테스트 수를 결함으로 세지 않고, 확인된 두 실행 결함에 대응하는 coverage만 잔존으로 기록한다. |

## 확인된 개선과 기존 폐쇄 유지

- **실제 공통 함수 재사용 확인:** `schedule-time-mutation.ts`의 `preserveScheduleTimeInstants`와 `executeScheduleTimeMutation`을 Calendar와 List 양쪽이 import/call한다. List untouched seconds/milliseconds 보존, uncertain committed/uncommitted no-replay, fresh role loss 잠금은 새 r3 tests가 실제 요청/상태로 검증한다. r2의 분리된 근사 구현은 교체됐다. 남은 문제는 Calendar caller의 override 정리와 target denial 전파다.
- **Calendar target 보존 확인:** Schedules383–384의 실제 `WorkspaceCalendarTarget`에는 config/zone/anchor/mode/after/before/from/to/identity가 들어가고, workspace recovery212–225는 이 target으로 complete traversal/cache key를 공유한다. Records는 blocked 상태에도 Calendar controller를 유지하고 Schedules390이 rows만 숨긴다. r3 test13–51이 New_York+명시적 기간의 page0/page1·size100·from/to/config 및 실패/성공 recovery 후 컨트롤/URL을 검증한다.
- **회귀 개선 확인:** r3 tests는 filter SAVE page2→URLpage0, properties/dashboard 실패 recovery→성공 recovery의 쓰기 잠금/재활성화, List subminute/uncertain outcomes/role loss를 추가했다. retained dashboard test는 clean refetch version4 및 dirty409→Cancel next-write version5를 요청 body로 확인한다. restored round5 date-filter/clear/status/ACK assertions가 유지된다.
- **이전 폐쇄 유지:** `SV-FE-001,003,009,010,011`과 `UPUX-FE-001,002,004,005`의 previously reviewed fixes에 이번 delta로 생긴 별도 source regression을 발견하지 못했다. immutable builtin, stable property/option IDs, custom-only isolation의 backend 계약, project-plan6 코드는 유지된다. 전 범위 런타임 무회귀 보증으로 확대하지 않는다.
- **qualification 수정 확인:** 새 속성 이름/유형의 persistent visible `<label>`이 추가됐다. 800px option-row override가 기본 선언 뒤로 추가되어 이전 cascade 우려를 해소한다. 기본 popup560과 settings760/properties800 범위를 유지한다. ScheduleForm의 invalid focus는 `useLayoutEffect`로 commit 직후 처리하며 원래 focus assertion을 삭제하지 않았다. exact-SHA CI/browser 최종 확인은 별도다.

## Rubric / AC mapping

| Criterion | 판정 / 근거 |
| --- | --- |
| Parent contract / Assignment / Model / role boundaries | PASS with recorded parent exception. r3 배정·ACK·accepted r1 scope·actual Sol/medium attempt5 결과와 독립 Astra/high를 연결했다. count reset, worker verdict, Astra execution 없음. |
| Scope safety | PASS. frontend 공통 helper/Calendar/List/Form/CSS/tests 변경은 계약 안이다. backend/deploy/harness/dependency 변경 없음. |
| Method consistency | 부분 PASS / overall FAIL. 실제 target/common helper 공유는 충족; caller의 최신 cache 정리와 access-denial 전파는 미완료. |
| Verification | FAIL overall. local focused44/full325/typecheck/build의 raw evidence는 유효하다. confirmed caller defects의 회귀가 없고 exact-SHA final CI/browser는 부모 수집 중이다. |
| UI gate / visual | specialist r1 gate PASS 유지. source label/focus/reflow 개선 확인. 최종 same-SHA viewport/zoom/keyboard/contrast/browser 증거는 pending이며 이전 immutable source 측정을 r3 전체 PASS로 대체하지 않는다. |
| Acceptance | AC1 FAIL(Calendar 최신값/복구), AC2 popup 기존 증거 유지·final browser pending, AC3 same IDs/typed/immutable scopes source 유지, AC4 frozen V10/backend PASS 재사용, AC5 backend isolation/auth PASS 재사용·frontend denial FAIL, AC6 FAIL006, AC7 Dashboard 회귀 개선·Calendar stale override FAIL003, AC8 page/filter/dirty/decimal source 및 targeted tests 개선, AC9 FAIL013, AC10 deferred PR22 문서 구분 유지. |
| Readiness | CHANGES_REQUESTED. 수집 중 CI/browser가 통과해도 이 소스 결함을 해소하지 않는다. 부모가 다음 배정을 결정하며 이 문서는 release verdict가 아니다. |

## 실제 증거와 미실행

- `git rev-parse HEAD`와 r2→r3 diff 확인. `git diff --name-only 47dac12 HEAD -- backend scripts/lib-deploy.sh scripts/tests/deployment_contract.py` 출력 없음. frozen backend47dac12의 independent PASS/CI36523924817,219개(159unit+8OpenAPI+실제PostgreSQL52) 증거 재사용.
- `frontend-correction-r3-result.md`와 raw `evidence/frontend-correction-r3-full.log`325/325, `frontend-correction-r3-focused.log`44/44를 읽었다. raw full log의 jsdom navigation/TimeoutOverflow warnings는 실패가 아니다. 소스와 test assertions를 직접 대조했으며 총개수만으로 closure를 판단하지 않았다.
- duplicate product tests/typecheck/build/PG, browser 조작, production/provider/deploy/recovery, Git mutation, Notion write를 실행하지 않았다. exact-SHA CI와 browser는 부모 담당이며 현 source FAIL 때문에 PASS를 발급하지 않는다.
- canonical source: `D:/onedrive/Documents/ChatGPT/AI ERP/tmp/unified-project-ux-resume/docs/planning/unified-project-ux/final-task-review-r3.md`. 부모가 통합 reader document에서 실제 reading-status를 확인한 뒤 Notion archive를 처리하도록 인계한다.
