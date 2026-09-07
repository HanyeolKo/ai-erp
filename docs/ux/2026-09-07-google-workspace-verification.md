# Google Workspace 상위 검증 기록

- 기준: `58bc6d6` 위 Google Workspace 미커밋 변경, 계약 revision 1 및 보완 지시.
- 실행자: `/root`. 독립 판정은 별도 구현 검토 문서가 소유한다.
- 현재 상태: 미완료 보존. 사용자 요청에 따라 이번 세션 배포 후보는 검증된 프로젝트 개편 커밋 `58bc6d6`으로 제한하며, 이 문서의 Google 변경은 배포에서 제외한다. Google 전체 독립 검토는 FAIL/PENDING이다.
- 실제 Google 계정의 메일 발송·Calendar 변경·Drive 권한 변경은 실행하지 않았다.

## 프런트엔드 자동 검증

| 실행 | 결과 | 원시 증거 |
| --- | --- | --- |
| 17:15 전체 Vitest | 14개 파일, 207개 테스트 PASS | `tmp/google-workspace-root-full-frontend.log` |
| 17:36 수정 후 전체 Vitest | 14개 파일, 207개 테스트 PASS, exit 0 | `tmp/google-workspace-root-full-frontend-repair.log` |
| 구현자 최종 typecheck/build | exit 0; JS `index-CoVatjBY.js`, CSS `index-CgCwjzu7.css` | `tmp/google-workspace-typecheck-latest.log`, `tmp/google-workspace-build-latest.log` |

## 실제 Chrome 로컬 검증

`tmp/google-workspace-preview.mjs`의 가상 사용자·프로젝트·Google 응답을 이용했다. 이 fixture는 실제 서버 계약 검증이나 Google 연결 성공을 대신하지 않는다. 별도 브라우저 context `root-google-workspace-verify`, `http://localhost:5181`에서 실행했다.

| ID | 시나리오 | 관찰 |
| --- | --- | --- |
| B01 | 초기 메일 목록과 본문 열기/닫기 | 본문을 텍스트로 표시하고 닫기 뒤 목록 버튼으로 포커스 복귀. 초기 브라우저 console error/warn 없음. |
| B02 | 초기 가상 발송 | 프로그램에서 Dialog를 닫을 때 작성 버리기 confirm이 잘못 뜸. 실제 Chrome에서 재현하여 GW-I16 수정 요청. |
| B03 | 수정된 번들 재검증 | `index-CoVatjBY.js` 실제 로딩 확인. 빈 작성창 Escape 닫기와 작성 버튼 포커스 복귀 확인. 가상 발송 뒤 불필요한 confirm이 뜨지 않음. |
| B04 | 불확실한 발송 결과 복구 | 발송 전 검토 화면을 거쳐 POST 1회. UNKNOWN 안내 표시. fixture 결과만 SENT로 전환하고 ‘전송 결과 확인’을 누르자 SENT 표시. 발송 endpoint 요청 수는 전후 모두 1, receipt 조회만 2회. |
| B05 | 브라우저 저장 범위 | sessionStorage에는 사용자 namespace의 requestId 한 개만 보관. 작성 본문·수신자·OAuth token은 저장되지 않음. 가상 서버 send 기록도 한 개. |
| B06 | 모바일 폭 | 실제 device emulation 390×844 적용. Gmail 결과 화면 document width 390, 가로 넘침 없음. 프로젝트 파일/첨부창 이전 관찰은 390px, Dialog 폭 352px, 내부 가로 넘침 없음이며 최종 변경 후 재검증 예정. |
| B07 | 수정 후 Drive 참조 첨부·제거 | 가상 회의기록.txt를 명시적으로 첨부하여 이름·첨부자 김관리자 표시를 확인했다. 원본을 삭제하지 않는다는 확인 후 참조를 제거했고 기존 운영계획 참조는 유지됐다. |
| B08 | Calendar 선택·해제 | 선택 전 연결 버튼 비활성, 키보드로 운영 Calendar 선택 후 명시적 연결 및 담당자 표시를 확인했다. 해제 Dialog의 기존 일정 보존·이미 발행한 요청 안내를 확인하고 해제 후 NOT_BOUND로 돌아왔다. 최종 번들 `index-Cbr3jyVV.js`, 390×844에서 실행했다. |

## 세션 마무리 시점의 서버 결과

- 상위 전체 실행 `test openapi3 compileIntegrationTestJava`: 114개 중 113개 통과, OAuth 중첩 state 테스트 한 개 NPE로 실패했다. 실패 때문에 뒤 작업 완료를 주장하지 않는다. 원시 로그 `tmp/google-workspace-root-backend-final.log`.
- 마지막 identity 보완과 Calendar audit 두 수정은 구현자에게 한정 인계했다. 해당 최종 결과는 구현 결과 문서에 기록하며 전체 Google PASS로 확대하지 않는다.
- Workspace focused 11/11은 실제 HTTP·DTO·MIME 및 mock 동시 claim 검사 통과다. 독립 검토에서 실제 JPA merge 기반 중복 claim, receipt CAS, 본문 deadline, Drive freshness/저장 권한 문제가 남았으므로 동시 발송 안전성을 입증하지 않는다.
- 최신 frontend focused 8/8 및 typecheck/build 통과. 전체 207개 통과 이후 마지막 변경은 Calendar 안내 문구다.
- 이번 상위 배포 계약 재실행은 상대 경로 `.` 입력으로 canonical absolute path 전제에서 실패했다. 이 실행을 V8 검증 통과로 기록하지 않는다. 프로젝트 개편 커밋의 앞선 145/560 결과와 구분한다.

## 남은 검증

- 권한 거절 후 private Dialog 차단과 최종 desktop/mobile 및 keyboard의 전체 경계 흐름.
- 서버 실제 context, OAuth callback/refresh/경쟁, Drive/Gmail/Calendar synthetic HTTP, OpenAPI 생성, 배포 migration 검증.
- Docker가 없어 PostgreSQL/Testcontainers는 현재 미실행. 실행 전 PASS로 바꾸지 않는다.
- 독립 검토 GW-I01~17의 수정 및 재검토.

로컬 기준 원본: `C:/Users/USER/.codex/worktrees/60aa/AI ERP/docs/ux/2026-09-07-google-workspace-verification.md`. 최종 갱신 후 Notion `AI 생성문서 관리`에 보관한다.

## 최종 사용자 지시

이번 세션은 프로젝트 개편과 미완성 Google 작업을 함께 커밋·push하고 Draft PR만 생성한다. 앞선 배포 계획은 철회됐으며 병합·배포하지 않는다. 최신 수정은 미검증이며 전체 독립 검토 FAIL/PENDING을 유지한다. 사용자는 localhost:5173 등록 완료를 알렸으나 실제 OAuth 연결은 미검증이다. Notion 보관은 무료 블록 한도 초과(403)로 실패하여 최신 문서는 로컬 원본에 보존한다.
