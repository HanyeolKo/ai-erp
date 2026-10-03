# 프로젝트 일정관리 SYNTH 시각 설계 제안

## Contract identity

- Task: `ERP-PM-20261002-VIS`; revision: `r2`.
- Source: `ERP-PM-20261002-SYNTH r1`, `ERP-PM-20261002-UI r2`, baseline `b7178f1`.
- UI r2 input: synthesis-ui.md r2 and tmp/pm-review-2026-10-02/guidance/ui-r2-guidance-evidence.md, actually consumed by native visual specialist.
- Author: native `/root/synthesis_visual`, `ai-erp-ui-visual-designer`, Sol/medium; read-only source access. Parent saved the returned proposal as the canonical file without production edits.
- Proposed pattern: `ERP-PM-EXECUTION-01/v1-candidate`.
- Stage: `pattern-stage proposal; parent acceptance pending`; status: `ready-for-ui-plan-review`.
- External mode: `offline-contract-only`.
- Staleness: affected changes to SYNTH r1, UI r2, b7178f1, selected guidance evidence or existing `ERP-WORKSPACE-01/v2` stale this proposal and related evidence. Changed r1 evidence is superseded in the affected scope by this r2.

이 문서는 기능 계획 이후의 읽기 전용 시각 제안이다. 패턴 후보는 독립 `ui-plan-review`와 부모 수락 전에는 채택된 패턴이 아니며 구현 화면이나 브라우저 검증 결과를 뜻하지 않는다. 현행 소스와 캡처에서 관찰한 `ERP-WORKSPACE-01/v2`의 남색·파랑 계열, 밝은 배경, 224px 프로젝트 사이드바, 8px 계열 간격, 목록 중심 구조와 명확한 포커스를 기반으로 한다.

## Evidence inventory

- observed: 현재 `frontend/src/app.css`의 의미 토큰, Segoe UI/Malgun Gothic, 15–16px 본문, 28px 제목, 8px/12px 반경, 40/44px 제어, 3px 호박색 포커스와 224px 사이드바.
- observed: `frontend/src/ui.tsx`의 셸·프로젝트 메뉴·건너뛰기 링크·오류·로딩·native Dialog·초점 복귀.
- observed: overview-desktop.jpg의 일정 중심 반복, plan-tasks-desktop.jpg의 여덟 열·행 내 편집기, plan-tasks-mobile.jpg의 긴 제어·카드, schedule-workspace-desktop.jpg의 상단 밀도와 일관된 탐색 위계.
- accepted functional input: 부모가 UI 전문 에이전트에 배정한 synthesis-ui.md의 정보 구조·네 상태 축·권한·기본/고급 경계·390px 순서·복구·키보드. 기능 계획은 전문 단계 완료이며 최종 독립 UI 검토는 아직 필요하다.
- candidate: 아래 공유 규칙과 제안 매핑. 아직 구현·수락되지 않았다.

## Shared visual rules

**PMV-01 Shell and hierarchy.** 기존 상단 바와 224px 프로젝트 사이드바를 유지한다. 전역 탐색은 내 작업·프로젝트·알림, 프로젝트 탐색은 개요·작업·일정·파일·설정 순서다. 제목·기준일·시간대, 주요 동작 하나, 필터와 본문 순서로 위계를 만든다. 동작 의미·순서·경로는 기능 계획을 따른다.

**PMV-02 Type and spacing.** 외부 글꼴 없이 Segoe UI, Malgun Gothic, Apple SD Gothic Neo, system-ui를 사용한다. 제목 28px/1.3, 절 제목 20px/1.4, 행 제목 16px/1.5, 본문 15px/1.6, 보조 13px/1.5를 제안한다. 간격은 4/8/12/16/20/24/32/40px이며 섹션 사이 32px, 행 내부 8–12px, 제어 사이 8px다.

**PMV-03 Surfaces and density.** 페이지 전체를 카드로 감싸지 않는다. 검색·필터, 표·목록, 상세와 오류·권한 안내에만 흰 표면과 1px 구분선을 쓴다. 장식용 카드 격자와 그림자 반복을 피한다. 읽기 행은 약 52–60px, 편집 행은 64px 이상을 허용한다. 기본 목록의 다중 편집기를 상세 편집으로 옮기는 기능 계획을 지원한다.

**PMV-04 Native lists and tables.** 반복 데이터는 기본 HTML 표·목록으로 표현한다. TASK 기본 열은 제목·프로젝트·우선순위·TASK 상태·마감·차단 이유·다음 시간이다. 머리글을 유지하고 날짜·수치에 tabular-nums를 사용한다. 행 상세 진입과 상태 변경 제어를 구분하며 가로 스크롤을 기본 탐색으로 삼지 않는다.

**PMV-05 State grammar.** 네 상태 축은 동일 의미의 배지로 합치지 않는다. ‘업무 · BACKLOG(대기)’, ‘일정 · CONFIRMED(확정)’, ‘내 확인 · 대기’, ‘Calendar · 실패’처럼 영역·정확한 계약 토큰·한국어 의미를 구분한다. 일정 종료·업무 완료·확인·투영을 합친 완료 배지는 금지한다. 계약에 없는 기계 토큰은 만들지 않는다.

**PMV-06 Semantic colors.** 기본 텍스트 #17243B, 보조 #526079, 링크·주요 동작 #2458A6, 선 #D9E1EC, 주의·포커스 #B45309, 위험·오류 #A82D26, 성공 #216244와 옅은 배경 #EDF3FC/#FFF8E8/#FFF1F0/#EDF8F1를 제안한다. 차단·기한 지남·접근 거부·저장 확인 중 같은 텍스트와 아이콘 이름·테두리를 함께 쓴다. 실제 대비 계산은 후속 검증이다.

**PMV-07 Fact, observation, judgment.** 저장 사실은 중립 행, 파생 관찰은 주의 테두리와 ‘시스템 관찰’, 건강 상태·마일스톤 결정은 ‘관리자 판단’으로 표시한다. 큰 숫자 타일이나 자동 위험 점수를 쓰지 않는다. 모든 위험에 근거·기준 시각·관련 링크를 같이 둔다.

**PMV-08 Read and edit modes.** 읽기 화면은 정의 목록과 평평한 행으로 비교하기 쉽게 한다. 편집은 label/control/도움말/오류를 세로로 묶고 저장 영역을 마지막에 둔다. VIEWER에게 빈 편집 제어를 보여 주지 않으며 비활성 이유는 인접 문구로 설명한다.

**PMV-09 Detail and side panel.** TASK 상세은 기본·시간·파일·마일스톤·고급의 세로 구조다. 보조 정보가 필요하면 최대 320px의 우측 요약 영역을 허용하되 저장·상태·권한 설명을 그 안에만 두지 않는다. 1024px 미만에서는 보조 영역을 본문 아래로 옮긴다.

**PMV-10 Responsive and keyboard.** 390px 순서는 메뉴·제목·위험 요약·주요 동작 하나·필터 펼침·목록·페이지 이동이다. 표는 프로젝트·상태·마감·차단·다음 시간 레이블이 있는 카드형 행으로 재배치한다. 필터는 문서 흐름의 펼침 영역이며 하단 시트를 쓰지 않는다. DOM·키보드·시각 순서를 일치시키고 3px 포커스, 44px 터치, 200% 재배치, Dialog Escape와 호출점 복귀를 유지한다.

**PMV-11 Calendar data boundary.** 월·주 일정의 top/height/minHeight/left/width, 일곱 열과 시간 캔버스 높이는 데이터를 부호화하므로 변경하지 않는다. 색·서체·테두리만 공유 토큰에 맞춘다. 좁은 화면은 기존 agenda/list를 활용하며 전역 가로 넘침을 만들지 않는다.

**PMV-12 Recovery and partial states.** 로딩 중에도 제목·기준일·필터를 유지한다. 부분 실패는 실패 영역만 경고한다. 403·409·저장 불명확·Drive 열기 미확인은 다른 제목과 복구 동작을 가진다. 애니메이션이나 색 변화만으로 상태를 설명하지 않는다.

## Proposed screen mapping

| 맥락 | 공유 규칙 | 표시 우선순위 | 좁은 화면 |
| --- | --- | --- | --- |
| 내 작업 | PMV-01–06,10,12 | 제목·시간대, 오늘/이번 주/전체, 필터, 주의 필요, 진행 가능, 페이지 | 제목, 필터 요약, 주의·진행 업무, 전체 필터, 페이지 |
| 프로젝트 개요 | PMV-01–03,06–07,10,12 | 목적·성공, 관리자 판단, 실행 사실, 위험, 마일스톤, 예정 시간 | 제목·동작, 위험, 마일스톤, 예정 시간, 정의·건강 상세 |
| TASK 목록·상세 | PMV-02–10,12 | 제한된 목록 열, 제목·상태·저장, 기본, 시간, 파일, 마일스톤, 고급 | 제목·저장, 차단·마감, 기본, 시간, 파일, 마일스톤, 고급 |
| 일정·Calendar | PMV-01–06,10–12 | 일정 상태·시간대, 내 확인, Calendar, 연결 업무 | 제목·시간, 일정, 확인, 투영, 연결, 동작, 고급 |
| 주간 검토·마일스톤 | PMV-02–03,05–08,10,12 | 기준일·필터, 불일치, 차단·후속, 증거 누락, 조건·증거·결정 | 기준일, 긴급 관찰, 근거·링크, 나머지 관찰, 결정·증거 |

매핑은 후보를 검토하기 위한 제안이다. 수락 전 구현 매핑은 N/A pending parent acceptance이며 후속 동일 ID의 VISUAL-CHANGE-PLAN이 필요하다.

## Functional invariants and exceptions

시각 변경은 기능 계획의 actions/handlers/routes/APIs/states/permissions/validation/data/columns/content/DOM semantics/accessible names/visibility conditions를 보존한다. 원본별 권한, 네 상태 축, date-only와 instant, 일반 회의의 업무 미연결, 여러 시간·파일 관계, 고급 보기·속성을 유지한다. 시각 작업으로 동작을 숨기거나 클릭을 추가하지 않는다.

PMV-E1: 모바일 셸의 긴 프로젝트명만 한 줄 말줄임하고 전체 이름을 접근 가능한 위치에 제공한다. PMV-E2: Calendar 데이터 좌표와 bounded handling을 유지한다. PMV-E3: 오류·차단 이유·시간대·권한 이유는 말줄임하지 않는다. 예외도 검토 전 후보다.

## Reader deck guidance

1280×720 자료는 Malgun Gothic, 제목 30–34px 이상, 본문 약 24px, 보조 18–20px를 사용한다. 한 슬라이드는 한 판단만 다루며 평평한 편집 가능한 표를 최대 다섯 행 정도로 제한한다. 실제 캡처에는 ‘현재 화면 · 합성 데이터 · 2026-10-02’, 제안에는 ‘제안 와이어프레임 · 미구현’을 붙인다. 측정처럼 보이는 차트·가짜 KPI·임의 사용자 수·진행률·장식 도형을 쓰지 않는다. 상태 관계는 텍스트와 표로 설명한다.

## r2 guidance consumption and bounded rule refinements

이 절은 native synthesis_visual이 UI r2와 실제 가이드 근거를 읽고 반환한 수정이며, 해당하는 위의 일반 문구·매핑을 구체화한다. 패턴 후보와 나머지 기능 불변 조건은 유지한다.

**Selected evidence.** G-R2-01: guidance lines 14–16의 내부 ERP 밀도 제약을 채택하고 hero/video/CTA·생성 팔레트·Fira를 배제했다. G-R2-02: lines 19–28의 지속 label, URL 상태, 정적 badge와 조작 control 구분, contextual feedback, 모바일 오류 요약과 stable identity를 채택했다. G-R2-03: lines 30–34의 사실·편집·관찰·권한 설명 위계를 채택했다. G-R2-04: lines 36–42의 semantic table/list, tabular numerals, unlink/reassign 확인 또는 undo, inline error와 focusable summary, 390px 읽기 순서를 채택했다. G-R2-05: lines 44–46은 네 상태 축과 primary/secondary permission 보존을 직접 요구한다. 세 고정 스킬과 일곱 검색의 실제 명령·출처 해시는 ui-r2-guidance-evidence.md에 있다.

**PMV-04 refinement.** 필터·탭·페이지의 URL 상태를 유지하고 필터·요약·레코드를 별도 영역으로 표현한다. 재정렬·갱신에서도 안정된 행 정체성을 유지한다. 근거 G-R2-02/04.

**PMV-05 refinement.** 연결 행에 ‘유형 · 기본 TASK’와 ‘유형 · 관련 항목’을 별도 정적 레이블로 표시하며 button/chip처럼 꾸미지 않는다. 근거 G-R2-02/05.

**PMV-08 refinement.** 기본 관계 영역에는 ‘일정 의미를 정하는 기본 TASK’와 ‘MANAGER 또는 이 일정의 작성 MEMBER’를 함께 표시한다. 관련 관계에는 ‘관련 TASK/MILESTONE’과 ‘계획 편집 권한 필요 · 읽을 수 있는 같은 프로젝트 대상만’을 표시한다. 다른 사람이 만든 일정을 읽는 TASK 담당 MEMBER에게는 관련 항목 추가·해제만 보이며 기본 TASK·일정·참석자 편집은 허용하지 않는다. 비활성 이유는 인접 문구로 설명한다. 근거 UI r2 관계 규칙과 G-R2-03/05.

**PMV-10 refinement.** 390px에서도 기본 TASK와 관련 항목을 합치지 않는다. 연결 유형 → 대상 이름 → 네 상태 축 → 권한 설명 → 허용된 동작 순서로 DOM과 시각 순서를 일치시킨다. 근거 G-R2-04/05.

**PMV-12 refinement.** 링크 충돌은 최신 링크·내 초안·겹친 필드로 설명하고 다시 불러오기·내 변경 검토 후 저장·취소를 제공한다. 외부 프로젝트와 접근 불가 대상은 확정 전에 거부 이유를 표시한다. 성공·실패 feedback은 연결 유형·대상 이름을 한 번만 알리며 초점을 옮기지 않는다. 근거 UI r2와 G-R2-02/04.

**Updated TASK mapping.** 기존 시간 절 안에서 기본 TASK와 관련 항목을 나눈다. 기본 지정·변경·해제·재지정은 일정 쓰기 권한자만, 관련 추가·해제는 계획 쓰기 권한자만 조작할 수 있다. 각 행의 일정 상태·내 확인·Calendar를 유지한다. 좁은 화면도 같은 두 관계를 보존한다.

**Updated schedule mapping.** 시간·일정 상태·내 확인·Calendar 다음에 기본 TASK와 관련 TASK/MILESTONE을 구분한다. 각 관계에 원본별 권한 설명을 둔다. 타인 생성 일정의 MEMBER는 관련 항목만 조작한다. 기존 Calendar 좌표·탐색 의미는 유지한다.

**Additional invariants and checks.** 기본 TASK의 설정·변경·제거·재지정은 MANAGER 또는 createdBy MEMBER만 수행한다. 보조 연결은 계획 쓰기와 같은 프로젝트의 읽기 권한을 요구한다. TASK 배정은 일정·참석자·Drive 권한을 주지 않는다. TASK 상세·일정 상세·선택기는 동일 레이블·권한·rowVersion/409 초안 보존을 사용한다. 후속 검증은 MANAGER·작성 MEMBER·타인 일정의 담당 MEMBER·VIEWER, 외부 프로젝트·접근 불가·stale revision, 연결 후 네 상태 축 불변을 포함한다.

이번 r2는 근거와 계약의 읽기 전용 대조다. 구현·테스트·브라우저·키보드·대비·스크린 리더·운영 ERP·외부 연동은 실행하지 않았다. 후보 패턴은 아직 독립 검토와 부모 수락 전이다.

## Validation and handoff, retained checks

소스·CSS/TSX와 합성 캡처를 읽기 전용으로 확인했다. 구현·브라우저·대비 계산·키보드·스크린 리더·실기기·200% 확대·운영 ERP·Google 연동은 실행하지 않았다. 캡처는 합성 화면 구성 근거다.

후속 independent ui-plan-review는 패턴 단계·규칙·불변 조건·예외·매핑을 확인한다. 이후 구현 검사는 1280/1024/390px 재배치, 일반 본문 가로 넘침 1px 이하, 네 상태 축 식별, 표 머리글·카드 라벨, 초점·키보드, 본문 4.5:1/UI 3:1 대비와 Calendar 좌표 동일성을 포함한다. 부모 수락과 별도 구현 계약 전에는 구현을 시작하지 않는다.

- Canonical path: D:/onedrive/Documents/ChatGPT/AI ERP/docs/ux/pm-review-2026-10-02/synthesis-visual.md
- Notion archive deferred; actual reading status unknown. Last synchronized: not synchronized.
