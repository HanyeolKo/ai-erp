# Latest UI refactor functional plan

## 요청 및 전문 역할 handoff

- 작업/일자/로컬 원본: `latest-ui-refactor`, 2026-09-11, `D:/onedrive/Documents/ChatGPT/AI ERP/tmp/ui-workspace-refactor/docs/ux/latest-ui-refactor-functional-plan.md`.
- 실제 역할: 기본 agent `/root/latest_ui_plan`이 `ui-ux-designer`로 수행, `gpt-5.6-sol`/`medium` (상위 dispatch metadata). 상위 `/root`가 `latestorigin/main`의 화면 인벤토리와 보존 조건을 전달했고, 이 역할은 제품 소스를 읽기 전용으로 조사해 이 계획 문서만 작성한다.
- 사용자/목표/진입점: 로그인한 프로젝트 구성원이 현재 셸과 모든 최신 업무 흐름을 그대로 사용하면서, 화면 전반을 절제된 navy/pale/white ERP 작업 공간으로 일관되게 읽도록 한다. 모든 기존 hash route와 직접 링크가 진입점이다.
- 범위: 현재 화면의 표현 계층 정리. 내비게이션·조건·상태·데이터·문구·DOM 순서·행동은 변경하지 않는다. 이 문서는 구현 승인이나 시각 최종안이 아니며, 다음 `ai-erp-ui-visual-designer` 매핑과 독립 `ui-plan-review`의 입력이다.

## 현재 근거와 기능 인벤토리

- 기준 revision은 `058f782`. 근거는 `frontend/src/App.tsx`, `frontend/src/ui.tsx`, `frontend/src/screens/*.{tsx,css}`, `frontend/src/app.css`와 현재 회귀 테스트다.
- 공통 셸: skip link, 상단 brand/알림/계정 `details` 메뉴, 프로젝트가 있을 때의 상단 2차 영역, `aria-expanded` 모바일 메뉴 버튼, 현재 프로젝트명, 접히는 프로젝트 sidebar, `aria-current` 내비게이션, 프로젝트 선택 복귀, main content가 이미 구현되어 있다.
- 프로젝트 내비게이션은 현재 순서와 조건으로 개요, 일정, 구성원, 파일, Calendar 설정을 제공한다. 이 구조가 최신 승인 셸이며 과거 13rem rail 계약으로 교체하지 않는다.
- 계정·로비: 로그인/세션 만료, 프로젝트 선택·생성·복구, 계정, 알림, 개인 Calendar 연결, 초대 링크/코드 미리보기·수락/거절, 구성원과 역할, 초대 생성·공유 Dialog를 포함한다.
- 프로젝트: 개요의 onboarding/다가오는 일정/처리 대기/3개 지표, 일정 월간·주간·모바일 agenda/필터/페이지 이동, 일정 생성·수정, 상세/참석자/이력/확정·변경·취소·ACK·Calendar 재시도를 포함한다.
- Google Workspace: 계정별 Drive/Gmail/Calendar 권한 상태, Drive 검색·페이지 이동, Gmail 받은/보낸편지함·검색·상세 Dialog·작성/검토/발송 receipt, 프로젝트 Drive 참조 선택·첨부·제거, 프로젝트 Calendar 선택·연결·해제를 포함한다.
- 공통 상태: `QueryState`, `Notice`, `Loading`, `ProjectMissing`, 재시도, 권한 거부/세션 만료, 초기·loading·empty·partial/error/success/disabled/unknown 결과와 native `Dialog`의 focus trap/Escape/호출점 복귀가 이미 존재한다.

## 적용할 패턴과 기존 제약

- 승인된 `ERP-WORKSPACE-01/v1`에서 색·로컬 글꼴·간격·표면·컨트롤·상태 원칙만 계승한다. 과거 shell/layout, route별 screen mapping, “dialog unused” 판단은 현재 소스와 충돌하므로 폐기한다.
- 기본 토큰 후보: page `#F3F5F8`, surface `#FFFFFF`, subtle `#F8FAFC`, text `#17243B`, secondary `#526079`, navy `#172B46`, navy hover `#294568`, accent/link `#2458A6`, accent hover `#1B478A`, accent soft `#EDF3FC`, divider `#D9E1EC`, interactive border `#7B899D`, focus `#B45309`, danger `#A82D26/#FFF1F0`, success `#216244/#EDF8F1`, disabled `#EEF1F5/#657187/#CDD5E0`.
- 글꼴은 `Segoe UI, Malgun Gothic, Apple SD Gothic Neo, system-ui, sans-serif`; body 15px/1.6, h1 28px/1.3, h2 20px/1.4, h3 16px/1.5, metadata 13px/1.5를 후보로 한다. 외부 font/dependency를 추가하지 않는다.
- spacing은 4/8/12/16/20/24/32/40px, 의미 있는 white task surface는 1px divider·12px radius·24px padding(모바일 16px)을 기본으로 한다. 하나의 연속 업무 묶음에 한 표면만 쓰고 nested card와 장식용 panel을 늘리지 않는다.
- 버튼은 기존 `.button-primary/.button-secondary/.button-danger` 의미를 유지해 fill/outline/destructive 위계를 통일한다. 원래 class가 없는 native action은 중요도와 기존 순서를 보고 중립 버튼으로만 표현한다. min-height 40px, 모바일 44px, radius 8px, 8px action gap이 후보다.
- 상단/side navigation은 현재 DOM과 반응형 토글을 유지한 채 navy를 방향 표식으로 사용할 수 있다. account menu, mobile second row, sidebar의 실제 배치·표시 조건·activation은 고정한다. 기존 topbar를 과거 고정 rail이나 새 hamburger 체계로 바꾸지 않는다.
- pale page 위에 로비 목록, forms, schedule groups, metrics, records, Google service/status/selection, Dialog body를 의미 단위의 white task surface로 정렬한다. 페이지 전체를 하나의 큰 카드로 감싸거나 모든 section에 전역 card 스타일을 적용하지 않는다.
- UI Pro Max 검색은 keyboard focus, responsive table/list handling, 375/768/1024/1440 확인을 지지했다. 자동 제안된 teal/orange·Playfair·marketing demo 패턴은 승인된 ERP 방향과 맞지 않아 채택하지 않았다. React stack 검색은 일치 결과가 없어 일반 프로젝트 규칙만 사용한다.
- pinned frontend/web guidance에 따라 visible `:focus-visible`, semantic button/link/label, 44px 모바일 target, 긴 문자열 줄바꿈, reduced motion, 대비와 상태 피드백을 유지한다. 새로운 motion, icon, hero, gradient, shadow 체계를 만들지 않는다.

## 구현 허용 범위와 불변 조건

- 우선 허용 presentation 파일은 `frontend/src/app.css`, `frontend/src/screens/schedules.css`, `frontend/src/screens/GoogleWorkspace.css`다.
- selector scope를 안전하게 만들 때만 현재 TSX에 중립 `className` 또는 비대화 없는 neutral wrapper를 추가할 수 있다. 후보는 `frontend/src/ui.tsx`와 실제 해당 screen 파일뿐이며, 별도 시각 검토와 구현 계약에서 정확한 추가점을 열거해야 한다.
- handler, hook, query key, API call, route/hash, condition/permission, displayed content, heading/list/form order, element role, `aria-*`, `id`/label 관계, focus logic, confirmation, disabled/loading 판단, 날짜·시간·calendar inline geometry를 변경하지 않는다.
- 프로젝트 sidebar의 링크·순서, 상단 account menu, 모바일 menu open/close와 2단 topbar, 모든 새 Google/project 흐름을 숨기거나 재배치하지 않는다. 모바일은 현행 `<768px` 토글과 agenda 대체를 보존한다.
- schedule calendar의 7열, month/week 전환, 모바일 agenda, `.day-events` 좌표 기준과 event inline `top/minHeight/left/width`, data attribute와 클릭 대상은 고정한다. typography/spacing 변경으로 wrapping-dependent 전체 bounding box가 달라질 수 있으므로 byte-identical 전체 geometry를 요구하지 않으며, 같은 fixture에서 데이터 인코딩 좌표와 day canvas 의미가 유지되어야 한다.
- lists는 기존 `ul/li`와 서버 순서를, forms는 native fieldset/label/control과 오류 결합을, Dialog는 현재 제목 연결·focus trap·Escape·호출점 복귀를 보존한다. Gmail 작성 검토 단계와 discard 확인, 파일/Calendar의 확정 전 단계도 그대로 둔다.
- 색만으로 상태를 구분하지 않으며 기존 상태 문구를 남긴다. loading은 animation/skeleton을 추가하지 않고, empty/error/denied/unavailable/unknown은 기존 회복 행동과 함께 표시한다.

## 반응형·접근성·수용 기준

- 데스크톱 1440px에서 현행 topbar+sidebar+content 구조가 유지되고, task surface와 action hierarchy가 모든 route에서 같은 토큰으로 읽힌다. 768~1100px schedule filter grid와 현재 shell 전환 경계에 새 충돌이 없어야 한다.
- 390/375/320px에서 현재 모바일 메뉴 버튼으로 project sidebar를 열고 닫을 수 있고 `aria-expanded/controls`가 그대로다. account menu, project title, page heading/action, filters, lists, forms, pagination, Dialog가 화면 밖으로 잘리지 않는다. calendar만 기존 의도대로 bounded handling을 허용한다.
- 긴 프로젝트명·이름·이메일·제목·ID·메일 본문은 ellipsis/line clamp 없이 줄바꿈하고 flex/grid child는 `min-width:0`로 수축한다. 200% zoom에서도 일반 문서 가로 overflow는 1px 이하를 목표로 한다.
- keyboard 순서, `aria-current/pressed/selected/live`, skip link, route 이동 h1 focus, Dialog focus 복귀와 모든 기존 tab stop이 유지된다. focus outline은 3px/2px offset으로 가려지지 않고 navy 위에는 동등한 밝은 ring 변형을 쓸 수 있다.
- 일반 텍스트 4.5:1, 큰 텍스트와 UI boundary/focus 3:1 이상을 실제 조합에서 측정한다. hover/active/focus/disabled가 구별되고 disabled form control을 전체 opacity로 흐리지 않는다.
- 구현 검증은 기존 frontend 전체 test와 typecheck/build, route별 role/name regression, desktop 1440x900·tablet 768x1024·mobile 390x844·320px/200% zoom screenshot 비교, keyboard-only 탐색, contrast 측정을 포함한다.
- 추가 회귀 표본은 empty project onboarding, VIEWER 권한, access recovery, schedule month/week/agenda와 event geometry, invitation Dialog, Google 부분 권한, Drive 선택 후 첨부, Gmail 상세·작성·검토·UNKNOWN receipt, Calendar bind/unbind다.
- 이 역할은 소스·테스트·pinned guidance를 읽고 계획 문서만 작성했다. 브라우저, screenshot, contrast, keyboard, test/typecheck/build는 실행하지 않았다. Reviewer는 `docs/ux/latest-ui-refactor-ui-review.md`에서 revision 일치와 전체 route mapping을 확인해야 한다.

## 아카이브

- 로컬 원본: `D:/onedrive/Documents/ChatGPT/AI ERP/tmp/ui-workspace-refactor/docs/ux/latest-ui-refactor-functional-plan.md`.
- Notion `AI 생성문서 관리`: 상위 오케스트레이터가 독립 리뷰와 최종 계획 확정 후 기존 같은 맥락의 `안읽음` 문서를 확인해 동기화한다. 이 역할은 외부 쓰기를 수행하지 않았다.
