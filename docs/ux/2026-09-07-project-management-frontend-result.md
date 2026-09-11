# 프로젝트 관리 전면 재설계 프론트엔드 구현 결과

- 작업일: 2026-09-07
- 구현 역할: `ai-erp-implementer`
- 실제 호출: `gpt-5.6-luna`, reasoning `high`
- fallback 사유: 기본 implementer인 `gpt-5.3-codex-spark`의 quota가 제한되어 상위 승인된 Luna/high fallback을 사용했다. Spark로 가장하지 않았다.
- 상태: `ready-for-review` (최종 verdict와 브라우저 확인은 상위 조정자 소유)

## 구현 범위

- 로그인 후 진입을 이름 기반 `프로젝트 선택` 로비로 바꾸고, 이름만 받는 직접 프로젝트 생성과 세션별 `requestId`를 구현했다. pending 또는 불확실 결과는 다이얼로그 닫기와 route unmount/remount 뒤에도 유지되며, 같은 요청 결과를 확인하기 전 새 POST를 막는다. route 이탈 뒤에도 mutation 결과를 retained attempt에 반영하고, completed 상태가 authoritative GET 전 cached secret을 노출하지 않도록 했다.
- 선택 프로젝트 셸에 프로젝트 이름, `개요·일정·구성원` 탐색, 알림, 표시 이름과 이메일을 포함한 계정 메뉴를 적용했다. 모바일은 brand/account 1행과 menu/project 2행으로 구성하고, 메뉴 Escape 닫기·포커스 복귀·skip link focus를 보장한다.
- 공유 초대는 현재 상태 GET 후 명시적인 POST/DELETE만 수행한다. ASCII Crockford 코드 및 same-origin join URL만 허용하고, 403/404·500·만료·철회·복사 API 부재를 안전한 상태와 수동 복사 안내로 처리한다. recovery는 project별 single-flight와 reactive revision을 사용하며 stale read가 cached secret을 되살리지 못한다.
- join preview는 이미 구성원인 경우 기존 역할을 유지한다고 표시하고, 409 뒤 fresh GET에서만 참여 가능 상태를 다시 결정한다. 만료 타이머와 클릭 직전 만료 검사를 포함하며 모든 await 이후 captured-session을 재검사한다.
- 구성원은 표시 이름·이메일·한국어 역할을 보여주고 역할은 명시적인 `저장/취소`로만 반영한다. manager 권한이 사라지면 저장·초대 및 cached share dialog가 즉시 차단된다. 기존 이메일 초대의 수락·거절·만료·권한 복구·계정 전환을 유지했다.
- 알림의 읽음 mutation/pagination, 안전한 project/schedule deep link와 한국어 event label, account-wide Calendar 설정 링크·상태 label·configuration-required 차단을 복원했다.
- 일정 worker가 소유한 schedule/detail/form 화면과 participant identity 변경을 보존하면서 공통 Shell/API/state 경계와 충돌하지 않게 연결했다.
- 빈 일정 개요의 다음 행동을 역할별로 정리했다. MANAGER는 구성원 초대 안내를, MEMBER는 첫 일정 작성 안내를, VIEWER는 읽기 전용 설명을 보며 생성·초대 동작은 노출되지 않는다.

## 검증

소유한 기존 프론트엔드 회귀 묶음은 다음 명령으로 `82/82` 통과했다. 이후 추가·수정한 검사는 아래 개별 로그와 상위의 최종 전체 실행 결과로 확인한다. 개별 실행의 합계를 하나의 실행 결과로 간주하지 않는다.

`pnpm --dir frontend exec vitest run src/auth.test.tsx src/project-create.test.tsx src/project-recovery.test.tsx src/project-redesign.test.tsx src/recovery.test.tsx src/regression.test.tsx src/session-boundary.test.tsx --reporter=dot`

기존 묶음 로그: `tmp/frontend-owned-suite-final.log`

핵심 생성·공유·세션 경계 묶음은 `22/22` 통과했다. 생성·회귀 묶음은 `26/26` 통과했다. 생성 테스트에는 successful POST 뒤 지연된 project refetch 중 route unmount/remount를 거쳐도 동일 attempt가 유지되고 두 번째 POST가 발생하지 않는 검사가 포함된다. join 409 뒤 자동 refetch 500 후 수동 fresh GET으로 이미 구성원/open-project 상태를 회복하는 검사도 포함된다.

생성·회귀 묶음 로그: `tmp/frontend-create-regression-final.log`

추가 경계 로그: `tmp/frontend-contract-boundaries-final.log`, `tmp/share-route-persistence-test2.log`, `tmp/share-session-recovery-test.log`, `tmp/session-stale-create-final3.log`

역할별 빈 개요 보강 검사는 `tmp/frontend-viewer-onboarding-focused.log`에서 `1/1` 통과했고, 마지막 변경 후 `pnpm --dir frontend run typecheck`도 exit 0 (`tmp/frontend-final-typecheck-after-copy.log`)이다.

`pnpm --dir frontend run typecheck`와 `pnpm --dir frontend run build`는 모두 exit 0이다. Vitest는 Windows Vite child-process 제한으로 기본 sandbox에서 `spawn EPERM`이 발생해 승인된 elevated 실행으로 검증했다.

root가 별도 executor에 이관한 `access-recovery-boundary.test.tsx`와 `round5.test.tsx`는 기존 이메일 초대 회귀 복원 후 `35/35` 통과했다(`tmp/project-regression-migration.log`). `behavior.test.tsx`, `closure.test.tsx`, `project-schedule-redesign.test.tsx` 및 `Schedules.tsx`, `Detail.tsx`, `ScheduleForm.tsx`, `schedules.css`는 일정 worker 소유다. `tmp/project-redesign-preview.mjs`는 별도 Luna fixture worker 이관 후 schedule detail GET participant identity 보강만 반영했으며 `node --check` exit 0 (`tmp/project-redesign-preview-node-check.log`)이다. 서버 실행과 브라우저 조작은 root가 수행했다.

2026-09-07 16:20 KST 상위 최종 실행: `pnpm --dir frontend exec vitest run --maxWorkers=2` — exit 0, 13개 파일 **199/199 통과**, `tmp/project-redesign-frontend-final.log`. 마지막 초대 완료 경합과 역할별 안내 수정을 포함한다. `pnpm frontend:build` — exit 0, `tmp/project-redesign-frontend-build.log`; 생성 자산은 `index-DAbxgsdf.js`다. 초대 완료 잠금은 완료 전에 시작한 GET으로 해제되지 않으며, 실제 성공 POST 응답 또는 완료 후 새 GET을 수용한다.

## 주요 파일

- `frontend/src/screens/Account.tsx`
- `frontend/src/screens/ProjectStart.tsx`
- `frontend/src/state.ts`
- `frontend/src/api/client.ts`
- `frontend/src/ui.tsx`
- `frontend/src/app.css`
- `frontend/src/test/http.ts`
- `frontend/src/project-create.test.tsx`
- `frontend/src/project-redesign.test.tsx`
- `frontend/src/session-boundary.test.tsx`

이 문서는 로컬 canonical 결과 문서다. 최종 combined suite, 브라우저 fixture 확인, verdict 및 Notion `AI 생성문서 관리` 동기화는 상위 조정자가 수행한다.
