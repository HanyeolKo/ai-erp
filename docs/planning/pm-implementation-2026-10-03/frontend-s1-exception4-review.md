# F1 attempt4 독립 소스·증거 검토

- Task: ERP-PM-IMPLEMENT-20261003-F1, authorized exception attempt4, contract r2. Native ai-erp-reviewer / gpt-6-astra / high. Usage: null.
- Worktree: C:/Users/USER/.codex/worktrees/project-management-redesign/AI ERP. Source 8d74efc37d34949053a974337128099ff8ce224b + 동결된 F1 미커밋 변경. Backend/B1은 범위 밖이다.
- **소스·증거 게이트 FAIL / changes-requested.** 기존 일곱 키 중 다섯 키는 OPEN이며 두 키는 소스에서 CLOSED다. 추가로 실제 입력 손실 P1 한 건을 확인했다. 실제 브라우저 관찰과 같은 SHA CI는 별도 pending이다. 미실행 관찰을 실행 실패로 집계하지 않았다.
- 계약 r2 전체, repair3 검토, 부모 self-review, 원래 F1 계약, 승인된 UI r3 / visual r1 / mapping r2, task-review 정책, 현재 소스·검사를 대조했다. Native Luna/high 배정과 UI 승인 연결은 exception-repair4-dispatch-record.md 및 부모 배정에서 확인했다.

## Stable keys

| Key | Source disposition | 근거 |
|---|---|---|
| F1-QUICK-CREATE-CONTINUITY | OPEN, P1 | ProjectPlan.tsx:211-215의 reactive query/gcTime Infinity는 5분 GC 원인을 제거한다. 공유 phase/submitted, 세션·requestId callback guard, dirty hook도 추가됐다. 그러나 :243은 동일 session/project mutationKey의 첫 mutation을 찾고 :234는 이를 영구 보관한다. 첫 409 또는 unknown500 후 동일 ID 재시도가 :236에서 sending이 되면, mutation cache 알림에서 :245,:251-255가 보관된 첫 error를 읽어 editable/uncertain으로 되돌린다. 현재 요청이 pending인데 입력 또는 재시도가 풀리는 반례다. 설치된 query-core mutationCache.find는 getAll().find이므로 최신 작업 선택이 아니다. 409 후 제목을 바꾸어도 :227,:261,:263은 requestId를 갱신하지 않아 바뀐 payload와 기존 ID를 보낸다. |
| F1-DEFAULT-TARGETS | CLOSED at source; runtime pending | project-plan.css:204는 6px 수직 padding, :206-213은 44px control, :216은 overview 제목 링크 44px를 지정한다. 단일 줄은 44+12+border로 계약 범위에 맞는 소스다. 실제 1280 행 높이와 viewport target은 부모 측정으로 확정해야 한다. |
| F1-OVERVIEW-REGION-INDEPENDENCE | OPEN, P2 | Schedules.tsx:55-65는 plan403을 latch하여 TASK rows/작성 링크를 숨기고 500 영역을 분리한다. 그러나 overview denial에는 project.refetch나 protected plan query 제거가 없다. :95의 QueryState 재시도는 동일 taskPlan.refetch만 수행한다. 기존 ProjectPlan deny의 프로젝트 재확인·보호 캐시 제거 요구를 충족하지 못한다. cached MANAGER+성공 plan 후 current403에서 캐시 제거와 프로젝트 권한 재확인을 검사해야 한다. 초기403에서 링크 하나가 없는 검사만으로 이 경계를 입증할 수 없다. |
| F1-TASK-EMPTY-STATE | OPEN, P2 | 실제 TASK+complete로 onboarding을 결정해 기존 TASK/일정0 모순은 해소됐다(:67-69). 반대로 TASK0/일정1 이상이면 isEmpty=true가 되고 :97-102가 예정 일정·처리 대기·일정 신호·ScheduleDashboardSection을 전부 숨긴다. 독립적인 기존 일정 영역을 보존하라는 F1 acceptance1/visual invariant에 어긋난다. helper task-empty는 schedules도 비워 이 반례를 검사하지 않는다. |
| F1-BLOCKED-FACTS | OPEN, P2; authoritative blocker 보존은 해소 | ProjectPlan.tsx:271-280은 blockerIds와 알려진 미완료 선행을 합치고 직접 BLOCKED 및 DONE/CANCELLED 집계 제외를 유지한다. 다만 complete=false, predecessorIds=[unobserved], blockerIds=[], 관측 node 없음이면 :280은 여전히 차단 없음을 표시한다. 미관측 선행의 상태가 불명확하다는 설명이 없으므로 계약5의 partial graph에서 알려진 차단 부재를 추정하지 말라는 조건이 남는다. 새 off-page 검사는 blockerIds가 있는 경우만 다룬다. |
| F1-PARTIAL-COVERAGE | CLOSED at source | ProjectPlan.tsx:275,278-279 및 Schedules.tsx:95는 zero/nonzero partial에 관측 범위를 표시한다. rows/IDs를 유지하고 전체 분모를 주장하지 않는다. 실패 영역은 empty로 처리하지 않는다. overview 실제 partial 관찰은 pending이다. |
| F1-REPAIR-REGRESSION-COVERAGE | OPEN, P2 | 신규 5개 검사는 행동·입력·결과를 사용하므로 소스 문구 복제 검사라고 판단하지 않는다. 그러나 아래 필수 시나리오와 최종 실행 원본이 빠졌다. 결과 문서의 통과 숫자로 독립 증거를 대신할 수 없다. |

## 기존 CLOSED 유지 및 검사 누락

- F1-COMPACT-FILTER-LABELS는 CSS:214-215에서 유지된다. F1-OVERVIEW-ACTION-AUTHORITY는 MANAGER/MEMBER 분기와 VIEWER 읽기 전용 동작을 유지한다.
- F1-DEFAULT-MODE-PRECEDENCE의 mode=default 우선/legacy selector 보존, F1-DEFAULT-INITIAL-LOADING의 초기 loading/retained refetch/error 분기도 유지된다. 기존 네 키를 재개방하지 않는다.
- pm-default-workspace.test.tsx:99-112는 같은 ProjectPlan의 advanced/default 전환으로 자식 폼 remount를 검사한다. 실제 다른 project route 왕복, 5분 이상 비활성 보존, 해결 후 새 ID 제출은 검사하지 않는다.
- 필수 누락: >5분+실제 route 복귀; TASK draft namespace의 session 종료/늦은 callback; 다음 생성의 새 ID; 409/current403 초안·dirty lifecycle; beforeunload; mode=default+view/types/scope; dashboard500/plan500 독립성; cached-success 뒤 plan403/recheck; true/container/filtered/partial zero와 overview nonempty partial; BLOCKED/DONE/CANCELLED와 미확인 off-page; initial pending/retained error/retry.
- session-boundary.test.tsx는 기존 schedule/invitation/group 검사이며 새 TASK draft namespace를 검증하지 않는다. regression VIEWER-empty 검사에 빈 plan fixture를 더한 수정은 기대 조건을 일치시키며 보안 assertion 약화라고 판단하지 않는다.

## 원본 증거와 한계

- 동결 manifest: tmp/pm-implementation-2026-10-03/browser/f1-candidate-r4/manifest.json. frontend diff hash A8733D85D263CE14266CEF058E7035DFF5747AE4A783F4A20800BCDA61DD94D0은 부모 증거를 사용했다. Git은 실행하지 않았다.
- 직접 확인한 SHA256은 manifest와 일치한다: frozen JS 994C415C15F60FF1749E619182E86420E0AA1BCB402F304E8FDDD81CFA11F515; 현재 helper 4E4EFDF6B4F0B75D4832495A3F1BC13EE5AFD9C6F30A1160B831C392C5DD7E1D; generated baseline 812AB73C0FB80E449862826585F6FCD4C60299BD93E2A3CD0435BB6A8B6ECE28.
- 지정 F1 디렉터리와 명명된 task evidence 범위에서 attempt4 원본은 exception-repair4/full-test.meta.txt 및 full-test.stdout.txt만 확인됐다. START=10/03/2026 23:12:32, END=23:13:12, EXIT=1, cwd=C worktree이다. stdout은 30 files 중 2 failed/28 passed, 346 tests 중 2 failed/344 passed이며 project-recovery missing project 및 regression VIEWER-empty 실패다.
- 현재 소스에는 해당 fixture 후속 수정이 보인다. 초기 실패를 현재 소스의 최종 실행 실패라고 단정하지 않는다. 다만 결과가 주장한 최종 67/67, 37/37, 346/346, tsc/build/helper/diff exit0의 정확한 원본·실행 metadata는 찾지 못했으므로 미검증이다. 과거 repair3의 341/109 성공을 attempt4에 전용하지 않았다. failure-before/fix-after 연결도 불충분하다.
- 부모가 보고한 실제1280 행 높이는 56.5/57px이고 기본 control은44px다. 소스의 밀도 수정과 일치한다. 전체 viewport/keyboard/Calendar 게이트는 별도 pending이다. >5분 absence의 최초 기록은 확인 dialog에서 멈춘 동작이므로 증거로 인정하지 않는다. 부모가 정정한 실제 project-overview absence 시작은14:42:43Z이며 완료 관찰은 이 검토 시점에 pending이다.
- tests/build/Git/browser/CI/server/provider를 실행하지 않았다. 소스·테스트·설정을 수정하지 않았고 지정 operational review만 작성했다. Live ERP/Google·배포 증거는 없다. offline-contract-only fixture는 live integration 성공이 아니다.
- 부모에게 changes-requested를 반환한다. attempt5, 추가 보수, 모델 변경, 병합·출시·최종 수락은 승인하지 않는다. 중간 운영 기록이므로 별도 Notion 읽기 페이지를 만들지 않았다.

## 추가 실제 반례: F1-QUICK-CREATE-INPUT-LOSS, OPEN P1

- 부모가 같은 Chrome fill로 ASCII 문자열 R4 durable draft a12345를 입력했다. D:/onedrive/Documents/ChatGPT/AI ERP/tmp/pm-implementation-2026-10-03/browser/f1-exception4-observations/r4-ascii-input-result.json을 직접 읽었으며 2026-10-03T14:43:28.693Z, port4210 기본 TASK 폼 값은 d 5다. 동일 디렉터리 r3-ascii-baseline-input-result.json은14:43:50.324Z, port4190에서 정확한 전체 문자열을 보존한다. 입력 행위와 동일 fill 설정은 부모 관찰을 사용하며 결과 원본은 독립 확인했다.
- R4 controlled input의 value={title}는 ProjectPlan.tsx:217,224-225의 useQuery.data에서만 온다. :263의 onChange는 :227의 qc.setQueryData만 호출하며 동기적인 React 입력 state를 갱신하지 않는다. 설치된 @tanstack/react-query5.90.20 useBaseQuery.ts:107은 observer 알림을 notifyManager.batchCalls로 감싼다. query-core notifyManager.ts:16,26-33,68-75는 systemSetTimeoutZero로 알림을 지연한다. 즉 React controlled input의 event 종료 시점에 새 value prop이 아직 전달되지 않아 이전 값으로 복구될 수 있는 소스 구조다. frontend/src에 scheduler override는 없다.
- 실제 ASCII 차등 결과와 이 알림 경로가 일치한다. 이전 한글 unknown 입력 불일치를 IME 문제로 단정하지 않는다. 사용자 초안이 저장 전 손실되는 새 P1이며, 기존 request phase/operation 결함과 원인이 달라 별도 key로 기록한다. 현재 userEvent.type 성공만으로 이 실제 입력 경계가 검증되지 않는다.
- 추가 테스트나 코드 수정은 하지 않았다. 필수 게이트 FAIL은 유지하고 이 반례를 부모에게 반환한다.
