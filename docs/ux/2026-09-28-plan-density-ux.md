# 계획 화면 밀도 UX 방향

## Request and specialist handoff

- 작업: `plan-density-ux-20260928 r1`, 2026-09-28, 기준 경로 `D:\Project\Ai ERP`.
- 실제 역할: `ui-ux-designer`, `gpt-5.6-sol/medium` attempt 1. 이 worker는 구현·배포·DB·network write를 수행하지 않았다. 부모는 점검 과정에서 loopback mock API에 6건을 POST했다.
- 사용자 목표: 여러 TASK·TOPIC·Milestone이 함께 있는 계획에서 구조를 읽고 항목을 찾으며, 작업 맥락을 잃지 않고 반복 등록할 수 있는 최소 UX 개선 방향을 정한다.
- 범위: 현재 상태 평가와 우선순위 제안만 포함한다. 기존 API 계약을 바꾸는 설계는 후속 검토 대상으로 둔다.

## Current evidence and constraints

### 관찰된 사실

- 최초 fixture는 총 68개였고, CANCELLED 1개를 제외한 기본 Roadmap에는 67개가 보였으며 표시 가능한 TASK는 63개였다.
- 부모가 local mock API로 6개를 추가한 확장 fixture는 총 74개다: EPIC 1개, TOPIC 3개, TASK 67개, Milestone 3개이며 TASK 중 1개는 CANCELLED다. 따라서 기본 표시 항목은 73개이고 표시 가능한 TASK는 66개다. 증거는 `.tmp/evidence/plan-density-ux-20260928/`에 있다.
- 기본 Roadmap에서 최초 fixture의 67개 행을 거의 같은 시각적 무게로 표시하는 것을 확인했다. 확장 fixture도 같은 렌더링 구조를 사용하므로 상위 구조와 핵심 시점을 먼저 읽기 어려울 것으로 예상한다.
- 최초 fixture에서 계층 보기의 `TASK·Milestone도 펼쳐 보기`를 켠 뒤 작업 보기로 이동하면 `types=all`이 유지되었다. 화면에는 TASK만 보이지만 필터 항목 수는 67개이고, EPIC·TOPIC·Milestone은 표시되지 않는다는 경고가 함께 나왔다. 실제 표시 가능한 TASK는 63개였다.
- 작업 행은 제목 영역 badge와 별도 열에서 상태·담당자를 반복하고, 시작·종료·마감 입력을 항상 노출한다. 한 번에 비교할 수 있는 행 수가 적고 제목·경로 탐색보다 편집 제어가 먼저 두드러진다.
- 작업에서 `그룹=상위 경로`를 선택한 뒤 Board를 거쳐 돌아오면 `그룹=없음`으로 초기화되었다. Board의 `준비` 열에는 42개 card가 한 열에 이어졌다.
- Milestone 3개는 상위 경로·담당자·상태·날짜를 중복해서 보여 준다. 날짜가 일부만 있으면 `날짜 미정 – 종료일`과 같은 표현이 나오고, 정상적인 Milestone에도 `진행 정보 없음`이 붙는다. 새 Milestone에는 연결 관계가 없었다.

### Source-confirmed facts

- 기준 source는 `.tmp/worktrees/project-plan-views-20260927/frontend/src/screens/ProjectPlan.tsx`와 `project-plan.css`이며, 역할 계약은 `.tmp/worktrees/project-plan-views-20260927/harness/team/agents/ui-ux-designer.md`다.
- URL의 검색·상태·담당자·기간·scope·types는 보기 이동 시 유지된다. 반면 계층 접힘, TASK 정렬·그룹·페이지는 component local state라서 보기 재진입 시 초기화된다.
- Roadmap과 Board는 pagination이 없고, TASK 표만 50개 단위로 나눈다. 명시적인 filtered-empty 안내는 각 보기 component에 없다.
- 생성 draft는 현재 scope의 parent, 기간 filter, 담당자 filter를 상속한다. 저장하면 dialog가 닫히며 `저장 후 다음 항목 추가` 동작은 없다.
- Milestone 화면의 `영향받는 미완료 TASK`는 `successorIds`를 직접 참조한다. 이는 “Milestone 달성에 필요한 선행 작업”과 같은 뜻이라고 추론할 수 없다.
- VIEWER는 생성·편집이 잠기고, 403이면 보호된 query를 제거한 뒤 권한을 다시 확인한다. Dialog는 제목으로 focus를 이동하고 닫을 때 호출점 focus를 복구한다.

### 검증이 필요한 가설

- 기본 Roadmap을 EPIC·TOPIC·Milestone 중심으로 줄이고 TASK를 scope 또는 명시적 확장으로 노출하면, 74개 규모에서 구조 파악 시간이 줄어들 것이다.
- 보기마다 유효한 유형과 표시 건수를 따로 계산하면 경고를 읽고 실제 항목 수를 재해석하는 비용이 줄어들 것이다.
- 반복 생성에서 parent·kind·assignee를 고정하고 제목·날짜를 새 항목마다 확인하게 하면 속도와 오등록 방지를 함께 개선할 수 있다.

## Workflow and screen structure

### 우선순위 1: 구조 우선 밀도와 보기 컨텍스트

- Roadmap 기본 행은 EPIC·TOPIC·Milestone으로 한정하고, 선택한 branch의 TASK만 펼친다. 전체 TASK timeline은 사용자가 TASK 유형을 명시했을 때만 연다.
- 계층 보기의 전역 `모두 펼쳐 보기` 대신 branch별 TASK 수와 `이 branch 펼치기`를 제공한다. 검색 결과는 일치 항목의 조상 branch만 자동으로 펼치고 제목을 강조한다.
- TASK 표의 기본 읽기 열은 제목·상위 경로·상태·담당자·마감·차단으로 줄인다. 시작·종료와 빠른 편집은 행 확장 또는 명시적 편집 상태에서 보여 준다.
- 공통 조건인 검색·상태·담당자·기간·scope와, 보기별 표현 조건인 유형·정렬·그룹을 분리한다.
- 상단 context strip에 `현재 범위`, `현재 보기에서 표시되는 유형`, `표시 건수 / 전체 건수`를 항상 보여 준다. 다른 보기에 유효하지 않은 유형은 건수에 포함하지 않는다.
- 보기 이동 후 해당 보기의 마지막 정렬·그룹·branch 접힘을 복원하되, `초기화`는 현재 보기 설정과 공통 조건을 구분해서 제공한다.

### 우선순위 2: 맥락을 유지하는 반복 생성

- scope 안에서 `이 TOPIC에 TASK 추가`를 제공하고, 저장 뒤 `저장 후 다음 TASK`를 선택할 수 있게 한다. parent·kind·assignee는 유지하고 제목은 비우며 날짜는 유지 여부를 명시적으로 선택한다.

### 우선순위 3: Milestone 역할과 연결 작업

- Milestone에는 TASK progress bar를 쓰지 않는다. 대신 날짜 완전성, 연결된 작업 수, 연결 없음 상태를 직접 표시한다.
- 관계는 `Milestone 달성 조건 작업`과 `Milestone 이후 시작 가능 작업`을 별도 명칭과 control로 구분한다. 현재 `successorIds`만 보고 전자를 표시하지 않는다.

## State and accessibility coverage

- loading은 기존 데이터를 유지한 갱신 상태와 최초 loading을 구분한다. filtered empty에는 적용 조건과 `필터 초기화`, project empty에는 역할별 첫 생성 또는 읽기 전용 설명을 제공한다.
- VIEWER에게 disabled input을 대량 노출하지 않고 읽기용 값으로 표시한다. 권한이 바뀌면 기존 403 복구 흐름과 같은 status 메시지를 제공한다.
- 검색으로 자동 확장된 branch와 결과 수를 `aria-live=polite`로 알린다. expand button의 accessible name에는 항목 제목과 하위 수를 포함한다.
- table 축소 후에도 header 관계를 유지하고, 행 확장·생성·저장 후 focus를 원래 행 또는 새 항목으로 되돌린다. 모든 핵심 동작은 keyboard만으로 순서대로 접근할 수 있어야 한다.
- 색상만으로 kind·state·관계를 구분하지 않는다. Milestone 날짜 불완전과 연결 없음은 text label로 병기한다.

## Decisions and acceptance criteria

- 최소 1차 증분은 우선순위 1만 포함한다. 데이터 모델을 바꾸지 않고 기본 밀도, 보기별 유효 유형·건수, scope·그룹 복원, 검색 시 branch 확장을 고친다.
- 74개 fixture의 기본 Roadmap에서 상위 구조와 Milestone 7개 이하가 먼저 보이고, TASK는 scope 또는 명시적 유형 선택 전에는 행으로 펼쳐지지 않는다.
- 확장 fixture에서 계층의 전체 유형을 본 뒤 작업 보기로 이동해도 `TASK 66개`처럼 현재 보기에 실제 표시 가능한 건수만 제시하며, EPIC·TOPIC·Milestone 미표시 경고를 만들지 않는다.
- 제목 검색 한 번으로 일치 TASK, 전체 상위 경로, 현재 scope를 확인할 수 있고, 검색을 지우면 사용자가 직접 접은 branch 상태로 복원된다.
- TASK 기본 표에는 동일한 상태·담당자 정보가 한 번씩만 보인다. 날짜 편집을 열기 전에도 마감과 차단 여부를 비교할 수 있다.
- 390px와 200% reflow에서 핵심 찾기·scope 이동·branch 확장에 수평 scroll이 필요하지 않고, pointer target은 44px 이상이다.
- 후속 증분은 `저장 후 다음 TASK`로 같은 TOPIC 아래 TASK 3개를 dialog 재호출 없이 만들고, 생성 중 parent·kind·assignee와 focus가 유지되는지 측정한다.
- Milestone 관계 후속 검증은 incoming prerequisite와 outgoing gated TASK를 서로 다른 목록으로 표시하고, 연결 없음·부분 날짜·권한 없음 상태를 fixture로 확인한다.
- 실행하지 않은 검사: production API, 실제 사용자 task timing, screen reader, contrast, responsive browser matrix, 테스트, 배포. 부모 검토로 이번 UX 방향을 확정하며, 구현은 별도 후속 작업으로 둔다.

## Archive

- Local source path: `D:\Project\Ai ERP\docs\ux\2026-09-28-plan-density-ux.md`
- Evidence path: `D:\Project\Ai ERP\.tmp\evidence\plan-density-ux-20260928\`
- Notion archive: 수행하지 않았다. External dependency mode는 `offline-contract-only`다.
