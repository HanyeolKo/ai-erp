# AI ERP

프로젝트 일정관리 시스템의 초기 기반입니다. 현재는 모듈 경계, 데이터베이스 스키마 기반, 보안 기본값, API 계약 생성과 연결 확인 화면만 제공합니다.

## 완료 범위

- Java 25 / Spring Boot 4.1.1 기반 모듈러 모놀리스와 7개 닫힌 업무 모듈
- PostgreSQL·Redis 개발 Compose, Flyway schema 초기화, Testcontainers 통합 검증
- `GET /api/v1/system/info` 및 liveness/readiness health 공개, 그 밖의 경로 deny-all
- REST Docs → OpenAPI 3.0.1 → TypeScript 타입·정적 Swagger 검토 산출물
- API 연결 상태와 재시도를 보여 주는 React foundation 화면

## 아직 구현하지 않는 범위

업무 API/DTO, 로그인·더미 계정·기본 비밀번호, Google OAuth/Calendar, 메일, 결재, AI, 운영 배포와 자동 Blue/Green 전환은 구현하지 않았습니다. 인증 principal, Redis 세션 JSON serializer, Blue/Green 세션 호환은 별도 승인 후 도입합니다.

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
python -m http.server 8081 --directory backend/build/api-docs
```

브라우저에서 `http://localhost:8081`을 엽니다.

## 아키텍처와 문서

업무 모듈 간 전달은 명시된 `api` named interface의 Java record DTO로만 하며, JPA Entity 공유와 다른 모듈 내부 참조를 금지합니다. `platform`은 `sharedModules`로 선언한 공통 기술 모듈입니다.

- [초기 결정](docs/architecture/foundation-decisions.md)
- [검증 기준](docs/architecture/bootstrap-verification.md)
- [기획 문서 정합성](docs/architecture/planning-consistency-2026-09-03.md)
- [개발 인프라](infra/README.md)

## 버전

| 구성 | 버전 |
| --- | --- |
| Java / Gradle / Spring Boot | 25 / 9.2.0 / 4.1.1 |
| Spring Modulith / QueryDSL / REST Docs / ePages | 2.1.1 / 7.5 / 4.0.1 / 0.20.1 |
| PostgreSQL / Redis | 18.6 / 8.2.9 |
| Node / pnpm / React / Vite / TypeScript | 22.19.0 / 10.33.0 / 19.2.7 / 8.2.2 / 6.0.2 |
