# 통합 프로젝트 UX 최종 독립 검토

- 판정: **FAIL / CHANGES_REQUESTED**. 작업 `consolidate-pr-17-18-22`, increment `unified-project-ux`, assignment/implementation/UI/visual r1. 검토일 2026-09-30.
- 정확한 검토 SHA: `29eeb024e3db0ba789611e3bf646c74616769fbf`; 기준 main `8991a0947d41580454da5fbcf23dcd247c852b45`; draft PR #23.
- 검토자: 실제 `/root/unified_final_review`, native `ai-erp-reviewer`, Astra/high. ACK 후 read-only 소스·diff·검사 증거 검토. 사용량 unavailable/null, minimal context, depth 1, 재위임 없음. 이 파일만 작성했다.
- 부모가 2026-09-30 월/주 숨김을 실제 브라우저에서 재현했고, 이 SHA를 안전하게 병합할 수 없다는 판정을 수락했다. 대기 중 CI/브라우저 결과가 아래 소스 결함을 해소하지 않는다. 최종 acceptance/merge/deployment는 부모 소유다.

## 안정 결함: 7개 (P1 6개, P2 1개)

위치는 `frontend/src/` 기준이며, 별도 표시가 없으면 재현 순서와 코드 경로로 확인한 소스 증거다. 테스트를 새로 작성하거나 실행해 확인했다고 주장하지 않는다.

| ID / 심각도 | 재현, 실제 결과, 필요한 수정 |
| --- | --- |
| `UPUX-FE-005` / P1 / 신규 | `screens/schedule-workspace.css:4`가 `.schedule-workspace .schedule-listing .schedule-view-toggle`을 `display:none`으로 숨긴다. Calendar를 열면 `Schedules.tsx:478`의 월간/주간 버튼 두 개 모두 시각·키보드·접근성 트리에서 사라진다. 다른 하위 전환은 없다. 부모 브라우저 재현과 일치한다. 하나의 peer selector를 유지하면서 필수 월/주 하위 전환을 복구해야 한다. jsdom의 버튼 클릭 통과는 실제 CSS 가시성 증거가 아니다. |
| `SV-FE-003` / P1 / 부분 수정 후 잔존 | `screens/ScheduleWorkspace.tsx:40,61`에서 MANAGER가 builtin 보기 설정의 공유 범위를 SHARED로 선택해 저장해도, mutation은 builtin 또는 MEMBER 공유 복사 분기 모두를 `scope:PERSONAL`로 덮어쓴다. 공유 설정이 개인 보기로 조용히 저장된다. MEMBER 공유 복사는 PERSONAL이어야 하나 MANAGER builtin 생성의 명시적 SHARED 선택은 유지해야 한다. `schedule-workspace.test.tsx:151`은 기존 공유 보기의 복사/PATCH만 검증한다. |
| `SV-FE-006` / P1 / 부분 수정 후 잔존 | 보기 설정을 연 상태에서 결과 재조회가 403을 반환하면 `ScheduleWorkspace.tsx:105,170`은 `resultsDenied`만 설정한다. `171`의 열린 ViewSettings는 유지되며 `40,59,68`의 Save/초안 복제는 `isPending`만으로 잠겨 추가 쓰기를 보낼 수 있다. 또한 Calendar 거부 후 `150–157`의 recovery는 날짜 범위 없는 size20 단일 쿼리만 확인하고 잠금을 푼다. 실제 target은 `Schedules.tsx:338–355`의 ranged size100 다중 페이지 쿼리다. 첫 페이지 성공·후속 403 상태에서 generic recovery 성공은 Calendar 재읽기 완료 전에 쓰기 버튼을 잠시 되살릴 수 있다. 모든 열린 mutation 경로를 잠그고 실제 대상 읽기 완료까지 유지해야 한다. |
| `UPUX-FE-003` / P1 / 부분 수정 후 잔존 | `ScheduleWorkspace.tsx:124–132`는 기존 Calendar 시간 복구를 공유하지 않는 독립 편집기다. 다른 작성자 수정 후 Save→409는 메시지만 설정하고 최신 detail/role/version을 읽지 않는다. 다시 Save하면 같은 버전으로 반복 409; Cancel/Escape는 변경 확인 없이 닫고, 재열기 때 최초 state의 시간 초안이 남는다. `Schedules.tsx:390–441`의 기존 복구·dirty guard와 다르다. 또 반환 context를 담은 edit 링크(`119`) 뒤 `ScheduleForm.tsx:176,261`의 성공/Cancel 이동은 `return`을 버린다. 목록에서도 기존 시간 편집의 복구·취소·최신 버전·반환 문맥을 보존해야 한다. |
| `SV-FE-010` / P1 / 부분 수정 후 잔존 | 사용자 속성만 변경한 상세에서 Cancel을 누르면 `screens/Detail.tsx:127`이 `markValueSaved`, draft reset, dirty reset을 즉시 수행한다. `99`의 `confirmValueDiscard`는 이 경로에 호출되지 않아 계속 편집/명시적 폐기 선택 없이 초안이 사라진다. 목록 이동 guard와 Form의 valuesDirty 추가는 개선됐으나 계약의 custom-only Cancel 보호는 미완료다. |
| `UPUX-FE-004` / P2 / 부분 수정 후 잔존 | `ScheduleWorkspace.tsx:18,148–149`: `?view=week`는 내부적으로 Calendar/week로 읽지만 URL을 canonical `view=builtin-calendar&mode=week`로 한 번 정규화하지 않는다. `view=list`도 동일하다. 유효한 legacy `view=month`는 mapping에서 누락되어 삭제된 보기 경고와 fallback으로 처리된다. UI r1의 legacy 정규화·date/filter 보존 계약에 맞는 한 번의 원자적 정규화와 회귀 증거가 필요하다. |
| `SV-FE-013` / P1 / 잔존 | 전체 304 PASS는 인정하지만 필수 행렬을 닫지 못한다. `schedule-workspace.test.tsx:137–242`에는 후속 페이지 401/403/404→실패 recovery 및 Calendar/settings/properties/dashboard 쓰기 잠금 행렬이 없다. `92–107`은 추가한 옵션의 레이블만 확인하고 기존 옵션 rename/order를 검증하지 않는다. `210–242`는 clean/dirty/409/Cancel 표시값을 검증하나 다음 저장의 rowVersion·409 탈출을 검증하지 않는다. custom-only Form/Detail Cancel/back/route 테스트도 없다. `round5.test.tsx:33–41`은 기존 date-filter 변경→page reset→filter clear 복원 검사를 peer switch로 대체했고 `48–56`은 기존 상태/ACK 표시 assertions를 제거했다. 유지된 행동 assertions를 복구하고 정확한 결함 회귀를 추가해야 한다. |

## 요구 결함별 폐쇄 상태

| 항목 | 증거 및 현재 판단 |
| --- | --- |
| `SV-FE-001` | 소스/집중 회귀 PASS. `Schedules.tsx:338–354` 모든 exhausted traversal을 partial 처리; overlapping 10페이지 테스트 `schedule-workspace.test.tsx:137–148`. |
| `SV-FE-003` | FAIL. MEMBER copy와 MANAGER 기존 SHARED PATCH는 테스트됐으나 MANAGER builtin SHARED 생성이 위와 같이 회귀했다. |
| `SV-FE-006` | FAIL. 후속 access error를 throw하는 `Schedules.tsx:347–348`과 List failed-recovery test 개선은 인정하나 열린 settings/Calendar recovery가 미완료다. |
| `SV-FE-007` | 소스 상태 전이는 개선됨: `ScheduleWorkspace.tsx:181–185` clean draft+version 동기화, dirty 보존, Cancel의 최신 version 채택. 검사 `210–242`는 표시값까지만 입증; 다음 저장의 최신 rowVersion/반복409 방지는 미검증(`SV-FE-013`). 완전 폐쇄 승인 보류. |
| `SV-FE-009` | source PASS: `145,168` onSaved/peer switch가 같은 URL 갱신에서 page0 기록. peer switch 회귀 `round5.test.tsx:33–41` 있음; nonzero page의 filter-save 회귀는 없음(`SV-FE-013`). |
| `SV-FE-010` | FAIL. PropertiesDialog의 자식 유지와 Form valuesDirty/Detail route guard는 개선됐으나 상세 Cancel과 필수 회귀가 미완료다. |
| `SV-FE-011` | 소스/집중 회귀 PASS. `ScheduleWorkspace.tsx:55,59` step any/finite validation; 테스트 `173–189` 1.25 전송 확인. |
| `SV-FE-013` | FAIL. 위 coverage 및 retained assertion 결함. |
| `UPUX-FE-001,002` | 소스 PASS. stale date-popup 참조 없음, 인라인 날짜/오늘 유지; `Schedules.tsx:488` Agenda 복구, 기존 mobile CSS와 연결. 최종 전체 responsive/browser matrix는 미완료. |
| `UPUX-FE-003,004` | FAIL. 위 잔존 경로. |
| 기존 `SV-FE-002,004,005,008,012` | immutable builtin, typed values/option IDs, broad refreshSchedule invalidation, 같은 schedule ID 및 calendar metadata 경로에서 별도의 신규 source defect는 확인하지 못함. 다만 신규 List 시간 흐름은 broad refresh 대신 query만 invalidate하므로 `UPUX-FE-003`의 보존 범위에 포함한다. 전체 런타임 재검증/무회귀 보증으로 확대하지 않는다. |

## Rubric와 AC 연결

| Criterion | 상태 / 증거 |
| --- | --- |
| Parent contract / Assignment linkage | PASS. assignment, accepted increment r1, plan-review, UI/visual r1, ui-review, READY implementation-contract가 범위·소유권·AC1–10·반환 조건과 일치한다. |
| Role boundaries / Model invocation | PASS. 실제 Luna/high→Terra/medium→Sol/medium invocation/ACK/return을 `MODEL-ESCALATION.md`가 기록하고 checkpoint FAIL을 보존한다. 부모 배정, bounded context/output, usage null, 최종 독립 Astra/high 분리. executor의 ready-for-review는 verdict가 아니다. 추가 attempt 승인은 본 검토가 부여하지 않는다. |
| Scope safety / external readiness | PASS for reviewed scope. backend/deploy guard와 frontend/preview 및 계획 문서 변경이 계약 안에 있다. 이번 검토는 로컬 소스·기존 실제 CI와 synthetic evidence로 제한; live provider/deployment 성공을 주장하지 않는다. |
| Method consistency | FAIL. 기존 List 시간 편집/복구 재사용과 회귀 assertions 보존 계약이 충족되지 않았다. |
| Verification | FAIL overall. raw local logs의 focused76/76/full304/304/typecheck/build PASS는 유효한 bounded evidence. 하지만 요구된 회귀·최종 browser matrix가 불완전하며 final CI는 검토 시 대기 중이다. |
| UI gate compliance | PASS. 기능 specialist→visual specialist→독립 UI PASS→부모 adoption/READY 순서가 기록됐다. |
| Visual UI evidence | FAIL. `UNIFIED-PROJECT-UX-01/r1`의 월/주 하위 전환 보존 규칙 위반이 부모 browser/source로 확인됐다. 모든 폭·200%zoom·contrast·keyboard 완료 증거는 아직 없다. |
| Acceptance mapping | FAIL. AC1: plan6/backend 보존 PASS지만 월/주·List time recovery FAIL. AC2: popup source/context/focus/session 및 canonical label 개선 인정, 전체 browser matrix 미완료. AC3: 같은 IDs/typed properties 유지, manager shared creation FAIL. AC4: unchanged backend V10 PASS. AC5: backend auth/atomic/custom-only isolation PASS 재사용, frontend recovery FAIL. AC6 FAIL(`SV-FE-006`), AC7 부분 검증(`SV-FE-003,007,013`), AC8 FAIL(`SV-FE-010`, legacy context), AC9 FAIL(`SV-FE-013`), AC10 PASS(PR22 네 문서와 reader-summary의 deferred 구분). |
| Readiness | FAIL for acceptance; result의 ready-for-review 상태 자체는 적절하다. 이 SHA는 CHANGES_REQUESTED로 부모에게 반환한다. |

## 증거 재사용과 미실행 검사

- `git rev-parse HEAD`로 SHA 확인. `git diff 47dac12..HEAD -- backend scripts/lib-deploy.sh scripts/tests/deployment_contract.py` 출력 없음: frozen backend가 바뀌지 않았다. `backend-review.md`의 독립 PASS와 `backend-ci-evidence.md`의 exact `47dac12ff50bf3bd3e696963c40de7c1cc30ad1e` CI36523924817(159 unit + 8 OpenAPI + 52 실제 PostgreSQL = 219, 실패/skip0)을 재사용한다. V1–V9/project-plan 보존과 V10/배포 guard 검증을 반복하지 않았다.
- `frontend-result.md`와 raw `evidence/frontend-final-focused.log`, `frontend-final-full.log`, `frontend-final-typecheck.log`, `frontend-final-build.log`를 읽었다. focused76/full304, tsc 성공, Vite85modules 성공과 원시 경고가 기록과 일치한다. 이 검토자는 해당 검사들을 재실행하지 않았다. `git status --short`는 검토 문서 작성 전 깨끗했다.
- 최종 CI36670313698은 부모가 모니터링하며 검토 시 pending. 부모의 월/주 숨김 browser 재현은 수신했으나 나머지 최종 폭/zoom/contrast/keyboard/권한 matrix, live Google, production data, deployment/recovery/observation은 미실행·미완료다. 이것은 release verdict가 아니다.
- 완료 직전 부모의 exact-SHA `evidence/browser-29eeb02.json`도 읽었다. 1440px week canvas1152px/이벤트 배치, 320px popup284px·close44px·overflow0, 관련 일정 이동 시 Dialog 종료, Escape 초점/해시 보존은 관찰됐다. 같은 증거가 월/주 컨트롤 부재를 확인하며 나머지 행렬/200% native zoom 미완료를 명시한다. 판정은 변경하지 않는다.
- 실패 증거와 stable IDs를 유지한 채 부모에게 반환한다. Sol ceiling 이후 처리·새 배정·시도 예외 여부는 부모 self-review와 명시적 기록 대상이며 이 reviewer가 implementation/retry를 승인하지 않는다.
- 로컬 canonical source: `D:/onedrive/Documents/ChatGPT/AI ERP/tmp/unified-project-ux-resume/docs/planning/unified-project-ux/final-task-review.md`. Notion 동기화는 부모의 통합 읽기 문서 관리로 인계한다. reviewer는 external write를 수행하지 않았고 읽기 상태를 추측하지 않았다.
