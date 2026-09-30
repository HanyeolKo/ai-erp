# 통합 프로젝트 UX 프런트엔드 보정 r3 실행 결과

- 실행 역할: canonical implementer, Sol/medium, attempt 5 bounded correction
- 기준 소스: `5d725fa1393f1c76111c1f0a712c7da1e30b670e`
- 작업 위치: `D:\onedrive\Documents\ChatGPT\AI ERP\tmp\unified-project-ux-resume`
- 모델 사용량: `null` (제공되지 않음)
- 상태: 소스 고정 완료, 부모 브라우저 검증·동일 SHA CI·독립 리뷰 대기

## 반영 내용과 기준 대응

1. **SV-FE-006 — 실제 Calendar target 공유**
   - Calendar가 사용하는 `config`, 사용자 시간대, `anchor`, `mode`, 명시적 `after`/`before`, 계산된 `from`/`to`, cache identity를 `WorkspaceCalendarTarget`으로 부모 작업공간에 공유한다.
   - 접근 거부 뒤에도 Calendar 컨트롤을 유지하고 결과만 숨기며, 복구는 공유된 target으로 `size=100` 전체 페이지를 다시 읽는다. 프로젝트 역할·작업공간 메타데이터·exact target이 모두 성공한 경우에만 쓰기 잠금을 해제한다.
   - 비기본 시간대와 명시적 기간에서 page 1 거부, 실패 복구, 성공 복구의 요청 본문과 URL/컨트롤 보존을 회귀 검사로 확인했다.

2. **UPUX-FE-003 — Calendar/List 공용 시간 저장 처리**
   - `schedule-time-mutation.ts`에 원본 instant 보존과 저장 결과 복구를 한 번 구현하고 Calendar와 List가 함께 사용한다.
   - 바꾸지 않은 필드는 초·밀리초를 포함한 원본 UTC instant를 그대로 전송한다. 바꾼 필드는 기존 DST 검증 변환을 사용한다.
   - 409·네트워크·5xx에서 최신 detail, 최신 역할, 실제 target을 함께 확인한다. 이미 반영된 저장은 재전송하지 않고, 미반영 결과는 최신 상태에서 명시적으로 다시 진입하게 하며, 역할 상실이나 읽기 실패는 전체 쓰기를 잠근다. List의 dirty/Cancel/409 최신 version 및 광범위 invalidation도 유지했다.

3. **SV-FE-013 — 회귀 행렬 완성**
   - exact Calendar 요청, subminute 보존, 불확실한 committed/uncommitted 결과와 no replay, 역할 상실, filter 저장의 page 2→0 원자 전환, property/dashboard 실패·성공 복구, clean refetch 및 conflict/Cancel 뒤 최신 version 쓰기를 실제 요청 본문으로 검증했다.
   - 기존 317개 검사와 새 8개 검사를 합친 325개 전체 검사가 통과했다.

4. **동시 qualification 보정**
   - Properties 새 속성 이름과 속성 유형에 지속적으로 보이는 label을 추가하고, 800px 이하 옵션 행을 한 열로 배치했다. 기존 settings/properties 폭과 기본 popup 560px 규칙은 유지했다.
   - `ScheduleForm` 오류 포커스를 commit 직후 `useLayoutEffect`에서 이동하도록 바꿔 CI의 316/317 focus race를 제거했다. 포커스 assertion은 그대로 유지했고 해당 검사를 포함한 focused 실행이 통과했다.

## 실제 검증

모든 명령은 위 작업 위치에서 실행했다.

| 명령 | 종료 | 결과 | 원시 로그 |
|---|---:|---|---|
| `pnpm --dir frontend exec vitest run --configLoader native --pool threads src/project-schedule-redesign.test.tsx src/frontend-correction-r3.test.tsx src/schedule-workspace.test.tsx src/calendar-direct-recovery.test.tsx` | 0 | 4 files, 44 passed | `evidence/frontend-correction-r3-focused.log` |
| `pnpm --dir frontend exec vitest run --configLoader native --pool threads` | 0 | 26 files, 325 passed | `evidence/frontend-correction-r3-full.log` |
| `pnpm --dir frontend exec tsc -b --pretty false` | 0 | 오류 없음 | `evidence/frontend-correction-r3-typecheck.log` |
| `pnpm --dir frontend exec vite build --configLoader native` | 0 | 86 modules, build 성공 | `evidence/frontend-correction-r3-build.log` |
| `node --check scripts/ui-preview-server.mjs` | 0 | 구문 오류 없음 | `evidence/frontend-correction-r3-preview-check.log` |
| `git diff --check` | 0 | whitespace 오류 없음; 기존 LF/CRLF 경고만 존재 | `evidence/frontend-correction-r3-diff-check.log` |

## 소스 고정 식별자

| 파일 | SHA-256 |
|---|---|
| `frontend/src/schedule-time-mutation.ts` | `E16E3242BAD8DAE6430096374D14C7154D4E991BE3E308962C45F027ACEF612E` |
| `frontend/src/screens/Schedules.tsx` | `B41DE655DEC8DA10BBD85F263931C7F4F4FBEF9E0FCC86C3BC9DDDC7E6A5BE9C` |
| `frontend/src/screens/ScheduleWorkspace.tsx` | `7477A2C71EAD8FB8025FC28D871A1501070C0004BF352CB16F3B588FC0624B2C` |
| `frontend/src/screens/ScheduleForm.tsx` | `110ECAE28B2655546E4F71403D9B186F5C8AA572D7BFFF0C9019631920BBEEB0` |
| `frontend/src/screens/schedule-workspace.css` | `46D9ACF357AD3253C9213F39CF22132306BAC18759D8855008B5078CF4933CF2` |
| `frontend/src/frontend-correction-r3.test.tsx` | `2A1854D81FC7AAE7488CE57422A958E564B66F649332607422130B8C84452150` |
| `frontend/src/schedule-workspace.test.tsx` | `ADA319309E0B98183B6B1FC90BAAEB0CDB6F8A7AD526295DFBF1B147CD07107E` |

## 재사용 및 미실행 검사

- 백엔드는 변경하지 않았으며 승인된 219개 검사 증거를 재사용했다.
- immutable `5d725fa`의 기존 반응형 브라우저 측정은 r3 최종 소스 증거로 주장하지 않는다. 최종 source freeze의 settings/properties, Calendar/List 시간 저장·거부·복구 흐름과 1440/1280/768/390/320 치수 재검증은 부모 소유다.
- 동일 최종 SHA CI, 독립 Astra/high task review, release-manager, 독립 release review, merge/deploy/관찰은 부모 소유다.
- r2 실패와 실제 attempt 5 이력은 유지한다.
- Notion 아카이브 동기화는 부모의 최종 독자 문서 정리 단계에서 수행한다.
