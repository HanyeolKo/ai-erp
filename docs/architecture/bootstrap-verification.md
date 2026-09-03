# 초기 구축 검증 기록

검증일: 2026-09-03. 성공·실패·미검증을 구분한다. 현재 원격 통합 검증과 최종 리뷰는 진행 중이다.

## 검증 결과

| 항목 | 결과 / 근거 |
|---|---|
| 문서 정합성 | Drive 최신 4종 전체 검토, 범위/계약/기술 조합 독립 검토 3개 및 보고서 재검토 완료. 미결 정책은 [정합성 보고서](planning-consistency-2026-09-03.md) 참조 |
| Java 환경 | workspace-local Temurin 25.0.4.1+1, 공식 배포 SHA-256 대조 후 실행. 전역 Java 설정 변경 없음 |
| 깨끗한 백엔드 빌드 | `gradlew clean test integrationTestClasses openapi3 bootJar --rerun-tasks --no-build-cache` 성공 |
| 백엔드 테스트 | 모듈 경계 1건 + API/보안 4건 + 세션 쿠키 설정 바인딩 2건, 총 7건 성공·실패/skip 0 |
| QueryDSL 준비 | OpenFeign7.5 Q타입 생성과 통합 테스트 소스 컴파일 성공. 실제 PostgreSQL 조회는 원격 통합 테스트 결과로 별도 판정 |
| 잠금 파일 | `pnpm install --frozen-lockfile` 성공 |
| API 계약 산출물 | `pnpm api:generate` 성공: OpenAPI3.0.1 검증 → TypeScript 타입 → Swagger 생성 |
| 프런트 | 실제 UI 테스트 3건, typecheck 및 Vite production build 성공 |
| 알려진 npm 취약점 | `pnpm audit --json`: 0건. 초기 발견된 Vitest 취약점은 4.1.0으로 패치 후 재검증 |
| 브라우저 확인 | Swagger에서 GET 경로와 200 JSON 예제 표시, Try it out 없음. 프런트는 백엔드 미기동 시 실패·재시도 UI 표시 확인 |
| Docker 통합 테스트 | 로컬 Docker 불가용으로 `integrationTest`가 명시 실패. 조용한 skip 없음. GitHub Linux runner에서 필수 검증 예정 |
| 개발 Compose | localhost 바인딩과 named volume 구성 읽기 검토. 실행 검증은 원격 Docker 환경에서 예정 |
| Git 포함 파일 | 기존 planning/PPT/임시 작업물, 비밀값 파일, node_modules/build/생성 타입 제외 확인 |
| 배포 JAR | 테스트용 QuerydslProbe 및 전용 V2 마이그레이션이 없고, 운영 V1 스키마 생성만 포함됨을 확인 |
| 코드 재검토 | 최초 검토의 8개 수정사항과 Swagger 외부 validator 차단을 독립 재검토하여 모두 해결 확인. 최종 전체 리뷰는 별도 진행 |

브라우저에서 백엔드까지 연결되는 성공 흐름은 로컬에서 확인하지 못했다. 성공 응답은 실제 Controller의 MockMvc 테스트와 프런트 HTTP 경계 테스트로 각각 검증했다. 전체 연결/DB·Redis는 통합 테스트 결과를 기준으로 한다.

## 알려진 호환성 참고

- ePages 0.20.1이 Gradle9.2에서 deprecation 경고를 출력한다. 현재 빌드는 성공하지만 Gradle10으로 올리기 전 플러그인 호환성을 재검증해야 한다.
- openapi-typescript7.10.1의 공식 peer 범위는 TypeScript5 계열이다. 요구된 TypeScript6.0.2에서 실제 타입 생성과 strict compile은 통과했다. 버전 변경 시 같은 PoC를 다시 실행한다.
- Java25/Gradle/테스트 런타임의 native access·클래스 공유 경고가 남아 있다. 현재 빌드 실패는 아니며 런타임/도구 갱신 시 다시 확인한다.
- Testcontainers 2 계열에서 구 PostgreSQLContainer 패키지 사용에 대한 deprecated API 노트가 있다. 현재 통합 테스트 컴파일은 성공하며 실제 실행 여부는 CI 결과로 별도 판정한다.
- npm 설치에서 `@scarf/scarf` 및 `esbuild` dependency install script가 차단되어 있다. 현재 생성·빌드는 통과했으며, 단순히 경고를 없애려고 임의 스크립트를 승인하지 않는다.

Vitest 수정 근거: [공식 보안 공지 GHSA-5xrq-8626-4rwp](https://github.com/vitest-dev/vitest/security/advisories/GHSA-5xrq-8626-4rwp). 현재 프로젝트는 취약 UI 서버를 사용하지 않았지만 패치 버전을 채택했다.

## 이번에 수행하지 않은 것

운영 배포, Google OIDC/Calendar 실제 계정 연결, 업무 CRUD와 권한 정책, BlueGreen 전환·세션 N/N-1·DB 구신 버전 공존·복구 리허설은 초기 범위 밖이다. 기본 코드와 테스트가 있다는 이유로 이 기능들이 완료되었다고 해석하지 않는다.
