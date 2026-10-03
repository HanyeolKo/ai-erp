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
