# 통합 프로젝트 UX 프런트엔드 보정 r4 실행 결과

- 실행 역할: canonical implementer, Sol/medium, actual attempt 6 bounded correction
- 기준 소스: `bd32ea71aefd0f3d5ea748eabbeb850dee530a10`
- 작업 위치: `D:\onedrive\Documents\ChatGPT\AI ERP\tmp\unified-project-ux-resume`
- 모델 사용량: `null` (제공되지 않음)
- 상태: 소스 고정 완료, 부모의 최종 브라우저·동일 SHA CI·독립 리뷰 대기

## 반영 내용과 기준 대응

1. **UPUX-FE-003 — 활성 Calendar 레코드로 override 정리**
   - 기존 override 정리가 workspace mode에서 비활성인 legacy `list.data`만 읽던 문제를 제거했다.
   - 일반 Calendar는 `list`, workspace Calendar는 `workspaceList.records`를 `activeServerRows`로 사용한다. 복구 detail이 target보다 최신이면 override를 유지하고, 실제 활성 target의 `rowVersion`이 따라잡거나 앞서면 override를 제거한다.
   - 통합 회귀는 409 뒤 detail version 4를 표시한 다음 workspace target이 초·밀리초를 포함한 version 5로 바뀌는 흐름을 실행한다. UI가 version 5 시간을 표시하고 다음 PATCH가 `rowVersion: 5`와 바꾸지 않은 원본 instant를 그대로 보내는 것을 확인한다.

2. **SV-FE-006 — 수동 exact-target 거부를 전역 잠금으로 전파**
   - 공용 시간 mutation helper의 `locked` 결과에 target read 실패 원인을 별도로 보존했다. Calendar caller는 mutation recovery의 exact-target 401/403/404를 구분해 부모 `onWorkspaceDenied` 경계로 전파한다.
   - 명시적 일정 recovery에서도 session guard 이후 target access denial을 같은 전역 경계로 올린다. target denial에는 per-row lock을 남기지 않으므로, 부모가 fresh project role+workspace metadata+exact target 전체 읽기에 성공한 뒤 전역 잠금을 해제하면 stale row lock이 남지 않는다.
   - 직접 PATCH access denial도 workspace Calendar에서는 같은 전역 경계를 사용한다. 결과 행을 숨기고 create/settings/property 및 record 쓰기를 잠그며, 실패한 fresh recovery에서는 잠금을 유지한다.

3. **SV-FE-013 — 통합 회귀 3건**
   - recovered v4 → active workspace v5 → UI 최신값 → 다음 PATCH version 5 및 untouched subminute instant.
   - mutation recovery 내부 exact-target 403 → 보호 행 숨김, create 숨김, settings/property 잠금, 추가 PATCH 없음.
   - per-row retry의 exact-target 403 → 전역 잠금 → fresh recovery 실패 시 유지 → project/metadata/exact-target 성공 후 unlock, 전체 과정 no replay.

## 실제 검증

모든 명령은 위 작업 위치에서 실행했다.

| 명령 | 종료 | 결과 | 원시 로그 |
|---|---:|---|---|
| `pnpm --dir frontend exec vitest run --configLoader native --pool threads src/frontend-correction-r4.test.tsx src/frontend-correction-r3.test.tsx src/calendar-direct-recovery.test.tsx src/schedule-workspace.test.tsx` | 0 | 4 files, 40 passed | `evidence/frontend-correction-r4-focused.log` |
| `pnpm --dir frontend exec vitest run --configLoader native --pool threads` | 0 | 27 files, 328 passed | `evidence/frontend-correction-r4-full.log` |
| `pnpm --dir frontend exec tsc -b --pretty false` | 0 | 오류 없음 | `evidence/frontend-correction-r4-typecheck.log` |
| `pnpm --dir frontend exec vite build --configLoader native` | 0 | 86 modules, build 성공 | `evidence/frontend-correction-r4-build.log` |
| `git diff --check` | 0 | whitespace 오류 없음; 기존 LF/CRLF 경고만 존재 | `evidence/frontend-correction-r4-diff-check.log` |

## 소스 고정 식별자

| 파일 | SHA-256 |
|---|---|
| `frontend/src/schedule-time-mutation.ts` | `2479B3EFE5489C6B6C87B8761ED7F9B31C9B0CE0440379305F5C7A708CF24A09` |
| `frontend/src/screens/Schedules.tsx` | `C621213DD553C51A492FDF394CF53C6E2D3F3B8E58B6425F56E2964054CBCFCA` |
| `frontend/src/frontend-correction-r4.test.tsx` | `1B5BB22ED2E8ED75D8D0E95090D0452F71F20D24524A4DAC5CB212CBDB1FAE22` |

## 범위와 미실행 검사

- r4는 위 세 frontend 파일만 변경했다. Properties, option, shared view, Detail/Form, CSS, synthetic fixture 및 backend는 변경하지 않았다.
- 기존 backend `47dac12`의 승인된 219개 검사 증거를 재사용했다.
- 부모가 immutable `bd32ea71`에서 확인한 변경되지 않은 Properties/option/shared/detail 브라우저 증거는 구분해 재사용할 수 있다. r4가 바꾼 Calendar 최신 version 전환과 전역 recovery는 최종 빌드에서 부모가 별도로 확인한다.
- 동일 최종 SHA CI, 독립 Astra/high task review, release-manager, 독립 release review, Git/merge/deploy/관찰은 부모 소유다.
- 이전 실패와 실제 attempt 6 이력을 유지하며 이번 결과는 implementer 자체 판정이 아니다.
- Notion 아카이브 동기화는 부모의 최종 독자 문서 정리 단계에서 수행한다.
