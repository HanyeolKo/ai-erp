# 통합 프로젝트 UX 프런트엔드 보정 r6 실행 결과

- 실행 역할: canonical implementer, Sol/medium, actual attempt 8 bounded correction
- 기준 소스: `bd32ea71aefd0f3d5ea748eabbeb850dee530a10` + 미커밋 r4/r5 source freeze
- 작업 위치: `D:\onedrive\Documents\ChatGPT\AI ERP\tmp\unified-project-ux-resume`
- 모델 사용량: `null` (제공되지 않음)
- 상태: r4/r5 보존 포함 최종 source freeze 완료, 부모 native browser·동일 SHA CI·독립 리뷰 대기

## 반영 내용과 기준 대응

1. **AC1 — modal Escape 우선순위**
   - `Shell`의 mobile menu 전역 `keydown` listener가 `dialog[open]`을 발견하면 Escape를 처리하지 않고 반환하도록 한 줄을 추가했다.
   - modal이 열려 있을 때 menu listener는 `preventDefault`, menu close, menu trigger focus를 실행하지 않는다. Escape는 native dialog cancel 및 기존 dirty guard 경계에 도달한다.
   - dialog가 없으면 기존 menu-only Escape가 그대로 기본 동작을 막고 메뉴를 닫은 뒤 Menu button에 포커스를 복원한다. 공용 `Dialog`의 cancel/close/focus lifecycle은 변경하지 않았다.

2. **의미 있는 회귀 2건**
   - mobile menu와 notification dialog를 함께 연 상태의 실제 cancelable Escape `keydown`이 `defaultPrevented=false`이고, underlying menu가 열린 채 유지되며 menu focus hijack이 없는지 확인한다. 이어 native `cancel`을 발생시켜 popup이 한 번 닫히고 notification trigger focus와 hash가 복원되는지 검증한다.
   - dialog가 없는 menu-only 상태에서는 같은 Escape가 `defaultPrevented=true`, menu close, `aria-expanded=false`, Menu focus 복원을 수행하는지 검증한다.

3. **r4/r5 source 보존**
   - Calendar 최신 version/recovery 전역 잠금과 초기 legacy URL page 원자화 source 및 회귀를 변경하지 않았다. 기존 332개에 r6 2개를 추가한 전체 334개 검사가 통과했다.

## 실제 검증

모든 명령은 위 작업 위치에서 실행했다.

| 명령 | 종료 | 결과 | 원시 로그 |
|---|---:|---|---|
| `pnpm --dir frontend exec vitest run --configLoader native --pool threads src/frontend-correction-r6.test.tsx src/notification-popup.test.tsx src/dialog-focus.test.tsx src/schedule-ui-refinement.test.tsx` | 0 | 4 files, 23 passed | `evidence/frontend-correction-r6-focused.log` |
| `pnpm --dir frontend exec vitest run --configLoader native --pool threads` | 0 | 29 files, 334 passed | `evidence/frontend-correction-r6-full.log` |
| `pnpm --dir frontend exec tsc -b --pretty false` | 0 | 오류 없음 | `evidence/frontend-correction-r6-typecheck.log` |
| `pnpm --dir frontend exec vite build --configLoader native` | 0 | 86 modules, build 성공 | `evidence/frontend-correction-r6-build.log` |
| `git diff --check` | 0 | whitespace 오류 없음; 기존 LF/CRLF 경고만 존재 | `evidence/frontend-correction-r6-diff-check.log` |

## 소스 고정 식별자

| 파일 | SHA-256 |
|---|---|
| `frontend/src/ui.tsx` | `D346DECF321B0493E906051BAC02488FF6D80DD96EB2E137AE0D055E20EF9352` |
| `frontend/src/schedule-time-mutation.ts` | `2479B3EFE5489C6B6C87B8761ED7F9B31C9B0CE0440379305F5C7A708CF24A09` |
| `frontend/src/screens/Schedules.tsx` | `C621213DD553C51A492FDF394CF53C6E2D3F3B8E58B6425F56E2964054CBCFCA` |
| `frontend/src/screens/ScheduleWorkspace.tsx` | `6959155767A9D09E867AF7A73F354203E8705AA92301EB9834FFBB64EAA93F92` |
| `frontend/src/frontend-correction-r4.test.tsx` | `1B5BB22ED2E8ED75D8D0E95090D0452F71F20D24524A4DAC5CB212CBDB1FAE22` |
| `frontend/src/frontend-correction-r5.test.tsx` | `7DCF505F32FB21B4907E03D34101011BFB0A4C4411C5A030B4DD170CB1F87E12` |
| `frontend/src/frontend-correction-r6.test.tsx` | `B8ED344A4AE23CD56CA940B83CCDE66663D491ECC3EB590D65CD62E5078F48E0` |

## 범위와 미실행 검사

- r6 production 변경은 `ui.tsx`의 menu Escape listener 한 줄이며 회귀 파일 하나를 추가했다. 다른 production source, CSS, fixture 및 backend는 변경하지 않았다.
- 부모가 immutable `bd32ea71`에서 확인한 변경되지 않은 Member/shared copy, Properties/options, Detail/Form 브라우저 증거는 source 구분을 유지해 재사용할 수 있다.
- final source의 320px menu+notification native Escape 순서와 r4/r5 changed flows 브라우저 확인, 동일 최종 SHA CI, 독립 Astra/high task review, release-manager, 독립 release review, Git/merge/deploy/관찰은 부모 소유다.
- backend `47dac12`의 승인된 219개 검사 증거를 재사용했다. 이전 실패와 actual attempt 8 이력을 유지하며 이번 결과는 implementer 자체 판정이 아니다.
- Notion 아카이브 동기화는 부모의 최종 독자 문서 정리 단계에서 수행한다.
