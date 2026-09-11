# AI ERP

프로젝트 일정관리 시스템의 Phase 1 구현입니다.

## 완료 범위

- Java 25 / Spring Boot 4.1.1 기반 모듈러 모놀리스와 7개 닫힌 업무 모듈
- PostgreSQL·Redis 개발 Compose, Flyway schema 초기화, Testcontainers 통합 검증
- OIDC 설정 기반의 초대·프로젝트·일정·확인·알림·Calendar 단방향 투영 API와 React SPA
- `GET /api/v1/system/info` 및 liveness/readiness health 공개, 그 밖의 경로 deny-all
- REST Docs → OpenAPI 3.0.1 → TypeScript 타입·정적 Swagger 검토 산출물
- API 연결 상태와 재시도를 보여 주는 React foundation 화면

## 아직 구현하지 않는 범위

실제 Google OAuth/Calendar 외부 연결은 운영 환경의 자격 증명이 있어야 활성화됩니다. 더미 계정·기본 비밀번호·운영 헤더 로그인, 메일, 결재, AI, Drive 문서·상세 업무·마일스톤·양방향 Calendar 동기화는 구현하지 않습니다.

## 실행 순서

1. Node 22.19.0과 pnpm 10.33.0, Java 25를 준비합니다.
2. `pnpm install --frozen-lockfile`
3. 필요하면 `docker compose --env-file infra/.env.example -f infra/compose.dev.yml up -d`
4. `cd backend && ./gradlew clean test openapi3 bootJar`
5. 루트에서 `pnpm api:generate`
6. `pnpm frontend:test && pnpm frontend:typecheck && pnpm frontend:build`

통합 검증은 Docker가 필요하며 `cd backend && ./gradlew integrationTest`로 별도 실행합니다. Docker가 없으면 이 작업은 명시적으로 실패합니다.

## 로컬 개발과 API 검토

PowerShell에서는 Java 25 경로를 `JAVA_HOME`으로 설정한 뒤 실행합니다. Windows는 `backend\gradlew.bat`, macOS/Linux는 `backend/gradlew`를 사용합니다.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
Set-Location backend
.\gradlew.bat bootRun --args='--spring.profiles.active=local'
```

다른 터미널에서 `pnpm --dir frontend dev`를 실행하면 `/api` 요청은 `localhost:8080`으로 프록시됩니다. API 명세는 `pnpm api:generate` 후 아래처럼 정적으로 검토할 수 있습니다. Swagger의 Try it out은 비활성화되어 있고 운영에 자동 공개되지 않습니다.

```sh
pnpm --dir frontend exec vite ../backend/build/api-docs --host 127.0.0.1 --port 4174 --strictPort
```

브라우저에서 `http://127.0.0.1:4174`을 엽니다.

## 운영 배포

운영 배포는 승인된 self-hosted job에서 clean `main` HEAD와 같은 `scripts/deploy.sh <40-character-main-commit-sha>`로 실행한다. 서버는 `192.168.219.100`, checkout은 `/home/deploy/actions-runner-ai-erp/_work/ai-erp/ai-erp`, 운영 파일은 `/home/deploy/ai-erp`이다. 준비 디렉터리는 deploy 소유의 mode `700`, `.env`와 state·manifest·log·backup은 mode `600`이며 symbolic link를 허용하지 않는다. [운영 runbook](infra/README.md)의 초기 준비·secret 형식·필수 도구를 먼저 확인한다.

배포는 실제 lock, OCI revision/image ID, project-owned network/volumes, inactive slot을 검증한다. 최초 빈 DB를 포함해 검증된 backup을 만든 뒤 Flyway를 한 번 실행하고, 새 app의 readiness·SPA·API·Swagger를 확인한다. 전체 Caddy config 검증·전환 뒤 두 차례 public smoke와 관찰을 통과해야 immutable manifest와 atomic `active-state.json`을 확정하고 이전 app을 중지한다. 전환 뒤 실패하면 이전 upstream과 state를 복원한다. 복구 자체가 실패하면 exit `2`와 보존된 transaction 파일을 확인한다.

Caddy는 단일 파일 대신 `infra` 디렉터리를 read-only로 연결한다. 시작·reload는 `/etc/caddy/source/Caddyfile`을 읽고, candidate 검증과 checksum도 같은 현재 checkout 파일을 사용한다.

`rollback.sh`는 atomic state의 직전 release 또는 명시한 보존 release를 현재 inactive slot으로 전환한다. 연속 rollback은 직전 active release로 되돌아가며 보존 manifest와 DB schema를 변경하지 않는다. 같은 active SHA의 배포는 검증만 수행하고, 완료된 비활성 SHA는 `rollback.sh`로 복원한다. Script는 volume/image 또는 비프로젝트 container를 제거하지 않는다. 운영에서 test mode나 경로 override를 설정하지 않는다.

서버에서만 아래 검증을 실행합니다. `.env.prod.example`은 키 목록 예시이며 실제 값으로 사용하면 안 됩니다.

```sh
APP_IMAGE=ai-erp:config-check docker compose --env-file infra/.env.prod.example -f infra/compose.prod.yml config --quiet
bash scripts/tests/deployment-contract.sh
```

OpenAPI와 Swagger 검토 artifact는 production image의 `/assets/api-docs/`에 포함되며, 같은 image의 `/assets/api-docs/index.html`에서 읽는다.

## 아키텍처와 문서

업무 모듈 간 전달은 명시된 `api` named interface의 Java record DTO로만 하며, JPA Entity 공유와 다른 모듈 내부 참조를 금지합니다. `platform`은 `sharedModules`로 선언한 공통 기술 모듈입니다.

- [초기 결정](docs/architecture/foundation-decisions.md)
- [검증 기준](docs/architecture/bootstrap-verification.md)
- [기획 문서 정합성](docs/architecture/planning-consistency-2026-09-03.md)
- [개발 인프라](infra/README.md)
- [UI/UX 전담 에이전트와 화면 기획 절차](docs/architecture/ui-ux-agent-guide.md)

화면 기획은 `router -> ui-ux-designer -> reviewer`를 거치며, 구현은 승인된 상위 계약과 경량 `implementer` 실행 단계를 따른다. 하네스와 고정된 UX 스킬은 Git에서 함께 관리하고 `python -B scripts/verify-harness.py`로 구조를 검증한다.
모델 기본값은 최상위 오케스트레이션·최종 검토에 Astra, 하위 기획·화면 설계에 Sol, 확정 구현·릴리스 실행에 Luna를 명시한다. 실행자 에스컬레이션은 부모가 Luna → Terra → Sol 순서로만 선택하며 Astra 실행자로 올리지 않는다. 동시 작업은 두 개, 위임 깊이는 한 단계로 제한한다.

## 하네스 라이프사이클과 외부 의존성

`router`는 범위를 정한 `TASK-ASSIGNMENT.md`를 만들고, `product-planner`는 제품 증분 계획을 작성한다. 화면 구조와 상호작용은 `ui-ux-designer`, 코드·테스트·동작 설정은 승인된 계약을 받은 `implementer`, 독립 검토와 판정은 `reviewer`, 배포 준비·관찰·복구 근거는 `release-manager`가 담당한다. 흐름은 상위 에이전트의 dispatch → 수신자의 acknowledgement → 범위가 제한된 실행 → 근거 반환 → 독립 검토 → 상위 승인 또는 수정 요청이다. `INCREMENT-PLAN.md`, `SCREEN-PLAN.md`, `IMPLEMENTATION-CONTRACT.md`, `RELEASE-CONTRACT.md`가 각 단계의 연결 문서다.

외부 API 예를 들어 Google OAuth/Calendar를 운영 연결하려면 대상 계정·tenant, API 버전, OAuth callback과 권한, 외부 담당자, 현재 환경에서 수행한 안전한 readiness check가 모두 기록되어야 한다. 설정이 `missing` 또는 `unknown`이면 의존하는 작업은 즉시 중단하고 `harness/templates/BLOCKER-REPORT.md`에 소유자, 해결 선택지, 재개 검사와 정제된 근거를 적어 상위 에이전트로 돌린다. 명시적으로 승인된 `offline-contract-only` 테스트는 독립적인 경우 진행할 수 있지만 live integration 성공으로 표시하지 않는다. goal-mode 지속 실행도 이 차단을 해제하지 않는다.

작은 수정은 유효한 증분 ID·계획 revision·현재 검토를 재사용할 수 있고, 계획만 필요한 작업은 배포 없이 독립 검토와 상위 승인을 거쳐 완료할 수 있다. 파일 지침과 구조 테스트는 자동 스케줄러가 아니며 Native 역할 호출이나 운영 결과를 증명하지 않는다. 현재 `native-planner-smoke.json`은 `unknown agent_type`을 기록하므로 새 역할의 실제 런타임 동작은 인증되지 않았다.

## 버전

| 구성 | 버전 |
| --- | --- |
| Java / Gradle / Spring Boot | 25 / 9.2.0 / 4.1.1 |
| Spring Modulith / QueryDSL / REST Docs / ePages | 2.1.1 / 7.5 / 4.0.1 / 0.20.1 |
| PostgreSQL / Redis | 18.6 / 8.2.9 |
| Node / pnpm / React / Vite / TypeScript | 22.19.0 / 10.33.0 / 19.2.7 / 8.2.2 / 6.0.2 |
