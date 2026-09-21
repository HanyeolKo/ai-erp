# Schedule Views 백엔드 최종 구현 결과

- 작업: `schedule-views-delivery`; implementation contract **r1**, API contract **r2**; 최종 attempt3 `gpt-5.6-sol` / `medium`; canonical implementer. 상태: PostgreSQL CI 검증 진행 중이며 최종 판정은 상위 orchestrator와 독립 reviewer 소유이다.
- 범위: schedule workspace 백엔드, 관련 단위/REST Docs/PostgreSQL 통합 테스트, V9 검증, 정확한 V9 배포 입력 안전 계약. 프런트엔드/Git/PR/배포/Notion/판정 작업은 수행하지 않았다.

## 구현 및 수용 기준 대응

1. **권한과 정보 노출**: project lock 뒤 멤버십/쓰기 권한을 property/view/config 조회보다 먼저 확인한다. PERSONAL은 MANAGER/MEMBER만 생성·수정하며 owner 검사를 version 검사보다 먼저 수행한다. 통합 테스트는 nonmember/VIEWER/MEMBER/MANAGER와 stale mutation을 다룬다.
2. **엄격한 타입/쿼리**: DATE는 정확한 `YYYY-MM-DD`와 strict `LocalDate`를 요구하고 status는 `ScheduleEntity.Status`만 허용한다. 고정 timestamp/UUID는 JDBC 타입으로 바인딩하고 CONTAINS는 단일 `!` escape로 `%`, `_`, `!`, SQL 형태 문자열을 literal 검색한다.
3. **동시성/원자성**: option-only 변경은 조건부 repository update로 property aggregate `rowVersion`을 provider-safe하게 증가시킨다. dashboard 최초 저장은 명시적 insert로 stored version 1을 만들고 이후 JPA `@Version`을 사용한다. 통합 테스트는 동시 property write 1-success/1-stale, saved view/record/dashboard stale, create/update rollback, custom-only business revision 보존을 검증한다.
4. **PostgreSQL 7개 범주**: workspace 통합 클래스 11개 test(기존 3 + 신규 8)와 V8→V9 migration test에 five-type/null/no-op/archive/foreign input, atomic create/update rollback과 participant/ACK/projection 보존, config/record/dashboard concurrency와 role revocation matrix, latch 기반 REPEATABLE READ count/page/group snapshot, 120행 complete-scope typed/fixed query·nulls-last·UUID tie·select order·literal·EXPLAIN, V9 typed-column/프로젝트 관계 제약 및 V8 데이터 보존, WORKSPACE durable event/audit와 notification/projection 격리, dashboard first/subsequent/null/builtin/shared/archive/unavailable behavior를 실제 DB assertion으로 작성했다.
5. **API 문서**: 11개 route 모두 populated request/response로 실행하고 Property/Option/View/Filter/Sort/Query/Group/Record/Schedule/Participant/Change/value-map 필드를 기술한다. `OpenApiContractTest`가 11개 operation과 핵심 typed schema를 파싱해 확인한다.
6. **배포 입력**: `scripts/lib-deploy.sh`는 V1,V2,V4–V9 정확히 8개만 허용하고 모두 checksum에 포함한다. regression은 missing V9, V9 checksum 변경, build 이후 V9 tamper와 기존 missing/unexpected/symlink 방어를 검증한다. 상위 parent가 이 격리 변경을 commit `e2e9d8c`로 push했다.

## 실행 증거

- JDK: `D:/onedrive/Documents/ChatGPT/AI ERP/.tools/jdk-25.0.4.1+1`; Gradle 출력은 ignored `tmp/gradle-output.init.gradle`과 각 fresh `AI_ERP_TEMP_BUILD_DIR/<project>/build` 레이아웃을 사용했다.
- `compileJava compileTestJava compileIntegrationTestJava`: exit 0, `tmp/backend-compile-1/ai-erp-backend/build`.
- `test --tests com.aierp.ScheduleWorkspaceApiDocumentationTest`: exit 0, `tmp/backend-docs-3/ai-erp-backend/build`.
- 최종 source-freeze `test openapi3 compileIntegrationTestJava`: `tmp/backend-source-freeze/ai-erp-backend/build`; 30 unit/REST Docs XML suites, **141 tests / 0 failures / 0 errors / 0 skipped**, integration class output 생성.
- `openapi3` 및 강화된 `openapiContractTest`: exit 0; **8 / 0 / 0 / 0**. 실제 생성물 `backend/build/api-spec/openapi3.yaml`, 134,615 bytes, 9 workspace paths / 11 operations. 프런트엔드 executor와 parent에 generator 준비를 통지했다.
- Git Bash `bash -n scripts/lib-deploy.sh` + `CONTRACT_FOCUS=migration python scripts/tests/deployment_contract.py .`: exit 0. Parent CI `35618634717`에서도 deployment contract PASS.
- 이전 CI `35618634717`의 PostgreSQL 시작 실패는 test entity `platform.querydsl_probe`에 integration-only migration 경로가 없던 fixture 문제였다. 기존 통합 테스트 패턴대로 `classpath:db/migration,classpath:db/integration-migration`을 이 테스트에 지정했고 production V9/검증을 약화하지 않았다.
- 현재 Docker-capable CI 입력: parent snapshot `fef6267`. 실제 PostgreSQL 결과 및 후속 보정은 이 문서에 이어 기록한다.
