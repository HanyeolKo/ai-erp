# 최신 UI 리팩터링 계획 독립 리뷰

- 검토일: 2026-09-11. 작업: `ui-workspace-refactor`. 평가: `ui-plan-review`.
- 검토자: `/root/latest_review`, 독립 `reviewer`, Astra/high. 제품 소스 읽기 전용; 이 판정 문서만 작성했다. 새 제한 문맥, 출력 예산 2000 tokens 이하, usage/cache `null`.
- 기준 HEAD: `058f7827393f76715d80706bf383ccac6ae23ee6`. 입력: `latest-ui-refactor-assignment.md` (`latest-r1`), functional plan, visual contract (`ERP-WORKSPACE-01/v2-candidate`), implementation contract (`latest-r2`/`latest-css-r1`). 문서 접두사는 모두 `docs/ux/`이다.
- 실제 handoff: 상위 배정 기록과 functional plan의 `/root/latest_ui_plan` Sol/medium 수행, 이후 상위가 확인한 `/root/latest_visual_plan` Sol/medium 시각 handoff를 근거로 한다. native wrapper 실행을 주장하지 않는다. 최종 채택·구현 배정·병합은 `/root` 소유다.
- 실행 차이: 시각 전문가는 generic default Sol/medium으로 배정되어 자신의 계획 문서와 functional 문서의 사실 정정만 쓰도록 상위가 허용했다. 이는 새 native visual wrapper의 읽기 전용·파일 쓰기 금지와 다른 문서 한정 실행이며, 제품 편집은 없었다. 상위가 정식 문서 저장 책임을 회수했으며 향후 native 호출은 응답만 반환한다. 이번 generic 호출은 native 무쓰기 강제나 새 런타임 역할 발견의 검증 근거가 아니다.

| 기준 | 판정 | 근거 |
| --- | --- | --- |
| Specialist routing | pass | assignment 및 functional plan의 실제 역할/상위 dispatch 기록. 현재 소스 기반 기능 계획을 먼저 작성했다. |
| Visual specialist routing | pass | 상위 배정의 후속 시각 handoff와 visual contract의 functional handoff 참조. 제품 소스 변경 없이 후보 규칙을 제안했다. |
| User and task | pass | functional plan의 요청/범위: 로그인한 프로젝트 구성원, 기존 hash route/직접 링크, 표현 일관성 목표. 동작·권한·문구 변경 제외. |
| Workflow | pass | 기능 인벤토리 및 불변 조건이 프로젝트·계정·초대·Google 업무의 기존 순서, 권한, 복구, 검토/확정 단계를 보존한다. `App.tsx` route와 `ui.tsx` Shell/ Dialog를 대조했다. |
| Screen contract | pass | visual contract의 전체 route/dialog 매핑 및 V2-SHELL/HEADER/SURFACE/CONTROL/RESP/DATA. 세 CSS 파일만 허용하며 TSX 확장 후보는 구현 계약에서 제외했다. |
| State coverage | pass | functional 공통 상태와 visual V2-STATE/필수 fixture가 initial/loading/empty/partial/error/success/disabled/permission/unknown을 다룬다. 실제 Google 성공 검증은 명시적으로 범위 밖이다. |
| Accessibility | pass | V2-FOCUS/TYPE/CONTROL/RESP, 기존 이름·순서·ARIA·Dialog·skip link 보존 및 실제 대비/키보드 검증 계획. 런타임 접근성 통과를 주장하지 않는다. |
| Design rationale | pass | functional 패턴/기존 제약과 visual 원본 경로: 현재 topbar/sidebar를 유지하고 이전 rail 규칙을 제외하며, 고정된 로컬 글꼴·토큰·의미 단위 표면을 적용한다. |
| Shared visual contract — pattern stage | pass | 명시적 후보 ID/version, V2 규칙, 원본, 기능·데이터 좌표 불변 조건, E1–E5 예외가 있다. 이 단계의 승인된 화면 매핑은 not-applicable: 상위 후보 채택 전이다. 기재된 route 표는 후속 채택을 위한 제안으로 검토했다. |
| Validation | pass | implementation contract의 8개 관찰 기준 및 visual 검증 계획: 세 CSS diff, 전체 기존 테스트/typecheck/build, 폭별 reflow, 상태·Dialog·동일 행동·좌표 비교. 미실행 검사가 구분되어 있다. |
| Ownership | pass | 각 계획 문서의 로컬 원본과 상위 Notion 동기화 책임 명시. 기존 같은 맥락의 `안읽음` 페이지 확인 후 통합 보관한다. |

패턴 단계 전체 판정은 **pass**, 안정 결함 수는 **0**이다. 이는 후보 패턴의 계획 통과이며 구현 결과나 배포 판정이 아니다. 상위가 `ERP-WORKSPACE-01/v2`를 채택하고 동일 화면 매핑을 승인한 기록을 추가하면, shared visual contract의 화면 단계만 제한적으로 재검토한다. 그 전에는 구현 준비 완료로 간주하지 않는다.

## 상위 채택 후 화면 단계 종결

2026-09-11 visual contract의 `Parent adoption`과 implementation contract 말미를 실제 읽어 `/root`의 `ERP-WORKSPACE-01/v2` 및 동일 화면 매핑 채택을 확인했다. 변경된 이 기준만 재검토했으며 위 나머지 판정은 재사용한다.

| 기준 | 판정 | 근거 |
| --- | --- | --- |
| Shared visual contract — screen stage | pass | 채택된 v2와 전체 route/dialog별 V2 규칙 연결, E1–E5 예외, 기능·260px 기준 및 inline 데이터 좌표 보존, 화면/반응형/키보드/동일 행동 검증 계획이 일치한다. 실제 구현 증거는 후속 task-review 대상이다. |

최종 `ui-plan-review`는 **pass**, 안정 결함 **0**이다. 상위는 승인된 세 CSS 구현 계약을 배정할 수 있다. generic 시각 역할의 문서 쓰기 차이는 위 기록과 상위 채택에 명시되었고, native 런타임 무쓰기 보장이나 발견 성공으로 간주하지 않는다. 상위 승인 없이 범위를 넓히지 않으며, 이 pass는 후속 구현·harness·release 판정을 대체하지 않는다.

실제 확인은 `Get-Content`/`rg`로 위 계약·평가 규칙·세 CSS·관련 TSX를 읽고, `git rev-parse HEAD`와 `git diff --stat -- frontend/src`로 기준 revision 및 제품 변경 없음(빈 diff)을 확인한 것이다. `App.tsx`가 screen import 후 `app.css`를 가져오며, `app.css`의 `.day-events` 260px와 schedules.css의 280px 중 baseline 유효 값은 제공된 `latest-ui-evidence/before-week-geometry.json`의 7개 260px 기록과 일치한다. `Schedules.tsx:159`의 inline `top/minHeight/left/width`, 시간 눈금 `top`, 7열 및 모바일 agenda 보존 요구가 구체적이다. 본 검토자는 브라우저를 실행하지 않았다.

후속 구현 검토에는 실제 세 CSS diff, 기존 테스트/typecheck/build 결과, 모든 매핑 화면과 필수 상태의 로컬 합성 fixture 근거, 1440/768/390/375/320 및 200% reflow, 키보드/초점/대비, 일정 좌표 전후 비교가 필요하다. 전체 외곽 bounding box 차이는 허용된 타이포그래피·간격 변경만으로 실패시키지 않는다. 실제 Google 연동·프로덕션 배포·후속 harness 검증·release-review는 이번 계획 판정에서 실행하거나 인증하지 않았다.

로컬 원본: `D:/onedrive/Documents/ChatGPT/AI ERP/tmp/ui-workspace-refactor/docs/ux/latest-ui-refactor-ui-review.md`. 작업 중 리뷰 근거이며 Notion 외부 쓰기는 하지 않았다. 상위가 최종 독자 문서에 반영하고 읽기 상태를 검증한 뒤 동기화한다.
