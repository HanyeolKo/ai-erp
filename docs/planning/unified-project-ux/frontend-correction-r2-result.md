# 통합 프로젝트 UX 프런트엔드 보정 r2 실행 결과

- 실행 역할: canonical implementer, Sol/medium, attempt 4 bounded correction
- 기준 소스: `29eeb024e3db0ba789611e3bf646c74616769fbf`
- 작업 위치: `D:\onedrive\Documents\ChatGPT\AI ERP\tmp\unified-project-ux-resume`
- 모델 사용량: `null` (제공되지 않음)
- 상태: 소스 고정 완료, 부모 브라우저 검증 및 독립 리뷰 대기

## 부모 브라우저 qualification 보정

부모가 768px viewport에서 ViewSettings 내부 720px 최소 폭이 기본 560px Dialog body를 넘어 오른쪽 입력과 버튼을 잘라내는 현상을 확인했다. 공용 `Dialog`에 선택적 class를 추가하고 ViewSettings는 최대 760px, Properties는 최대 800px로 지정했으며 두 폭 모두 `100vw - 32px` 이내로 제한했다. form/fieldset/input의 최소 폭을 0으로 만들고 800px 이하에서 filter/property/option 행을 한 열로 재배치했다. 기본 Dialog 560px 규칙과 알림 popup은 변경하지 않았다.

## 반영 내용

1. **UPUX-FE-005**: 모바일/데스크톱 Calendar의 월/주 전환 컨트롤을 다시 표시했다.
2. **SV-FE-003**: MANAGER가 builtin view에서 명시한 `SHARED` scope를 그대로 생성하며, MEMBER가 기존 공유 view를 편집하면 `PERSONAL` 복사본을 생성한다.
3. **SV-FE-006**: 401/403/404 이후 열려 있던 settings/property/dashboard/value 편집을 잠그고, Calendar 복구는 동일 범위·동일 구성으로 `size=100`, 최대 10페이지를 완전히 읽은 경우에만 잠금을 해제한다. 실패 복구는 잠금 상태를 유지한다.
4. **UPUX-FE-003**: List 시간 편집에 dirty 이탈 확인, Cancel 복원, 409 최신 detail/role/version 재진입, 광범위 invalidation을 연결했다. Form의 성공/Cancel도 검증된 return context를 보존한다.
5. **SV-FE-010**: custom-only Detail/Form 이탈에서 확인 전 draft를 보존하고 승인된 Discard에서만 제거한다. 중첩 옵션 추가·이름 변경·순서 draft도 Continue 후 유지한다.
6. **UPUX-FE-004 / SV-FE-009**: legacy `month/week/list`를 canonical builtin view로 한 번만 치환하고 `date/filter/return`을 보존하며 page를 원자적으로 0으로 초기화한다.
7. **SV-FE-013**: 위 동작과 10페이지 중복 partial, fractional NUMBER, Dashboard clean/dirty/409/Cancel, 날짜 filter reset/clear 및 상태/ACK를 실제 회귀 테스트로 보강했다.

브라우저 검증용 loopback fixture에 `member`, `workspace-denied`, `workspace-conflict` 시나리오를 추가했다. 모두 합성 데이터만 사용한다. 서버 재시작 시 각각 다음 환경값을 사용한다.

```powershell
$env:UI_PREVIEW_SCENARIO='member'
$env:UI_PREVIEW_SCENARIO='workspace-denied'
$env:UI_PREVIEW_SCENARIO='workspace-conflict'
```

`member`는 `MEMBER` 역할과 `view-shared-member` 공유 view를 제공한다. `workspace-denied`는 MANAGER 화면을 연 뒤 workspace write에 403을 반환한다. `workspace-conflict`는 첫 record/value PATCH에서 서버 제목과 version을 갱신하고 409를 반환한다. 변경된 fixture를 사용하려면 부모 소유 8080 서버를 재시작해야 한다.

## 실제 검증

| 검사 | 종료 | 결과 | 원시 로그 |
|---|---:|---|---|
| `pnpm --dir frontend exec vitest run --configLoader native --pool threads src/frontend-correction-r2.test.tsx src/schedule-workspace.test.tsx src/round5.test.tsx` | 0 | 3 files, 44 passed | `evidence/frontend-correction-r2-focused.log` |
| `pnpm --dir frontend exec vitest run --configLoader native --pool threads` | 0 | 25 files, 317 passed | `evidence/frontend-correction-r2-full.log` |
| `pnpm --dir frontend exec tsc -b --pretty false` | 0 | 오류 없음 | `evidence/frontend-correction-r2-typecheck.log` |
| `pnpm --dir frontend exec vite build --configLoader native` | 0 | 85 modules, build 성공 | `evidence/frontend-correction-r2-build.log` |
| `node --check scripts/ui-preview-server.mjs` | 0 | 구문 오류 없음 | `evidence/frontend-correction-r2-node-check.log` |
| `git diff --check` | 0 | whitespace 오류 없음; 기존 LF/CRLF 경고만 존재 | `evidence/frontend-correction-r2-diff-check.log` |
| 세 fixture 시나리오 smoke | 0 | MEMBER+SHARED, 403, 409+version 4 확인 | `evidence/frontend-correction-r2-preview-smoke.log` |
| qualification 후 `src/schedule-workspace.test.tsx` | 0 | 1 file, 12 passed | `evidence/frontend-correction-r2-dialog-focused.log` |
| qualification 후 `pnpm --dir frontend exec tsc -b --pretty false` | 0 | 오류 없음 | `evidence/frontend-correction-r2-dialog-typecheck.log` |
| qualification 후 `pnpm --dir frontend exec vite build --configLoader native` | 0 | 85 modules, build 성공 | `evidence/frontend-correction-r2-dialog-build.log` |
| qualification 후 `git diff --check` | 0 | whitespace 오류 없음; 기존 LF/CRLF 경고만 존재 | `evidence/frontend-correction-r2-dialog-diff-check.log` |

## 아직 실행하지 않은 검사

- qualification 보정 후 1440/1280/768/390/320 실제 브라우저 치수와 캡처 재확인: 부모 소유. 이번 fixture 변경 후 재시작이 필요하다.
- 동일 SHA CI, 독립 Astra/high 최종 리뷰, merge/deploy/관찰: 부모 소유.
- 백엔드 219개 검사는 승인된 기존 증거를 재사용했으며 변경 범위 밖이라 다시 실행하지 않았다.
- Notion 아카이브 동기화: 부모 최종 독자 문서 정리 단계에서 수행한다.
