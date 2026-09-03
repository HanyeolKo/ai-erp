# planning 문서 상호 정합성 검토

검토일: 2026-09-03 · 상태: **기반 구축 진행 가능 / 업무 기능 계약 확정 전 보완 필요**

이 문서는 독립 검토 3개(기획 범위·권한, API·모듈 계약, 기술 조합)를 메인 에이전트가 원문과 대조해 취합한 결과다. Google Slides 원본은 이번 점검에서 수정하지 않았다. 아래 '권고'는 최종 승인된 업무 정책이 아니다.

## 검토 원본

[Google Drive planning](https://drive.google.com/drive/folders/1IvWfdFs4B8n8RmFhddJOitq3-Tpxy6Ir)의 최신 문서 4종을 전체 읽었다. `old`는 이력 보관이며 현재 구현 기준에서 제외한다.

| 약칭 | 문서 | 범위 |
|---|---|---|
| SYS | [시스템 아키텍처 설계서 v0.1](https://docs.google.com/presentation/d/15e0bT8FEBs0wNIXSzzMiNWgLjfCsVbtYTp5jPq9gE5E/edit) | 33장, 화면설계 포함 |
| INF | [서버 인프라 구축 설계서 v0.1](https://docs.google.com/presentation/d/1m13aNdgTbqlbRDXmGQCeNufm_rZoFlJcqQoh8Sl1Y88/edit) | 22장 |
| SCR | [화면기획 v0.7](https://docs.google.com/presentation/d/1j74T6G7-tuEISKSQtScUSKSWRvlogMeQwzanlb5Dyyc/edit) | 18장 |
| FUT | [확장 참고안 v0.7](https://docs.google.com/presentation/d/1B0SGiCvTflgq-6IHju8-VA6TahBO25sDsoixwpaNWtI/edit) | 10장 |

장 번호는 2026-09-03 검토본 기준이다. 슬라이드 재배열 시 번호보다 제목과 화면 ID를 우선한다. 로컬 기존 기획 자산·변환용 PPT·원문 추출 파일은 Git에 포함하지 않는다. Google Slides를 기획 원본으로 계속 관리한다.

## 서로 일치하는 기반

- 1차 제품 축은 **프로젝트 일정관리**다. 챗봇·AI 회의록·일반 메일함·결재는 1차 범위가 아니다. 알림과 미래 확장 가능성은 남긴다.
- 프런트 TypeScript, 백엔드 Java, 명확한 모듈 경계를 가진 모듈러 모놀리식이다. 모듈 간 Entity/Repository 직접 참조·cross-schema join을 허용하지 않는다.
- 일정 원본은 ERP이며 Google Calendar는 단방향 파생 데이터다. 외부 연동 실패는 이미 확정된 일정의 커밋을 되돌리지 않는다.
- 일정 상태, 사용자 변경 확인, Calendar 연결/동기화 상태는 서로 다른 개념이다.
- PostgreSQL은 업무 원본, Redis는 세션 저장소다. Redis Cluster·Kafka·Kubernetes를 선제 도입하지 않는다.
- 테스트에서 REST Docs와 OpenAPI를 생성하고 동일 스펙을 Swagger UI와 프런트 타입이 소비한다.
- 운영은 소형 온프레미스를 우선 고려하며 Caddy 2 + Blue/Green을 지향한다. 하나의 호스트에서 Blue/Green은 배포 전환 수단이지 호스트 장애에 대한 고가용성이 아니다.

## 업무 구현 전에 맞춰야 할 항목

| ID / 우선순위 | 원문 근거와 충돌 | 권고 정리 | 이번 초기 구축 처리 |
|---|---|---|---|
| C01 높음 | SYS 20·31: Member 일정 생성·변경 허용. SCR 8: Member는 본인 생성 일정만 수정 | 역할뿐 아니라 일정 작성자 조건을 명시. 타인 일정 수정/Manager 예외를 계약 테스트로 표현 | 일정 권한/API/Entity 미구현. 정책 승인 대기 |
| C02 높음 | SYS 11·21: rowVersion이 낙관적 잠금, businessRevision은 의미 있는 변경. SYS 28: businessRevision으로 동시 수정 감지. SYS 16은 PUT, 31은 PATCH | 두 revision의 목적을 먼저 일치시킨 후 각 수정/확인 요청에 어떤 값을 각각 또는 함께 요구할지 확정. HTTP 수정 방식도 한 가지로 통일 | 업무 요청 DTO와 HTTP 수정 계약을 만들지 않음 |
| C03 높음 | SCR 16/P04 및 SYS 28은 공유 대상과 참석자 이메일 입력을 분리. SYS 9에는 memberId 중심 참여자 모델, SYS 22는 ProjectMember 이메일 reader ACL | 프로젝트 접근권한·일정 참여자·외부 이메일 초대를 별도 정의. 외부 이메일의 1차 지원 여부를 승인받아야 함 | 외부 참석자/Calendar 공유 API 미구현 |
| C04 높음 | SYS 9·19에서 invitation/ProjectMember는 project 소유. SYS 30의 createInvitation 담당은 group·notification. SYS 31의 Calendar 연결 담당은 project·calendar 혼재 | **명령 소유 모듈 하나**와 협력 모듈을 표에서 분리. 초대는 project, Calendar 연결은 calendar-integration 소유를 권고 | 모듈 경계만 구성. 초대·연결 쓰기 구현 없음 |
| C05 높음 | SYS 8: 자기 schema만 쓰기. SYS 9·16·17: 업무 변경과 platform.event_publication을 같은 DB 트랜잭션에 기록 | 업무 데이터 소유권 규칙에 '기술적 publication 레지스트리 기록' 예외를 명시하거나 모듈별 publication 저장 위치를 결정 | schema만 생성. publication 런타임·테이블·작업자 도입 보류 |
| C06 중간 | SCR 초대 응답은 승인 즉시 접근·Calendar 공유가 되는 것으로 읽힘. SYS 19·22는 멤버 커밋 이후 ACL 비동기 반영 | '프로젝트 접근 완료 / Calendar 반영 대기'를 구분. 실패·재인증 안내와 재시도 주체 명시 | 초대 완료 화면/ACL 구현 없음 |
| C07 중간 | SYS 9·21은 변경 확인을 schedule이 소유. SYS 12 notification 항목에 '변경 확인' 혼재, SYS 31에는 ACK API 누락 | 알림 읽음과 일정 변경 ACK를 분리. ACK 명령은 schedule, 알림은 진입 링크/읽음만 소유 | ACK 및 알림 계약 미구현 |
| C08 중간 | SYS 22는 connection DISCONNECTED/CONNECTED/REAUTH, projection PENDING/SYNCED/FAILED. SCR 9는 표시 상태를 한 목록으로 표현. SYS 17은 REAUTH_REQUIRED | 도메인 두 상태를 유지하고 화면의 파생 표시 우선순위를 정의. REAUTH 계열 enum 이름 통일 | Calendar enum/상태 계산 구현 없음 |
| C09 중간 | SCR 11·14/P01에 작업 마감·프로젝트 목표가 등장하지만 SYS 3·SCR 2·FUT는 일정 중심 1차 범위 | 1차 대시보드는 일정·미확인 변경·연동 오류를 축으로 정리. Task/Milestone 기능 추가 여부 별도 승인 | Task/Milestone/실적 모듈 생성하지 않음 |
| C10 중간 | SYS 30·31의 화면/API 표가 생성·확정·취소·ACK·초대 거절을 완전히 열거하지 않음 | 대표 API 표와 전체 API inventory를 구분. 각 업무 행위를 operationId·권한·성공/오류 테스트에 연결 | 시스템 정보 조회 1개로 테스트→문서 파이프라인만 검증 |
| C11 검증 과제 | SYS 18, INF 13~15·21은 세션 값 호환·Java 객체 직렬화 금지·PostgreSQL lease/멱등 키·DB 호환·앱 역전환·복구 리허설을 명시 | **문서 간 충돌이 아닌 구현/검증 과제**. 합의된 운영 계약을 실제 릴리스와 리허설 증거로 확인 | 개발 Compose 및 운영 방향 설명만. 생산 배포 자동화 미구현 |
| C12 낮음 | SYS audit append-only 기록과 FUT 사용자용 활동 타임라인이 동일 기능으로 읽힐 수 있음 | 보안/운영 감사 저장과 사용자 활동 화면은 목적·노출권한·보존기간이 다른 기능임을 명시 | audit 경계만 선언. 타임라인 기능 없음 |
| C13 중간 | INF 20은 'Redis 소실 → 서비스 가능 → 재로그인'으로 요약. 세션 의존 서비스에서 Redis 접속 장애와 데이터만 유실된 상황은 영향이 다름 | Redis 정상 복구 후 세션 데이터 유실은 재로그인으로 회복. Redis 접속 불가 중에는 인증 서비스 영향/readiness 실패를 명시하고 liveness와 구분 | readiness에 Redis 포함, liveness에서는 제외. 운영 장애 복구 완료로 오해하지 않도록 문서화 |

### 권한·버전·초대에 대한 핵심 판정

기반 프로젝트가 만들어졌다고 위 충돌이 해소된 것은 아니다. 특히 **Member 수정 범위, 외부 참석자, 초대/Calendar 명령 소유권**은 사용자의 최종 검토 후 업무 코드를 시작한다. 기존 설계의 명확한 원칙과 화면의 요약 문구가 다를 때 임의로 한쪽을 구현해 사실상 정책을 확정하지 않는다.

추가 운영 검토 제안: 프런트 정적 자산의 digest·캐시와 백엔드 릴리스를 어떻게 함께 전환할지 명시하면 좋다. 이는 원문 간 모순 판정이 아니라 운영 구현 전에 보강할 제안이다.

## 기술 버전 검토

공식 문서와 Maven Central/npm/공식 이미지 태그에서 존재를 확인했다. 레지스트리에 존재하는 것과 이 프로젝트에서 빌드·호환 테스트가 통과하는 것은 별도다.

| 구분 | 문서 기준 | 검토 결과 |
|---|---|---|
| Java / Boot | 25 LTS / 4.1.1 | Boot는 Java25 지원. Java25로 검증해야 하며 로컬 Java21로 대체하지 않음 |
| Modulith | 2.1.1 | BOM 존재, Boot4.1.1 조합 확인. 초기에는 모듈 경계 검증만 사용 |
| QueryDSL | 7.5 | **OpenFeign fork** `io.github.openfeign.querydsl:querydsl-jpa:7.5`, APT `:querydsl-apt:7.5:jpa` 사용. Boot 기본 `com.querydsl:5.1.0`와 구분 |
| REST Docs | 4.0.1 | Boot4.1.1 BOM 관리 |
| OpenAPI 생성 | ePages 0.20.1 / OpenAPI3.0.1 | upstream은 Boot4.x와 0.20.x+ 호환 안내. 실제 clean test→openapi3→명세 검증→Swagger 결과로 PoC 판단 |
| React / Vite / TS | 19.2.7 / 8.2.2 / 6.0.2 | 각 정확한 버전 존재. Vite 요구 Node ^20.19 또는 >=22.12, 초기 Node22.19 사용 |
| 테스트 BOM 범위 | SYS 4의 Boot BOM 관리 표기 | Boot4.1.1 BOM은 JUnit Jupiter6.0.3·Testcontainers2.0.5를 관리한다. Playwright는 포함하지 않으므로 화면 E2E 도입 시 별도 버전/브라우저 잠금이 필요 |
| 데이터 | PostgreSQL18.6 / Redis8.2.9 | 이미지 태그 존재. 개발 테스트로 실제 구동 검증 |
| 운영 후보 | Caddy2.11.4-alpine / Docker29.7.2 / Compose5.5 | 릴리스 존재. 이번에는 설치/업그레이드·운영 배포 안 함. 운영 전 플랫폼별 digest·메모리 예산·복구 검증 필요 |

근거: [Spring Boot 시스템 요구사항](https://docs.spring.io/spring-boot/system-requirements.html), [Spring Modulith](https://docs.spring.io/spring-modulith/reference/), [Spring REST Docs](https://spring.io/projects/spring-restdocs/), [ePages 공식 호환표](https://github.com/ePages-de/restdocs-api-spec), [OpenFeign QueryDSL 7.5](https://central.sonatype.com/artifact/io.github.openfeign.querydsl/querydsl-bom/7.5), [Docker Engine 릴리스](https://docs.docker.com/engine/release-notes/29/).

## 다음 검토 순서

1. C01~C05의 정책/계약을 Google Slides에 보완하고 사용자 최종 검토.
2. 나머지 화면 문구·API inventory를 같은 결정에 맞춰 동기화.
3. 승인된 한 사용자 흐름을 선택해 기능 구현, 테스트, API 문서, 화면을 함께 추가.
4. 운영 환경 사양과 접근 방식을 확정한 후 BlueGreen·세션 호환·복구 리허설을 별도 구현.

현재 저장소의 통과한 검증과 미검증 항목은 [초기 구축 검증 기록](bootstrap-verification.md)에 별도로 남긴다.
