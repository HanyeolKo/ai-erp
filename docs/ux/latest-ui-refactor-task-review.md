# 최신 UI·시각 하네스 구현 독립 리뷰

- 작업: `ui-workspace-refactor`; 검토일 2026-09-11; 평가 `task-review`; 위험 등급 `high`.
- 검토자: `/root/latest_review`, Astra/high, 독립 reviewer. 제품·하네스·검사 소스는 읽기 전용으로 검토했다. usage/cache `null`. 상위 `/root`가 수용·발행·병합을 소유한다.
- 기준: `058f7827393f76715d80706bf383ccac6ae23ee6`; 승인 계획 `ERP-WORKSPACE-01/v2`; 구현 `latest-css-r1`; 미리보기 `preview-r2`.
- 범위: 세 product CSS, additive visual harness, `scripts/ui-preview-server.mjs`. 계획/계약/결과는 `docs/ux/latest-ui-refactor-*`, `latest-harness-port-result.md`, `latest-ui-preview-{contract,result}.md`.
- 최종 판정: **pass**. 확인된 안정 결함 3개는 모두 해결됐고, 미해결 안정 결함은 **0**이다. 이 판정은 아래 식별된 소스 및 명시적 offline 검증 범위에 한정된다.

| 기준 | 현재 판정 | 근거 |
| --- | --- | --- |
| Parent contract | pass | latest-css-r1의 정확한 세 CSS, 번호별 기준, v2 채택 및 최종 READY. assignment의 additive harness 범위와 preview-r2의 script 승격 승인. |
| Assignment linkage | pass | 상위 배정·결과가 기준 commit, 각 worker 소유 경로, Luna/high 구현, 제한 문맥·반환·발행 금지를 연결한다. |
| Role boundaries | pass | 기능 계획→generic 시각 계획→독립 UI 리뷰→상위 READY→구현. 구현자는 독립 verdict를 소유하지 않는다. generic 시각 문서 쓰기 차이는 계획 리뷰/상위 채택에 기록됐으며 native 런타임 증명으로 사용하지 않는다. |
| Model invocation | pass | 상위의 실제 Luna/high CSS·하네스·preview 배정 및 반환 기록. 검토자는 Astra/high. 권한 상승을 통한 EPERM 재실행은 모델 capability escalation이 아니다. |
| Scope safety | pass | `git diff HEAD`에서 제품 변경은 `app.css`, `schedules.css`, `GoogleWorkspace.css`뿐이다. 나머지 소스/설정은 승인된 하네스·검증기 및 loopback fixture 범위다. TSX/API/backend/session/dependency 변경 없음. |
| Method consistency | pass | 기존 selector와 공유 토큰을 정규화했다. 16px root, 15px body, 기존 셸·모바일 표시 조건·7열·260px 데이터 기준을 유지한다. 하네스의 현재 모델/위험/수명주기 규칙을 덮어쓰지 않았다. |
| Verification | pass | CSS test/typecheck/build 로그 exit 0, 15 files/232 tests. 하네스 결과의 verify 0, 92 tests OK, UX smoke 0 및 상위 require-tracked 통과 기록. preview syntax/loopback smoke 근거. 계정 보정 후 build 0, focus 보정 후 diff-check 0과 실제 브라우저 재검사를 확인했다. 유효 전체 검사를 반복하지 않았다. |
| UI gate compliance | pass | `latest-ui-refactor-ui-review.md`의 패턴/화면 단계 pass와 상위 채택 후 구현 READY. |
| Visual UI evidence | pass | 전후 viewport 이미지/컨트롤 비교, 104개 추가 reflow 표본, 합성 시나리오, 실제 키 입력과 공유 Dialog·동일 행동 근거 및 세 후속 보정 재검사를 확인했다. native 브라우저 초점 경계와 실제 zoom 미검증은 아래 한계에 명시한다. |
| Acceptance mapping | pass | 1: 세 CSS product diff. 2: 채택 v2/전체 화면 매핑과 소스·표본 이미지. 3: 18/19 비교 일치 및 members 수집 시점 차이 설명, TSX 무변경·동일 행동/모바일 검사. 4: reflow 행렬과 긴 계정 보정. 5: root/day/inline/hour 근거. 6: 11개 대비 조합·native keyboard·focus 보정. 7: 테스트/typecheck/build. 8: 이 독립 high 리뷰 pass. |
| Readiness | pass | 구현 결과 `ready-for-review`, 검토 verdict/상위 수용 역할 구분. 이 단계는 release 승인이나 배포 완료가 아니다. |

## 독립적으로 확인한 근거

하네스 diff는 시각 역할/스킬/두 시각 템플릿과 기능 계획 뒤의 리뷰 경로를 추가한다. native wrapper는 Sol/medium·`sandbox_mode="read-only"`, canonical 역할은 어떤 파일도 쓰지 않도록 명시한다. verifier 회귀는 write-access drift, 금지된 구현 shortcut, 기능 gate 약화, non-visual 직접 리뷰 제한, 필수 템플릿 누락을 검사한다. `harness/policies`, `workflows`, `loops`, `state` 및 기존 구현자·리뷰어·기능 디자이너 wrapper에는 diff가 없다. 실제 새 native 역할 발견과 런타임 파일 쓰기 차단은 미검증이다.

기존 JSON을 Python으로 읽어 재계산했다: `control-comparison.json` 38개 중 36개 동일, 각 폭에서 18/19 route 동일이다. members만 닫힌 share dialog의 비동기 404 retry DOM 수집 시점이 달랐다. 19개 전체 DOM 동일로 해석하지 않는다. `reflow-matrix.json` 76개와 `google-populated-reflow.json` 28개 모두 document/main overflow 1px 초과가 없다. `contrast.json`의 11개 선택 색 조합이 기준을 만족한다. 이는 모든 상태의 전수 대비 인증은 아니다.

`before-week-geometry.json`/`after-week-geometry.json`에서 7개의 260px·relative day canvas 기록과 두 event inline style 문자열이 같고 root는 모두 16px다. 이벤트 높이 64.7812→68.3125px는 허용된 줄바꿈/타이포그래피 외곽 변화로 실패 처리하지 않는다. 별도 hour-label JSON은 없지만 TSX의 시간 눈금 공식은 diff가 없고 기준 canvas가 동일하다.

제공된 dashboard desktop, create 320px, Gmail review 320px, members mobile 스크린샷을 직접 열어 공통 표면·행동 배치·긴 메일 제목/본문 줄바꿈을 확인했다. 브라우저 실행 자체는 상위가 수행했다. preview script는 고정 `127.0.0.1`, 환경변수 시나리오, 메모리 상태 및 `node:http` 서버이며 외부 호출이나 디스크 저장이 없다. fixture는 실서비스 계정·OAuth·메일 발송·권한의 성공 근거가 아니다.

## 패치 식별과 남은 검토

검토 소스 31개 경로의 LF 정규화 SHA-256을 `latest-ui-evidence/review-content-manifest.json`에 저장했다. 최초 manifest SHA-256은 `7be6d357074cc1175653eeffc47e1839191bd83cdd30a5b5f9ddba74513ff90c`였다. 계정·초점 보정의 두 CSS 파일만 변경됨을 확인하고 재검토해 최종 manifest를 갱신했다: **`fcb7dd59406fd6cd96c3aa47336a9574b7e433195ffe52651fc37c842a762d53`**. 나머지 29개 파일은 최초 리뷰 내용과 동일하다. 최종 commit의 해당 31개 파일을 이 내용과 결합해야 한다.

- `MOBILE-TARGET-001` — resolved: `.text-button` 수정과 `mobile-target-fix.json`에서 320/375/390/720px의 높이 44px, overflow 0을 확인했다.
- `ACCOUNT-REFLOW-001` — resolved, P2: `long-account-before.json`의 320px 조건에서 document scrollWidth 553px, summary 폭 420px/right 552.796875px가 재현됐다. 모바일 기존 actions/menu shrink·wrap·panel 폭 제약 수정과 `long-account-after.json`에서 summary 높이 72px, document 320px, 열린 panel left24/right304/폭280px, email 높이56.59375px를 확인했다. 문구·표시 조건·DOM 변경은 없다.
- `interaction-results.json`에서 Drive 첨부, Gmail 긴 본문/review/SENT, Calendar bind/unbind/Escape 호출점 복귀, join code preview 근거를 읽었다. 두 calendar tab-wrap 기록은 active가 전체 body 텍스트이고 focus outline이 none이므로 trap/visible-focus 통과 근거로 쓰지 않는다. 실제 처음/마지막 대화상자 control을 경계 이동한 뒤 active tag/name·dialog 내부 여부·focus ring을 기록하도록 상위에 반환했다. 아직 제품 회귀 결함으로 단정하지 않는다.
- 보완 `dialog-keyboard-native.json`은 실제 키 입력으로 닫기→input→취소→초대 확인과 역방향 복귀를 기록한다. 내부 control은 modal true·inDialog true·3px amber outline이며, 경계에서 BODY가 한 단계 관찰된 뒤 대화상자 첫/마지막 control로 돌아온다. 배경 앱 control로 이동한 기록은 없다. 이는 유지된 native `showModal`/브라우저 초점 경계의 제한 사항으로 기록한다. 모든 Tab이 DOM 내부에 머무는 별도 JavaScript trap 성공을 주장하지 않으며, CSS 작업을 기존 JS 초점 로직 변경으로 확대하지 않는다.
- `FOCUS-CLIP-001` — resolved: `week-focus.json`의 event overflow hidden·내부 여백4px에서 3px outline/2px offset의 위·오른쪽 가장자리 1px가 잘렸다. V2-FOCUS 비가림 기준에 맞춰 `.week-event` overflow visible로 수정됐다. `week-focus-after.json`과 PNG를 직접 확인해 3px/2px ring, 두 링크 중심 hitHref가 각각 s1/s2인 점, root16·7개 day260·inline 좌표 보존을 확인했다. 기존 `week-focus.json`의 28개 시간 눈금은 0/25/50/75% 및 0/65/130/195px다.
- 최종 `latest-ui-refactor-report.md`, 7종 `scenario-*.txt` 및 별도 `scenario-mail-unknown.txt`를 읽어 empty/VIEWER/login/unconfigured/error/Google partial/UNKNOWN 상태와 현재 회복 행동 표시를 확인했다. UNKNOWN fixture의 첫 화면 자체와 실제 UNKNOWN receipt 화면을 구분했다. 상위가 수집한 Google 동작은 전부 합성 loopback 근거다.

## 판정 범위와 다음 단계

전후 캡처는 viewport 이미지이며 전체 페이지 이미지가 아니다. 720 CSS px 검사는 1440px의 200%에 대응하는 reflow 근거이고 실제 브라우저 zoom 조작 인증은 아니다. 모든 route/state/색 조합의 전수 접근성 인증을 주장하지 않는다. 기존 native Dialog 경계 초점과 동일 TSX 의미를 보존한 이번 CSS 작업에 별도 JS trap 도입을 요구하지 않는다. 실제 Google/OAuth, native 역할 발견·런타임 무쓰기, 프로덕션 배포는 검증 범위 밖이다.

후속 correction 이외에는 유효한 전체 suite를 반복하지 않았고, reviewer의 최종 `git diff --check`도 exit 0이었다. 필요한 기존 검사와 실제 로컬 브라우저 증거는 이 변경의 수용 기준을 충족한다. 상위는 구현을 수용하고 사용자 승인 범위 내 PR 준비로 진행할 수 있다. 병합 전에는 최종 소스 manifest와 commit의 일치, 동일 SHA CI, 독립 `release-review`가 별도로 필요하다. 이 task-review는 release readiness나 배포 완료를 대신하지 않는다.

로컬 원본: `D:/onedrive/Documents/ChatGPT/AI ERP/tmp/ui-workspace-refactor/docs/ux/latest-ui-refactor-task-review.md`. 중간 리뷰 문서의 별도 Notion 페이지는 만들지 않았으며 상위가 최종 독자 문서를 기존 읽기 큐 정책에 따라 보관한다.
