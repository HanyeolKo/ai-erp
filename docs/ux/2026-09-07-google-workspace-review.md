# Google Workspace 연동 독립 계획 검토

- 검토일: 2026-09-07 (Asia/Seoul)
- 최신 대상: `2026-09-07-google-workspace-plan.md` revision 4, `2026-09-07-google-workspace-contract.md` revision 1의 최종 Clarifications 포함 본문, 기준 코드 `58bc6d6`.
- 실제 전문 기획자: `/root/google_integrations_designer`. 상위 `/root`의 계획 전용 위임과 revision 3·4 완료 메시지 및 로컬 산출물을 확인했다.
- 증거 실행·최종 수용 소유자: `/root`. 독립 판정 소유자: `/root/ui_plan_reviewer`.
- **최신 ui-plan-review: PASS. GW-R01~06 모두 해소, 필수 계획·계약 불일치 0개.** 상위는 이 독립 판정을 수용한 뒤 계약을 READY로 전환할 수 있다. 구현·브라우저·실제 Google 연결의 통과 판정은 아니다.

## 최초 검토 이력 — revision 3 / FAIL

아래 최초 판정과 결함은 수정 이유를 보존한 이력이다. 현재 상태는 문서 마지막의 revision 4 재검토를 따른다.

| 필수 기준 | 판정 | 근거 |
| --- | --- | --- |
| Specialist routing | PASS | 실제 별도 설계자 위임·완료 메시지, 로컬 전문 기획 revision 3. 앱 구현 승인을 기획자가 소유하지 않는다. |
| User and task | PASS | 개인 Google 기능과 프로젝트 공유 참조·Calendar의 소유권, 실제 쓰기 시험 제외, 프로젝트 첫 depth 유지가 명시됐다. |
| Workflow | FAIL | 연결 복귀 문맥과 Calendar 해제·재연결 완료 기준이 API 계약으로 완결되지 않았다. GW-R03, GW-R04. |
| Screen contract | FAIL | 목록 수·시각·첨부자·Calendar 정보와 실제 DTO/소유 경로가 일치하지 않는다. GW-R01, GW-R06. |
| State coverage | FAIL | 발송 FAILED 및 일부 권한의 기능별 가능 상태가 서버 계약과 일치하지 않는다. GW-R02, GW-R03. |
| Accessibility | PASS | 키보드 순서, Dialog 이름·포커스·Escape·복원, 작성 버리기 확인, 대비·44px·320/375/1440px·200% 검증 계획이 구체적이다. 구현 통과 판정은 아니다. |
| Design rationale | PASS | 기존 프로젝트 중심 탐색·토큰·한국어 서체를 유지하는 이유와 pinned 지침 사용 근거가 명시됐다. |
| Validation | FAIL | GW-01~08은 관찰 가능하나 Calendar 범위·제공자 보장 및 GW-02의 식별자 금지 범위를 정정해야 검사 oracle이 확정된다. GW-R04~06. |
| Ownership | PASS | 로컬 원본·인용 소스와 상위의 Notion 보관 책임이 명시됐다. |

## 필수 보완

### GW-R01 — 화면 데이터와 API 응답 정합성

기획 45~46·54행은 개인 Drive·메일·프로젝트 파일을 각 25개로 표시하지만 계약 36~37행은 20/50개 한도이며 요청에 크기 인자가 없다. 기획의 Drive 수정 시각·최근 수정순에 필요한 `modifiedTime`과 정렬도 계약의 provider fields/DTO에 없다. 첨부자 `attachedBy`가 공개 이름을 포함하는지 명확하지 않다. Calendar 선택의 시간대·쓰기 권한, 연결 담당자 이름·초기 반영 진행·마지막 성공 시각도 계약 43행 응답과 기존 Projection DTO만으로 제공할 수 없다.

상위가 목록 크기·정렬과 최소 공개 DTO 필드를 확정하고 기획·계약을 일치시켜야 한다. 기존 대상 이름을 해제 뒤에도 표시하려면 필요한 데이터 보관 또는 문구 축소도 확정한다. 없는 데이터를 프런트엔드가 추정하거나 UUID를 이름으로 표시해서는 안 된다.

### GW-R02 — 메일 검증과 발송 상태

기획 56행은 빈 제목을 허용하고 합계 수신자 1명을 요구한다. 계약 38행은 제목 1~200자와 최소 To 1명을 요구한다. 계약의 `FAILED` receipt는 기획 58행에 없고, 실패 후 작성 내용 보존·수정·새 requestId 생성 여부도 명시되지 않았다. 계약은 INBOX/SENT 조회를 포함하지만 기획의 메일 탐색에는 받은 메일만 있다.

수신자·제목 규칙, 확정적으로 미발송인 FAILED와 결과 불명 UNKNOWN의 차이, 기존 키 재사용/새 작성 경계 및 SENT 탐색 범위를 하나로 맞춘다. UNKNOWN의 결과 확인이 실제 재발송을 하지 않는 원칙은 유지한다.

### GW-R03 — 연결 복귀와 일부 승인

기획 37행은 원래 계정/프로젝트 위치로 복귀한다. 계약 26행의 connect 입력은 feature뿐이며 허용된 복귀 대상의 전달·보관 방식이 없다. fragment 경로는 서버 요청만으로 알아낼 수 없다. 임의 URL을 허용하지 않으면서 서버가 검증하는 목적지 식별 방식 또는 고정 복귀 정책을 확정해야 한다.

기획은 일부 승인에도 가능한 기능 사용을 약속한다. 계약 27행은 해당 feature의 모든 scope가 있어야 저장하며 상태 DTO는 Gmail을 한 상태로만 표현한다. 예를 들어 gmail.readonly만 승인된 경우 읽기 가능 여부를 명시해야 한다. 서비스 단위의 전부/일부 정책 또는 read/send capability를 상위가 선택해 두 문서와 검사에 반영한다. 다른 계정·취소·불충분 승인에서 기존 ERP 로그인/프로필을 보존하는 계약은 수용한다.

### GW-R04 — Calendar 대상 범위와 해제 완료

기획 64행의 기존·앞으로 만드는 일정 전체라는 설명과 계약의 backfill에는 DRAFT 포함 여부가 없다. 기존 `CalendarEventConsumer.java:18`은 businessRevision 0을 제외한다. 확정 일정과 확정 뒤 취소만 내보낼지, 초안까지 범위를 바꿀지 상위 결정을 명시해야 한다.

계약의 generation 검사는 늦은 앱 상태 완료를 차단하지만 이미 출발한 외부 HTTP를 취소하지는 않는다. 기획 65행의 중단 완료와 계약 45행의 disconnect/demotion 보장을 실제 종료 확인 또는 신규 전송 중단과 진행 중 요청의 제한으로 정확히 정의해야 한다. 계약 44행은 해제 때 이전 대상 이름도 지우므로, 재연결 시 이전 이름을 보여 주는 기획의 근거도 확정해야 한다.

### GW-R05 — 제공자 취소 상태의 지속성을 가정하지 않기

계약 46행은 같은 ID의 cancelled tombstone과 ETag/revision으로 취소의 복원을 막는다. 그러나 Google은 일반 cancelled 이벤트를 삭제 상태로 취급하며, 결국 사라질 수 있고 `id` 이외 필드는 보장하지 않는다. 제공자 tombstone·private extended properties의 영속성에 의존해서는 안 된다. [Google Events 공식 명세](https://developers.google.com/workspace/calendar/api/v3/reference/events)

취소 상태의 권위가 로컬 일정/projection에 있음을 명시하고, Google 404/410 또는 메타데이터가 없는 cancelled 응답에서는 취소된 일정을 재삽입하지 않는 처리를 확정한다. 이전 claim의 insert/update, 취소 후 늦은 응답, 재바인딩을 포함하는 실제 HTTP 대역 검증을 요구한다. 새 엔드포인트나 양방향 동기화는 필요하지 않다.

### GW-R06 — 실행 경로와 공개 식별자 범위

기획 66행의 ScheduleForm 안내 수정이 필수인데 계약 21행의 프런트엔드 소유 경로에는 `ScheduleForm.tsx`가 빠져 있다. 허용 경로에 이 제한된 변경을 포함해야 한다.

기획 GW-02의 식별자가 공개 응답·DOM·URL에 없어야 한다는 표현은 `{messageId}` 경로와 프로젝트·파일 ID API에 충돌한다. UUID/sub를 사람의 표시 이름으로 사용하지 않으며 OAuth token/code를 공개하지 않는다는 정확한 범위로 고친다. 기존 가입 전 초대자 이메일 비노출 규칙도 유지한다.

## 수용한 구조와 검토 범위

identity가 vault·연결 callback·refresh를 소유하고 googleworkspace와 calendarintegration가 named API를 소비하는 단방향 구조, 서버 전용 암호화 자격 증명, 고정 host·제한 응답·자동 POST 재시도 금지, 개인 메일/Drive와 프로젝트 참조의 분리, 사용자별 발송 claim 및 UNKNOWN 회복은 적절하다. Calendar의 schedule::api snapshot과 별도 projection worker도 현재 모듈·트랜잭션 경계에 맞는다.

로컬 문서와 인증·Calendar·outbox·프런트엔드 표시 소스를 읽었고 Google의 공식 OAuth/Calendar 자료로 제공자 보장을 확인했다. 앱·테스트·환경 설정·실제 계정 데이터는 변경하지 않았고 브라우저·메일·Calendar 외부 쓰기를 실행하지 않았다. 이 단계의 PASS는 구현·접근성·실제 Google delivery 검증을 대체하지 않는다.

상위가 위 항목을 확정하고 전문 기획자가 해당 화면 계약을 갱신하면 영향받은 기준만 재검토한다. 로컬 원본은 `C:/Users/USER/.codex/worktrees/60aa/AI ERP/docs/ux/2026-09-07-google-workspace-review.md`이며, Notion `AI 생성문서 관리` 보관은 상위가 담당한다.

## revision 4 독립 재검토 — PASS

상위가 확정한 제한된 정책을 전문 기획자가 반영한 완료 메시지와 현재 로컬 두 문서를 다시 읽었다. 영향받은 Workflow, Screen contract, State coverage, Validation을 재검토했고 나머지 다섯 기준의 이전 유효 증거를 유지했다.

| 기존 결함 | 판정 | 현재 근거 |
| --- | --- | --- |
| GW-R01 | 해소 | Drive 20·프로젝트 참조 25·메일 20개, modifiedTime/최근 수정순, 공개 attachedBy, Calendar ownerName/backfillPending이 일치한다. 시간대·마지막 성공 시각·이전 대상 이름·진행 건수의 미지원 표시는 제거했다. |
| GW-R02 | 해소 | INBOX/SENT 탐색, To 최소 1명·전체 최대 20명·제목 1~200자·본문 최대 100,000자가 일치한다. UNKNOWN은 읽기 확인만 제공하고 FAILED는 새 UUID·새 검토 후 명시적으로 발송한다. |
| GW-R03 | 해소 | OAuth 복귀는 고정 `#/account/google`다. Gmail은 readonly+send의 서비스 단위 승인을 요구하며 부족하면 기능을 열지 않는다. 기존 승인·ERP 로그인 보존과 서비스별 상태가 두 문서에 명시됐다. |
| GW-R04 | 해소 | businessRevision > 0인 확정·확정 이력이 있는 취소 일정만 반영한다. 해제는 신규 전송 시작을 막으며 이미 발행한 HTTP가 완료될 수 있음을 설명한다. 이전 대상 이름을 보관한다는 약속을 제거했다. |
| GW-R05 | 해소 | 취소 권위는 로컬 projection에 둔다. desired CANCELLED의 404/410에서는 insert하지 않으며 이전 불확실 claim에 대한 제한된 재확인, 현재 revision/binding/credential 검사와 조건부 완료를 요구한다. 제공자 취소 기록의 영속성·외부 exactly-once를 주장하지 않는다. |
| GW-R06 | 해소 | ScheduleForm.tsx를 프런트엔드 소유 경로에 추가했다. GW-02는 사람 이름에 UUID/sub를 사용하는 것과 OAuth 자격 증명·코드 공개를 금지하며 정상 route/data 식별자를 허용한다. |

최신 9개 기준 판정은 Specialist routing PASS, User and task PASS, Workflow PASS, Screen contract PASS, State coverage PASS, Accessibility PASS, Design rationale PASS, Validation PASS, Ownership PASS다.

사용자가 추가한 localhost OAuth 요구에 따른 `frontend/vite.config.ts`의 5173 strictPort 및 `/api`, `/oauth2`, `/login`의 8080 proxy 소유 범위도 확인했다. 기존 Host 유지·기존 callback 경로·local secure=false와 production secure=true 분리는 명시되어 있다. 실제 등록된 redirect URI, OAuth 동의·callback/session, 실제 Google grant 동작은 구현 이후 별도 검증 대상이다.

문서 대조와 `git diff --check`는 exit 0이다. 이번 재검토에서 앱·테스트·환경 설정을 수정하거나 브라우저·실제 Google 데이터를 조작하지 않았다. 실제 모델 invocation, 회귀·HTTP 대역·동시성 검사, 브라우저 접근성·반응형 및 미실행 PostgreSQL/Google 검증의 구분은 후속 task-review에서 확인한다. 상위가 로컬 원본 이후 Notion 보관본을 동기화한다.
