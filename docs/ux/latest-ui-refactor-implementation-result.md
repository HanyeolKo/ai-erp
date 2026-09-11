# latest-ui-refactor 구현 결과

- 작업: `ui-workspace-refactor` / `latest-css-r1`
- 기준: `058f782`
- 구현 모델: 상위 dispatch가 지정한 `gpt-5.6-luna` / `high`
- 상태: `ready-for-review`
- 범위: `frontend/src/app.css`, `frontend/src/screens/schedules.css`, `frontend/src/screens/GoogleWorkspace.css`와 본 결과·증거 문서만 변경

## 구현 내용

세 CSS 파일의 기존 selector owner를 정리해 `ERP-WORKSPACE-01/v2` 토큰, Segoe UI 계열 로컬 글꼴, 16px root와 15px 본문, 의미 단위 white surface, 40px desktop/44px mobile control, visible focus ring, 상태·disabled 색, 긴 문자열 줄바꿈을 공통 규칙으로 통일했다. 현재 topbar/sidebar/mobile toggle DOM·표시 조건·순서와 action/content order는 건드리지 않았다.

Schedules는 `--schedule-*`를 root token에 alias하고 기존 7열 month/week, `<768px` agenda 전환, 필터 breakpoint(3/2/1열), inline event coordinate와 `.day-events { position: relative; height: 260px; }` desktop canvas를 보존했다. Google 화면은 account/service/binding/picker, Drive/Gmail rows, selection, compose/review/detail을 동일 surface/control/state 체계로 맞추고 mobile query를 `max-width: 767px`로 정렬했다.

부모 브라우저 행렬에서 narrow Projects의 `.text-button`만 40px로 남는 `MOBILE-TARGET-001`을 확인해 mobile control selector에 `.text-button`을 포함하는 단일 CSS 수정으로 해결했다. 이 수정은 동작·DOM·문구를 바꾸지 않는다.

## 수용 기준과 증거

| 기준 | 결과 | 증거 |
| --- | --- | --- |
| 정확히 세 product CSS 파일, source behavior diff 없음 | 통과 | `git diff --name-only`; TSX/API/backend/tests/config/dependencies diff 없음 |
| 공통 ERP-WORKSPACE-01/v2 token/surface/control/state | 통과 | 세 CSS의 `:root` token과 기존 selector owner 정규화 |
| shell/mobile visibility와 action order 보존 | 통과 | 부모 `control-comparison.json`; 19 route before/after mapping |
| 1440/768/390/375/320 및 200% 일반 overflow | 통과 | 부모 `reflow-matrix.json`의 `mainOverflow: 0`; after browser matrix |
| root 16px, desktop day canvas 260px, event data geometry | 통과 | 부모 `after-week-geometry.json`: root `16px`, days `260px`, `position: relative`, inline top/minHeight/left/width 보존 |
| contrast와 focus palette | 통과 | 부모 `contrast.json`; 정의된 실제 조합 모두 pass |
| frontend tests/typecheck/build | 통과 | [latest-css-test-escalated.txt](D:/onedrive/Documents/ChatGPT/AI%20ERP/tmp/ui-workspace-refactor/docs/ux/latest-ui-evidence/latest-css-test-escalated.txt), [latest-css-typecheck.txt](D:/onedrive/Documents/ChatGPT/AI%20ERP/tmp/ui-workspace-refactor/docs/ux/latest-ui-evidence/latest-css-typecheck.txt), [latest-css-build-escalated.txt](D:/onedrive/Documents/ChatGPT/AI%20ERP/tmp/ui-workspace-refactor/docs/ux/latest-ui-evidence/latest-css-build-escalated.txt) |
| independent task review | 대기 | 상위 reviewer가 최종 diff와 부모 browser evidence를 검토해야 함 |

## 실행 명령과 결과

- `pnpm --dir frontend test` — sandbox 실행은 `spawn EPERM`으로 exit `1` ([초기 기록](D:/onedrive/Documents/ChatGPT/AI%20ERP/tmp/ui-workspace-refactor/docs/ux/latest-ui-evidence/latest-css-test.txt)); 동일 명령 `require_escalated` 재실행은 exit `0`, `15` files / `232` tests passed.
- `pnpm --dir frontend typecheck` — exit `0` ([기록](D:/onedrive/Documents/ChatGPT/AI%20ERP/tmp/ui-workspace-refactor/docs/ux/latest-ui-evidence/latest-css-typecheck.txt)). 최종 mobile target 수정 후에도 escalated build의 `tsc -b`가 통과했다.
- `pnpm --dir frontend build` — sandbox 실행은 `spawn EPERM` exit `1` ([초기 기록](D:/onedrive/Documents/ChatGPT/AI%20ERP/tmp/ui-workspace-refactor/docs/ux/latest-ui-evidence/latest-css-build.txt)); 동일 명령 `require_escalated` 재실행은 exit `0`, Vite `77 modules transformed` ([성공 기록](D:/onedrive/Documents/ChatGPT/AI%20ERP/tmp/ui-workspace-refactor/docs/ux/latest-ui-evidence/latest-css-build-escalated.txt)).
- `git diff --check` — focused final rerun exit `0` ([기록](D:/onedrive/Documents/ChatGPT/AI%20ERP/tmp/ui-workspace-refactor/docs/ux/latest-ui-evidence/latest-css-diff-check-fix.txt)). Git의 LF→CRLF warning만 출력됐다.

브라우저·screenshot·keyboard 확인은 부모가 수행했으며, 구현자는 verdict를 소유하지 않는다. Google 실서비스 연동은 계약대로 인증하거나 변경하지 않았고, 증거는 loopback synthetic fixture 범위다.

## 후속 보정

부모 장문 계정 fixture에서 320px `document.scrollWidth`가 553px로 커지는 `ACCOUNT-REFLOW-001`을 확인했다. 기존 `.account-menu`의 `white-space: nowrap`을 모바일에서만 해제하고, 기존 `.topbar-actions`·`.account-menu`에 `min-width: 0`/shrink를 허용했으며, summary를 44px focus/control target과 anywhere wrapping으로 정리하고 overlay 폭을 viewport 안으로 제한했다. DOM, account menu, mobile shell 조건과 동작은 유지했다.

후속 확인: [latest-css-build-account-reflow.txt](D:/onedrive/Documents/ChatGPT/AI%20ERP/tmp/ui-workspace-refactor/docs/ux/latest-ui-evidence/latest-css-build-account-reflow.txt) exit `0`, [latest-css-diff-check-account-reflow.txt](D:/onedrive/Documents/ChatGPT/AI%20ERP/tmp/ui-workspace-refactor/docs/ux/latest-ui-evidence/latest-css-diff-check-account-reflow.txt) exit `0`. 부모가 동일 long-account fixture의 focused browser recheck를 수행한다.

추가 보정: `week-event`의 기존 `overflow: hidden`이 3px focus ring과 2px offset을 안쪽 4px padding에서 잘라내는 `FOCUS-CLIP-001`을 확인해 `overflow: visible`로 변경했다. 일정 inline 좌표, day canvas 260px, intrinsic content/wrapping과 click target은 유지한다. [latest-css-diff-check-focus-clip.txt](D:/onedrive/Documents/ChatGPT/AI%20ERP/tmp/ui-workspace-refactor/docs/ux/latest-ui-evidence/latest-css-diff-check-focus-clip.txt) exit `0`; 부모가 focus ring과 hit target을 재확인한다.
