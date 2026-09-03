# AI ERP 초기 프로젝트 구축 계획

> 사용자 요청: Google Drive planning 문서 정합성 확인, GitHub 저장소 생성, 초기 프로젝트를 main에 푸시.
> For agentic workers: use superpowers:subagent-driven-development to execute this plan.

## Spec / Global Constraints

- 기획 원본은 Google Drive planning의 최신 네이티브 Google Slides 4종이다. 링크와 충돌 사항은 `docs/architecture/planning-consistency-2026-09-03.md`에 기록한다. Drive 원본을 이번 요청에서 임의로 고치지 않는다.
- 초기 구축은 프로젝트 일정관리 시스템의 기반이다. 업무 기능 완성·OAuth 가짜 로그인·Calendar 작업자·메일/결재·AI·프로덕션 배포는 포함하지 않는다.
- TypeScript 프런트엔드, Java 백엔드, 명확한 모듈 경계를 가진 모듈러 모놀리식. 과도한 추상화 금지.
- Java 25, Spring Boot 4.1.1, Spring Modulith 2.1.1, OpenFeign QueryDSL 7.5, REST Docs 4.0.1, ePages restdocs-api-spec 0.20.1, React 19.2.7, Vite 8.2.2, TypeScript 6.0.2를 사용한다. 실제 빌드/테스트로 호환성을 검증한다. OpenFeign 좌표는 `io.github.openfeign.querydsl`이며 Boot 기본 `com.querydsl`과 다르다.
- PostgreSQL 18.6, Redis 8.2.9 개발 환경. 운영 방향은 Caddy 2 + Blue/Green이며 실제 운영 배포 자동화는 아직 만들지 않는다.
- 테스트 중심 개발. API 계약은 REST Docs 테스트에서 생성하고 ePages를 통해 OpenAPI 3.0.1로 변환한다. Swagger UI와 TypeScript 타입은 동일 생성 스펙을 소비한다. springdoc나 별도 수기 API 스펙 금지.
- 사용자 요청대로 신규 main 브랜치로 작업하고 비공개 `HanyeolKo/ai-erp`에 푸시한다. 기존 로컬 기획 자산과 비밀값은 커밋하지 않는다.
- README/설명 문서는 한국어. 커밋은 `feat : 프로젝트 초기 기반 구성`처럼 핵심 주제를 표현한다.

## Task 1: 실행 가능한 기반 프로젝트와 검증 파이프라인

### 범위와 소유 파일

`backend/`, `frontend/`, `infra/`, `scripts/`, `.github/workflows/`, 루트 `package.json`, `pnpm-workspace.yaml`, `pnpm-lock.yaml`, `.node-version`, `.editorconfig`, `README.md`를 구현한다. `.gitignore`, `.gitattributes`, `docs/architecture/`, 이 계획과 로컬 planning 자산은 메인 에이전트 소유다. 다른 경로를 수정하지 않는다.

### Backend

1. Gradle Wrapper 9.2.0(공식 배포 체크섬 포함), Java toolchain 25, Spring Boot 4.1.1. 루트 패키지는 `com.aierp`. JPA/PostgreSQL, Flyway, Redis Session, Security, Actuator, Modulith 경계검증, QueryDSL APT, REST Docs/ePages 의존성.
2. `identity`, `group`, `project`, `schedule`, `notification`, `calendarintegration`, `audit`의 7개 업무 모듈을 package-info의 닫힌 `@ApplicationModule`로 선언한다. 최소 공개 API package-info(`@NamedInterface("api")`)와 module descriptor가 실제 탐지되도록 필요한 최소 marker 사용. `platform`은 업무 모듈이 아닌 공통 기술 모듈이며 sharedModules로 명시한다. 순환 참조/타 모듈 내부 참조를 `ApplicationModules.verify()` 테스트로 막는다. 모듈 전달 규약은 Java record DTO, JPA Entity 공유 금지, 명시한 api 인터페이스만 사용. 미확정 업무 DTO를 가짜로 만들지 않는다.
3. 비즈니스 API는 구현하지 않는다. 문서 파이프라인용 `GET /api/v1/system/info`가 JSON `{ "name": "AI ERP", "phase": "foundation" }`만 반환하도록 구현한다. 배포 ID/비밀/사용자 정보를 노출하지 않는다. 이 조회와 Actuator liveness/readiness만 공개하며 나머지 경로는 denyAll. 로그인/더미 계정/기본 비밀번호를 만들지 않는다. CSRF는 끄지 않는다.
4. 공통 `application.yml`은 PostgreSQL/Redis 연결을 환경 변수로 받는다. `local` 프로파일만 localhost 개발 기본값을 제공한다. JPA ddl-auto=validate, open-in-view=false. health liveness는 DB/Redis와 무관, readiness는 DB/Redis를 포함. details 공개 금지. Redis 세션 쿠키 HttpOnly/SameSite=Lax, 운영 Secure, local HTTP만 Secure=false. 초기에는 인증 구현이 없으며 userId-only principal/JSON serializer/BlueGreen 세션 호환은 후속 승인 및 구현 대상임을 명시한다.
5. Flyway V1은 7개 업무 schema와 platform schema만 생성한다. 업무 테이블/전역 FK/출판 레지스트리/미확정 마이그레이션은 만들지 않는다. `calendarintegration` schema 명명은 문서와 일치하도록 `calendar_integration`으로 명시한다. 공통 Flyway history는 platform. starter-jpa의 event publication 자동 스키마/런타임은 이번에 활성화하지 않는다(경계검증 가능한 최소 Modulith core/starter-test 사용 가능).
6. 단위/경계/MockMvc 문서 테스트는 Docker 없이 실행 가능하게 분리한다. REST Docs strict responseFields로 누락 필드가 테스트를 실패시켜야 한다. ePages resource.json을 같은 테스트에서 만들고 `openapi3`는 성공한 테스트를 선행한다. HTTP 보안 차단 테스트 포함.
7. 별도 `integrationTest` 태스크는 Testcontainers PostgreSQL18.6/Redis8.2.9로 실제 context/Flyway/health 연동을 검증한다. Docker가 없으면 명시적으로 실패시킨다(조용한 skip 금지). 외부 Google/OAuth/메일 호출 금지.
8. QueryDSL test source용 JPA entity의 Q타입 생성/실제 HQL 쿼리를 통합 테스트로 검증한다. 업무 모델을 도입하지 않고 테스트 schema/entity에만 한정한다. Java25/Boot4.1.1/ePages0.20.1 호환 확인이 핵심.

### Frontend / API docs

1. pnpm 10.33.0 workspace, Node22.19.0, React19.2.7/Vite8.2.2/TS6.0.2 strict 설정. 프런트는 foundation 안내 화면만 제공한다. 구현되지 않은 일정 UI나 가짜 데이터/클릭해도 동작하지 않는 업무 버튼은 만들지 않는다.
2. `GET /api/v1/system/info`를 호출해 연결 확인 중/성공/실패 상태를 한국어로 표시하고 실패 시 재시도가 동작한다. semantic HTML과 기본 반응형 CSS만 사용, UI framework는 도입하지 않는다. 개발 프록시 `/api`→localhost8080.
3. React Testing Library + Vitest로 실제 UI의 로딩·성공·실패·재시도 행동을 검증한다. HTTP 경계만 fake 가능.
4. 루트 스크립트로 backend 생성 `openapi3.yaml` 검증, `openapi-typescript` 타입 생성, `swagger-ui-dist` 기반 정적 Swagger UI 생성(예: `backend/build/api-docs/`)을 제공한다. 생성 타입은 프런트 API 함수에서 사용한다. 정적 Swagger는 개발자 검토 산출물이며 운영에 자동 공개하지 않는다. API 스펙의 유효성과 필수 경로/필드 포함을 확인한다. 새로운 부가 라이브러리는 stable exact version을 확인하고 lockfile로 고정한다.
5. 생성 타입/Swagger/스펙은 build 산출물로 두되 CI/README의 선행 생성 순서를 정확히 묶어 fresh clone 빌드가 가능해야 한다.

### Development infra / CI

1. 개발 Compose는 `infra/compose.dev.yml`, PostgreSQL18.6/Redis8.2.9만 포함. 포트는 127.0.0.1 바인딩, named volumes, 실제 healthcheck. `.env.example`은 개발 전용 비밀이 아닌 예시값임을 표시한다. 운영 비밀/Google credentials를 요구하지 않는다.
2. `infra/README.md`에 개발 start/stop(볼륨 유지)/로그/초기화 시 데이터 손실 경고, 운영 BlueGreen 방향(Caddy 외부 단일 진입, DB/Redis 공유, 앱 두 슬롯, expand-contract migration, ready→전환→관찰→rollback)과 미구현 경계를 한국어로 설명한다. 실제 운영 Compose/배포 scripts/자동 전환은 만들지 않는다.
3. GitHub Actions: push main/PR에서 Java25/Node22.19/pnpm10.33.0 준비, frozen install, backend clean test integrationTest openapi3 bootJar, OpenAPI검증+타입/Swagger생성, frontend test/typecheck/build. Gradle wrapper validation, least privilege contents:read, 동시 실행 취소, test reports + API docs artifact. 배포 credential·publish job 없음.
4. README는 완료 범위/미구현 범위/문서 링크/정확한 실행 순서/검증 명령/아키텍처 경계 설명/버전표를 포함. 문서 상충 목록은 `docs/architecture/planning-consistency-2026-09-03.md`로 연결한다.

### 검증 순서

- 테스트를 구현과 함께 먼저 작성하고, 실제 실패→성공 증거를 남긴다. 빈 boilerplate나 한국어 설명문을 검사하는 무의미한 테스트는 만들지 않는다.
- JDK25는 메인 에이전트가 workspace `.tools`에 준비 중이다. 환경 준비 전에는 구현을 진행하고 컴파일 준비 상태를 공유한다.
- 기본: backend `clean test openapi3 bootJar`, 전체: `integrationTest`, root docs/types 생성, frontend test/typecheck/build, Compose config 검증.
- 테스트 결과와 남은 위험을 보고서에 남긴다. 메인 에이전트가 문서 작성 및 환경 준비 중이므로 자기 소유 경로만 stage/commit한다.
- 커밋 후 task reviewer의 명세/품질 검토를 거친다. 외부 repo 생성과 push는 메인 에이전트가 한다.

## Task 2: 문서·전체 검증 및 GitHub 공개 범위 확인

메인 에이전트가 네 문서의 정합성 보고서, 원문 링크, 초기 결정과 미결 정책을 작성한다. Task 1과 문서의 명세 충돌을 검토하고 fresh clean build/보안 및 Git 포함 파일을 확인한다. 전체 코드 리뷰 후 수정, 비공개 repository 생성과 main push, 원격 SHA/CI 확인. 문서 원본 수정 및 생산 배포는 하지 않는다.
