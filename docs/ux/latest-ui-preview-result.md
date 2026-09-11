# 최신 UI 미리보기 fixture 결과

## 범위

- 원본 fixture를 확장해 재현 가능한 `scripts/ui-preview-server.mjs`로 반영했다.
- 모든 응답은 가상 데이터이며 메모리에만 저장된다. 서버는 `127.0.0.1`에만 바인딩하고 Google/OAuth/Gmail 네트워크 호출, 자격 증명, 실제 발송, 디스크 저장을 사용하지 않는다.
- 기본값은 `UI_PREVIEW_SCENARIO=populated`, `UI_PREVIEW_PORT=8081`이다. `UI_PREVIEW_DISPLAY_NAME`과 `UI_PREVIEW_EMAIL`로 합성 `me`·Google 연결 계정 라벨만 선택적으로 바꿀 수 있으며, 미설정 시 `김관리자`/`manager@example.com`을 유지한다. 포트·시나리오·라벨은 환경변수로만 전환한다.

## endpoint coverage

기존 configuration, csrf, me, projects, dashboard, schedules(s1/s2), members, invitations, notifications, Calendar connection/projection 경로를 유지했다. 최신 화면 계약 경로도 제공한다.

- Project: `GET /projects/creation-options`, `POST /projects`, share invitation GET/POST/DELETE, project invitation preview/join
- Google: connection/connect/disconnect, Drive files, Gmail messages/detail/send/send receipt, Google calendars
- Project integration: project files GET/POST/DELETE, project calendar GET/POST/DELETE
- 상태 전이는 메모리에서만 수행한다. 파일 첨부·제거, Calendar bind/unbind, share rotate/revoke를 재조회할 수 있다.

## scenario switches

`populated`(기본 성공 데이터), `empty`(프로젝트/일정/참조 파일 없음), `viewer`(VIEWER 권한), `login`(me 401), `unconfigured`(Google 설정 필요), `error`(API 503), `google-partial`(Drive CONNECTED/Gmail PERMISSION_REQUIRED/Calendar REAUTH_REQUIRED), `unknown`(프로젝트 첫 생성 500 RESULT_UNKNOWN, Gmail POST UNKNOWN → receipt 재조회 후 SENT) 를 지원한다. s1/s2 기본 내용은 기존 fixture 값을 보존했다.

## verification evidence

- `node --check scripts/ui-preview-server.mjs` — cwd `D:\onedrive\Documents\ChatGPT\AI ERP\tmp\ui-workspace-refactor` — exit 0.
- populated loopback smoke on `127.0.0.1:8081` — configuration/me/projects/creation-options/share/google connection/Drive/Gmail/calendars/project files/project calendar GET 200; file attach와 Calendar bind POST 200; Gmail fake SENT와 receipt 조회 200; process 종료 후 포트 해제.
- unknown loopback smoke on `127.0.0.1:8082` with `UI_PREVIEW_SCENARIO=unknown` — project create first 500 then same requestId 200; Gmail POST UNKNOWN, first receipt UNKNOWN, second receipt SENT; exit 0.
- google-partial loopback smoke on `127.0.0.1:8083` — connection 200 with mixed statuses, Drive 200, Gmail/Calendar access 403; exit 0.

실제 Google 계정, OAuth callback, 외부 메일 발송, 운영 readiness는 이 offline fixture로 검증하지 않는다. 브라우저 화면 확인은 부모 작업에서 수행한다.
