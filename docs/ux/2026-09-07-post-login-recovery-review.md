# 로그인 이후 프로젝트 복구 화면 계획 독립 검토

- 검토일: 2026-09-07
- 대상: `docs/ux/2026-09-07-post-login-recovery-plan.md`
- 기준 코드: `codex/post-login-project-recovery`, `24659a645e24ac6d3ee5664a1ed7e4ab372ab47f`
- 실행자: `/root/ui_ux_designer` (`ui-ux-designer` 역할)
- 근거 수집·조정: `/root` (`router` 역할)
- 판정자: `/root/ui_plan_reviewer` (`reviewer` 역할)
- 최신 판정: **PASS — 2026-09-07 생성 흐름 확장본(AC1–AC8)을 별도로 재검토했다. 구현 진행 가능하며 구현 완료 판정은 아니다.**
- 범위 변경: 아래 최초 검토 근거는 이력으로 보존한다. 당시 초대 중심 범위의 PASS를 생성 기능 승인으로 확장하지 않았다. 현재 범위와 판정 근거는 이 문서의 `생성 흐름 확장 재검토` 절이 우선한다.

## 독립성과 실제 역할 위임 근거

계획 작성자와 검토자는 별도 하위 에이전트다. 검토 중 `collaboration.list_agents`에서 `/root/ui_ux_designer`의 완료 상태와 해당 계획 파일을 반환한 결과를 직접 확인했다. 작성자는 초대 기반 복구 경로, 생성 API 부재, 구현·브라우저 검사 미실행을 명시했다. 계획 파일의 선언만으로 역할 위임을 인정하지 않았다. 검토자는 계획이나 구현 코드를 수정하지 않고 이 검토 기록만 작성했다.

검토 기준은 별도 하네스 작업 위치 `D:/onedrive/Documents/ChatGPT/AI ERP`에 있는 다음 파일을 읽기 전용으로 적용했다.

- `harness/team/agents/reviewer.md`
- `harness/harness-spec.json`의 `evaluators[id=ui-plan-review]`: 모든 적용 기준에 근거가 있고 실제 전문가 위임이 기록된 경우에만 `pass`.
- `harness/evaluation/UI-PLAN-RUBRIC.md`
- `harness/templates/UI-REVIEW.md`
- `harness/skills/ai-erp-ui-ux/SKILL.md`, `harness/team/agents/ui-ux-designer.md`
- `harness/skills/ai-erp-web-design-guidelines/SKILL.md` 및 `vendor/ux-skills/web-design-guidelines/references/web-interface-guidelines/command.md`의 고정된 접근성 지침

## 기준별 판정

| 기준 | 판정 | 근거 |
| --- | --- | --- |
| Specialist routing | pass | 실제 에이전트 목록에서 전담 작성자와 결과 파일을 확인했다. 계획의 `Request and specialist handoff` 6행과 일치한다. 독립 검토자에게 넘긴 뒤 구현하도록 지정했다. |
| User and task | pass | 계획 `Request and specialist handoff`가 프로젝트 미참여 계정, 초대 사용자, 오래된 링크, 세션·권한 변경을 구분한다. 진입점과 그룹·프로젝트 생성 등 제외 범위가 명시되어 있다. |
| Workflow | pass | `Workflow and screen structure`에 초대 입력 → 기존 초대 조회 → 명시적 수락 → 응답의 프로젝트로 이동, 새로고침, 계정 전환, 실패 시 재시도가 정의되어 있다. 기존 서버 계약과 일치한다. 생성은 현재 화면에서 제공하지 않음을 설명하고 관리자 요청을 안내하므로 허위 생성 버튼이 없다. |
| Screen contract | pass | 같은 절에 제목·설명·주요 동작·보조 동작, 명시적인 프로젝트 선택 링크, 입력 검증, 성공 화면, 첫 페이지/이후 페이지 구분이 있다. 1280px·390px에서 줄바꿈·입력 너비·터치 크기를 규정했다. 이 복구 화면에 신규 정렬·필터·대량 작업은 없다는 제외가 타당하다. |
| State coverage | pass | `State and accessibility coverage`는 인증/프로젝트 로딩, 빈 결과, 초기 오류, 배경 갱신, 누락 프로젝트, 401/403/404, 성공·비활성화를 구분한다. API 실패를 미참여로 표시하지 않으며, 서버의 접근 거부가 캐시된 성공·권한보다 우선한다. |
| Accessibility | pass | 의미에 맞는 링크·버튼, 레이블, 오류 연결, Enter 제출, Tab 순서, 경로 이동/성공 시 초점, 상태·오류 알림, 색 이외의 오류 표현, 기존 포커스 표시 유지가 명시되어 있다. 실제 보조기술·브라우저 통과를 주장하지 않는다. |
| Design rationale | pass | 기존 해시 라우터, React Query, 간결한 헤더·섹션·색상을 유지한다. 고정된 vendor 지침의 의미 구조·레이블·초점·비동기 피드백을 복구 동작과 연결한다. 별도 시각 체계 변경을 요구하지 않는다. |
| Validation | pass | `Decisions and acceptance criteria`의 AC1–AC6은 빈 목록, 입력 검증, 수락 이후 이동, 누락/복구된 접근권한, 오류 구분, 중복 요청 방지, 세션 종료 후 데이터 제거, 페이지네이션과 기존 역할을 관찰 가능한 결과로 정의한다. 자동 검사와 브라우저 크기·키보드·네트워크 검사 및 미실행 항목을 분리했다. |
| Ownership | pass | `Archive`는 이 작업 저장소의 로컬 원본 경로와 Notion `AI 생성문서 관리` 사본 동기화 대기를 명시한다. 원본 우선 갱신 원칙과 일치한다. 아카이브 완료를 주장하지 않는다. |

## 실제 코드와 대조한 주요 판단

1. **생성을 임의 구현하지 않는 범위는 타당하다.** `backend/src/main/java/com/aierp/project/api/ProjectController.java:22` 이하에는 참여 목록 조회, 구성원 조회, 역할 변경만 있다. `GroupMemberEntity.java`에는 그룹 역할 필드가 없고 `CurrentUserController.java`는 계정 ID·authorities만 반환한다. 따라서 빈 목록이나 `ROLE_USER`를 프로젝트 생성 권한으로 해석할 수 없다. 계획은 생성 기능의 부재를 안내하면서 실제 참여 기능을 제공한다.
2. **참여와 계정 전환은 구현 가능한 경로다.** `InvitationController.java`의 조회·수락·거절 API와 `InvitationService.java:47` 이후 이메일 검증·멤버십 생성·응답의 `projectId`가 계획을 뒷받침한다. 초대 생성은 그룹 멤버이면서 프로젝트 MANAGER인 경우로 제한된다. `SecurityConfiguration.java:24`의 로그아웃은 204를 반환하며, `frontend/src/api/client.ts`의 공통 변경 요청은 기존 CSRF 방식과 204 처리를 지원한다. 프런트엔드에서 로그아웃 호출을 연결하는 것은 새 서버 권한 모델을 만드는 일이 아니다.
3. **현재 빈 화면과 불명확한 오류의 근거가 확인된다.** `frontend/src/screens/Account.tsx:37`의 빈 목록은 한 문장과 비활성 페이지 버튼만 보여 준다. `frontend/src/ui.tsx:39`의 프로젝트 누락 표시는 프로젝트 선택 링크만 있으며 재조회 동작이 없다. 공통 `Notice`는 오류 코드를 그대로 보여 준다. 계획은 이 세 경로의 설명과 복구 동작을 구체화한다.
4. **삭제와 권한 회수를 단정하지 않는 표현이 맞다.** `frontend/src/state.ts:14`의 프로젝트 선택 조회는 참여 목록을 페이지별로 찾는다. 성공한 목록에서 찾지 못했다는 사실만으로 삭제와 접근권한 상실을 구별할 수 없다. `ApiExceptionHandler.java` 역시 접근 거부와 누락을 각각 일반 403·404로 정규화한다. 선택 프로젝트에는 두 가능성을 함께 안내하고, 일정의 404는 해당 일정 수준에서 처리한다는 계획이 적절하다.
5. **기존 권한·전역 탐색과 양립한다.** `state.ts`의 VIEWER/MEMBER/MANAGER별 능력, `Schedules.tsx`·`Detail.tsx`·`ScheduleForm.tsx`의 프로젝트 의존 조회와 `Account.tsx`의 구성원·초대 화면을 검토했다. 알림과 Calendar 연결 API는 현재 사용자 기준이므로 프로젝트 없이도 전역 탐색을 유지할 수 있다. AC3·AC5는 오래된 프로젝트 경로와 캐시된 권한이 업무 동작을 열어 두는 문제를 명시적으로 검증하도록 한다.

## 결함·검사·다음 단계

- 미해결 필수 계획 결함: **0**. 안정 결함 키: 해당 없음.
- 실행한 확인: `git rev-parse HEAD`, `git branch --show-current`, `git status --short`; 위 역할·평가기·계획·프런트엔드·서버 소스의 `Get-Content`; `rg -n`으로 계획 절/AC, 실제 API·로그아웃·권한·기존 회귀 검사 위치 확인; `collaboration.list_agents`로 실제 위임 확인.
- 근거 출력은 이 검토 태스크의 도구 기록에 있으며, 판단에 필요한 경로·결과를 위에 기록했다. 원시 명령 출력은 별도 지속 문서로 보관하지 않는다.
- 경로 탐색에서 `harness/evaluators` 디렉터리는 없음을 확인했다. 실제 평가기 정의는 `harness/harness-spec.json`에 있어 이를 사용했다. vendor 원본은 `harness/vendor`가 아니라 저장소 최상위의 `vendor/ux-skills`에서 확인했다.
- 실행하지 않은 검사: 실제 앱 증상 재현, 수정 후 브라우저 검증, 프런트엔드 테스트·타입 검사·빌드, 백엔드 테스트, 실제 Google OAuth·운영 검증, 실제 스크린리더 검사. 이 판정은 계획의 근거와 검증 가능성을 심사한 것이다.
- `python scripts/verify-harness.py`: 이 작업은 하네스 구조를 변경하지 않아 해당 없음. 별도 하네스 PR의 구조 검사 결과를 대신 승인하지 않는다.
- 다음 단계: 구현자가 계획에 따라 수정하고 AC1–AC6을 회귀·브라우저 검사로 검증한다. 특히 프로젝트 조회가 성공하기 전 구성원 등 하위 요청의 실행 방지, 401 시 진행 중 요청과 캐시 처리, 캐시된 권한 뒤 403 발생 시 화면 상태를 확인해야 한다. 이는 승인 조건을 추가한 것이 아니라 이미 승인된 AC3–AC5의 구현 확인 지점이다.

## 생성 흐름 확장 재검토

- 재검토일: 2026-09-07
- 대상 버전: 계획의 `Scope`에 GroupRole 기반 생성이 포함되고 AC7·AC8이 추가된 버전.
- 실제 위임 재확인: `collaboration.list_agents`에서 `/root/ui_ux_designer`가 생성 흐름·nullable GroupRole·AC7/AC8을 추가해 반환한 새 완료 결과를 확인했다. 수정 작성자와 이 재검토자는 별도다.
- 독립 원문 확인: Google Drive `get_presentation_text`로 [화면기획 v0.7](https://docs.google.com/presentation/d/1j74T6G7-tuEISKSQtScUSKSWRvlogMeQwzanlb5Dyyc/edit)을 직접 읽었다. 반환 제목은 `프로젝트_일정관리_시스템_화면기획서_v0.7`, revision은 `3oyVJH0Bdb8cEg`, 총 18장이다. 4장의 프로젝트 생성 행은 소유자·관리자에게 허용, 구성원에게 불가로 명시하며 GroupRole과 ProjectRole의 독립 판정을 요구한다.
- 추가 대조: 읽기 전용 원본 작업 위치의 `planning/system-architecture/project_schedule_system_architecture_v0.1.pptx.inspect.ndjson` 7·20장과 `planning/technical-design/project_schedule_technical_design_v0.2.pptx.inspect.ndjson` 18장의 GroupRole/ProjectRole 분리를 확인했다. 현재 `V2__create_phase1_tables.sql`, `GroupMemberEntity.java`, `GroupAccess.java`, `IdentityProvisioning.java`에서는 생성 권한 데이터와 최초 그룹/소유자 자동 지정 계약을 확인할 수 없다.

### 변경된 기준의 재판정

| 기준 | 판정 | 확장본의 근거와 판단 |
| --- | --- | --- |
| Specialist routing | pass | 생성 확장도 실제 전담 디자이너가 수행한 새 위임 결과를 확인했다. 최초 위임만으로 갈음하지 않았다. |
| User and task | pass | `Scope`가 프로젝트가 없는 OWNER/ADMIN의 직접 생성까지 포함하도록 수정됐다. 그룹 없는 사용자·기존 역할 미설정·일반 그룹 구성원을 구분한다. 최초 그룹/소유자 프로비저닝과 일반 역할 관리 화면의 제외를 명시한다. |
| Workflow | pass | `Minimal backend contract`와 `Creation lifecycle`이 본인 그룹 조회 → 그룹 선택 → 서버 권한 재검증 → 프로젝트와 새 MANAGER 멤버십 원자적 생성 → 반환 ID의 대시보드 진입을 연결한다. 권한 없는 그룹 ID를 직접 보내는 경우도 서버가 거절한다. GroupRole만으로 기존 프로젝트의 편집 권한을 부여하지 않는다. |
| Screen contract | pass | 생성 질문·그룹 선택·이름 입력·생성 역할 안내·제출과 권한 재조회가 구체적이다. 생성 옵션도 페이지네이션을 보장하므로 첫 페이지에 허용 그룹이 없다고 전체 생성 불가로 오인하지 않는다. 불가 사유와 계정/그룹을 포함한 선택 가능한 요청문, 복사 실패의 수동 경로가 정의돼 있다. |
| State coverage | pass | 생성 권한 조회 로딩/오류, OWNER/ADMIN 허용, MEMBER 거부, null 미설정, 그룹 없음, 생성 중/성공/400/403/불확실한 실패가 구분된다. 권한 조회 실패를 거부로 처리하지 않고 초대 참여는 독립적으로 유지한다. 생성 결과가 불확실할 때 저장 실패를 단정하거나 자동 재제출하지 않는다. |
| Accessibility | pass | 생성 입력·선택의 레이블·오류 연결·첫 잘못된 입력의 초점, 해당 폼만 Enter 제출, 복사 결과의 상태 알림과 수동 선택 fallback이 추가됐다. 기존 키보드·모바일·초점 기준도 유지된다. |
| Design rationale | pass | 생성 계약의 미구현은 보완해야 할 구현 공백으로 바로잡았다. 생성 허용 대상은 제품 원문에서 도출한다. legacy null 유지와 명시적인 새 프로젝트 MANAGER 등록은 기존 권한의 임의 승격과 구별되는 설계 결정으로 공개돼 있다. |
| Validation | pass | AC7은 OWNER/ADMIN 생성·새 프로젝트의 별도 멤버십, MEMBER/null/타인 그룹 거부와 부분 레코드 방지, 옵션 후속 페이지, 마이그레이션 보존을 명시한다. AC8은 권한 조회/입력/중복 제출/역할 변경/복사 결과/캐시 및 탐색 갱신을 검증한다. 서버 인가·트랜잭션·마이그레이션·REST Docs/OpenAPI와 프런트엔드·브라우저 검사를 계획했다. |
| Ownership | pass | 로컬 계획을 먼저 변경했고 확장본의 Notion 재동기화 대기를 명시한다. 기존 아카이브가 새 판정까지 반영됐다고 주장하지 않는다. |

### 경계 결정과 최종 판정

**PASS. 필수 계획 결함 0개.** 생성 확장에 대해 별도의 판정을 완료했으므로 계획의 AC1–AC8에 따라 구현을 진행할 수 있다. 최초 검토의 “생성 API가 없으므로 생성 제외가 타당하다”는 판단은 확장된 사용자 요구와 직접 확인한 제품 권한 근거에 적용되지 않으며, 현재 결론으로 사용하면 안 된다.

- nullable GroupRole은 기존 모든 행을 임의로 MEMBER나 OWNER로 분류하지 않는다. 값이 없는 경우 서버가 생성을 허용하지 않고 화면에서 설정 미완료를 설명하므로 기존 권한을 추측하지 않는다. 마이그레이션 결과와 조회/POST의 실제 동작은 구현 검사로 확인해야 한다.
- 생성 옵션은 본인 그룹의 이름·ID·허용 여부·사유만 제공한다. OWNER/ADMIN도 생성 POST에서 최신 권한을 다시 판정하고, 프로젝트와 생성자 MANAGER 멤버십을 같은 트랜잭션으로 저장한다는 범위가 분명하다.
- 새 프로젝트 생성자의 MANAGER 등록은 이 생성 행위에 따른 별도 ProjectRole 부여 결정이다. 화면에 이를 설명하며, 같은 그룹의 기존 프로젝트에 권한을 확산하지 않는다. 제품 원문이 생성자 기본 역할 자체까지 명시한다고 해석하지 않았다.
- 최초 그룹/소유자 자동 지정 규칙은 원문 18장 검색과 현재 프로비저닝 코드, 참조한 로컬 권한 설계에서 확인되지 않았다. 따라서 최초 로그인 자동 승격을 새로 도입하지 않고 설정 요청문 복사와 초대 참여를 제공하는 제외 범위가 타당하다. 운영 프로비저닝 절차가 이미 존재하거나 이번 수정으로 완성됐다는 주장은 승인하지 않는다.
- 생성할 수 없는 계정에 연락처나 동작하지 않는 설정 링크를 만들어 주지 않는다. 대신 사용자 계정·선택 그룹을 담은 실제 복사 기능과 수동 복사 경로를 제공한다. 외부 메시지를 보내는 행위는 포함되지 않는다.
- 이번 재검토에서는 생성 구현·서버 테스트·마이그레이션·브라우저 검사를 실행하지 않았다. 앞서 별도로 실행한 계정 회귀 검사나 조정자의 운영 빈 상태 재현을 생성 기능의 검증으로 간주하지 않는다. 원문 권한과 계획 적합성을 승인한 것이다.

## 보관

- 로컬 원본: `C:/Users/USER/.codex/worktrees/60aa/AI ERP/docs/ux/2026-09-07-post-login-recovery-review.md`
- Notion 사본: [AI 생성문서 관리 보관 사본](https://app.notion.com/p/3d4a8ad6aa4a81fa8664ef290f3a0f0d). 로컬 원본을 먼저 반영하고 사본을 동기화한다.
- 마지막 동기화: 2026-09-07 (Asia/Seoul), 생성 확장 재검토 포함. 사본은 편집 원본이 아니다.
