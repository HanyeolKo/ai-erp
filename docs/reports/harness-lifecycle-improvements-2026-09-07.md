# AI ERP 하네스 라이프사이클 개선 보고서

작성일: 2026-09-07. 상태: 모델 정책 revision 3와 라이프사이클 1·2·3단계의 구현·독립 검토·상위 수락 완료. 이 로컬 파일이 원본이며 Notion `AI 생성문서 관리`에 열람용 사본을 보관한다.

## 현재 범위

여섯 역할의 계약을 `router`, `product-planner`, `ui-ux-designer`, `implementer`, `reviewer`, `release-manager`로 분리하고, 상위 dispatch → acknowledgement → 제한된 실행 → 근거 반환 → 독립 검토 → 상위 승인 또는 수정 요청 순서를 유지한다. 제품 계획은 `INCREMENT-PLAN.md`, 화면 계획은 `SCREEN-PLAN.md`, 구현은 `IMPLEMENTATION-CONTRACT.md`, 릴리스 준비는 `RELEASE-CONTRACT.md`와 `RELEASE-RESULT.md`를 사용한다.

## 모델 배정과 업무 하달

최상위 감독과 최종 독립 판정은 Astra/high가 맡는다. 요청 분류·제품 기획·화면 설계는 Sol/medium, 구현과 승인된 배포 실행은 Luna/high를 기본으로 두며 Spark/high는 이유를 기록하는 구현 대안이다. 하위 실행자가 계약을 이해하거나 수행하지 못한 근거가 있을 때만 상위 판단자가 `Luna/high → Terra/medium → Sol/medium` 순서로 승격한다. Sol에서도 실패하면 Astra 실행자를 투입하지 않고 상위 판단자가 지시·분해·문맥·인터페이스·선행조건·수용 기준을 재검토한다.

최초 실행과 승격을 포함해 같은 영향 범위의 시도는 최대 세 번이다. 지시 버전이나 작업 이름만 바꾸어 횟수를 초기화하지 않는다. 일반 결함은 충분하다면 같은 모델로 수정하고, 외부 설정·사용량 제한은 모델 역량 부족으로 분류하지 않는다. 최종 독립 검토를 구현자나 상위의 자기 검토로 대체하지 않는다.

기본 실행자 한 명, 독립 작업만 최대 두 명, 위임 깊이 1을 설정한다. 전체 대화 대신 필요한 계약·소스와 문맥 예산을 하달하고, 같은 범위의 수정에는 결함과 변경분을 전달한다. 실제 작업별 토큰·캐시 사용량이 제공되지 않으면 미확인으로 기록한다. 설정과 구조 검사는 실제 토큰 절감률을 입증하지 않는다. 상세 설계는 `docs/architecture/orchestration-model-policy.md`를 참조한다.

## 점진적 기획에서 배포까지

제품 기획은 현재 증분의 요구사항·API·데이터·권한과 수용 기준을 정하고, 화면 결정은 별도 UI/UX 설계를 거친다. 구현·검토가 수락된 뒤 요청 범위에 배포가 포함된 경우에만 릴리스 준비와 실행을 진행한다. 기획만 요청했거나 배포하지 않는 증분도 해당 단계의 근거와 수락으로 완료할 수 있다.

배포 준비는 대상 SHA와 같은 SHA의 CI 성공, 환경·산출물·백업·복구 범위를 확인한다. 현재 main 게시가 CI 후 자동 배포를 유발할 수 있으며, 수동 dispatch는 CI 성공 이력을 자동 확인하지 않으므로 계약에서 별도로 확인해야 한다. readiness와 실제 배포 결과·관찰 결과를 구분한다. 앱 롤백을 DB 복구 승인으로 보지 않고, 로컬 우회 smoke를 공개 DNS/TLS 검증으로 보지 않는다. 기존 앱·CI·배포 구현은 이번 하네스 변경 대상에 포함하지 않았다.

## 외부 의존성 차단

Google OAuth/Calendar 같은 외부 API를 live-integration으로 사용하려면 계정·tenant·프로젝트, API 계약 버전, enablement·permission·OAuth callback, 키 이름(비밀값 제외), 외부 소유자와 현재 readiness check를 기록한다. 필수 설정이 `missing` 또는 `unknown`이면 의존 작업을 즉시 중단하고 `BLOCKER-REPORT`에 관찰된 실패, 영향 범위, 소유자, 해결 선택지와 재개 검사를 기록한다. 외부 소유자의 설정 제공, 검증된 기존 연동 사용, 명시적 `offline-contract-only` 대안 승인 중 하나가 선택되어 새 근거와 최신 plan/contract/review 및 상위 assignment 확인을 거쳐야 재개할 수 있다.

명시적으로 독립적인 오프라인 계약 테스트와 일반 코드 작업은 진행할 수 있지만 live integration 성공으로 표시하지 않는다. goal-mode 지속 실행은 외부 차단을 덮지 않으며, 변경되지 않은 외부 상태를 반복 시도하지 않는다. 파일 지침과 구조 테스트는 자동 스케줄러, Native 역할 호출, 실제 서비스 readiness 또는 운영 배포 결과의 증명이 아니다.

## 검증 한계

현재 Native planner smoke는 `unknown agent_type`을 반환했으며 `harness/maintenance/runs/lifecycle-management/native-planner-smoke.json`에 기록되어 있다. 따라서 새 역할의 실시간 Native 실행 성공은 인증되지 않았다. 현재 체크 출력은 `harness/maintenance/runs/lifecycle-management/`에 보관한다. 앞선 stage 3와 model revision 2 결과는 당시 정책의 이력이며, 최신 정책의 통과 근거로 재사용하지 않는다. 최신 검증과 독립 판정은 아래 근거에 확정했다. 실제 외부 API 연동·운영 배포·복구·토큰 절감 실험은 수행하지 않았다.

## 최종 검증과 수락

상위의 최종 실제 실행에서 `python -B scripts/test_verify_harness.py`의 77개 회귀가 통과했다. `python -B scripts/verify-harness.py`, `python -B scripts/smoke-ux-skills.py`, 고정 Factory의 전체 검증·provider preflight와 `git diff --check`도 종료 코드 0이다. 원시 명령·cwd·stdout·stderr·종료 코드는 `harness/maintenance/runs/lifecycle-management/external-guard-terra-checks.json`에 있다. 앞선 73개 검사는 Terra 수정 전 시점의 근거다.

독립 Astra 검토자는 모델 정책과 3단계 모두 `pass`, 남은 결함 0개로 판정했다. 최초 검토에서 발견한 비UI 계약 조건, 배포 계약 참조, 외부 중단 규칙의 부정 표현, 승격 주체·상한과 모델별 effort 충돌을 수정했다. 마지막 외부 규칙 결함은 반복된 실패 근거를 바탕으로 Luna에서 Terra/medium으로 한정 승격했고, 상위가 모호했던 지시 선택지도 명확히 했다. Astra 구현자나 자동 모델 순회는 사용하지 않았다.

Terra 작업자가 제출한 `checks.json`은 요약 출력과 명령 설명이 포함된 자료로 보존했다. 이를 원시 실행 근거로 인정하지 않고 상위가 전체 명령을 직접 다시 실행해 위 파일에 수집했다. 세부 판정과 근거 편차는 `final-review-2.json`, `stage-3-acceptance.json`, `evidence-rulings-final.json`에 기록했다.

보존 검사에서 원본 354개 파일의 허용 범위 밖 변경·삭제가 없고, 기존 작업 목록·저널과 AGENTS 관리 블록 밖 안내가 유지됨을 확인했다. 기존 앱·CI·배포·고정 vendor 구현도 보존했다. 변경은 현재 작업 트리에 있으며 실제 Git 커밋·게시·운영 배포는 수행하지 않았다. `scenario-cases.json`의 16개 사례는 정책 검토용 가상 상황이며 실제 승인·CI·배포 근거가 아니다.
