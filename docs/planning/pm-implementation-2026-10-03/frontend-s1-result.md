# F1 S1 구현 결과

## 범위와 상태

부모가 승인한 `ERP-PM-IMPLEMENT-20261003-F1` 계약에 따라 `codex/project-management-redesign-20261003` worktree에서 `ai-erp-implementer` / `gpt-5.6-luna` / `high`로 프런트엔드 S1을 구현했다. 작업 대상은 프로젝트 작업 진입점, 기본 TASK workspace, 개요의 TASK 요약, 기존 고급 계획 경로 보존, 오프라인 브라우저 fixture다. 상태는 **ready-for-review**이며, 브라우저 관찰과 최종 verdict는 부모가 맡는다.

## 구현 내용

- 프로젝트 셸의 계획 진입점을 `작업`으로 바꾸고, 개요에 기존 일정 섹션과 분리된 TASK 실행 요약·직접 진입·생성 링크를 추가했다.
- `/projects/:id/plan`은 명시적 legacy selector가 없을 때 TASK 중심 workspace를 연다. 제목·상태·대상일·마감·담당자·차단 설명을 표로 제공하고, 나머지 계획 필드는 세부 정보에서 유지한다.
- 명시적 `view`, legacy `types`/`kind`/`scopeId`, `mode=default`를 보존해 여섯 고급 관점과 기존 필터·쿼리 맥락을 유지했다. 기본/필터 빈 상태 문구도 분리했다.
- quick create는 session context, 403 잠금, 409 초안 보존, unknown 결과의 동일 `requestId`·동일 payload 재확인을 처리한다. `itemId` deep link는 한 번 열리고 닫기/편집 시 URL에서 제거된다.
- Calendar API와 좌표, backend/client/generated/config/auth 파일은 수정하지 않았다.

## 변경 파일

- `frontend/src/ui.tsx`
- `frontend/src/screens/Schedules.tsx`
- `frontend/src/screens/ProjectPlan.tsx`
- `frontend/src/screens/project-plan.css`
- `frontend/src/screens/schedules.css`
- `frontend/src/test/http.ts`
- `frontend/src/pm-default-workspace.test.tsx`
- `frontend/src/project-recovery.test.tsx` (membership recovery 이후 새 plan read fixture만 추가)
- `tmp/pm-implementation-2026-10-03/browser-fixtures-s1.mjs`
- `tmp/pm-implementation-2026-10-03/browser-fixtures-s1-result.json`

## 검증 근거

최종 표적 묶음은 다음 명령으로 `exit=0`이었다.

```text
command=pnpm --dir frontend test -- plan-ui-refinement.test.tsx behavior.test.tsx regression.test.tsx pm-default-workspace.test.tsx project-recovery.test.tsx
cwd=C:\Users\USER\.codex\worktrees\project-management-redesign\AI ERP
start=2026-10-03T17:28:00.7511533+09:00
end=2026-10-03T17:28:18.1231642+09:00
exit=0
Tests 109 passed (109)
```

최종 전체 테스트도 `exit=0`이었다.

```text
command=pnpm frontend:test
cwd=C:\Users\USER\.codex\worktrees\project-management-redesign\AI ERP
start=2026-10-03T17:28:54.6930574+09:00
end=2026-10-03T17:29:37.8185993+09:00
exit=0
Test Files 30 passed (30), Tests 341 passed (341)
```

`git diff --check`는 `2026-10-03T17:31:45.5949427+09:00`–`17:31:45.7500134+09:00`, `exit=0`이었다. fixture `node --check`도 `exit=0`이다. 원시 stdout/stderr와 메타데이터는 `tmp/pm-implementation-2026-10-03/f1/targeted-final.*`, `full-test-final.*`, `diff-check-final.*`, `fixture-check.*`에 있다. 최초 full-test의 실패(`exit=1`, missing project membership recovery 중 새 `/plan` read fixture 누락)도 `full-test.*`에 보존했으며, 수정 후 최종 전체 테스트가 통과했다.

base worktree에는 처음 `frontend/src/api/generated.ts`가 없어 `pnpm frontend:typecheck`와 `pnpm frontend:build`가 각각 `exit=2`였고 원시 기록을 `typecheck.*`, `build.*`에 보존했다. 부모가 승인한 CI `37002422579`의 고정 baseline spec `D:/onedrive/Documents/ChatGPT/AI ERP/tmp/pm-implementation-2026-10-03/baseline-ci/api-spec/openapi3.yaml`만 사용해 ignored type cache를 생성했다. Windows 공백 경로 때문에 첫 quoted path 시도는 `exit=1`이었으며 `generated.*`에 보존했다. file URI 재시도는 `2026-10-03T17:30:42.5671333+09:00`–`17:30:44.0096232+09:00`, `exit=0`, source SHA-256 `073989825B73541C8CCC85DDA65CD3DFD4E314F3F2B322F39BF8C4D5556A23B2`, generated SHA-256 `812AB73C0FB80E449862826585F6FCD4C60299BD93E2A3CD0435BB6A8B6ECE28`, 120029 bytes였다. 생성 명령은 `pnpm exec openapi-typescript 'file:///D:/onedrive/Documents/ChatGPT/AI%20ERP/tmp/pm-implementation-2026-10-03/baseline-ci/api-spec/openapi3.yaml' -o frontend/src/api/generated.ts`이며 generated cache는 ignored다.

generated cache를 사용한 최종 `pnpm frontend:typecheck`는 `2026-10-03T17:31:02.9325569+09:00`–`17:31:11.8253807+09:00`, `exit=0`, `pnpm frontend:build`는 `2026-10-03T17:31:23.9899255+09:00`–`17:31:32.9699261+09:00`, `exit=0`이었다. 결과 원시는 `typecheck-final.*`, `build-final.*`, `diff-check-final.*`에 있다. generated/client 파일 본문은 소유 변경으로 제출하지 않는다.

fixture는 `OFFLINE SYNTHETIC F1 S1`로 표시되며 MANAGER/MEMBER/VIEWER/CREATOR_MEMBER와 `none`/`forbidden`/`conflict`/`lost-response` 시나리오를 제공한다. lost-response는 첫 요청을 적용하고 연결을 닫은 뒤 같은 `requestId` replay에 저장된 TASK를 반환한다. 이번 helper 보완에서 `/api/v1/system/configuration`을 실제 프런트 계약의 `login: READY`, `calendar: READY`, `loginUrl` 형태로 맞추고, `/api/v1/me`에 `authorities`를 포함했다. MEMBER/VIEWER는 `u2`, MANAGER/CREATOR_MEMBER는 `u1`로 역할 의미를 고정했다. ScheduleWorkspace GET/query/record와 Calendar projection/connection 경로를 실제 클라이언트 계약에 맞춰 추가하고, write body·requestId·canonical JSON·resource ID를 stdout에 남기며 fixture 상태를 메모리에서 갱신한다. conflict는 첫 요청 한 번만 409를 반환하고 최신 재시도는 적용한다.

helper가 `--dist`로 candidate 또는 b660 baseline의 실제 `frontend/dist`를 정적 서빙하고 SPA fallback과 path traversal guard를 적용한다. candidate는 port `4173`, baseline은 port `4170`으로 준비했다. 두 서버의 `/`, `/api/v1/system/configuration`, `/projects/p1/plan` curl dry-read는 모두 `exit=0`이며, 원시는 `tmp/pm-implementation-2026-10-03/f1/fixture-dry-final.*`에 있다. 보완 후 `node --check tmp/pm-implementation-2026-10-03/browser-fixtures-s1.mjs`도 `exit=0`이며 raw evidence는 `tmp/pm-implementation-2026-10-03/f1/fixture-helper-nodecheck-final.*`이다. b660 archive/build 근거는 `tmp/pm-implementation-2026-10-03/baseline/fixture-baseline-result.json`과 `build.*`에 있다. 실제 브라우저 상호작용과 parent-owned server 재시작은 부모가 수행하며, ERP/Google provider·credentials는 사용하지 않았다.

## repair attempt 2 결과

부모가 제공한 `default-1280-dom.json`의 경계 이탈에 따라 compact TASK 화면의 기본 필터는 검색·상태·담당자만 남기고, 6개 고급 관점·종류·상세 필터는 명시적 고급 계획 진입 뒤에만 표시되도록 수정했다. scoped CSS로 compact TASK 제목·세부 정보·상태 선택의 desktop target을 44px 이상으로 맞췄다. 기존 고급 URL·kind/scope 보존, 세션/requestId 복구, deep-link 동작과 Calendar 전역 좌표는 유지했다.

추가로 부모가 제공한 overview 관찰에 맞춰 개요 헤더의 주 동작을 `TASK 만들기`·`작업 열기`로 배치하고 `일정 만들기`는 보조 동작으로 유지했다. 빈 상태 문구도 프로젝트 작업 중심으로 조정했으며, `READY`·`BLOCKED` 등 기계 상태 토큰은 기존 한국어 상태 라벨로 표시한다.

helper에는 `unknown-response` 시나리오를 추가했다. 첫 POST는 상태를 적용한 뒤 HTTP 500 problem을 반환하고, 같은 `requestId`와 payload의 다음 사용자 재시도는 저장된 TASK를 반환한다. 두 요청 모두 body·requestId·canonical JSON·resource ID를 기록하며 기존 `lost-response` socket-loss 시나리오는 유지한다.

검증은 `tmp/pm-implementation-2026-10-03/f1/repair2-*` 원시 기록에 있다. 최종 표적 109개·전체 341개 테스트, typecheck, 최종 build, 최종 `git diff --check`, helper `node --check`가 모두 `exit=0`이었다. 최종 candidate bundle SHA-256은 `index.html=88BDF54A9FABEC0A824FA7676A9FC7D00766FFD88220ABBAAD675F796672CE9F`, `index-BliWI170.css=56C3AAD6E3578E106298D69B7A45032BB4A0C345AE246DC5AA5487B427950024`, `index-y0-s38Iv.js=FBBC1F7990314D911D1459B13A66417E55A473F3B14005DDE1F489EFDF30D25C`이다. isolated helper smoke는 unknown-response 첫 요청 HTTP 500, 동일 requestId replay HTTP 200/resource `task-created-1`을 확인했고 raw는 `tmp/pm-implementation-2026-10-03/f1/repair2-unknown-response-smoke.*`이다. 새 browser observation과 verdict는 부모가 수행한다.

overview 문구를 반영하기 전 표적 실행의 기존 기대문구 실패는 `repair2-final-targeted.*`에 보존했고, 승인된 문구로 갱신한 최종 표적 실행은 `repair2-final-targeted2.*`에서 `exit=0`이다.

## repair attempt 4 결과

동일 Luna/high로 승인된 한 번의 예외 보수 시도에서 F1의 일곱 stable key를 보완했다. quick create는 session/project draft namespace에 `gcTime: Infinity`와 명시적 `editable`/`sending`/`uncertain` phase, immutable submitted payload/requestId를 저장하고 mutation cache의 stale callback을 현재 session·operation과 대조한다. route remount 중에도 pending 요청은 잠기며, 완료 후 동일 draft가 한 번만 reset된다. dirty route/beforeunload 보호도 compact draft에 적용했다. App session termination 경계는 수정하지 않았다.

overview plan read는 기존 access-denial boundary를 사용해 current 403에서 보호 TASK 데이터와 작성 링크를 동결하고, retryable 500과 분리한다. onboarding은 schedule count가 아니라 complete TASK 관측으로 결정하며, schedule-empty는 일정 영역 문구로만 표시한다. partial 응답은 zero/nonzero 모두 관측 범위를 표시하고, authoritative blockerIds와 off-page predecessor 사실을 유지한다. compact table의 scoped vertical padding은 44px control을 포함한 실제 단일 행 밀도를 52–60px 목표로 낮추며 긴 내용은 wrap한다. Overview title links에도 44px target을 적용했다.

회귀 범위에는 in-flight route remount·동일 UUID, dirty departure, partial/off-page blocker, schedule-empty/TASK 분리, current plan 403 동결을 추가했다. 표적 실행은 `pnpm exec vitest run src/pm-default-workspace.test.tsx src/behavior.test.tsx --reporter=dot`에서 67/67, 보완 후 `src/project-recovery.test.tsx src/regression.test.tsx`에서 37/37, 최종 전체 `pnpm exec vitest run --reporter=dot`에서 30 files / 346 tests, 모두 exit 0이다. 최종 typecheck(`pnpm exec tsc -b --pretty false`), build(`pnpm exec vite build`), `git diff --check`, helper `node --check tmp/pm-implementation-2026-10-03/browser-fixtures-s1.mjs`도 exit 0이다. helper의 `delayed-create`는 15초 지연 후 적용·응답 시각과 원 requestId를 stdout에 기록한다.

부모 소유 브라우저/keyboard/responsive/1280·390·200%/Calendar 비교, live ERP·Google/provider, server, CI, production capture는 실행하지 않았다. 외부 모드는 offline-contract-only이며 fixture 결과는 live integration 증거가 아니다. 부모 native reviewer의 source/raw verdict와 acceptance가 남아 있으므로 현재 상태는 **ready-for-review**다. 변경 대상 외 backend/B1 파일은 concurrent worker 소유로 건드리지 않았다.

## repair attempt 5 결과

부모가 승인한 동일 Luna/high 예외 시도에서 F1의 입력·연속성·overview 경계를 보수했다. quick create의 제목·상태는 React local state로 즉시 반영하고, 같은 session/project draft cache에는 완전한 입력값을 동기적으로 기록한다. mutation callback과 remount observer는 session generation, operation ID, invocation attempt ID, submitted request ID를 모두 대조한다. 첫 historical mutation을 선택하는 `mutationCache.find` 경로를 제거했으며, pending retry가 이전 error를 되살리지 않도록 했다. unknown 500은 같은 immutable UUID/payload를 재시도하고, known 409 뒤 입력을 바꾸면 새 UUID와 새 payload를 사용한다. 성공 후 다음 생성도 새 UUID를 사용한다. remount 뒤 외부 cache reset은 query-cache subscription으로 현재 입력에 반영한다.

개요 화면의 current plan 403/404는 보호된 plan/member cache를 제거하고 현재 project membership을 다시 확인하며 TASK 작성 동작을 잠근다. actor 소유 draft namespace는 보존한다. plan 500은 retryable TASK 영역으로 남기고 dashboard 일정 영역과 분리했다. TASK가 0개이고 일정이 1개인 경우에도 예정 일정·처리 대기·신호·일정 카드 영역을 표시한다. 불완전한 plan에서 관측되지 않은 선행 항목은 `선행 상태 확인 필요`로 표시하며, authoritative `blockerIds`·관측된 미완료 선행·명시적 BLOCKED 규칙을 유지한다.

이번 시도의 raw 명령 결과는 `tmp/pm-implementation-2026-10-03/f1/exception-repair5/`에 저장했다. 최초 typecheck 실패(`typecheck.meta.json`, exit 2), 최초 targeted remount 실패(`targeted.meta.json`, exit 1), 수정 후 focused remount 통과, expanded targeted 실패(`targeted3.meta.json`, exit 1), 그리고 수정 후 expanded targeted 통과(`targeted5.meta.json`, exit 0)를 모두 보존했다. ACK transport 제한과 실제 시각은 `ACK.md`에 기록했다. Native collaboration namespace가 노출되지 않아 ancestor thread 전송은 거절되었으며, 이 파일은 사후 transport fallback으로 작성했다.

현재 추가된 named regression은 21개 PM test와 8개 session-boundary test를 포함한다. 입력 즉시 보존, known 409 새 UUID, unknown 500 동일 UUID, 성공 후 새 UUID, 5분 초과 route 왕복, current 403 membership 재확인, TASK0+schedule1, unobserved predecessor, plan 500 독립성, remounted pending retry 잠금을 실제 App·session·HTTP primitives로 검증한다. 부모의 실제 Chrome 관찰, same-SHA CI, live ERP/Google provider, release와 최종 verdict는 실행하지 않았다. 최종 root test/typecheck/build 실행 후 이 결과의 상태를 갱신한다.

최종 root 검증은 `root-test-final.meta.json`의 `pnpm frontend:test`, 30 files / 355 tests / exit 0, `root-typecheck-final.meta.json`의 `pnpm frontend:typecheck` / exit 0, `root-build-final.meta.json`의 `pnpm frontend:build` / exit 0으로 완료했다. 최종 whitespace scan도 exit 0이다. 따라서 이 구현 결과는 부모의 independent review와 same-SHA CI를 기다리는 **ready-for-review** 상태다.

## repair attempt 6 결과

부모 승인 attempt6에서 `ProjectPlan.tsx`의 TaskQuickCreate reconciliation만 보수했다. reconciliation은 현재 `operationKey`와 일치하는 mutation 후보만 보고, variables/body/requestId/operationId/attemptId/sessionGeneration의 런타임 shape를 확인한 뒤 기존 session·operation·attempt 비교를 수행한다. 기존 TASK 상태 PATCH의 body 없는 mutation이 cache에 남아 있어도 quick-create POST 성공 후 화면이 빈 상태가 되지 않는다. 변경 테스트는 실제 `http().on` PATCH 1회 → POST 1회 순서를 같은 App/QueryClient에서 실행하고 성공 문구·작업 화면 복귀를 확인한다.

추가 회귀는 inactive route에서 Date와 실제 `setTimeout`/`clearTimeout` fake timer를 전진한 뒤 draft title을 보존하고, 현재 overview TASK 403에서 exact plan/member `removeQueries` 호출·project 재확인·TASK 작성 동작 잠금·같은 generation own draft 보존을 확인한다. session-boundary 회귀는 deferred TASK POST를 보유한 상태에서 기존 App 401 경계로 종료하고 old-generation draft 제거와 늦은 응답의 login UI 무영향을 확인한다.

최종 실행은 아래 raw transcript에 보존했다.

- `tmp/pm-implementation-2026-10-03/f1/exception-repair6/frontend-test-final.meta.txt`: `pnpm frontend:test`, exit 0, 30 files / 358 tests passed.
- `tmp/pm-implementation-2026-10-03/f1/exception-repair6/frontend-typecheck-final.meta.txt`: `pnpm frontend:typecheck`, exit 0.
- `tmp/pm-implementation-2026-10-03/f1/exception-repair6/frontend-build-final.meta.txt`: `pnpm frontend:build`, exit 0.
- `tmp/pm-implementation-2026-10-03/f1/exception-repair6/p1-update-quick-create-final.meta.txt`: focused real TASK update PATCH → quick-create POST regression, 1 passed / 22 skipped, exit 0.
- `tmp/pm-implementation-2026-10-03/f1/exception-repair6/mixed-cache-before-fix.md`: fresh baseline은 guard 편집 전 실행하지 않았으며 frozen R5의 실제 failure-before raw 경로를 연결한다.

브라우저·same-SHA CI·live ERP/Google/provider·release는 부모 소유로 실행하지 않았다. Fixture와 이 검사는 offline-contract-only이며 live integration 성공을 의미하지 않는다. 지정된 named assertion 중 이 결과에서 새로 추가하지 않은 항목은 기존 유효 경로와 부모 검토가 필요하며, broad test count로 충족을 주장하지 않는다. 상태는 **ready-for-review**다.
