# 통합 프로젝트 UX 프런트엔드 구현 결과

- 상태: `ready-for-review`
- 실행 역할: canonical implementer, escalation rung 3 (`gpt-5.6-sol`, `medium`)
- 모델 사용량: `null` (실행 환경에서 제공되지 않음)
- 작업 디렉터리: `D:\onedrive\Documents\ChatGPT\AI ERP\tmp\unified-project-ux-resume`
- 기준 커밋: `47dac12ff50bf3bd3e696963c40de7c1cc30ad1e`
- 결과 작성일: 2026-09-30

## 구현 결과

기존 PR 17/18 조합을 유지하면서 일정 작업공간과 알림 팝업의 최종 교정을 완료했다. 캘린더의 Agenda와 조건부 범위 밖 결과가 동일한 `일정 목록` 영역을 중복 생성하던 문제를 제거했고, 명시적인 기간 필터에서 실제 범위 밖 결과가 있을 때만 별도 `범위 밖 일정 목록`을 표시한다. 날짜 팝업은 다시 도입하지 않았으며 인라인 날짜와 오늘 이동을 유지했다.

작업공간 목록의 시간 변경 버튼을 실제 시간 편집 대화상자와 PATCH 흐름에 연결했다. 요청 본문은 제목, 설명, 시작/종료 시각, 행 버전만 포함하며 참가자를 재전송하지 않는다. 목록의 상세/편집 링크는 활성 보기와 페이지 반환 문맥을 유지한다.

접근 거부 후 복구는 프로젝트, 작업공간 메타데이터, 현재 대상 쿼리의 새 읽기가 모두 성공해야 잠금을 해제한다. 성공한 대상 결과를 정확한 React Query 키에 기록한 뒤 잠금을 해제하므로 이전 401/403/404 오류가 즉시 다시 잠그지 않는다. 실패한 복구는 결과와 모든 쓰기 기능을 계속 숨기고 잠근다.

대시보드 카드 편집은 깨끗한 초안이면 외부 재조회 값과 행 버전을 동기화하고, 사용자가 수정한 초안이면 외부 값을 덮어쓰지 않는다. 409는 초안을 유지하고 최신 결과를 다시 읽으며, 취소 후 다시 편집하면 최신 서버 값과 버전을 사용한다.

알림 내용은 팝업이 열릴 때만 마운트해 닫힌 알림이 세션 오류를 일으키지 않게 했다. 단일 표시 트리거, 직접 경로 링크, 읽음 처리, Escape 초점 복귀와 모바일 동작을 유지했다. 모바일 아이콘 버튼의 최소 너비를 44px로 맞췄다.

## 기준별 증거

| 기준 | 구현 및 회귀 증거 |
|---|---|
| `SVFE001` | 캘린더가 최대 10페이지를 읽고 중복 ID 때문에 고유 항목이 1000개보다 적어도 10번째 페이지의 `hasMore`를 부분 결과로 표시한다. 겹치는 10페이지 회귀 테스트 포함. |
| `SVFE003` | MEMBER의 공유 보기 저장은 PERSONAL POST 복사본, MANAGER는 SHARED PATCH이며 범위가 바뀌지 않는 회귀 테스트 포함. |
| `SVFE006` | 뒤늦은 접근 오류가 보호 결과와 쓰기를 잠그며, 프로젝트/메타데이터/정확한 대상 읽기가 모두 성공할 때만 복구한다. 실패 복구와 성공 복구 회귀를 갱신했다. |
| `SVFE007` | 깨끗한 편집 중 외부 재조회 동기화, 더러운 초안 보존, 409 초안 보존, 취소 후 최신 값 채택을 하나의 상태 전이 회귀 테스트로 검증했다. |
| `SVFE009` | 보기 변경 시 `page=0`을 같은 URL 갱신에 기록하며 캘린더 mode/date를 보존한다. page 2에서 보기 변경 회귀 테스트 포함. |
| `SVFE010` | 중첩 단일 선택 옵션의 추가/이름 변경 초안이 닫기 후 `계속 편집`에서 유지되는 회귀 테스트 포함. 기존 Form/Detail 이탈 방지 회귀도 전체 스위트에서 통과했다. |
| `SVFE011` | NUMBER 필터 입력은 `step="any"`이고 유한 소수 `1.25`를 숫자로 저장하는 회귀 테스트 포함. |
| `SVFE013` | 위 기준과 목록 시간 변경/반환 문맥을 포함한 새 집중 회귀를 추가했다. 전체 결과는 24개 파일, 304개 테스트 통과다. |
| `UPUXFE001` | 날짜 선택 팝업 없이 기존 인라인 날짜 입력과 오늘 이동을 유지한다. |
| `UPUXFE002` | 모바일에서 숨겨지는 캘린더 격자를 보완하는 기존 Agenda, 작업, 권한 제어를 유지한다. |
| `UPUXFE003` | 목록 시간 버튼을 동작하는 편집 흐름에 연결하고 상세/편집 반환 문맥을 보존한다. 시간 전용 PATCH 본문 회귀 포함. |
| `UPUXFE004` | legacy month/week/list 문맥을 builtin calendar/list와 calendar mode로 복구하고 page를 0으로 원자적으로 초기화한다. |
| 알림 팝업 | 닫힌 상태의 지연 마운트, 단일 트리거, 안전한 직접 링크, 읽음/페이징, 안정 해시, 초점/Escape, 모바일 상호작용 회귀가 통과했다. |

## 실행한 검사

모든 명령은 위 작업 디렉터리에서 실행했다.

| 검사 | 정확한 명령 | 종료 코드 | 결과 | 원시 로그 |
|---|---|---:|---|---|
| 집중 회귀 | `pnpm --dir frontend exec vitest run --configLoader native --pool threads src/schedule-workspace.test.tsx src/project-recovery.test.tsx src/calendar-direct-manipulation.test.tsx src/calendar-direct-recovery.test.tsx src/access-recovery-boundary.test.tsx src/session-boundary.test.tsx` | 0 | 6 files, 76 tests passed | `docs/planning/unified-project-ux/evidence/frontend-final-focused.log` |
| 전체 프런트엔드 테스트 | `pnpm --dir frontend exec vitest run --configLoader native --pool threads` | 0 | 24 files, 304 tests passed | `docs/planning/unified-project-ux/evidence/frontend-final-full.log` |
| TypeScript | `pnpm --dir frontend run typecheck` | 0 | `tsc -b --pretty false` 통과 | `docs/planning/unified-project-ux/evidence/frontend-final-typecheck.log` |
| 프로덕션 빌드 | `pnpm --dir frontend exec vite build --configLoader native` | 0 | 85 modules transformed, bundle 생성 | `docs/planning/unified-project-ux/evidence/frontend-final-build.log` |
| 미리보기 서버 구문 | `node --check scripts/ui-preview-server.mjs` | 0 | 통과 | `docs/planning/unified-project-ux/evidence/frontend-final-preview-check.log` |
| diff 위생 | `git diff --check` | 0 | 오류 없음. 기존 작업 파일의 LF→CRLF 경고 3건만 출력 | `docs/planning/unified-project-ux/evidence/frontend-final-diff-check.log` |

전체 테스트 로그의 jsdom `Not implemented: navigation to another Document` 2건과 장기 미래 시각을 사용하는 테스트의 `TimeoutOverflowWarning` 1건은 테스트 실패가 아니며 304개 테스트가 모두 통과했다.

## 실행하지 않은 검사와 인계

- 브라우저 실측과 최종 접근성 확인은 부모가 소스 고정 후 기존 Chrome page 3에서 수행한다. 구현자는 부모 소유 브라우저 세션을 조작하지 않았다.
- 백엔드 219개 CI와 V10 검증은 기준 커밋의 이미 수락된 증거를 재사용했으며 다시 실행하지 않았다.
- Git 커밋, push, PR, 배포, 이슈 종료는 부모 소유이므로 실행하지 않았다.
- Notion 동기화는 최종 통합 문서와 함께 부모가 수행하도록 인계한다. 이 파일이 로컬 canonical source다.

소스는 위 기준 커밋 위의 현재 작업 트리로 고정했으며, 이 결과 이후 구현자 변경은 없다. 최종 커밋 SHA와 동일 SHA CI/독립 검토 연결은 부모가 기록한다.
