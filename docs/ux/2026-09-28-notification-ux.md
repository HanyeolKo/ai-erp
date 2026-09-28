# 알림 UX 점검 및 개선 기준

## 범위와 결론

- 이 문서는 `project-plan-views-20260927`의 commit `8991a09`와 2026-09-28 production Chrome 관찰을 근거로 작성한 UX 제안이다. 구현 완료를 주장하지 않는다.
- 현재 알림은 사건의 종류와 발생 시각만 알려 주므로, 사용자가 어느 프로젝트의 어떤 일정이 누구에 의해 어떻게 바뀌었는지 판단할 수 없다.
- 가장 먼저 알림 content contract와 event type 불일치를 고치고, 그다음 읽기와 후속 행동의 흐름을 연결해야 한다.

## 확인한 사실

- production에는 모두 읽은 알림 6개가 있었고, 두 일정에 관한 `일정 생성`, `일정 확정`, `일정 취소`가 반복되었다.
- 각 행에는 event label, 시각, `관련 일정 보기`, `읽음`만 표시되었다. 프로젝트명, 일정명, 변경자, 변경 요약, 전후 값, 필터, 주의가 필요한 항목의 구분은 없었다.
- 과거의 `일정 확정` 링크를 열면 현재 `취소` 상태인 일정 상세로 이동했지만, 과거 event와 현재 상태의 차이를 설명하는 context가 없었다.
- frontend label map은 `SCHEDULE_UPDATED`를 `일정 변경`으로 정의하지만, `ScheduleService`는 `SCHEDULE_CHANGED`를 발행한다. 따라서 실제 변경 알림은 `프로젝트 알림` fallback으로 표시될 수 있다.
- `NotificationController.Response`는 `id`, `type`, `link`, `readAt`, `createdAt`만 반환한다.
- 저장된 notification payload에는 `businessRevision`만 있고 일정명, 프로젝트명, actor, 변경 요약은 없다.
- 알림 조회와 읽음 처리는 로그인한 본인의 `userAccountId`로 제한된다. `SCHEDULE_ACKNOWLEDGED`는 notification 생성 대상에서 제외된다.

## 정보 우선순위

1. 첫 줄에는 `[프로젝트명] 일정명`과 사용자가 판단해야 할 결과를 표시한다.
2. 둘째 줄에는 `actor · event 발생 시각 · 변경 요약`을 표시한다. 변경 요약은 바뀐 필드와 이전 값에서 새 값으로의 전환을 포함한다.
3. 셋째 줄에는 현재 상태가 event 당시와 다를 때 `이후 취소됨`처럼 현재 상태를 별도로 표시한다.
4. 기본 행동은 event가 가리키는 일정과 revision context를 여는 `변경 내용 보기`로 제공한다.
5. Google Calendar 동기화 결과는 일정의 업무 변경보다 낮은 우선순위로 배치하고, 실패하여 사용자의 조치가 필요할 때만 전면에 표시한다.

## 예시(가상 데이터)

> [봄 캠페인] 촬영 시간이 변경되었다
> 김기획 · 2026-09-28 14:20 · 10월 3일 10:00 → 13:00
> 이후 취소됨 · 변경 내용 보기

## P0: content semantics와 type 정합성

- backend와 frontend에서 canonical type을 `SCHEDULE_CHANGED`로 통일하고, 알려지지 않은 type은 사용자가 이해할 수 있는 일반 문구로 표시하고, raw type은 진단 기록에만 남긴다.
- API가 `projectName`, `scheduleTitle`, `actorDisplayName`, `eventSummary`, `businessRevision`, `currentStatus`를 전달하도록 content contract를 확장한다. 기존 JSON payload를 확장할 수 있으므로 이 제안 자체가 DB schema 변경을 요구하지는 않는다.
- 알림은 발생 당시 event를 보존하고, 현재 상태를 별도 필드로 제공한다. 현재 상세 상태로 과거 사건을 덮어쓰지 않는다.
- 권한이 사라진 프로젝트의 제목이나 actor를 노출하지 않는다. 링크를 열 때도 현재 project access를 다시 검사하고, 접근 불가이면 안전한 안내만 표시한다.
- legacy 알림은 누락 필드를 추측하지 않고 `일정이 변경되었다 · 발생 시각`과 유효한 링크를 제공한다. 현재 조회한 제목은 `현재 제목`으로 명시할 수 있지만 historical diff는 `기록 없음`으로 표시하며, 링크가 없거나 권한이 없으면 행동을 숨긴다.

## P1: 읽기와 행동 흐름

- unread를 먼저 모으고, `확인 필요`, `업데이트`, `연동`으로 구분한다. 날짜별 구분과 프로젝트 filter를 제공한다.
- 항목은 제목과 한 줄 변경 요약을 중심으로 구성한다. 시각과 변경자는 보조 정보로 두고, 읽지 않음은 점과 문구로 구분한다. 긴 제목은 모바일에서도 핵심 내용을 읽을 수 있게 줄바꿈한다.
- 상세 내용이 정상적으로 열리면 읽음 처리하되, 읽음은 단순히 내용을 본 상태로 정의한다. 열기 실패 시 읽지 않음 상태를 유지한다.
- 일정 `확인`은 현재 `businessRevision`에 대한 명시적인 업무 의사 표시이며 읽음과 분리한다. 알림 화면의 `읽음 처리`가 schedule acknowledgement를 생성해서는 안 된다.
- 상세 화면에는 `이 알림은 9월 28일의 확정 event이며 일정은 이후 취소되었다`처럼 event 시점과 현재 상태를 함께 보여 준다.

## 측정 가능한 acceptance criteria

- 새 schedule 알림 표본 30건의 100%에서 프로젝트, 일정, event, 시각, actor 또는 개인정보 보호 fallback을 한 화면에서 식별할 수 있다.
- `SCHEDULE_CHANGED` 알림 100%가 `프로젝트 알림`이 아니라 `일정 변경`으로 표시된다.
- 과거 event 이후 상태가 바뀐 표본 10건의 100%에서 event 당시 결과와 현재 상태를 서로 다른 문구로 표시한다.
- 알림 읽기 20회에서 schedule acknowledgement 생성은 0회이며, 명시적 `일정 확인`만 현재 revision에 acknowledgement를 생성한다.
- 접근 권한을 회수한 계정 10건에서 프로젝트명, 일정명, actor 개인정보 노출은 0건이고 직접 링크도 권한 오류 안내로 종료된다.
- legacy payload, unknown type, null link를 포함한 fixture에서 빈 제목, 깨진 링크, 화면 crash는 각각 0건이다.
- Google 동기화 성공은 기본 정보보다 낮게 표시되고, 실패 중 사용자 조치가 필요한 사례만 `확인 필요`에 포함된다.
