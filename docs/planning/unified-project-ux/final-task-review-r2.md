# 통합 프로젝트 UX 독립 재검토 r2

- 판정: **FAIL / CHANGES_REQUESTED**. 작업 `consolidate-pr-17-18-22`; 검토일 2026-09-30.
- 정확한 검토 SHA: `5d725fa1393f1c76111c1f0a712c7da1e30b670e`; 이전 FAIL SHA `29eeb024e3db0ba789611e3bf646c74616769fbf`; 기준 main `8991a0947d41580454da5fbcf23dcd247c852b45`.
- 실제 검토자 `/root/unified_final_review`, native `ai-erp-reviewer` Astra/high. 동일 product/UI/visual r1 및 부모 correction contract r2를 ACK했다. usage unavailable/null, bounded context, depth1, 재위임 없음. 소스/테스트/config/browser/Git 변경 없이 이 파일만 작성했다. 이전 `final-task-review.md`는 보존했다.
- `parent-self-review.md`의 attempt4는 부모가 기존 사용자 권한을 적용한 명시적 한정 예외다. 새로 받은 인간의 숫자별 승인이라고 표현하지 않으며, Luna→Terra→Sol 세 시도와 Sol ceiling 반환 이력을 지우지 않는다. 본 검토는 추가 실행 시도나 release/merge/deployment를 승인하지 않는다.
- 부모 root는 이번 잔존3 stable IDs와 CHANGES_REQUESTED를 수락하고 CI/browser를 기다리지 않고 검토를 종결하도록 회신했다. 이전7개 중 source가 닫힌4개와 남은3개를 아래에 구분한다.

## 남은 안정 결함: 3개 (모두 P1, 신규 ID 없음)

아래 위치는 `frontend/src/` 기준이다. 계약의 기존 Calendar/시간 편집 보존 및 정확한 recovery 요구를 기준으로 판정했으며, 이 시점의 통과한 테스트 개수를 부정하지 않는다.

| ID | 재현과 근거 / 남은 수정 |
| --- | --- |
| `SV-FE-006` | `screens/ScheduleWorkspace.tsx:183–194`의 recovery는 항상 `viewWindow(calendarDate, calendarView, "Asia/Seoul")`를 검증한다. 실제 Calendar는 `Schedules.tsx:320–354`에서 사용자가 선택한 zone과 `after/before`를 반영한 `effectiveRange`를 조회한다. 예: New_York 또는 명시적 날짜 범위를 선택하고 후속 페이지에서403을 받으면, recovery는 원래 범위와 다른 기본 서울 월/주 범위를 조회해 성공 시 잠금을 푼다. `ScheduleWorkspace.tsx:119`의 blocked return은 Calendar를 unmount하여 원래 zone/date-filter local state도 없앤다. size100/전체 traversal을 공유하도록 바뀐 점은 맞지만 **원래 target과 동일한 range를 확인하지 않는다**. 정확한 active config/range/zone을 보존·전달하고 그 대상의 완전한 재읽기까지 잠가야 한다. 현재403/404 테스트는 기본 서울 범위만 사용하고 요청 from/to를 assertion하지 않는다. |
| `UPUX-FE-003` | List 시간 편집 `ScheduleWorkspace.tsx:137–163`은 여전히 기존 main 흐름을 완전히 보존하지 않는다. `147`은 변경하지 않은 시간도 `utcToLocalDateTime`→`localDateTimeToUtc`로 다시 만들어 초/밀리초를 없앤다. 종료 `2090-09-10T02:00:37.250Z`인 일정에서 시작만 바꾸어 저장하면 종료도 `02:00:00.000Z`로 PATCH된다. read-only Node 변환 재현 exit0 확인. 기존 `Schedules.tsx:459–464`는 변경하지 않은 입력에 originalInstant를 유지한다. 또한 List의 network/500 경로 `158`은 오류 문구만 설정하고 쓰기를 다시 허용한다. 기존 `Schedules.tsx:397–445`는 저장 결과가 불확실하면 최신 detail/role을 읽고 실패 시 잠금과 명시적 recovery를 유지한다. List는 저장 성공 후 응답 유실 상황을 확인할 recovery 동작이 없다. 409 최신 버전 재시도·dirty Cancel·return context 수정은 인정하되, untouched instant와 uncertain outcome 회복까지 동일한 계약을 충족해야 한다. |
| `SV-FE-013` | r2의 13개 추가 테스트와 기존 테스트 보강은 의미가 있으나 부모 r2 필수 행렬 전체를 검증하지 않는다. `frontend-correction-r2.test.tsx:155–176`의 Calendar recovery는 기본 zone/range이고 성공 시 한 페이지만 반환하여 실제 활성 from/to·size100·다중 페이지 성공·중간 잠금 유지 assertion이 없다. `118–152`의 dashboard/property tests는 최초 denial만 검증하며 실패 recovery→성공 recovery까지 없다. custom-only Form은 route decline(`101–116`), Detail은 Cancel decline/accept(`85–99`)만 있어 요청된 Cancel/back/route 행렬이 완성되지 않는다. nonzero page에서 filter-save→URLpage0 테스트가 없다. `schedule-workspace.test.tsx:220–260`은 dirty409→Cancel의 다음 쓰기 version5는 확인하지만 clean-refetch 직후 next-write version4와 명시적 reload 이후 쓰기를 검증하지 않는다. List 테스트는 dirty Cancel decline과409 성공만 있어 위 untouched precision/network recovery 결함을 놓친다. 정확한 source paths에 대응하는 회귀와 r2 행렬의 나머지 증거를 보완해야 한다. |

## 이전 결함 폐쇄와 보존 확인

| ID / 영역 | r2 증거와 판단 |
| --- | --- |
| `UPUX-FE-005` | source CLOSED. 숨기던 `.schedule-workspace .schedule-listing .schedule-view-toggle{display:none}`가 제거됐다. 기존 subordinate 월/주 JSX와 단일 Calendar/Cards/List peer selector를 보존한다. 최종 실제 가시성/키보드는 부모 browser evidence 대상이다. |
| `SV-FE-003` | CLOSED. `ScheduleWorkspace.tsx:59,77` builtin은 선택한 body scope를 유지하고 MEMBER shared copy만 PERSONAL로 변환한다. r2 test10–21 MANAGER builtin SHARED와 retained MEMBER POST/MANAGER PATCH 검사가 연결된다. |
| `SV-FE-010` | 이전 구체적 source defect CLOSED. Detail127이 `confirmValueDiscard()` 후에만 취소한다. r2 test85–99 decline draft 유지/accept 서버값 복원, Form custom-only route decline, 기존 옵션 rename/add/order Continue/Discard(`schedule-workspace.test.tsx:92–116`)가 추가됐다. 전체 행렬 미완료는 `SV-FE-013`에 집계한다. |
| `UPUX-FE-004` | CLOSED for specified initial legacy URLs. normalizeLegacyHash가 month/week/list를 canonical builtin과 page0로 replace하며 다른 params를 보존한다. r2 parameterized test23–40이 date/text/status 유지와 false missing-view 경고 부재를 검증한다. |
| `SV-FE-001,009,011` | 소스 보존 확인. readWorkspaceCalendar는 exhausted/overlapping ten-page 결과를 partial로 유지한다. peer/onSaved는 page0을 URL에 함께 쓴다. NUMBER step any/finite 검증과1.25 저장 회귀가 남아 있다. filter-save의 비영 page 회귀 부재는013에 집계한다. |
| `SV-FE-007` | Cancel 후 최신 version 채택과 dirty draft 보존 source를 유지했고, next PATCH의 version5와 values를 추가 assertion했다. explicit reload control도 추가됐다. clean next-write 및 reload outcome 미검증은013에 집계한다. |
| `UPUX-FE-001,002`; 기존 closed `SV-FE-002,004,005,008,012` | inline date/today, mobile Agenda, builtin immutable identity, typed/stable-ID values와 option archive, 동일 schedule IDs, broad refreshSchedule 경로에서 이번 delta로 생긴 별도 결함은 발견하지 못했다. 이 판단은 모든 런타임 행렬을 재실행한 무회귀 보증이 아니다. |
| retained tests / plan6 / backend | round5가 date-change→range query→clear 복원과 상태/ACK assertions를 다시 검사한다. project-plan6 소스와 frozen backend/deploy guard에 r2 변경이 없다. |
| Dialog qualification | className은 optional이며 기존 Dialog lifecycle/focus 코드는 변경하지 않았다. settings760/properties800 및 viewport cap/min-width0이 accepted SV06 범위와 맞고 기본 popup560을 변경하지 않는다. 다만 CSS의 `@media(max-width:800px)` option-row 선언 뒤 기본 option-row 선언이 있어768px option row의 실제 reflow는 특히 부모 browser evidence로 확인해야 한다. 이 항목만으로 새 결함을 집계하지 않았다. |

## Rubric와 AC 연결

| Criterion | 판정 / 근거 |
| --- | --- |
| Parent contract / Assignment linkage | PASS. accepted r1 계획/UI/visual/READY contract와 parent-self-review의 구체적 correctionr2, 실제 exact-SHA 배정·ACK가 연결된다. |
| Role boundaries / Model invocation | PASS with recorded exception. 실제 same Sol/medium attempt4 결과, 범위·usage null·source freeze와 부모 exception 기록을 확인했다. reviewer는 read-only Astra/high이고 executor와 분리됐다. 한도 예외를 일반 정책 변경이나 reviewer 승인으로 취급하지 않는다. |
| Scope safety | PASS. r2 source changes는 frontend 및 synthetic preview fixture; backend/deployment/harness/dependency 변경 없음. 부모의 settings reflow qualification도 이미 승인된 SV06 안에 있다. |
| Method consistency | FAIL. exact active Calendar target 회복과 기존 main 시간 편집의 uncertain outcome/unchanged precision 보존이 부분 구현에 그친다. |
| Verification | FAIL overall. local focused44/full317 및 final Dialog affected12/typecheck/build의 실제 로그는 PASS. 그러나 필수 회귀013 미완료, 동일 SHA CI/최종 browser matrix는 검토 중 부모 수집 대상이다. |
| UI gate compliance | PASS. 기존 UI/visual r1 specialist→독립 UI review→부모 adoption 이력 유효; r2는 범위 변경이 아니다. |
| Visual UI evidence | PENDING. 소스의 월/주 숨김 제거와 accepted Dialog 폭 수정은 확인했으나 exact-SHA 모든 viewport/zoom/contrast/keyboard 결과를 아직 받지 않았다. 미완료 evidence를 PASS로 처리하지 않는다. |
| Acceptance mapping | AC1 FAIL(List time precision/recovery), AC2 기존 popup source/session/focus 증거 재사용·최종 browser pending, AC3 same IDs/typed views/scope source PASS, AC4 frozen V10/backend PASS 재사용, AC5 backend isolation/auth PASS 재사용·frontend recovery FAIL, AC6 FAIL006, AC7 source 개선/013 회귀 미완료, AC8 source 수정·013 행렬 미완료, AC9 FAIL013, AC10 기존 implemented/deferred 문서 분리 PASS. |
| Readiness | CHANGES_REQUESTED. CI/browser가 추가로 통과해도 여기 기록한 source defects를 고치지 않으므로 이 SHA는 수락 불가. 부모가 다음 처리를 결정하며 본 문서는 release verdict가 아니다. |

## 실제 확인과 미실행

- `git rev-parse HEAD` 확인; `git diff --name-only 47dac12 HEAD -- backend scripts/lib-deploy.sh scripts/tests/deployment_contract.py` 출력 없음. 독립 승인된 backend `47dac12ff50bf3bd3e696963c40de7c1cc30ad1e` CI36523924817의219검사(159 unit+8 OpenAPI+실제 PostgreSQL52)와 V9보존/V10 증거를 재사용한다.
- `frontend-correction-r2-result.md`, `evidence/frontend-correction-r2-focused.log`44/44, `frontend-correction-r2-full.log`317/317, `frontend-correction-r2-dialog-focused.log`12/12, final Dialog typecheck/build85modules, synthetic member/403/409 fixture smoke 로그를 읽었다. 전체 suite와 final Dialog 범위의 증거 시점을 구분하며 결과 작성자의 전체 suite 재실행을 요구하지 않았다.
- read-only Node 명령 `node --experimental-strip-types --input-type=module -e`로 실제 time.ts의 두 변환 함수를 import하여 원본 `02:00:37.250Z`→display `11:00`→List 전송 `02:00:00.000Z`를 확인했다(exit0). 새 테스트/fixture/소스는 만들지 않았다.
- 제품 tests/typecheck/build/PostgreSQL 재실행, browser 조작, live provider/production/deployment/recovery/관찰, Git 변경, Notion 쓰기는 수행하지 않았다. 최종 같은 SHA CI와 부모 browser 증거는 수집 중이며 source FAIL을 변경할 근거가 아니다.
- canonical local source: `D:/onedrive/Documents/ChatGPT/AI ERP/tmp/unified-project-ux-resume/docs/planning/unified-project-ux/final-task-review-r2.md`. Notion은 부모 통합 읽기 문서에서 실제 reading-status 확인 후 처리하도록 인계한다.
