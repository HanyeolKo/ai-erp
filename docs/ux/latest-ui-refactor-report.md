# 전면 UI 리팩터링 결과

2026-09-11 · 기준 main058f782 · 패턴 ERP-WORKSPACE-01/v2

현재 프로젝트·계정·일정·Google Workspace 화면의 색상, 글꼴, 간격, 카드, 폼, 버튼과 포커스 표시를 공통 규칙으로 정리했다. 제품 변경은 app.css, schedules.css, GoogleWorkspace.css 세 파일이며 TSX, API, 권한, 세션, 업무 흐름, 배포 설정은 변경하지 않았다. 최신 상단 메뉴·프로젝트 사이드바·모바일 메뉴를 보존했다.

시각 전용 역할 ai-erp-ui-visual-designer와 전용 스킬을 추가했다. 저장된 native wrapper는 Sol/medium 및 read-only로 제한하고, 기능 계획 → 시각 패턴 제안 → 독립 검토 → 구현자 경로를 검증한다. 시각 역할은 버튼 배치·간격·표현만 제안하며 기능·데이터 좌표·노출 조건을 바꿀 수 없다. 현재 최신 planner/release/Luna 구현 정책을 유지했다. 이번 계획 호출은 문서만 쓰도록 상위가 배정한 generic 역할이며, 새 native 역할의 실행·발견을 실제 검증했다는 의미는 아니다.

## 화면 검증

- 19개 route를1440px/390px에서 전후 캡처했다. 각 폭에서18개 route의 DOM 컨트롤 이름·순서·링크·disabled 상태가 정확히 같았다. 구성원 화면은 닫힌 초대 Dialog의 비동기404 응답 시점 때문에 숨겨진 재확인 버튼1개 차이가 있었으며, 보이는 화면과 제품TSX는 동일하다. 이를19개 전체 DOM 일치로 주장하지 않는다.
- 추가19route×768/320/375/720CSS폭=76표본, Google 관련7화면×1440/768/390/320=28표본에서 일반 문서·본문 가로 overflow가 없었다.720CSS폭은1440화면의200% 확대에 상당하는 reflow 검사이며 브라우저 실제 zoom 설정 검증은 아니다.
- 캘린더 root16px,7개 day canvas260px, 일정 inline top/minHeight/left/width와 시간 눈금0/65/130/195px를 유지했다. 타이포그래피 변경에 따른 내용 높이 차이는 데이터 좌표 변경과 구분했다.
- 실제 토큰11개 조합의 텍스트·컨트롤·포커스 대비를 측정해 기준을 충족했다. 모바일 새로고침44px, 긴 계정명·이메일320px 줄바꿈, 주간 일정 focus ring 잘림을 확인하고 보완했다. 인접 일정의 실제 hit target도 각각 원래 상세 링크를 가리킨다.
- 로컬 합성 데이터에서 초대 코드 미리보기, Drive 선택/첨부, Gmail 작성/검토/SENT/UNKNOWN, Calendar 연결/해제, 빈 목록·VIEWER·로그인 만료·설정 미완료·오류·Google 부분 권한을 확인했다. 실제 Google 파일·메일·계정에 대한 연동 검증이나 외부 변경은 수행하지 않았다.
- Native Dialog의 제목 초점, 닫기/입력/취소/확인 순서와3px 포커스, Escape 후 호출 버튼 복귀를 확인했다. 경계 Tab에서 BODY로 관측되는 브라우저 초점 간격 후 Dialog로 돌아오는 기존 native 동작을 기록했다. 모든 Tab이 DOM 내부에 머무는 별도 JS focus trap을 검증했다고 주장하지 않는다.

원시 근거는 latest-ui-evidence/의 PNG·JSON·명령 결과에 있다. screenshots는 viewport 캡처이며 페이지 전체 캡처는 아니다. 재현용 scripts/ui-preview-server.mjs는127.0.0.1 전용·비영속 합성 서버이며 기본8081, UI_PREVIEW_PORT/UI_PREVIEW_SCENARIO로 조절한다. 개발 서버의 기존 proxy8080에 사용하려면 UI_PREVIEW_PORT=8080으로 실행한다.

## 자동 검사와 검토

- Frontend15파일232테스트 통과, typecheck 및 production build 통과. Windows sandbox의 최초 spawn EPERM은 동일 명령을 허용된 권한으로 재실행해 해결했고 초기/성공 기록을 모두 남겼다.
- 최신 backend135테스트 및 OpenAPI 생성 통과. backend/API 소스는 변경하지 않았다.
- Harness verifier, Git 추적 검사, 회귀92테스트, 고정 UX 스킬 smoke 통과. 새 시각 권한/라우팅 회귀6개를 포함한다.
- 독립 ui-plan-review가 패턴과 화면 매핑을 통과시켰으며, 최종 task-review는 latest-ui-refactor-task-review.md에 기록한다. PR 동일SHA CI와 별도 release readiness 확인 후 사용자 승인된 병합을 수행한다. 이 문서는 프로덕션 배포 완료를 주장하지 않는다.

## 작업 원본과 보관

원래 작업 폴더에는 기존 rebase가 진행 중이므로 그대로 보존했다. 최신 main을 기준으로 tmp/ui-workspace-refactor의 codex/ui-workspace-refactor 브랜치에서 통합했다.

로컬 원본: D:/onedrive/Documents/ChatGPT/AI ERP/tmp/ui-workspace-refactor/docs/ux/latest-ui-refactor-report.md

Notion AI 생성문서 관리 보관은 기존 entitlement_required(블록 한도) 상태에 변화가 확인되지 않아 보류했다. 로컬 문서를 원본으로 유지하고, 상태가 복구되면 같은 맥락의 안읽음 문서를 확인해 갱신한다. 중간 계획·원시 로그를 각각 새 페이지로 만들지 않는다.
