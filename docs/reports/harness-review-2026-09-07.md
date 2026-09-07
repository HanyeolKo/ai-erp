# AI ERP 하네스 전체 점검과 개선안

작성일: 2026-09-07 (Asia/Seoul)
원본: `D:/onedrive/Documents/ChatGPT/AI ERP/docs/reports/harness-review-2026-09-07.md`
기준: `main` / `a7f9b329d676a5babd6feae4b95043b5010a506d`
검토 목적: **점진적 기획·설계 → 프런트·백엔드 개발 → 검증 → 배포·복구 → 다음 기획 반영**을 관리하는 하네스로서의 적합성.
Notion 보관 위치: `AI 생성문서 관리`. 원본을 먼저 작성하고 동일 내용을 보관한다. 동기화 날짜: 2026-09-07.

## 판정

설치된 하네스의 프로젝트 구조 검증은 **통과**했다. 다만 사용자가 명확히 한 전체 개발 과정의 관리 목적에는 **보완이 필요하다**. 현재 목적과 흐름은 UI 전문 기획·검토와 상위 계약에 따른 코드 구현에 집중되어 있다. 업무 기획을 작은 개발 단위로 구체화하고, 통합 검증과 배포 결과를 다음 기획으로 연결하는 공통 계약·평가·상태 연결이 부족하다.

독립 검토 역할 `ai-erp-reviewer`의 판정도 설치 구조는 통과, 전체 과정의 요구 범위는 미충족이다. 이는 현재 하네스가 깨졌다는 판정이나 실제 개발 성과가 나쁘다는 측정 결과는 아니다.

이번 작업은 점검과 개선안 작성이다. 하네스 설정·코드·기존 상태는 변경하지 않았으며, 보고서와 검증 근거만 추가했다.

## 직접 실행한 검사

환경: Windows / Python 3.14.3. 시작 시 Git 작업 트리는 깨끗했다.

| 검사 | 결과 | 근거 |
| --- | --- | --- |
| 설치된 Factory 0.3.0 원본 검증기 | 종료 1 | 상위 역할 3개의 `model`, `model_reasoning_effort` 누락만 보고 |
| 프로젝트 검증기 | 종료 0 | schema, permissions, DAG, Codex parity, UI routing, vendor 잠금 통과 |
| Git 추적 포함 프로젝트 검증 | 종료 0 | `--require-tracked` 통과 |
| 검증기 회귀 테스트 | 종료 0 | 22개 모두 성공 |
| 오프라인 UX 검색 스모크 | 종료 0 | SaaS 및 접근성 폼 검색 결과 확인 |

정확한 실행 명령:

```powershell
python -B 'C:\Users\USER\.codex\plugins\cache\harness-factory-marketplace\harness-factory\0.3.0\scripts\validate_runtime_neutral.py' 'D:\onedrive\Documents\ChatGPT\AI ERP'
python -B scripts/verify-harness.py
python -B scripts/verify-harness.py --require-tracked
python -B scripts/test_verify_harness.py
python -B scripts/smoke-ux-skills.py
```

원시 출력·종료 코드·명령은 `docs/reports/evidence/harness-review-2026-09-07/checks.json`에 보존했다. Factory 실패는 상위 모델 상속을 허용하는 프로젝트 정책과 구버전 검증기의 차이다. 프로젝트 검증기는 해당 두 필수 키만 완화하며, 이 호환 처리는 `scripts/verify-harness.py:54`와 `docs/architecture/ui-ux-agent-guide.md:69`에 설명되어 있다. 원본 Factory까지 모두 통과했다고 보고해서는 안 된다.

## 현재 기반에서 확인한 장점

- `core` 프로필, Codex 대상, 4개 역할과 8개 스킬이 일치한다. 8개 스킬의 원본·검색용 사본은 바이트가 같다.
- 실행용 Markdown 23개는 가장 긴 파일도 32줄이다. 문서가 과도하게 길어지는 문제는 확인하지 못했다.
- UI 기획의 전문가 경로, 구현자의 제한된 실행 권한, 독립 reviewer의 판정 소유권이 명시되어 있다.
- 프런트·백엔드·스크립트·인프라·CI·루트 설정·테스트는 모두 상위 구현 계약의 적용 대상이다.
- 외부 UX 자료와 Factory 코드의 고정 출처·해시, Git 추적, CI 하네스 검사가 연결되어 있다.
- 배포 자동화에는 main/CI SHA 확인, 백업, 마이그레이션, Blue/Green 전환, smoke, 실패 복구 및 rollback 구현이 이미 있다. 이번에는 해당 코드를 읽었으며 운영에서 실행하지 않았다.

## 전체 과정 기준의 부족한 연결

| 단계 | 현재 지원 | 필요한 보완 |
| --- | --- | --- |
| 점진적 기획 | 부모가 요구를 해석하고 구현 계약 작성 | 문제·업무 목표·우선순위·현재 개발 단위·보류 요구·미결 가정을 관리하는 기획 계약과 검토 |
| 설계 | UI 화면 계획과 일반 구현 계약 | 업무 규칙·API·데이터·권한·프런트/백엔드 영향과 설계 변경의 연결 |
| 개발 | UI/비 UI 구현자 경로 및 독립 검토 | 하나의 기능 단위 아래 프런트·백엔드 계약과 의존성, 변경된 API 기준의 통합 |
| 검증 | 하네스·프런트 지침과 일반 검증 필드, 저장소 CI | 변경 유형별 백엔드·API·DB·프런트·통합·배포 검사 선택 및 수용조건 연결 |
| 배포·복구 | 저장소 배포 코드와 일반 외부 쓰기 승인 규칙 | 배포 책임자, 대상 SHA/환경, 승인 범위, CI 실행, 배포/복구 결과와 하네스 작업 연결 |
| 다음 개발 단위 | 결함 재시도와 next_action | 사용자 피드백·검증 결과를 요구/우선순위/계획 revision에 반영하는 전이 |

범위 근거: `harness/harness-spec.json:6`의 목적, `harness/loops/EXECUTION-LOOP.md:3`의 흐름, `harness/templates/IMPLEMENTATION-CONTRACT.md:5`의 계약 필드, `harness/evaluation/TASK-REVIEW-RUBRIC.md:5`의 계획 전용 처리, `harness/ENVIRONMENT.md:5`의 검사 목록. 일반적인 비 UI 기획·설계를 위한 별도 평가 기준은 없다.

배포는 `harness/team/agents/implementer.md:28`에서 구현자의 권한 밖으로 분리되어 있다. 이는 유지할 수 있는 경계지만, 상위 조정자가 어떤 근거로 기존 배포 workflow를 실행·추적하고 reviewer가 언제 완료를 판정하는지는 하네스에 연결해야 한다. `.github/workflows/deploy-production.yml:19`의 CI 완료 후 자동 실행 경로도 있으므로, 배포 계약은 main 반영이 배포를 유발한다는 효과와 기존 승인 범위를 함께 기록해야 한다.

운영의 `active-state.json`과 하네스 작업 상태는 목적이 다르다. 운영 상태를 복제하지 말고 release SHA·manifest·실행 결과의 위치를 하네스의 개발 단위에 연결하는 방식이 적절하다.

## 우선순위가 높은 개선안

1. **점진적 기획·설계 계약부터 추가한다.** 부모/라우터가 문제·목표·사용자 가치·우선순위·이번 개발 단위·보류 범위·가정·검증 기준을 정리하고 reviewer가 검토한다. 화면 결정은 기존 `ui-ux-designer` 경로로 이어진다. 모든 미래 요구를 미리 확정할 필요는 없으며, 구현에 들어갈 현재 단위의 미결 결정만 해소한다.
2. **기획부터 배포까지 같은 작업 식별자로 연결한다.** 개발 단위 ID → 기획 revision → UI/API/데이터 설계 → 프런트/백엔드 구현 계약 → 통합 검증 → commit/CI → 배포 결과 → 피드백을 연결한다. 구현 완료와 운영 반영 완료를 별도로 판정한다.
3. **배포 계약·결과·검토 기준을 기존 운영 도구에 붙인다.** 대상 환경/SHA, 이미 받은 승인, CI 근거, DB 호환성, 백업/복구 근거, smoke 결과, 실제 manifest 위치, 실패 시 다음 행동을 기록한다. 상위 조정자가 배포를 관리하고 reviewer가 결과를 검토하며, 구현자의 제한된 권한은 유지한다.
4. **변경 유형별 검사 목록을 구체화한다.** 백엔드는 저장소 CI의 test/integrationTest/OpenAPI 경로, 프런트는 test/typecheck/build, API 변경은 생성 타입과 계약 정합성, DB 변경은 migration/이전 릴리스 호환성, 배포 변경은 배포 계약 테스트를 연결한다. 매번 모든 검사를 반복하기보다 해당 개발 단위의 영향으로 선택한다.
5. **상태와 복구 기록을 개발 단위 중심으로 갱신한다.** `state.json`의 기존 queue/evaluator/next_action을 활용하고, 계획·계약·review·release의 상세 연결은 지속 보관되는 작업 기록에 둔다. 사용자 피드백으로 새 기획 revision을 만드는 것은 업무 반복이며, 하네스 자체의 자동 개선이나 적응형 메모리를 추가하는 일과 구분한다.

최소 변경 후보는 `INCREMENT-PLAN.md`, 기획 검토 rubric, `RELEASE-CONTRACT.md`, `RELEASE-RESULT.md`, 릴리스 검토 rubric 및 기존 역할·entry skill·loop·검증기의 연결이다. 파일명은 제안이며 아직 설치하지 않았다. 새 에이전트나 `adaptive/governed` 프로필을 추가하지 않고 기존 4개 역할과 `core`에서 먼저 정리할 수 있다.

점진적 DB 확장에서 특히 확인할 사항: `scripts/lib-deploy.sh:120`의 `verify_migrations()`는 현재 SQL 4개와 전체 개수 4를 요구하고 체크섬도 이 목록을 사용한다. 새 migration을 추가하면 배포가 거부되므로, DB 변경 계약에 migration 목록·체크섬·호환성과 관련 배포 테스트 갱신을 포함해야 한다. 현재의 제한 자체를 제거하라는 권고는 아니다.

추가 배포 검토 기준: 현재 rollback은 이전 애플리케이션 전환이며 DB undo/복원을 수행하지 않는다(`scripts/rollback.sh:35`). 백업의 `pg_restore -l` 확인도 실제 복원 훈련을 대신하지 않는다. 운영 smoke는 고정 IP의 `--resolve`와 `--insecure`를 사용하므로 공인 DNS/TLS 신뢰까지 검증했다고 기록하면 안 된다(`scripts/smoke.sh:19`). 수동 workflow dispatch는 main SHA를 확인하지만 CI 성공 이력을 다시 확인하지 않으므로, 수동 배포의 CI 근거 확인을 release 계약에 명시해야 한다(`.github/workflows/deploy-production.yml:52`).

## 검증기에서 재현한 결함과 개선 후보

독립 코드 검토는 실제 파일을 바꾸지 않고 메모리상의 읽기 결과만 바꿔 관련 함수에 입력했다. 아래 재현은 함수 단위 검사이며 변경된 전체 프로젝트 검증을 실행한 결과는 아니다. 정확한 재현 명령과 출력은 `docs/reports/evidence/harness-review-2026-09-07/targeted-probes.txt`에 보존했다.

- **P2 / V-01 — 저장된 기본 모델과 호출 시 fallback을 구분하지 못한다.** `scripts/verify-harness.py:173`은 wrapper의 Luna 기본값도 허용한다. wrapper의 Spark만 Luna로 치환하면 `verify_model_and_paths()`가 오류 없음으로 반환한다. `scripts/test_verify_harness.py:130`도 이를 허용한다. `implementer.md:15`의 Spark 기본/Luna 명시적 호출 정책에 맞춰 기본값 검사와 실제 invocation 근거 검토를 분리해야 한다.
- **P2 / V-02 — UI 구현 진입의 검토·계약 조건이 검사되지 않는다.** `scripts/verify-harness.py:202`는 router→implementer 조건을 확인하지만, reviewer→implementer는 :212에서 edge 존재만 확인한다. 계획 미통과·부모 계약 없음도 허용하는 문구로 `when`을 바꿔도 `verify_routing()`은 오류 없음이다. 필수 선행조건과 이를 제거했을 때 실패하는 회귀 검사를 추가해야 한다. 문자열 포함만으로 의미를 증명하기보다 명시적 조건과 artifact 참조를 사용하는 것이 안정적이다.
- **방어 강화 — 권한 동시 변경 감지.** reviewer의 spec·역할 frontmatter·native wrapper를 모두 workspace-write로 맞추면 관련 일치성 검사를 통과한다. 현재 실제 값은 read-only다. `verify_roles()`에 router/reviewer의 기대 access도 고정하면 경계 약화를 탐지할 수 있다.
- **출력 정확성.** `scripts/verify-harness.py:361`의 성공 메시지는 fallback evidence를 확인한 듯 읽힐 수 있다. 구조 검사가 확인한 기본 설정·허용 정책과 실제 호출 증거를 구분해 출력해야 한다.

## 기타 정리할 사항

- `harness/state/state.json:16`은 하네스 설치 뒤 PR/보관본 작업을 다음 행동으로 남긴다. 현재 main에 설치 커밋이 있지만 이것만으로 외부 전달 완료를 증명할 수는 없다. 완료 근거를 확인해 최신 개발 단위로 정리하면 재시작 혼선을 줄일 수 있다.
- `harness/ledger/journal.jsonl:5`의 임시 결과 경로는 Git에 추적되지 않는다. 영구 `execution-evidence.json`에 최종 근거가 있으므로 기존 참조를 영구 근거로 안내하는 편이 이식성에 좋다.
- 보고서는 한국어 정책이지만 `harness/reports/create-ui-ux-agent/CHANGE-REPORT.md`는 영어다. Factory의 `validate_english_artifact_markdown()`이 `harness/reports/`도 영어 canonical 대상으로 검사하는 정책 경계 충돌이 있다. 독자 문서를 `docs/reports/`로 일관되게 분리하거나 검증기의 보고서 경로 분류를 정리해야 한다.
- Factory 원본과 프로젝트 검증기의 모델 상속 호환 차이는 계속 명시하고 회귀 검사로 제한 범위를 유지한다.

## 실행하지 않은 검사와 해석의 한계

실제 배포·rollback·운영 서버 접근, 전체 프런트/백엔드 빌드와 통합 테스트, 현재 원격 CI/운영 상태 조회, 실제 기능 한 단위의 기획부터 배포까지 리허설은 수행하지 않았다. 배포 기능 설명은 현재 로컬 코드의 정적 확인이다. 모든 native 역할의 실제 실행과 모델 선택을 시험하지도 않았다.

이번 검증은 하네스 구조와 목적 적합성을 확인한 것이다. 속도·비용·개발 성공률 향상은 측정하지 않았으며, 자동 개선·메모리 계층을 추가할 필요가 입증된 것도 아니다. 개선 후에는 기획 전용, 비 UI/API 개발, UI 포함 기능 개발, 배포 성공/실패·복구, 중단 후 재개 시나리오를 각각 통과 기준과 실제 근거로 검증하는 것이 적절하다.
