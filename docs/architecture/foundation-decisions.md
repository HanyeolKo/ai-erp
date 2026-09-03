# 초기 프로젝트의 경계와 계약

이 문서는 Google Slides를 대체하지 않는다. 초기 코드의 범위와 후속 구현 전 지켜야 할 조건을 설명한다.

## 지금 구현하는 것

하나의 Spring Boot 배포 단위 안에 7개 업무 모듈을 둔다. 각 모듈은 자기 데이터의 소유자이며 공개 계약만 외부에 노출한다. 현재는 기능이 없는 경계가 대부분이고, 경계 테스트와 테스트 기반 API 문서화 경로를 먼저 검증한다.

| 코드 모듈 | 데이터 schema | 책임 |
|---|---|---|
| identity | identity | 사용자 식별·로그인 |
| group | group | 그룹·그룹 구성원 |
| project | project | 프로젝트·프로젝트 참여자 |
| schedule | schedule | 일정 원본·일정 변경 확인 |
| notification | notification | 인앱 알림·발신 결과 |
| calendarintegration | calendar_integration | Calendar 연결·비권위 투영 |
| audit | audit | 운영·보안 감사 기록 |
| platform | platform | 공통 기술 설정. **업무 모듈 아님** |

Java의 package 이름은 `calendarintegration`, DB는 설계서의 `calendar_integration`으로 정규화한다. SQL 예약어와 겹치는 `group` schema는 필요한 곳에서 인용한다. Flyway history는 platform에 보관한다. Flyway는 모듈의 운영 DB 계정 분리가 아니라 **마이그레이션 소유권**과 이력의 출발점이다.

## 모듈 간 데이터 전달 규약

- 모듈 외부로 Entity/Repository를 전달하지 않는다. 값은 불변 Java `record` DTO로 전달한다.
- 공개 계약은 `api` named interface에 둔다. 타 모듈의 구현 클래스·내부 패키지를 직접 참조하지 않는다.
- 외부 HTTP DTO와 내부 모듈 DTO는 수명과 책임이 다르므로 동일 객체를 강제 재사용하지 않는다. 현재 존재하지 않는 업무 DTO나 범용 만능 envelope를 선제적으로 만들지 않는다.
- 조회·즉시 검증은 소유 모듈 공개 API로 요청한다. 한 업무 명령의 데이터 쓰기는 한 소유 모듈이 책임진다.
- 후속 쓰기·외부 연동은 커밋 이후 이벤트로 분리하는 방향이다. Publication 레지스트리의 저장 schema·트랜잭션 예외·재처리/멱등성 정책은 C05 검토 후 구현한다.
- HTTP의 ID/시간/오류/버전 규약은 SYS 11을 기준으로 상세화하되 C02 충돌을 해소한 뒤 업무 DTO를 고정한다.

모듈 경계 검증은 시작점이며 데이터베이스 접근까지 완벽히 강제하는 보안 경계는 아니다. 후속 기능마다 공개 API 의존성, SQL, 테스트에서 소유권을 함께 확인한다.

프런트는 현재 연결 확인용 단일 조회만 제공한다. SYS 12의 TanStack Query 기반 업무 서버 상태 관리와 화면별 기능 분리는 첫 업무 화면을 구현할 때 적용한다. 현재의 연결 확인 화면을 전체 화면설계 구현으로 보지 않는다.

## 인증·세션의 현재 상태

Spring Security와 Redis Session 의존성·기본 설정을 준비한다. **Google OIDC 로그인, 초대 기반 접근, 실제 권한 평가, 사용자 세션 저장 포맷은 아직 구현하지 않는다.** 기능이 준비되기 전 임의 경로는 차단한다. 시스템 정보 조회는 문서 파이프라인 확인을 위한 비민감 읽기 전용 API다.

BlueGreen 전에 userId/sub 중심 principal, 역할 요청별 조회, 세션 ID 재발급, JSON 직렬화, N/N-1 호환을 실제 인증 흐름에서 시험해야 한다. Redis 의존성이 존재하거나 세션 쿠키 속성이 설정되었다는 사실만으로 세션 호환이 완료됐다고 판단하지 않는다.

## 테스트와 문서가 한 계약을 공유

MockMvc에서 실제 응답과 엄격한 REST Docs 필드 명세를 검증한다. 같은 테스트 결과의 resource.json을 OpenAPI로 변환하고, 동일 스펙에서 TypeScript 타입과 Swagger UI를 만든다.

```text
실제 Controller + 보안 설정
    → MockMvc 계약 테스트 / REST Docs
    → resource.json → openapi3.yaml
    → 명세 검증 → TypeScript 타입 / 정적 Swagger UI
```

API 변경 시 테스트·문서가 같이 갱신되어야 한다. 현재 PoC는 공개 시스템 조회 1개에 한정한다. 로그인·쿠키 인증·CSRF·400/401/403/404/409 업무 오류의 문서화는 각 업무 API를 추가할 때 확장한다. SYS 13·INF 10에 따라 **운영 Swagger UI는 내부 접근과 Try it out 제한**을 적용한다. 초기 산출물은 자동 배포하지 않는다.

## 운영과 개발 환경을 혼동하지 않기

개발 Compose는 PostgreSQL과 Redis를 localhost에만 노출한다. 운영은 Caddy와 Blue/Green 앱 슬롯, 공유 DB/Redis를 기준으로 설계했지만 초기 저장소에는 생산 배포 자동화가 없다. 개발 기본 비밀번호를 운영에 사용하지 않는다.

소형 서버에 두 앱 슬롯과 DB/Redis를 함께 올릴 메모리가 있는지, 공인 접근 방식/TLS/백업 대상/복구 목표가 무엇인지 확정한 뒤 운영 구성을 구현한다. DB 마이그레이션은 expand-contract를 따르고, 롤백은 앱 전환이지 DB를 무조건 되돌리는 동작이 아니다.
