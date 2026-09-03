# 초기 구축 검증 기록

검증일: 2026-09-03. 최종 코드 리뷰·수정 재검토·원격 통합 검증을 완료했다. 검증 코드 커밋은 `51f62690c292f794ea91ec7b8433c0f470ed476f`이며, [GitHub Actions 실행 33725888023](https://github.com/HanyeolKo/ai-erp/actions/runs/33725888023)이 전체 성공했다. 아래 기록 갱신은 업무 코드 변경이 아니다.

## 검증 결과

| 항목 | 결과 / 근거 |
|---|---|
| 문서 정합성 | Drive 최신 4종 전체 검토, 범위/계약/기술 조합 독립 검토 3개 및 보고서 재검토 완료. 미결 정책은 [정합성 보고서](planning-consistency-2026-09-03.md) 참조 |
| Java 환경 | workspace-local Temurin 25.0.4.1+1, 공식 배포 SHA-256 대조 후 실행. 전역 Java 설정 변경 없음 |
| 깨끗한 백엔드 빌드 | `gradlew clean test integrationTestClasses openapi3 bootJar --rerun-tasks --no-build-cache` 성공 |
| 백엔드 테스트 | 모듈 경계 1건 + API/보안 4건 + 세션 쿠키 설정 바인딩 2건 + Flyway PostgreSQL 플러그인 선택 1건, 총 8건 성공·실패/skip 0 |
| QueryDSL 준비 | OpenFeign7.5 Q타입 생성·컴파일 및 실제 PostgreSQL18.6의 HQL/QueryDSL 조회 통과 |
| 잠금 파일 | `pnpm install --frozen-lockfile` 성공 |
| API 계약 산출물 | `pnpm api:generate` 성공: OpenAPI3.0.1 검증 → TypeScript 타입 → Swagger 생성 |
| 프런트 | 실제 UI 테스트 3건, typecheck 및 Vite production build 성공 |
| 알려진 npm 취약점 | `pnpm audit --json`: 0건. 초기 발견된 Vitest 취약점은 4.1.0으로 패치 후 재검증 |
| 브라우저 확인 | Swagger에서 GET 경로와 200 JSON 예제 표시, Try it out 없음. 프런트는 백엔드 미기동 시 실패·재시도 UI 표시 확인. border-box 수정 후 1280×720에서 문서 폭 1280, main 높이 720, 카드 폭 576 확인. 모바일 실측은 미수행 |
| Docker 통합 테스트 | GitHub Linux runner에서 PostgreSQL18.6·Redis8.2.9 실제 구동, Flyway V1/V2 적용·readiness·QueryDSL 검증 2건 성공. 실패/생략 0. 로컬 Docker 불가용은 별도 환경 제약으로 유지 |
| 개발 Compose | localhost 바인딩과 named volume 읽기 검토 및 원격 `docker compose ... config --quiet` 성공. Compose 전체 up은 미실행, 실제 DB·Redis 구동은 Testcontainers로 검증 |
| Git 포함 파일 | 기존 planning/PPT/임시 작업물, 비밀값 파일, node_modules/build/생성 타입 제외 확인 |
| 배포 JAR | 테스트용 QuerydslProbe 및 전용 V2 마이그레이션이 없고, 운영 V1 스키마 생성만 포함됨을 확인. Flyway PostgreSQL 12.4.0 플러그인 포함 확인 |
| 코드 재검토 | 최초 8개 수정사항과 Swagger 외부 validator 차단 재검토 완료. 최종 전체 리뷰의 Flyway 플러그인·Boot4 테스트 클라이언트 자동 설정·CSS 크기 계산 3건도 수정 후 독립 재검토에서 모두 해결 판정 |
| 원격 재현성 | 새 runner에서 Wrapper 검증·고정 의존성 설치·백엔드 8건·통합 2건·프런트 3건·문서 생성·전체 빌드 성공. 총 13건 성공, 검증 산출물 업로드 완료 |
| GitHub | 비공개 [HanyeolKo/ai-erp](https://github.com/HanyeolKo/ai-erp), 기본 브랜치 main. 초기 프로젝트와 한국어 검토 문서를 push |

브라우저에서 백엔드까지 연결되는 성공 흐름은 로컬에서 확인하지 못했다. 시스템 정보 성공 응답은 실제 Controller의 MockMvc 테스트와 프런트 HTTP 경계 테스트로 각각 검증했다. 원격 통합 테스트는 실제 서버의 readiness 응답과 DB·Redis·Flyway·QueryDSL 동작을 검증하며, 브라우저 E2E 테스트를 대체했다고 주장하지 않는다.

## 새 환경 재현성 점검

- 첫 원격 실행은 Node 준비 단계의 자동 pnpm 캐시 탐지가 pnpm 설치보다 먼저 실행되어 실패했다. `package-manager-cache: false`로 자동 캐시를 명시적으로 끄고 필수 검증 단계는 모두 유지했다. [setup-node v5 공식 설명](https://github.com/actions/setup-node/tree/v5#caching-global-packages-data)
- 두 번째 원격 실행은 도구 준비·잠금 설치·Compose 검증을 통과했지만 Gradle 배포 체크섬이 잘못 기입되어 실패했다. 기존 로컬 Gradle 캐시는 다운로드 검증을 재실행하지 않아 로컬 빌드만으로는 이 오류가 드러나지 않았다.
- Gradle 9.2.0 binary-only 공식 SHA-256은 `df67a32e86e3276d011735facb1535f64d0d88df84fa87521e90becc2d735444`, wrapper JAR은 `423cb469ccc0ecc31f0e4e1c309976198ccb734cdcbb7029d4bda0f18f57e8d9`이다. 검증을 끄지 않고 공식 값으로 대조한다. [공식 체크섬 목록](https://gradle.org/release-checksums/)
- 올바른 체크섬 반영 후 별도 빈 Gradle 사용자 홈에서 신규 다운로드·검증·실행이 성공했고, 후속 원격 실행도 전체 성공했다. 두 CI 설정 수정은 각각 변경분만 독립 재검토했다.

## 알려진 호환성 참고

- ePages 0.20.1이 Gradle9.2에서 deprecation 경고를 출력한다. 현재 빌드는 성공하지만 Gradle10으로 올리기 전 플러그인 호환성을 재검증해야 한다.
- openapi-typescript7.10.1의 공식 peer 범위는 TypeScript5 계열이다. 요구된 TypeScript6.0.2에서 실제 타입 생성과 strict compile은 통과했다. 버전 변경 시 같은 PoC를 다시 실행한다.
- Java25/Gradle/테스트 런타임의 native access·클래스 공유 경고가 남아 있다. 현재 빌드 실패는 아니며 런타임/도구 갱신 시 다시 확인한다.
- Testcontainers 2 계열에서 구 PostgreSQLContainer 패키지 사용에 대한 deprecated API 노트가 있다. 현재 컴파일과 실제 원격 통합 테스트는 통과했으며 향후 API 갱신 시 패키지를 전환한다.
- npm 설치에서 `@scarf/scarf` 및 `esbuild` dependency install script가 차단되어 있다. 현재 생성·빌드는 통과했으며, 단순히 경고를 없애려고 임의 스크립트를 승인하지 않는다.
- 일부 Actions v4가 Node20 대상으로 선언되어 runner에서 Node24로 실행된다는 경고가 있다. 현재 원격 검증은 성공했고, 해당 Actions의 Node24 명시 지원 버전으로 갱신할 때 전체 파이프라인을 다시 검증한다. 프런트 빌드용 Node22.19.0과 Actions 자체 실행 엔진은 별개다. [GitHub 전환 안내](https://github.blog/changelog/2025-09-19-deprecation-of-node-20-on-github-actions-runners/)

Vitest 수정 근거: [공식 보안 공지 GHSA-5xrq-8626-4rwp](https://github.com/vitest-dev/vitest/security/advisories/GHSA-5xrq-8626-4rwp). 현재 프로젝트는 취약 UI 서버를 사용하지 않았지만 패치 버전을 채택했다.

런타임 수정 근거: [Flyway PostgreSQL 별도 지원 모듈](https://documentation.red-gate.com/fd/postgresql-database-277579325.html), [Boot4 TestRestTemplate 명시적 자동 설정](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide#using-webclient-or-testresttemplate-and-springboottest). PostgreSQL URL에 맞는 플러그인을 실제로 선택하는 회귀 테스트의 실패→성공을 확인했다.

## 이번에 수행하지 않은 것

운영 배포, Google OIDC/Calendar 실제 계정 연결, 업무 CRUD와 권한 정책, BlueGreen 전환·세션 N/N-1·DB 구신 버전 공존·복구 리허설은 초기 범위 밖이다. 기본 코드와 테스트가 있다는 이유로 이 기능들이 완료되었다고 해석하지 않는다.
