# AI ERP UI/UX 및 실행 에이전트 운영 가이드

작성일: 2026-09-07. 이 파일이 원본이며 Notion `AI 생성문서 관리`에는 열람용 사본을 보관한다.

## 역할과 책임

상위 에이전트는 요구사항, 범위, 아키텍처, 권한, UX와 검증 기준을 깊게 분석해 계약을 확정하고 최종 승인을 담당한다. 경량 구현자는 그 계약을 실행하고 근거를 돌려주지만 설계 범위나 최종 판정을 바꾸지 않는다.

| 역할 | Native wrapper | 모델 정책 | 책임과 권한 |
| --- | --- | --- | --- |
| `router` | `ai-erp-router` | 상위 설정 상속, `deep` | 요청 분류, 범위를 정한 작업 지시(`bounded assignment`) 작성, 근거 수집. read-only |
| `ui-ux-designer` | `ai-erp-ui-ux-designer` | 상위 설정 상속, `deep` | 화면·흐름·상태·접근성 계획. 구현 코드와 테스트는 수정하지 않음 |
| `reviewer` | `ai-erp-reviewer` | 상위 설정 상속, `deep` | 계획·계약·실행 근거의 독립 검토와 `pass/fail` 판정. read-only |
| `implementer` | `ai-erp-implementer` | `fast`; 기본 `gpt-5.3-codex-spark`/`high` | 상위 에이전트 계약에 따른 코드·테스트·동작 설정 실행과 검증. workspace-write |

상위 세 역할은 호출한 Native Codex 모델과 추론 수준을 상속한다. 구현자는 기본적으로 `gpt-5.3-codex-spark`와 `model_reasoning_effort=high`를 사용한다. Spark를 호출할 수 없거나 할당량이 제한될 때만 상위 에이전트가 `gpt-5.6-luna`/`high`를 명시적으로 선택할 수 있으며, 실제 모델·추론 수준·호출 근거를 `IMPLEMENTATION-RESULT.md`에 남긴다. 앞으로 다른 경량 모델을 고려할 때도 상위 에이전트가 적합성과 실행 가능성을 확인하고, 채택하면 정책과 검증을 함께 갱신한다. 따라서 경량 모델 선호는 Spark와 Luna 두 제품에 영구히 한정되지 않는다. [Codex 공식 subagent 형식](https://learn.chatgpt.com/docs/agent-configuration/subagents)에 맞춰 Native wrapper를 관리한다. 구현자는 라우팅·평가자 소유·최종 승인·push·merge·publish·재위임을 수행하지 않는다.

## 필수 흐름

모든 화면 요청에는 화면, 사용자 흐름, 메뉴, 폼, 테이블, 필터, 상호작용, 반응형, 접근성 또는 기존 화면 개선이 포함된다. 단계별 호출은 상위 에이전트가 순서대로 수행하고 각 결과를 회수해 다음 단계에 전달한다.
전담 호출이 불가능하면 그 사실과 미실행 검증을 알리며, 호출했다고 기록하거나 결과를 꾸며내지 않는다.

| 요청 유형 | 산출물 순서 | 완료 조건 |
| --- | --- | --- |
| 계획만 요청 | `router -> ui-ux-designer -> reviewer` | `ui-plan-review` 통과. 구현자 호출과 코드 변경 없음 |
| UI 구현 | `router -> ui-ux-designer -> reviewer -> implementer` | 계획 리뷰 통과, 상위 에이전트 계약 확정, 구현 결과의 독립 검토와 상위 최종 승인 |
| 비 UI 구현 | `router -> implementer` | 상위 에이전트 계약 확정, 실행 결과와 검사 근거를 상위 에이전트가 독립 검토 |

디자이너에서 구현자로 바로 넘기는 우회와 구현자에서 라우터·리뷰어로 되돌리는 DAG 연결은 사용하지 않는다. 화면 구현은 계획과 `ui-plan-review`가 먼저 있어야 한다. 백엔드, 스크립트, 루트 빌드·설정, 테스트와 동작을 바꾸는 설정도 같은 상위 에이전트 계약 및 경량 구현자 경로에 포함한다.

## 상위 에이전트 계약과 실행 결과

구현 전에 상위 에이전트가 `harness/templates/IMPLEMENTATION-CONTRACT.md`에 목표, 현재 동작과 출처, 선택한 설계 이유, 정확한 허용 파일·심볼, 보존할 변경과 제외 범위, 인터페이스·데이터·권한·상태 규칙, 번호가 있는 수용조건(`acceptance criteria`), 검증 명령과 예상 결과를 확정한다. UI 작업이면 실제 디자이너 계획과 리뷰 근거를 포함하고, 미해결 설계 결정이 없어야 한다.

구현자는 `harness/templates/IMPLEMENTATION-RESULT.md` 형식으로 일치하는 작업·revision, 실제 선택 모델과 호출 근거, 변경 경로, 수용조건별 근거, 명령·종료 코드·출력 경로, 실행하지 않은 검사, 편차와 불확실성을 기록한다. 결과 상태는 `ready-for-review` 또는 `blocked`이며, 구현자가 최종 승인으로 표시하지 않는다. 상위 에이전트는 결과를 독립적으로 검토하고 필요한 경우 범위가 정해진 수정 계약을 작성한다.

## 설치한 UX 스킬과 검색

[참고 문서](https://nanoskill.ai/ko/blog/best-agent-skills-for-ux-design)의 후보 중 AI ERP에 직접 필요한 세 가지를 설치했다.

- `ai-erp-frontend-design`: 시각적 위계, 타이포그래피, 색상, 레이아웃의 디자인 방향을 정한다. [Anthropic 원본](https://github.com/anthropics/skills/tree/41bbe19d1a1a7eaab5e7bb9050a417e5c6cffc8f/skills/frontend-design).
- `ai-erp-ui-ux-pro-max`: 디자인 시스템과 상호작용·접근성·React 관련 지침을 로컬 데이터에서 검색한다. [UI UX Pro Max 원본](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill/tree/4aad0584d92131626b16d4ff4d77f0455385013c).
- `ai-erp-web-design-guidelines`: 구현 결과의 접근성·폼·탐색·응답 상태 등을 검토한다. [Vercel 원본](https://github.com/vercel-labs/agent-skills/tree/063bee94c3f4df8453406c830b0a7df0f2860278/skills/web-design-guidelines).

원본 80개 파일은 `vendor/ux-skills/`에 보존한다. `skills.lock.json`이 저장소·커밋·원본 경로·SHA-256·파일 크기를 고정한다. 라이선스와 별도 Vercel 지침 참조본의 출처는 `THIRD-PARTY.md`에 있다. `harness/skills/`가 프로젝트용 원본이고 `.agents/skills/`는 같은 내용의 Codex 검색용 사본이다.

Python 3.11 이상이면 검증 및 검색에 외부 패키지나 npm CLI 설치가 필요 없다. 저장소 루트에서 실행한다.

```sh
python -B vendor/ux-skills/ui-ux-pro-max/scripts/search.py "B2B SaaS dashboard" --domain product --json
python -B vendor/ux-skills/ui-ux-pro-max/scripts/search.py "error summary validation" --domain ux --json
python -B vendor/ux-skills/ui-ux-pro-max/scripts/search.py "form validation" --stack react
```

검색어는 실제 업무 목표를 반영해야 한다. `enterprise resource planning`은 결혼·이벤트 기획을 최상위로 반환할 수 있고 `B2B SaaS dashboard`처럼 좁히면 SaaS 결과를 반환한다. 디자인 시스템 생성기가 마케팅용 Hero·CTA를 제안하더라도 ERP의 작업 밀도, 표·필터·폼·권한·오류 복구에 맞는지 판단해 부적절한 결과를 제외한다. 검색 결과를 기획으로 그대로 확정하거나 저장하지 않는다. 원본 Pro Max의 Claude 전용 경로 대신 `vendor/ux-skills/` 경로를 사용하며 `--persist`는 검토한 결과를 명시한 출력 디렉터리에 저장할 때만 사용한다.

## 하네스, Git과 검증

기본 구성은 Harness Factory 0.3.0의 `core`이며 Codex에서 사용한다. 하네스 원본, 역할·스킬 설정, 고정된 UX 스냅샷, 잠금 파일, 검증 스크립트와 근거 문서는 Git에서 함께 관리한다. 개인 인증 정보와 세션·캐시는 `.gitignore`로 제외한다. 외부 원본에는 줄바꿈 변환을 적용하지 않아 Windows와 CI에서 해시가 같다.

```sh
python -B scripts/verify-harness.py
python -B scripts/test_verify_harness.py
python -B scripts/smoke-ux-skills.py
# Git 추적 상태까지 확인할 때:
python -B scripts/verify-harness.py --require-tracked
```

검증기는 스키마, 권한, 참조, 역할 순서, 문서 길이, Codex 사본과 원본 해시를 확인한다. 원본 Factory 검증기는 Codex 모델 필드를 필수로 보는 구버전이므로, 프로젝트 검증기에서는 상위 역할의 상속 정책에 필요한 두 키만 선택 사항으로 취급하고 나머지 검사는 유지한다. 설정과 증거의 일관성을 확인하는 검사이며 실제 에이전트 호출, 애플리케이션 동작 또는 사람의 시각적 만족도를 증명하지 않는다. 구현 결과와 현재 변경의 최종 검증 상태는 실제 실행 근거에 따라 기록한다.

CLI에서 구현자를 직접 실행해야 할 때는 확정된 실제 계약 파일의 내용을 stdin으로 전달한다. 아래 경로는 형식 예시이므로 실행할 때 상위 에이전트가 확정한 실제 계약 파일 경로로 바꾼다.

```powershell
Get-Content -Raw .\path\to\approved-implementation-contract.md | codex exec -m gpt-5.3-codex-spark -c model_reasoning_effort=high --sandbox workspace-write -
```

Spark가 불가능해 Luna를 선택한 경우에도 같은 계약과 근거 규칙을 적용한다. `--dangerously-bypass` 계열 플래그는 사용하지 않는다.

## 문서 보관

지속해서 읽을 화면 기획·결정·가이드는 로컬 파일을 먼저 갱신하고 Notion `AI 생성문서 관리`에 사본을 동기화한다. 사본에는 원문 경로와 동기화 날짜를 남긴다. 짧은 답변, 임시 노트와 원시 실행 로그는 보관 대상에서 제외한다.
