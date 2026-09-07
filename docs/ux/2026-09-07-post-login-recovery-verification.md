# 로그인 후 프로젝트 진입 복구 구현·검증 기록

- 작성일: 2026-09-07 (Asia/Seoul)
- 로컬 원본: `C:/Users/USER/.codex/worktrees/60aa/AI ERP/docs/ux/2026-09-07-post-login-recovery-verification.md`
- 작업 브랜치: `codex/post-login-project-recovery`
- 검증한 구현 커밋: `ddb1f7c1fff6858b0736edb5c2099a344f4c987d`
- [PR #8](https://github.com/HanyeolKo/ai-erp/pull/8), [성공한 전체 CI](https://github.com/HanyeolKo/ai-erp/actions/runs/34074982221)
- 최종 독립 `task-review`: **PASS**, 필수 미해결 결함 0개. `/root/ui_plan_reviewer`가 위 커밋의 원시 CI 기록·코드·테스트와 AC1–AC8을 대조하고 9개 필수 평가 기준을 모두 수용했다. 상위 조정자가 이 판정을 받아 구현·검증 완료로 판단했다. PR 병합과 운영 반영 상태는 별도 전달 절차로 확인한다.

## 문제와 변경 결과

운영 사이트의 로그인된 Chrome에서 참여 프로젝트가 없는 상태를 읽기 전용으로 확인했다. 기존 화면에는 빈 목록 문구와 사용할 수 없는 페이지 버튼만 있어 프로젝트 생성·참여·계정 복구로 이어지지 않았다. 기준 코드는 `24659a6`이며, 화면기획 v0.7의 그룹 소유자·관리자 생성 권한을 확인한 뒤 실제 UI/UX 전담 에이전트가 계획을 작성하고 별도 에이전트가 생성 확장안을 검토했다.

이제 프로젝트 선택 화면은 생성 권한 조회, 그룹과 이름 입력, 초대 링크 입력, 새로고침, 계정 변경을 제공한다. 생성할 수 없는 경우에는 그룹 없음·일반 구성원·역할 미설정을 구분하고 계정과 그룹 정보를 담은 요청문을 복사할 수 있다. 요청문은 외부로 자동 전송되지 않는다.

서버는 본인 그룹의 생성 가능 여부를 페이지 단위로 반환하고, 생성 POST에서 저장된 OWNER/ADMIN 권한을 다시 확인한다. 프로젝트와 생성자 MANAGER 멤버십을 한 트랜잭션에서 저장한다. V6는 기존 그룹 역할을 NULL로 보존하며 기존 ProjectRole을 바꾸지 않는다. 생성 이름은 공백 제거 후 1~200자로 검증한다. 배포의 엄격한 마이그레이션 목록과 체크섬에도 V6를 포함했다.

권한 거부는 일시적인 조회 실패와 분리해 보관한다. 거부 뒤 500 응답이나 화면 재진입으로 보호된 데이터와 작업 버튼이 다시 나타나지 않으며, 실제 최신 조회로 복구한다. 생성 결과가 불확실하면 자동 재제출을 막고 새 프로젝트 목록을 확인하도록 안내한다. 세션 종료는 401 또는 로그아웃 성공 응답 관찰 시점에 적용해 늦은 응답·콜백·CSRF 조회가 이전 세션의 화면이나 쓰기 요청을 되살리지 못하게 한다.

## 실행 역할과 판단

- 화면 기획: 실제 `/root/ui_ux_designer` 전담 실행. 계획은 `2026-09-07-post-login-recovery-plan.md`, 독립 계획 검토는 `2026-09-07-post-login-recovery-review.md`이며 생성 확장 AC1–AC8에 PASS를 받았다.
- 초기 구현: 실제 `gpt-5.3-codex-spark`, high. CLI 세션은 프런트엔드 `01a07967-40b7-76a2-93ca-e51befd65ece`, 백엔드 `01a07967-4495-76a3-bafe-5b2e045201d1`이다. 임시 실행 증거는 `tmp/spark-frontend-events.jsonl`, `tmp/spark-backend-events.jsonl`에 남아 있다.
- 후속 구현·회귀 검사: Spark 전용 사용량 제한 이후 사용자의 경량 모델 허용 지시에 따라 실제 `gpt-5.6-luna/high`로 호출한 `luna_frontend_corrections`, `luna_session_tests`, `luna_backend_finish`가 맡았다. 정적 래퍼 설정을 실행 증거로 대신하지 않았다.
- 상위는 권한 계약, 원자적 저장, 마이그레이션 보존, 비동기 응답 순서, 테스트 판정 기준을 결정하고 독립 리뷰를 요청했다. 모델 배정 지시 이후 상위가 앱 코드나 테스트를 직접 작성하지 않았다.
- 프런트엔드 상위 리뷰는 남은 6개 결함과 이전 세션의 생성 실패가 새 세션을 오염시키는 경계를 수정하도록 반환했다. 테스트의 조기 통과 가능성도 보강한 뒤 권한·세션 검사 24개를 독립 실행해 PASS를 확인했다.
- 하네스 PR #7의 main 병합 커밋 `a7f9b329d676a5babd6feae4b95043b5010a506d`를 충돌 없이 반영했다. 현재 저장소의 전담 기획·경량 구현·상위 판단 규칙을 적용한다.

## 수용 기준과 증거

| 기준 | 확인한 동작 | 주요 증거 |
| --- | --- | --- |
| AC1 | 빈 상태에서 생성·참여·새로고침·계정 변경을 제공하고 로딩/오류와 구분 | `recovery.test.tsx`, `project-create.test.tsx`, 모바일 빈 상태 화면 확인 |
| AC2 | 유효한 초대 열기와 한 번의 수락 후 실제 프로젝트 진입, 외부/잘못된 링크 거부 | `recovery.test.tsx`, 로컬 브라우저 PENDING → ACCEPTED → p1 대시보드 |
| AC3 | 접근 불가/없는 프로젝트의 조회·재시도·프로젝트 선택 경로 | `project-recovery.test.tsx`, `access-recovery-boundary.test.tsx`, 브라우저 404 복구 화면 |
| AC4 | 401/403/404/500·네트워크 실패 구분, 재시도 및 세션 데이터 제거 | `recovery.test.tsx`, `session-boundary.test.tsx`, 생성 후 목록 500에서도 입력과 차단 유지 |
| AC5 | 캐시 이후 권한 거부가 일시 오류나 재진입으로 해제되지 않음, 독립 Calendar 오류 처리 | `access-recovery-boundary.test.tsx` 15개, `project-recovery.test.tsx` |
| AC6 | 후속 페이지·기존 역할·409 복구·안전한 초대 로그인 경로 보존 | 기존 전체 회귀 검사, `session-boundary.test.tsx` 9개 |
| AC7 | OWNER/ADMIN 생성, MEMBER/NULL/타인 그룹 거부, 새 MANAGER 저장, 부분 저장 롤백, V5→V6 보존 | `ProjectCreationApiTest`, `ProjectCreationTransactionTest`, `Phase1PersistenceIntegrationTest`, 후속 그룹 g101 브라우저 생성 |
| AC8 | 권한 조회 상태·사유·복사 결과·입력 오류·중복 제출·POST 권한 변경·성공 후 탐색 갱신 | `project-create.test.tsx`, `access-recovery-boundary.test.tsx`, 실제 생성 브라우저 요청 횟수 1 |

## 검사 결과

전체 CI는 위 구현 커밋에 대해 2026-09-07 11:17 KST에 성공했다. 아래 수치는 업로드된 `verification-artifacts`와 실제 작업 로그를 다시 읽어 확인했다.

- 백엔드 `clean test integrationTest openapi3 bootJar`: 성공. 단위 71개, PostgreSQL 통합 10개, 실패·건너뜀 0개. V5→V6 보존과 잘못된 역할 제약 검사도 포함한다.
- OpenAPI 생성과 `pnpm api:generate`: 성공. 별도 로컬 OpenAPI 계약 검사 6개도 통과했다.
- `pnpm frontend:test`: 11개 파일, 189개 통과. `pnpm frontend:typecheck`, `pnpm frontend:build`: 로컬과 CI 모두 성공.
- 배포 계약: Linux 149개 시나리오·574개 단언 통과. 별도 ERR 보상 1개 시나리오·2개 단언 통과. V6 누락·추가 파일·심볼릭 링크 거부, 체크섬 반영, 빌드 후 SQL 변경 시 Flyway 이전 중단을 확인한다.
- Caddy 검사: 1개 시나리오·25개 단언 통과. Compose 설정, 셸 문법, Docker 이미지 빌드도 CI에서 성공했다.
- 하네스 구조·22개 회귀 검사·UI/UX 런타임 스모크: CI 성공. 로컬 구조 검사와 런타임 스모크도 성공했다.
- CI 산출물의 로컬 읽기 사본: `tmp/ci-34074982221/reports/tests/test/index.html`, `tmp/ci-34074982221/reports/tests/integrationTest/index.html`.

로컬 Windows에는 Docker 실행 환경이 없어 PostgreSQL 통합 실행을 완료하지 못했고 통합 소스 컴파일까지만 확인했다. 실제 PostgreSQL 검증은 성공한 Linux CI로 충족했다. 배포 검사 구현 에이전트의 로컬 Windows 전체 실행 결과는 최종 확보하지 못했으므로 로컬 통과라고 주장하지 않는다.

## 브라우저 검증과 한계

수정 화면은 `127.0.0.1:5178`의 실제 앱과 `tmp/recovery-preview.mjs`의 로컬 테스트 API로 검증했다. 운영 로그인·실데이터 쓰기 검증으로 해석하지 않는다.

- 빈 상태의 관리자 요청문·초대 참여·계정 변경이 보였고 390px 모바일에서 가로 넘침이 없었다. 전체 화면 스크린샷을 확인했다.
- OWNER 생성 및 첫 페이지 100개 비허용 그룹 뒤 g101 생성이 실제 대시보드로 이어졌다. g101 생성은 Enter 제출, POST 1회, 실제 반환 groupId·프로젝트 이름·MANAGER 역할, 새 제목 초점을 확인했다.
- 초대 수락은 POST 1회 후 참여 완료 제목에 초점을 맞추고 프로젝트 열기 링크로 대시보드에 진입했다.
- 생성 500 이후 프로젝트 목록 갱신도 500인 경우 이름을 유지하고 생성 버튼을 계속 차단했다. 삭제/접근 불가 응답은 설명과 재시도·프로젝트 선택을 제공했다.
- 성공 경로와 마지막 빈 상태 화면의 브라우저 콘솔에서 경고·오류가 없음을 확인했다. 이는 모든 외부 Google 연동이나 모든 접근성 항목에 대한 인증을 뜻하지 않는다.

첫 그룹 생성·OWNER 부여의 운영 정책은 확인된 제품 원문에 없으므로 자동 승격이나 임의 설정 API를 추가하지 않았다. 기존 역할이 NULL인 그룹은 명시적인 역할 설정이 필요하다. 이 문서는 병합 전 구현 검증 기록이며, 이후 PR 병합·운영 배포 결과를 대신하지 않는다.

## 보관

이 로컬 파일이 편집 원본이다. Notion `AI 생성문서 관리`에는 동일 내용의 탐색·보관용 사본을 동기화한다.

- [Notion 보관 사본](https://app.notion.com/p/3d4a8ad6aa4a8185ab87d9622eb983af)
- 마지막 동기화: 2026-09-07 (Asia/Seoul), 최종 독립 판정과 CI 증거 포함.
