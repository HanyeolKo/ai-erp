# AI ERP UI/UX 에이전트 운영 가이드

작성일: 2026-09-07. 이 파일이 원본이며 Notion `AI 생성문서 관리`에는 열람용 사본을 보관한다.

## 화면 기획 흐름

화면 기획은 `ai-erp-ui-ux-designer`가 담당한다. 새 페이지뿐 아니라 사용자 흐름, 메뉴, 폼, 테이블, 필터, 상호작용, 반응형과 기존 화면의 개선 기획도 포함한다.
메인 에이전트는 기획 내용을 직접 확정하기 전에 전담 에이전트에 위임하고 결과를 받은 다음 검토한다. 전담 에이전트 호출이 불가능하면 그 사실을 알리고, 호출했다고 기록하지 않는다.

1. `AGENTS.md`에서 `harness/HARNESS.md`와 프로젝트 진입 스킬 `ai-erp`로 연결한다.
2. `router`가 요청과 현재 화면, 업무 제약을 정리하고 `ui-ux-designer`에 전달한다.
3. 디자이너는 사용자 목표, 핵심 흐름, 정보 구조, 화면 상태, 반응형, 접근성, 검증 기준을 작성한다.
4. `reviewer`가 전담 에이전트의 실제 결과와 기획 산출물을 검토한다. 실패한 항목은 재시도 절차로 돌린다.
5. 구현 담당자는 검토된 기획을 바탕으로 작업한다. 실제 구현·브라우저 검증을 하지 않은 항목은 완료로 표시하지 않는다.

`ui-ux-designer`가 전문 역할이며 `router`와 `reviewer`는 배정·검증을 위한 기본 역할이다. 불필요한 화면 재설계를 자동으로 수행하지 않는다.

## 설치한 스킬

[참고 문서](https://nanoskill.ai/ko/blog/best-agent-skills-for-ux-design)의 후보 중 AI ERP에 직접 필요한 세 가지를 설치했다.

- `ai-erp-frontend-design`: 시각적 위계, 타이포그래피, 색상, 레이아웃의 디자인 방향을 정한다. [Anthropic 원본](https://github.com/anthropics/skills/tree/41bbe19d1a1a7eaab5e7bb9050a417e5c6cffc8f/skills/frontend-design).
- `ai-erp-ui-ux-pro-max`: 디자인 시스템과 상호작용·접근성·React 관련 지침을 로컬 데이터에서 검색한다. [UI UX Pro Max 원본](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill/tree/4aad0584d92131626b16d4ff4d77f0455385013c).
- `ai-erp-web-design-guidelines`: 구현 결과의 접근성·폼·탐색·응답 상태 등을 검토한다. [Vercel 원본](https://github.com/vercel-labs/agent-skills/tree/063bee94c3f4df8453406c830b0a7df0f2860278/skills/web-design-guidelines).

원본 80개 파일은 `vendor/ux-skills/`에 보존한다. `skills.lock.json`이 저장소·커밋·원본 경로·SHA-256·파일 크기를 고정한다. 라이선스와 별도 Vercel 지침 참조본의 출처는 `THIRD-PARTY.md`에 있다.
프로젝트용 짧은 진입 스킬은 `harness/skills/`가 원본이고 `.agents/skills/`는 내용이 같은 Codex 검색용 사본이다. 긴 외부 자료는 필요한 때만 읽는다.

## 검색 사용과 품질 판단

Python 3.11 이상이면 검증 및 검색에 외부 패키지나 npm CLI 설치가 필요 없다. 저장소 루트에서 실행한다.

```sh
python -B vendor/ux-skills/ui-ux-pro-max/scripts/search.py "B2B SaaS dashboard" --domain product --json
python -B vendor/ux-skills/ui-ux-pro-max/scripts/search.py "error summary validation" --domain ux --json
python -B vendor/ux-skills/ui-ux-pro-max/scripts/search.py "form validation" --stack react
```

검색어는 실제 업무 목표를 반영해야 한다. 설치 확인 중 `enterprise resource planning`은 결혼·이벤트 기획을 최상위로 반환했고, `B2B SaaS dashboard`로 좁히면 SaaS 결과를 반환했다.
디자인 시스템 생성기도 마케팅용 Hero·CTA 구조를 제안할 수 있다. 전담 에이전트는 ERP의 작업 밀도, 표·필터·폼·권한·오류 복구에 맞는지 판단하고 부적절한 결과를 제외한다. 검색 결과를 기획으로 그대로 확정하거나 저장하지 않는다.
원본 Pro Max의 Claude 전용 경로 대신 프로젝트 진입 스킬에 명시된 `vendor/ux-skills/` 경로를 사용한다. `--persist`는 검토한 결과를 명시한 출력 디렉터리에 저장할 때만 사용한다.

## 하네스와 Git 관리

기본 구성은 Harness Factory 0.3.0의 `core`이며 Codex에서 사용한다. 배정·실행·검토·검증과 보고를 포함한다. 자동 하네스 개선, 장기 메모리, 학습 퀴즈는 설치하지 않는다.
역할과 절차는 `harness/harness-spec.json` 및 `harness/team/agents/`에 정의하고, `.codex/agents/`는 해당 원본을 읽는 얇은 설정 파일로 둔다.
[Codex 공식 형식](https://learn.chatgpt.com/docs/agent-configuration/subagents)에 따라 모델과 추론 수준을 지정하지 않아 호출한 작업의 설정을 상속한다.

다음 파일을 변경할 때 같은 PR에 함께 포함한다.

- `AGENTS.md`, `harness/`, `.codex/config.toml`, `.codex/agents/`, `.agents/skills/`.
- `vendor/ux-skills/`와 잠금 파일, 검증 스크립트와 CI 설정.
- 변경 결정 및 검증 근거와 운영 가이드.

개인 인증 정보와 세션·캐시는 `.gitignore`로 제외한다. 외부 원본에는 줄바꿈 변환을 적용하지 않아 Windows와 CI에서 해시가 같다.
업데이트는 원본 커밋·라이선스·스크립트를 검토한 다음 해당 스냅샷과 잠금 파일을 함께 교체한다. 프로젝트 스킬을 수정할 때는 원본과 Codex 사본을 함께 갱신한다.

## 검증

```sh
python -B scripts/verify-harness.py
python -B scripts/test_verify_harness.py
python -B scripts/smoke-ux-skills.py
# Git에 추가한 뒤 또는 CI에서:
python -B scripts/verify-harness.py --require-tracked
```

Git에 포함한 Factory 검증기와 별도 프로젝트 검사가 스키마, 권한, 참조, 배정 순서, 문서 길이, Codex 사본, 원본 해시를 확인한다. 회귀 검사는 스킬 사본 변경, 권한 설정 누락, 디자이너 우회, 원본 변조·누락을 실패로 검출한다.
Factory 원본 검증기는 모델 필드를 필수로 보던 구버전이다. 프로젝트 검사에서는 현재 Codex가 허용하는 모델·추론 수준 생략만 허용하고 나머지 검사는 그대로 실행한다. 원본 검증기는 수정하지 않는다.
CI의 `harness` 작업은 이 검사들과 Git 추적 여부를 실행한다. 설정과 증거의 일관성을 검증하는 검사이며, 실제 에이전트 호출이나 사람의 시각적 만족도를 증명하는 검사는 아니다.

## 문서 보관

지속해서 읽을 화면 기획·결정·가이드는 로컬 파일을 먼저 갱신하고 Notion `AI 생성문서 관리`에 사본을 동기화한다. 사본에는 원문 경로와 동기화 날짜를 남긴다. 짧은 답변, 임시 노트와 원시 실행 로그는 보관 대상에서 제외한다.
