# 일정 보기 프런트엔드 최종 구현 결과

- 실행 역할: `implementer` (`gpt-5.6-sol`, medium), 모델 에스컬레이션 attempt 3
- 승인 계약: implementation contract r1, API contract r2
- 작업 범위: `frontend/src/**`, 관련 프런트엔드 테스트/fixture, `scripts/ui-preview-server.mjs`
- 결과: 구현과 로컬 검증 완료. 독립 리뷰 판정은 상위 오케스트레이터가 소유한다.

## 구현 결과

- 캘린더, 카드, 목록 기본 보기와 PERSONAL/SHARED 저장 보기, 타입별 필터·정렬·그룹·범례·표시 필드를 연결했다.
- TEXT, NUMBER, CHECKBOX, DATE, SINGLE_SELECT 속성의 null 값, 보관 속성/옵션, 안정적인 옵션 ID와 행 버전을 처리했다.
- 일정 본문과 사용자 정의 값을 하나의 `/schedule-workspace/records` 요청으로 생성·수정하며, 작업공간 메타데이터가 준비되기 전에는 편집기를 노출하지 않는다.
- Calendar 기간을 100건 단위로 최대 1,000건까지 읽고, 중간 페이지 실패 시 이미 읽은 일정과 명시적 부분 결과 경고를 유지한다.
- 보기/날짜/월·주/페이지 hash 문맥을 Back/Forward와 상세·수정·생성 왕복에서 보존한다. 제거되거나 보관된 보기는 기본 캘린더로 복구하고 이를 알린다.
- 403/404 뒤 쓰기 기능을 잠그며, 성공한 최신 프로젝트/작업공간 읽기 뒤에만 다시 연다. 409에서는 초안을 유지하고 최신 보기 재읽기 또는 개인 초안 복제를 제공한다.
- 속성/옵션/보기의 미저장 변경과 보기 보관에 앱 `Dialog`를 사용한다. 닫을 때 호출 버튼으로 초점을 복원하고 모바일 너비에서 가로 넘침을 막았다.
- 대시보드 저장 보기, 페이지 이동, 동일 일정 ID의 인라인 사용자 정의 값 수정, 카드/상세 간 일관성을 구현했다.
- preview fixture에 보기, 속성, 옵션, 대시보드, query, atomic record/value API와 충돌/권한 동작을 추가했다.
- 검증된 backend OpenAPI 산출물로 `frontend/src/api/generated.ts`와 Swagger 산출물을 재생성했다.

## 검증 증거

- `pnpm api:generate`: PASS — OpenAPI 3.0.1 검증 및 클라이언트 생성
- `pnpm typecheck`: PASS
- `pnpm test -- --reporter=dot --maxWorkers=4`: PASS — 20 files, 267 tests
- `pnpm build`: PASS — 80 modules, production bundle 생성
- `node --check scripts/ui-preview-server.mjs`: PASS
- 집중 회귀: 작업공간 보기/옵션/문맥/초점 6 tests PASS, Calendar 직접 조작 7 tests PASS
- 브라우저 확인: SHARED 보기 저장·재로드, 그룹/범례, 카드/상세 return 문맥, 대시보드 인라인 수정, 옵션 연속 2회 저장, dirty-close 초안 유지, Escape 뒤 `속성` 버튼 초점 복원, Calendar grid/list/create 링크의 동일 문맥과 상세 왕복, 320/390px 가로 넘침 없음 확인.

## 주요 파일

- `frontend/src/screens/ScheduleWorkspace.tsx`
- `frontend/src/screens/Schedules.tsx`
- `frontend/src/screens/ScheduleForm.tsx`
- `frontend/src/screens/Detail.tsx`
- `frontend/src/screens/schedule-workspace.css`
- `frontend/src/api/client.ts`
- `frontend/src/api/generated.ts`
- `frontend/src/test/http.ts`
- `frontend/src/schedule-workspace.test.tsx`
- `scripts/ui-preview-server.mjs`
