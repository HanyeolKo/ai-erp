# 프로젝트 관리 재설계 독립 계획 검토

- 검토일: 2026-09-07 (Asia/Seoul)
- 대상: [UI 전문 기획](2026-09-07-project-management-redesign-plan.md), 상위 구현 계약
- 실제 기획자: `/root/project_redesign_designer` (`ai-erp-ui-ux-designer`)
- 실제 독립 검토자: `/root/ui_plan_reviewer`; 상위 결정 소유자: `/root`
- 기준 코드: `1e2435b7b88fdb91e234e74122e5a4b430d3f50c`
- **ui-plan-review: PASS**, 필수 계획 결함 0개. 이는 구현·브라우저 검증의 통과 판정이 아니다.

## 독립 판정 근거

| 필수 기준 | 판정과 근거 |
| --- | --- |
| 전담 경로 | 실제 별도 UI 기획 위임·완료 문서를 확인했다. 상위가 앱 코드를 먼저 작성하지 않았다. |
| 사용자·과업 | 첫 사용자·기존 참여자·관리자·초대 수신자와 범위가 명시됐다. 사용자가 거부한 PR #8의 요청문·그룹 선행 조건을 대체한다. |
| 사용자 흐름 | 프로젝트 로비 → 직접 생성 → 프로젝트 내부 → 공유 초대 → 확인 후 가입이 이어진다. 계정 Calendar 연결과 프로젝트 일정을 구분한다. |
| 화면 계약 | 탐색 깊이·다이얼로그·목록 페이지·전체 화면 시각 체계가 구체화됐다. UI 그룹 선택 없이 이름만으로 생성한다. |
| 상태 | 생성 응답 유실·초대 발행 결과 불명·권한 상실·세션 교체·만료·재가입 역할 보존을 포함한다. |
| 접근성 | 키보드, 다이얼로그 포커스, 대비, 320~1440px와 200% 확대 기준을 명시했다. 실제 구현 검증은 별도다. |
| 설계 이유 | 실제 코드 문제와 고정 지침의 채택·배제 근거가 연결된다. |
| 검증 | AC-PM-01~11 및 D1~D8이 확정됐다. 아직 실행하지 않은 앱·DB·브라우저 검사를 구분했다. |
| 문서 소유 | 로컬 원본과 Notion 동기화 책임을 명시했다. |

## 상위가 확정한 구현 경계

- 직접 생성은 새 내부 그룹·OWNER·프로젝트·MANAGER를 같은 트랜잭션에 만든다. 지정한 기존 그룹이 잘못됐거나 권한이 없으면 실패하며 새 그룹 생성으로 몰래 전환하지 않는다.
- 새 공유 초대는 기존 이메일 전용 초대와 다른 테이블·계약이다. 프로젝트 MANAGER만 발급·철회하며 신규 참여자는 MEMBER다. 기존 참여자의 역할은 바꾸지 않는다.
- 역할 변경·초대 발급/철회·가입은 프로젝트 잠금 뒤 현재 권한과 코드를 다시 읽는다. 마지막 관리자를 강등하지 못한다.
- 사용자와 requestId에 묶인 생성 재시도는 같은 프로젝트를 반환한다. 초대 회전 결과 불명은 현재 코드를 새로 조회해 회복하며 자동 회전 재시도하지 않는다.
- 생성 시도와 초대 불명 상태는 다이얼로그 닫기·화면 재진입으로 사라지지 않도록 구현 계약에 추가했다. 로그인 세션이 바뀌면 이전 상태를 제거한다.
- 표시 이름 조회는 내부 모듈 API이며 공개 계정 조회 엔드포인트가 아니다. 가입 전 초대한 사람 이름이 이메일 형식이면 일반 명칭을 쓴다.
- 공유 초대는 인증·검증된 사용자와 80비트 난수 코드를 전제로 한다. 별도 요청 제한 계층은 이번 범위 밖이며 구현한 것처럼 주장하지 않는다.

## 실행·보관 상태

계획과 두 구현 계약은 상위가 실행 가능으로 수용했다. 독립 검토가 요구한 실제 생성 파일 경로 `frontend/src/api/generated.ts` 보호와 다이얼로그·화면 재진입 상태 보존·실패 후 새 조회 회귀를 프런트엔드 계약에 반영했다. 최종 구현은 별도 증거로 검토한다. 실제 구현은 Spark/high, 한도 도달 시 승인된 Luna/high만 사용한다. 이번 실행 준비 시점 Spark 5시간 사용량은 100%여서 Luna/high 대체를 선택했다.

이 파일이 편집 원본이다. 로컬 경로: `C:/Users/USER/.codex/worktrees/60aa/AI ERP/docs/ux/2026-09-07-project-management-redesign-review.md`. Notion `AI 생성문서 관리` 보관본은 상위가 동기화한다.

## 구현 중간 검토: 백엔드 r3

2026-09-07 독립 검토자 `/root/ui_plan_reviewer`는 초대·생성·마이그레이션 검증 보완에 대해 **소스 수준 PASS**를 판정했다. 실제 PostgreSQL PID의 Lock 대기 관찰, repository spy의 원래 delegate 호출, 잠금 연결 해제 후 worker 종료 확인, 같은 사용자 동시 가입의 membership/event 각 1건, 기존 MANAGER/VIEWER 유지, 미검증 사용자 가입 거절, 다섯 생성 레코드의 rollback, 정확한 마지막 관리자 충돌 예외, V5→V6 및 V6→V7 검사를 확인했다.

이 판정은 전체 구현 완료 판정이 아니다. PostgreSQL 동시성·rollback·migration의 실제 실행은 Docker 환경 검증 대기이며, 진행 중인 참석자 identity 확장과 프론트엔드 회귀 검사는 별도 판정 대상이다. 상위 에이전트 실행 증거는 단위·API 계약 검사 81개 통과, 배포 계약 145개 시나리오/560개 단언 통과, 갱신된 V7 집중 검사 4개 시나리오/23개 단언 통과다. Windows 배포 검사는 명시적인 명령 대역을 사용하며 실제 Linux 소유권·잠금 검사를 대신하지 않는다.

## 최종 소스 및 로컬 검증 검토

2026-09-07 16:20 실행 결과까지 독립 검토했다. 대상은 위 기준 커밋 이후 현재 작업 트리와 두 구현 계약의 참석자 identity 부록 revision 2다. **현재 소스·로컬 검증: PASS. 전체 task-review: PENDING.** 남은 필수 조건은 Docker를 사용할 수 있는 환경에서 실제 PostgreSQL 통합검사를 실행하고 결과를 확인하는 것이다. 컴파일과 테스트 소스 검토를 실제 DB 실행 통과로 간주하지 않았다. 현재 소스에서 추가로 수정을 요구할 확정 결함은 없다.

| task-review 기준 | 판정과 근거 |
| --- | --- |
| Parent contract | PASS — 전문 기획과 상위 READY 계약, bounded participant identities revision 2에 대조했다. 임시 identity 실행 계약의 자체 revision 1은 동일 범위다. |
| Role boundaries | PASS — 별도 전문 기획·독립 검토·구현 위임 경로를 확인했다. 구현자는 코드와 실행 증거를 제공했으며 최종 판정은 검토자가 소유한다. |
| Model invocation | PASS — 상위 대화의 실제 Spark/high CLI 실행과 quota 중단, 승인된 Luna/high 위임·완료 메시지를 확인했다. 실제 `/root/project_redesign_backend`, `/root/project_redesign_frontend`, `/root/project_regression_migration` 실행 증거를 사용했으며 정적 모델 설정만으로 판정하지 않았다. |
| Scope safety | PASS — 프로젝트·초대·권한·일정 identity, 대응 UI·검사·V7 배포 계약 범위다. Java 25를 유지하고 생성 API 타입은 정상 OpenAPI 경로로 갱신했다. 공개 프로필 검색·전역 구성원 순회·새 요청 제한 계층은 추가하지 않았다. |
| Method consistency | PASS — 사용자별 생성 멱등성, 프로젝트 잠금 후 현재 권한 재검사, 초대 결과 불명의 GET 회복, 계정 단위 Calendar 구분을 유지했다. 테스트 병렬 실행의 자원 경합은 기존 timeout을 늘리지 않고 `--maxWorkers=2`로 재검증했다. |
| Verification | PENDING — 아래 로컬 실행은 통과했다. PostgreSQL 동시성·rollback·마이그레이션 검사는 컴파일 및 oracle 검토까지이며 실제 실행은 남았다. |
| UI gate compliance | PASS — 이 문서의 새 프로젝트 관리 전문 기획 PASS 이후 구현했다. 이전 PR #8의 판정을 이번 화면 승인으로 재사용하지 않았다. |
| Acceptance mapping | PASS — AC-PM-01~11의 구현·검사·브라우저 증거와 DB 실행 대기를 아래에 연결했다. 매핑 통과가 미실행 DB 요구의 통과를 뜻하지 않는다. |
| Readiness | PASS — 현재 결과는 독립 검토 가능한 상태다. 로컬 수용과 전체 task-review PENDING을 구분하며 상위가 최종 수용·공개·병합을 결정한다. |

### 수용 조건별 증거

| 조건 | 확인한 구현·증거와 남은 경계 |
| --- | --- |
| AC-PM-01 | `App.tsx`, 프로젝트 로비와 라우트 회귀. 상위 로컬 브라우저에서 로비 업무 메뉴에 Calendar가 없음을 확인했다. |
| AC-PM-02 | `ProjectStart.tsx`, 생성 멱등성·트랜잭션 구현과 테스트. 브라우저 생성 POST 1회 및 실제 프로젝트 진입을 확인했다. 새 그룹·OWNER·MANAGER·요청 매핑의 원자성은 실제 PostgreSQL 실행 대기다. |
| AC-PM-03 | 공통 탐색과 실제 프로젝트 ID를 사용하는 화면·직접 링크 회귀. 실제 다른 화면 진입을 확인한 뒤 재진입하는 검사로 상태 보존을 검증했다. |
| AC-PM-04 | `ShareInvitationDialog`의 프로젝트 내부 발행·복사·공유·철회와 관리자 경계. 상위 브라우저 발행 POST 1회, Escape 후 초대 버튼 포커스 복원. Clipboard 미지원·실패도 처리한다. |
| AC-PM-05 | 공유 코드 서비스와 기존 이메일 초대 회귀. 상위 브라우저에서 같은 코드로 서로 다른 두 사용자가 명시적으로 MEMBER 가입했다. 동일 사용자 중복 방지·기존 역할 보존·교체/철회/만료 경합의 PostgreSQL 실행은 대기다. |
| AC-PM-06 | 가입 전 미리보기·명시적 가입·허용된 로그인 복귀 경로·초대자 이메일 비노출. 409 후 자동 재조회 실패와 수동 새 조회 성공까지 복구하는 회귀를 확인했다. |
| AC-PM-07 | 현재 프로젝트 역할로 초대 다이얼로그와 역할 저장을 즉시 제한한다. 마지막 관리자와 현재 행위자 권한 재검사 소스·HTTP 검사를 확인했다. DB 잠금 경합 실행은 대기다. |
| AC-PM-08 | durable denial, 실제 route remount 중 pending POST, 이전 세션의 늦은 완료·복구 finally, 같은 revision의 새 세션, 응답 유실 회귀를 확인했다. 초대 완료보다 먼저 시작한 캐시/GET은 완료 잠금을 해제하지 않으며 실제 성공 POST 응답 또는 그 뒤의 새 조회만 사용한다. |
| AC-PM-09 | 화면 전반의 토큰·제목·행동 우선순위와 역할별 빈 개요 문구를 확인했다. 일정 상세 identity는 저장된 최대 200명만 한 번 조회하며 101·200번째 참석자, 누락·외부 참석자, 권한 거절 시 조회 금지를 테스트한다. 목록·대시보드 조회 확장은 없다. |
| AC-PM-10 | 검토자가 저장된 로비·모바일 PNG를 읽었고, 상위가 320px scrollWidth 320, 기간 버튼 44×44px, 메뉴 Escape 포커스 복원 및 720 CSS px reflow를 실측했다. 마지막 내부 상태·문구 수정 전 스크린샷임을 아래에 구분했다. |
| AC-PM-11 | 기존 일정·시간대·참석자·Calendar·CSRF·세션 회귀를 포함한 최종 199개 검사가 통과했다. 기존 이메일 초대의 mutation 403 → 늦은 GET → 실제 화면 재진입 → 재조회 500 → 새 조회 성공 검사를 별도로 복원했다. |

### 실행 증거와 한계

- `tmp/project-redesign-frontend-final.log`: 상위 실행 `pnpm --dir frontend exec vitest run --maxWorkers=2`, exit 0. 16:20:01 시작, 13개 파일 **199/199 PASS**, 44.79초. 검토자가 원본 로그를 읽었다.
- `tmp/project-redesign-frontend-build.log`: `pnpm frontend:build`, exit 0. `tsc -b && vite build` 통과, 최종 asset `index-DAbxgsdf.js`. 마지막 공유 초대 캐시 및 역할별 빈 상태 수정 이후 빌드다. `git diff --check`도 exit 0이다.
- `tmp/project-regression-migration.log`: 복원한 경계·기존 회귀 35/35 PASS. 최종 199개 실행에 포함되므로 전체 검사 수에 다시 더하지 않는다.
- `tmp/project-redesign-backend-identity-check.log` 및 JUnit XML: Java 25 단위 검사 77개와 OpenAPI 계약 검사 7개, 합계 **84/84 PASS**, 실패·오류·skip 0. 정상 OpenAPI 생성 및 integration test 컴파일을 확인했다. `compileIntegrationTestJava`는 PostgreSQL 검사 실행이 아니다.
- 배포 계약 145개 시나리오/560개 단언, 갱신된 V7 집중 검사 4개 시나리오/23개 단언 통과는 상위가 제공한 로컬 실행 증거다. PostgreSQL PID의 실제 Lock 대기 관찰, 원래 repository delegate, 실패 시 잠금 해제·executor 종료, 전체 생성 rollback 및 V6→V7 보존 oracle은 독립 소스 검토를 통과했으며 DB 런타임 검증은 남았다.
- 검토자가 앞서 열어 본 PNG와 중간 브라우저 증거의 asset은 `index-J6HuaJQx.js`였다. 이후 상위는 최신 `index-DAbxgsdf.js`에서 관리자 코드 표시, 사용 중지 후 코드·링크 제거, 기존 링크의 사용 불가 상태를 다시 확인했다. 최신 모바일 재확인과 최종 화면 증거는 상위 [검증 보고](2026-09-07-project-management-verification.md)에 기록한다. 검토자는 공유 브라우저나 fixture를 조작하지 않았다. 720 CSS px reflow를 브라우저 자체 200% 확대 검증으로 표시하지 않으며, 로컬 증거를 운영 배포·실제 데이터 변경으로 확대하지 않는다.

필수 미실행 항목을 PASS로 표시할 수 없다는 `TASK-REVIEW-RUBRIC.md`에 따라, 실제 PostgreSQL 검사 결과를 받은 뒤 전체 task-review를 다시 판정해야 한다. 이 추가 기록의 Notion 보관본 동기화는 상위가 담당한다.
