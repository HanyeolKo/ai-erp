# 프로젝트 중심 UI 재설계 구현·검증 보고

작성일: 2026-09-07 (Asia/Seoul). 원본: `C:/Users/USER/.codex/worktrees/60aa/AI ERP/docs/ux/2026-09-07-project-management-verification.md`.

## 변경 결과

첫 화면을 **프로젝트 선택**으로 재구성했다. 사용자는 이름만 입력해 프로젝트를 만들고, 생성된 프로젝트의 개요·구성원에서 초대 링크와 코드를 발급해 복사·공유한다. 받은 사람은 로그인한 뒤 프로젝트·역할·만료를 확인하고 직접 참여한다. 관리자 요청문 복사와 선행 그룹 선택은 새 기본 흐름에서 제거했다.

- 프로젝트 안의 탐색은 개요·일정·구성원으로 정리하고, Calendar 연결은 개인 계정 설정으로 이동했다.
- 구성원과 일정 참여자는 표시 이름·이메일·한국어 역할과 확인 상태를 사용한다. 일정 상세의 프로필 조회는 권한 확인 후 실제 참여자 ID 최대 200개로 제한한다.
- 관리자는 공유 초대를 발급·교체·사용 중지할 수 있다. 공유 초대는 7일 동안 재사용 가능하며 기존 구성원의 역할을 낮추지 않는다.
- 생성·초대의 진행 중 또는 결과 불명 상태는 화면을 닫거나 다른 경로를 다녀와도 유지한다. 이전 세션 응답과 늦은 조회가 새 상태나 폐기한 코드를 복원하지 못하도록 검증했다.
- 모바일 메뉴, 기간 탐색, 다이얼로그 포커스, 권한에 맞는 빈 화면 안내를 정리했다. 기존 일정의 시간대·확인·확정·취소 규칙과 이메일 전용 초대 호환은 유지했다.

## 오케스트레이션과 독립 검토

기존 `ai-erp` 설정을 변경하지 않았다. 실제 UI 전문 기획 → 독립 계획 PASS → 상위 READY 계약 → 경량 구현 → 독립 task-review 순서를 따랐다. 상위 에이전트는 앱·테스트 코드를 직접 구현하지 않았다.

기본 Spark/high를 실제 CLI로 호출했으나 사용량 제한으로 종료됐다. 실행 로그는 `tmp/project-redesign-spark-core.jsonl`, `tmp/project-redesign-spark-schedule.jsonl`이다. 이후 프로젝트에 명시된 대체 모델 `gpt-5.6-luna/high` 구현 에이전트가 작업했다. 모델 선택과 검증 결과를 구현 결과 문서에 기록했다.

독립 검토의 최신 판정은 [검토 원본](2026-09-07-project-management-redesign-review.md)에 있다. 로컬 소스와 실행 가능한 검사는 수용됐으나 **전체 task-review는 PostgreSQL 필수 통합 실행 대기**다. 로컬 단위 검사나 합성 API 브라우저 검사를 DB 통합 통과로 간주하지 않는다.

## 실행한 검증

| 검사 | 실제 결과 | 증거 |
| --- | --- | --- |
| 최종 프론트엔드 전체 | `pnpm --dir frontend exec vitest run --maxWorkers=2`, exit 0, 13개 파일 199/199 | `tmp/project-redesign-frontend-final.log` |
| 최종 타입 검사·빌드 | `pnpm frontend:build`, exit 0, TypeScript와 Vite 빌드 완료 | `tmp/project-redesign-frontend-build.log` |
| 백엔드·API 명세·통합 소스 컴파일 | Java 25 `gradlew.bat test openapi3 compileIntegrationTestJava`, exit 0, 단위 77 + OpenAPI 7, 실패·오류 0 | `tmp/project-redesign-backend-identity-check.log` |
| 생성 API 타입 | `pnpm api:generate`, exit 0 | `tmp/project-redesign-api-identity-generation.log` |
| 배포 계약 | 145개 시나리오·560개 단언 통과 | `tmp/project-redesign-deployment-tests.log` |
| 최신 V7 집중 검사 | 4개 시나리오·23개 단언 통과 | `tmp/project-redesign-migration-final.log` |
| 변경 공백 검사 | `git diff --check`, exit 0 | 상위 실행 출력 |

프론트엔드의 이전 기본 병렬 실행에서는 한 검사만 5초 제한에 걸렸다. 테스트 제한값·설정을 느슨하게 바꾸지 않고 실행 worker를 2개로 제한해 최종 전체 검사를 통과했다. Windows sandbox의 Vite 하위 프로세스 `spawn EPERM`은 정상 승인된 실행 권한으로 재실행했다.

## 실제 브라우저 확인

로컬 합성 API(`127.0.0.1:8080`)와 빌드된 UI(`127.0.0.1:5179`)를 사용했다. 운영 계정·데이터·서비스에는 쓰지 않았다. 최종 자산은 `index-DAbxgsdf.js`, `index-CrnnDiCH.css`다.

- 이름 입력으로 프로젝트 1개 생성 → 해당 프로젝트의 초대 발급 1회 → 코드 복사 성공 안내 → 서로 다른 두 계정의 명시적 참여 2회와 두 MEMBER 멤버십을 확인했다. 실제 상태 증거: `tmp/project-redesign-browser-two-joins.json`.
- 새 빌드에서 초대 사용 중지 뒤 코드·링크가 사라지고 기존 링크가 사용할 수 없는 상태로 전환됨을 확인했다.
- 320px 화면에서 문서 가로 폭 320px, 이전·다음 기간 버튼 44×44px, 일정 상세의 참여자 이름·이메일을 확인했다.
- 모바일 메뉴와 초대 다이얼로그는 Escape 후 호출 버튼으로 포커스가 돌아왔다. 720 CSS px / DPR 2 재배치에서도 가로 넘침이 없었다. 이는 200% 상당의 재배치 확인이며 브라우저 자체 확대 기능 검증으로 표기하지 않는다.
- 화면 증거: `tmp/project-redesign-lobby-final.png`, `tmp/project-redesign-share-final.png`, `tmp/project-redesign-mobile-final.png`. 초대 PNG는 최종 `index-DAbxgsdf.js`이며, 로비·모바일 PNG는 마지막 내부 경합·역할별 개요 문구 보완 전 `index-J6HuaJQx.js`다. 그 사이 로비와 일정 레이아웃 CSS 변경은 없다. 브라우저 재연결 이후 전체 PNG 재촬영은 도구 오류로 완료되지 않아 이전 화면 증거를 최신 촬영으로 표시하지 않는다.

## 남은 검증과 전달 경계

Docker 미설치로 PostgreSQL/Redis Testcontainers 실행은 초기화 단계에서 실패했다. DB 동시 가입·생성 재시도·마지막 관리자 잠금·rollback·V6→V7 데이터 보존은 컴파일과 소스 검토까지 완료했으며 **실제 통합 실행은 미완료**다. Windows 배포 검사의 명령 대역은 Linux 실제 파일 소유권·잠금 실행을 대신하지 않는다.

기존 CI에는 Java 25 통합 검사, Linux 배포 계약, 생성 API, 프론트엔드, Docker 이미지 빌드가 포함돼 있다. 공개 저장소의 새 작업 브랜치와 draft PR에 제출한 뒤 해당 검사를 확인해야 전체 task-review를 확정할 수 있다. 이 보고 시점에는 공개 push·PR 생성·운영 배포를 수행하지 않았다. 이전 PR #8의 push 승인은 해당 PR 범위였으며 새 변경은 별도 제출 대상으로 준비했다.

로컬 원본을 먼저 갱신하고 Notion `AI 생성문서 관리`에 보관한다. 보관본은 편집 기준이 아니다.
