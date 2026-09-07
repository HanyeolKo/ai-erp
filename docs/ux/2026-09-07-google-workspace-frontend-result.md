# Google Workspace 프론트엔드 구현 결과

- 작업일: 2026-09-07
- 구현 역할: `ai-erp-implementer`
- 실제 호출: `gpt-5.6-luna`, reasoning `high`
- fallback 사유: 기본 implementer인 `gpt-5.3-codex-spark`가 quota 제한 상태여서 상위 승인된 Luna/high fallback을 사용했다. Spark로 가장하지 않았다.
- 상태: `ready-for-review` (최종 task-review, 전체 회귀와 브라우저 verdict는 `/root` 소유)

## 변경 범위

- `api/client.ts`에 Google 연결, Drive 개인 조회·프로젝트 참조, Gmail 목록·상세·명시적 발송·receipt, Google Calendar 목록·프로젝트 binding API wrapper와 계약 타입을 추가했다. `generated.ts`는 수정하지 않았으며 backend OpenAPI 재생성 후 parent가 generated-derived alias를 정리할 수 있다.
- `GoogleWorkspace.tsx`에 개인 Google 연결 상태, 기능별 권한 연결, 안전한 로컬 OAuth authorization URL, 연결 해제, 개인 Drive 목록, Gmail INBOX/SENT·검색·일반 텍스트 상세·검토 후 발송 UI를 구현했다. Gmail requestId는 POST 직전에 계정별 sessionStorage에 저장하고 SENDING/UNKNOWN은 receipt 확인만 제공한다.
- `ProjectFiles.tsx`에 개인 Drive와 분리된 프로젝트 파일 참조 목록, 25개 페이지 이동, 명시적 선택·확인 첨부, safe Drive link, 참조 제거 확인을 구현했다. `ProjectCalendarSettings`는 관리자의 writable Calendar 선택, binding/backfill 상태, REAUTH_REQUIRED 복구 경로와 in-flight caveat를 포함한 접근 가능한 해제 Dialog를 제공한다.
- `App.tsx`, `ui.tsx`, `Account.tsx`, `state.ts`에 계정 메뉴·프로젝트 sidebar 진입과 query keys/routes를 연결했다. 개인 Google 캐시는 project 권한 상태와 분리되고, Google 연결 query가 403/500 또는 재조회 중이면 이전 private account/resource data를 표시하지 않는다.
- `vite.config.ts`는 `localhost:5173` strict port와 `/api`, `/oauth2`, `/login` proxy를 `changeOrigin:false`로 구성했다.
- `tmp/google-workspace-preview.mjs`는 `frontend/dist`를 5181에서 서빙하는 in-memory synthetic fixture다. 가상 Drive/Gmail/Calendar/project data와 connection toggles, UNKNOWN→receipt SENT 상태를 제공하며 외부 Google 호출이나 실제 메일 발송을 하지 않는다.

## 검증

- Google Workspace focused synthetic HTTP suite: `8/8` 통과 — `tmp/google-workspace-focused-repair-final.log`.
- Calendar copy alignment 후 재실행한 focused suite도 `8/8` 통과 — `tmp/google-workspace-focused-after-copy.log`.
- 포함한 경계: 기능별 OAuth intent/link와 외부 URL 거부, Drive explicit selection before POST, Gmail review-before-send·POST 전 requestId persistence·route leave/re-entry, UNKNOWN receipt check without resend, project Calendar selection, connection denial→500→fresh-success privacy clearing.
- `pnpm --dir frontend exec tsc --noEmit`: exit 0 — `tmp/google-workspace-typecheck-latest.log`.
- `pnpm --dir frontend build`: exit 0 — `tmp/google-workspace-build-latest.log`; current assets `index-CoVatjBY.js`, `index-CgCwjzu7.css`.
- 승인된 Calendar 문구 보완 후 typecheck/build도 exit 0 — `tmp/google-workspace-typecheck-after-copy.log`, `tmp/google-workspace-build-after-copy.log`; current JS asset `index-Cbr3jyVV.js`.
- `node --check tmp/google-workspace-preview.mjs`: exit 0 — `tmp/google-workspace-preview-node-check-latest.log`. Missing fixture receipt IDs return 404; fixture server는 시작하지 않았다; `/root`가 최종 브라우저 검증 시 시작한다.

## 미실행 및 parent 확인 필요

- 실제 Google OAuth consent, Drive ACL, Calendar 외부 쓰기, Gmail 외부 발송은 계약대로 실행하지 않았다.
- backend가 Google OpenAPI를 생성하기 전 상태에서 local wrapper types를 사용했다. backend generation 완료 후 `generated.ts` 직접 편집 없이 type alias를 parent가 검토해야 한다.
- 전체 frontend 회귀, backend/Java25, deployment 검증과 320/375/1440px 브라우저·키보드 검증은 `/root`가 수행한다.

로컬 canonical 결과 문서이며, Notion `AI 생성문서 관리` 동기화는 상위 조정자가 수행한다.
