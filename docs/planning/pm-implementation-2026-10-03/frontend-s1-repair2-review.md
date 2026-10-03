# F1 repair2 독립 검토 기록

- 검토 대상은 ERP-PM-IMPLEMENT-20261003-F1 repair2이며, 검토자는 native ai-erp-reviewer / Astra / high다. 부모가 지정한 worktree는 C:/Users/USER/.codex/worktrees/project-management-redesign/AI ERP이고 base는 b6603269800352073e68c6e76fe4bedf516c5ba6이다.
- frontend-s1-contract.md r1과 부모의 URL 해석 보충, 승인된 UI r3, ERP-PM-EXECUTION-01/v1, visual-contract r1 / visual-change-plan r2, repair2 구현 결과와 실제 원시 관측을 대조했다. B1 구현과 새로운 UI 설계 결정은 검토 범위 밖이다.
- 판정은 FAIL / changes-requested다. 아래 9개 결함은 소스 또는 명시한 실제 관측으로 확인했다. 새로운 테스트나 브라우저 조작은 실행하지 않았으며, 정적 재현 조건을 실제 실행 결과로 표현하지 않는다.
- 부모가 다음 배정과 최종 수용을 결정한다. usage: null.

## 확정 결함과 최소 수정 범위

| Stable key | 우선순위 | 근거와 재현 조건 | 필요한 보완 |
| --- | --- | --- | --- |
| F1-QUICK-CREATE-CONTINUITY | P1 | ProjectPlan.tsx:199-216에서 title/state/submitted/requestId/uncertain은 TaskQuickCreate 내부 상태에만 있다. 310행의 고급 계획 링크를 누르면 314행의 조건부 DefaultTaskWorkspace가 제거된다. unknown 상태에서 고급 보기로 갔다가 기본 보기로 돌아오면 잠긴 payload와 requestId를 잃고 새 UUID의 빈 폼으로 돌아온다. 일반 미저장 입력도 같은 경로에서 경고 없이 사라진다. 이 경로에는 이동 보호나 세션별 복구 저장소가 없다. | 프로젝트·세션에 종속된 초안과 미확정 요청의 수명을 화면 모드보다 길게 유지하거나, 미확정 요청을 해결하기 전 이탈을 막고 안전한 복구를 제공한다. 기존 same-ID/same-payload 재확인과 정상 복구 후 새 ID 사용을 유지한다. 다른 계정에 초안을 노출하지 않는다. advanced 왕복, 실제 경로 이탈, 복구 후 재편집을 검사한다. 단순 useUnsavedChanges 추가만으로는 query-only 모드 이동을 막지 못한다는 기존 hook 의미도 고려한다. |
| F1-DEFAULT-TARGETS | P2 | project-plan.css:206은 제목·text-button·select만 44px로 맞춘다. 새 quick-create input/submit과 기본 화면의 advanced 링크는 빠졌다. 실제 default-r2-1280-dom.json에서 input=40.5px, submit=40px이며 행 동작은 44px다. app.css:39의 기존 40px는 신규 S1 동작에 대한 계약의 44px 예외가 아니다. 새 overview 동작도 같은 .button 규칙을 상속한다. 추가로 83행의 cell padding 12px와 44px 행 컨트롤은 단일 행 높이를 약 69px로 만들어 PME-02의 52–60px compact desktop 행 범위를 넘는다. | 신규 compact/overview 동작과 입력에 한정하여 44px hit target을 적용하고, 단일 줄 desktop 행은 승인된 밀도로 맞춘다. 긴 내용은 잘라내지 않고 확장한다. global Calendar 좌표·hit area는 변경하지 않는다. 1280/390과 확대 reflow를 다시 측정한다. |
| F1-COMPACT-FILTER-LABELS | P2 | project-plan.css:17-18의 compact label/font 설정은 뒤에 있는 163행의 min-width:1200 규칙과 164행의 mobile 규칙에 동일한 specificity로 덮인다. 새 검색·상태·담당자 필터에 font-size:0, filter-label display:none이 적용된다. aria-label이 있어도 승인된 지속적인 시각적 label을 대체하지 않는다. | compact 범위에서 각 breakpoint 이후 올바른 label 스타일을 적용하여 검색 입력 후에도 검색/상태/담당자 라벨을 유지한다. 기존 advanced 화면과 Calendar 스타일은 보존한다. |
| F1-OVERVIEW-ACTION-AUTHORITY | P2 | Schedules.tsx:82,89의 TASK 만들기 링크는 role/canEdit 조건 없이 렌더링된다. VIEWER도 두 개의 활성 생성 동작을 보며 도착한 compact 폼은 비활성화되어 있고 인접한 읽기 전용 사유도 없다. 실제 쓰기 권한 우회가 확인된 것은 아니지만, 허용된 주 동작과 VIEWER read-only 설명 계약을 위반한다. | MANAGER/MEMBER의 현재 plan 권한으로 생성 동작을 한정하고 VIEWER에게는 작업 열기와 명확한 읽기 전용 설명을 제공한다. schedule 작성 권한을 plan 권한의 대용으로 쓰지 않는다. |
| F1-OVERVIEW-REGION-INDEPENDENCE | P2 | Schedules.tsx:54-55는 taskPlanReady를 dashboard.isSuccess와 memberCount>0에 종속시킨다. 접근 가능한 프로젝트에서 dashboard GET만 500이고 plan GET은 정상이어도 TASK GET 자체를 실행하지 않고 구성원 확인 상태에 멈춘다. TASK 영역을 독립적으로 새로고침할 수 없다. 이는 한 실패 영역이 다른 정상 영역을 보존하는 계약과 맞지 않는다. | TASK query를 현재 프로젝트 읽기 권한에 따라 독립 실행한다. 각 영역의 loading/error/retry를 분리하고 실제 access denial은 기존 보안 경계에 따라 처리한다. dashboard 실패/plan 성공 및 반대 방향을 각각 검증한다. |
| F1-TASK-EMPTY-STATE | P2 | Schedules.tsx:61의 isEmpty는 일정 수/목록만 확인하지만 64-72,91행은 이를 근거로 첫 작업 또는 실행 TASK 없음이라고 말한다. TASK가 이미 있고 일정만 없는 프로젝트에도 첫 작업을 요구한다. 89행은 all-kind items.length로 TASK 목록의 빈 상태를 판정하므로 EPIC/TOPIC만 있을 때 빈 ul이 나온다. ProjectPlan.tsx:225의 totalCount 역시 all-kind snapshot count이므로 TASK가 전혀 없는 프로젝트와 다른 종류만 있는 프로젝트를 구분하지 못한다. | 실제 TASK 컬렉션을 기준으로 무작업 상태를 계산한다. 성공한 원본 TASK 전체/현재 matched TASK 수를 구분하여 truly empty와 filtered empty를 표시한다. 일정 없음 메시지는 일정 영역에만 적용한다. TASK 존재+일정 없음, container만 존재, 필터 결과 없음 사례를 추가한다. |
| F1-BLOCKED-FACTS | P2 | ProjectPlan.tsx:221,225는 blockerIds.length만 차단으로 취급한다. state=BLOCKED이고 predecessor가 없는 정상 TASK에 상태 선택은 차단, 요약은 차단 0, 설명은 차단 없음이 동시에 나온다. 반대로 완료/취소된 항목의 남은 predecessor를 현재 차단 작업으로 셀 수 있다. | 현재 실행 상태와 실제 미완료 predecessor를 모두 반영한다. 명시적 BLOCKED와 선행 미완료를 구별해 설명하고 DONE/CANCELLED는 현재 차단 집계에서 제외한다. 선행 항목이 반드시 BLOCKED 상태인 것처럼 표현하지 않는다. |
| F1-DEFAULT-MODE-PRECEDENCE | P2 | ProjectPlan.tsx:63-66은 defaultMode를 !requested && (forceDefault || !hasLegacySelector)로 계산한다. /plan?mode=default&view=board는 부모가 명시한 mode=default 우선 규칙과 달리 고급 보드를 연다. | 명시적 기본 모드가 우선하도록 해석하고 legacy query 값은 보존한다. mode=default+view/types/scopeId 조합과, mode가 없는 legacy URL을 각각 검사한다. 새로운 URL 정책을 만들지 않는다. |
| F1-DEFAULT-INITIAL-LOADING | P2 | ProjectPlan.tsx:284의 plan query가 최초 pending일 때 314행은 isError와 데이터가 있는 refetch만 표시한다. 최초 TASK 로딩에는 heading/filters만 남고 진행 상태가 없다. query.data가 있어야 quick-create/list가 생기므로 사용자에게 로딩 여부를 알려 주지 못한다. | 최초 pending 상태를 영역 안에 표시하고 필터/heading은 유지한다. 성공한 빈 결과와 loading을 구분하며 오류에는 해당 영역 retry를 유지한다. |

## 인정한 검증 근거

- repair2-final-full.meta/stdout에서 2026-10-03 18:27:23–18:28:02+09:00, 전체 30 files / 341 tests, exit 0을 확인했다. repair2-final-targeted2.stdout의 5 files / 109 tests 성공도 확인했다. 실패한 이전 문구 기대 결과는 그대로 보존되어 있다.
- repair2-final-typecheck, repair2-final-build, repair2-final-diff 메타데이터는 각각 exit 0이다. 실제 tsc/Vite 성공 출력도 확인했다. 테스트 stderr의 jsdom navigation/TimeoutOverflowWarning은 성공으로 숨기지 않되 별도 제품 결함의 증거로 간주하지 않는다.
- C worktree의 browser/f1-candidate-r2/manifest.json과 원본 관측 폴더의 candidate-r2-bundle-manifest.json은 index.html 88BDF54A..., CSS 56C3AAD6..., JS FBBC1F79...를 명시한다. 실제 4186 서버 로그는 해당 JS/CSS 파일을 제공했다. helper 파일을 읽기 전용 hash 확인한 값은 164381A1A3CACF7DBD9669DB02CF926A83201610AE3FE2B8E3AD7497E78CFB03이다.
- default-r2-control-visibility.json은 advanced navigation이 hidden=true, visible=false, display=none, rect=0임을 보여 준다. default-r2-1280-snapshot의 접근성 트리에서도 고급 navigation은 빠져 있다. DOM 요소 존재만을 표시 실패로 판정하지 않는다. 기본 화면의 고급 종류 필터 제거는 인정한다.
- default-r2-390-dom은 390px, overflow=0, advancedNavVisible=false, quick-create control 44px, labelled table reflow를 확인한다. 1280px에서도 overflow=0이다. desktop 44px 결함은 mobile 성공으로 대체하지 않는다.
- unknown-response-r2 retained/recovered/next-edit/second-recovered와 실제 server log는 같은 화면에서 첫 요청 후 500, 동일 payload/requestId replay로 같은 resource 응답, 원본 재조회, 다음 입력의 새 requestId 사용을 증명한다. 첫 ID b960c664-3f7b-46b9-a88a-fe86bf1a2a40, 다음 ID eafd908b-a48c-41ea-a07d-2aeccdc2c30f다. 이는 화면을 떠나는 복구 시나리오를 증명하지 않는다.
- 기존 same-page-dialog-return-r1은 같은 페이지 caller로 복귀함을 보이며, keyboard-dialog-r1의 Escape는 itemId URL을 지우고 heading으로 복귀한다. 3px focus outline도 관측되었다. Shift+Tab 기록에는 BODY가 있으므로 이 자료만으로 모든 focus-trap 요구가 통과했다고 단정하지 않는다.
- calendar-r1-comparison과 agenda 비교의 동일 fixture에서 week/month/390 geometry는 동일하며 7-column/1152px canvas와 overflow=0 근거가 있다. 이번 검토는 이 유효한 자료를 재사용한다. 부모가 진행 중인 r2 Calendar/역할/고급 흐름 관측은 도착 전 완료로 간주하지 않는다.

## 남은 검증과 권한 경계

- 실제 native 200% zoom은 아직 증명되지 않았다. native-zoom-attempt는 Control++ 후에도 innerWidth/DPR/visualScale이 변하지 않았고, equivalent-200-reflow-r1은 명시적으로 640 CSS px/DPR2의 대등 reflow 관측이다. 이를 실제 브라우저 zoom 성공이라고 표현하지 않는다.
- 신규 결함의 예상 결과는 위 표에 명시했다. 수정된 정확한 candidate에서 affected role/partial/error/unknown navigation/empty/blocking/URL/loading/target/label 시나리오가 필요하며, 부모의 router 관측과 executor 테스트를 분리한다.
- 실제 ERP/Google/provider 연결, production TLS, backend PostgreSQL은 이 F1 source/browser gate의 성공으로 주장하지 않는다. 최종 high 통합 수용은 필수 관측과 동일 SHA CI가 갖춰진 뒤 별도 판단한다.
- 이 operational review 문서 하나만 작성했다. 소스·테스트·설정·Git·브라우저·외부 시스템을 변경하지 않았고 재위임하지 않았다. 초기 실패 기록은 보존했다. 403에서 quick-create가 실제 unmount되는지에 대한 조기 추정은 별도 확정 결함으로 세지 않는다. F1-QUICK-CREATE-CONTINUITY는 코드상 명백한 기본/고급 모드 전환으로 재현한다.
