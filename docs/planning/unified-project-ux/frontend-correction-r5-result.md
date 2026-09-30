# 통합 프로젝트 UX 프런트엔드 보정 r5 실행 결과

- 실행 역할: canonical implementer, Sol/medium, actual attempt 7 bounded correction
- 기준 소스: `bd32ea71aefd0f3d5ea748eabbeb850dee530a10` + 미커밋 r4 source freeze
- 작업 위치: `D:\onedrive\Documents\ChatGPT\AI ERP\tmp\unified-project-ux-resume`
- 모델 사용량: `null` (제공되지 않음)
- 상태: r4 보존 포함 source freeze 완료, 부모 최종 브라우저·동일 SHA CI·독립 리뷰 대기

## 반영 내용과 기준 대응

1. **SV-FE-009 / UPUX-FE-004 — 초기 legacy context 원자화**
   - `hashContext`가 legacy `list`, `month`, `week`를 발견하면 첫 React state부터 canonical peer와 page 0을 사용한다. 기존 URL 정규화 effect는 주소의 `view`, `mode`, `page`를 같은 canonical 값으로 치환한다.
   - 따라서 첫 렌더 전에 읽힌 page 2가 뒤늦은 hash 정규화와 경쟁하지 않는다. list의 실제 첫 workspace query, rows, footer가 page 0과 일치하고 month/week의 실제 Calendar query도 page 0만 사용한다.
   - `date`, `text`, `zone`과 올바른 month/week mode를 보존한다. 이미 canonical인 `builtin-list&page=2`는 page 2 state/query/footer를 그대로 유지한다.

2. **SV-FE-013 — 실제 요청·표시 회귀 4건**
   - fresh legacy list `page=2`: URL `builtin-list&page=0`, 실제 query page 0, page 0 row, `2건 · 1페이지`를 함께 검증한다.
   - fresh legacy month/week `page=7`: URL `builtin-calendar&page=0&mode=...`, 보존된 date/text/zone, pressed mode, 실제 grid/row, query `{page:0,size:100}`을 각각 검증한다.
   - fresh canonical `builtin-list&page=2`: 실제 query page 2, 해당 row, `41건 · 3페이지`, URL page 2를 검증한다.

3. **r4 source 보존**
   - 활성 workspace records 기반 Calendar override 정리와 exact-target access denial 전역 잠금 코드는 변경하지 않았다. r4 신규 회귀 3건을 포함한 전체 검사가 함께 통과했다.

## 실제 검증

모든 명령은 위 작업 위치에서 실행했다.

| 명령 | 종료 | 결과 | 원시 로그 |
|---|---:|---|---|
| `pnpm --dir frontend exec vitest run --configLoader native --pool threads src/frontend-correction-r5.test.tsx src/frontend-correction-r2.test.tsx src/frontend-correction-r3.test.tsx src/round5.test.tsx` | 0 | 4 files, 44 passed | `evidence/frontend-correction-r5-focused.log` |
| `pnpm --dir frontend exec vitest run --configLoader native --pool threads` | 0 | 28 files, 332 passed | `evidence/frontend-correction-r5-full.log` |
| `pnpm --dir frontend exec tsc -b --pretty false` | 0 | 오류 없음 | `evidence/frontend-correction-r5-typecheck.log` |
| `pnpm --dir frontend exec vite build --configLoader native` | 0 | 86 modules, build 성공 | `evidence/frontend-correction-r5-build.log` |
| `git diff --check` | 0 | whitespace 오류 없음; 기존 LF/CRLF 경고만 존재 | `evidence/frontend-correction-r5-diff-check.log` |

## 소스 고정 식별자

| 파일 | SHA-256 |
|---|---|
| `frontend/src/schedule-time-mutation.ts` | `2479B3EFE5489C6B6C87B8761ED7F9B31C9B0CE0440379305F5C7A708CF24A09` |
| `frontend/src/screens/Schedules.tsx` | `C621213DD553C51A492FDF394CF53C6E2D3F3B8E58B6425F56E2964054CBCFCA` |
| `frontend/src/screens/ScheduleWorkspace.tsx` | `6959155767A9D09E867AF7A73F354203E8705AA92301EB9834FFBB64EAA93F92` |
| `frontend/src/frontend-correction-r4.test.tsx` | `1B5BB22ED2E8ED75D8D0E95090D0452F71F20D24524A4DAC5CB212CBDB1FAE22` |
| `frontend/src/frontend-correction-r5.test.tsx` | `7DCF505F32FB21B4907E03D34101011BFB0A4C4411C5A030B4DD170CB1F87E12` |

## 범위와 미실행 검사

- r5 동작 변경은 `ScheduleWorkspace.tsx`의 초기 context page 계산 한 곳뿐이며 회귀 파일 하나를 추가했다. r4 시간 저장·복구 source와 tests는 그대로다.
- backend는 변경하지 않았고 `47dac12`의 승인된 219개 검사 증거를 재사용했다.
- 최종 source의 fresh legacy list/month/week와 r4 Calendar version/recovery 전이 브라우저 확인, 동일 최종 SHA CI, 독립 Astra/high task review, release-manager, 독립 release review, Git/merge/deploy/관찰은 부모 소유다.
- 이전 실패와 실제 attempt 7 이력을 유지하며 이번 결과는 implementer 자체 판정이 아니다.
- Notion 아카이브 동기화는 부모의 최종 독자 문서 정리 단계에서 수행한다.
