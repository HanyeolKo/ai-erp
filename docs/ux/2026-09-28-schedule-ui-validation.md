# 일정 UI 검증 기록

Task: schedule-ui-refinement-20260928. 구현과 운영 배포를 완료했다. 아래 초기 기록의 대기 상태는 작성 당시의 이력이며, 최종 상태는 배포 검증 절을 따른다.

## 변경 전 확인

운영 화면을 실제 Chrome에서 읽기 전용으로 확인했다. 원래 사용자의 편집 탭은 보존했고 별도 감사 탭에서 계획 월간, 생성, 일정 월간, 생성, 상세, 필터를 확인했다. 계획0개/일정4개 조건에서 긴 필터·요약과 중복 표시를 재현했다. 운영 저장은 하지 않았다.

동일 소스2943fb8의 가상 데이터에서도 확인했다. 1440×900에서 .plan-calendar 시작은837.67px였다. 일정 생성의 시작/종료는 비어 있었다. 주간 시간 canvas는 현재 소스에서1152px이며 이전 문서의260px는 최신 소스 기준이 아니다. 7열, 이벤트 top41.6667%/height4.16667%/left0%/width100%, 시간별4.16667% 간격을 변경 전 기준으로 저장했다.

근거: .tmp/evidence/schedule-ui-refinement-20260928/baseline-observations.json 및 fixture-before-*.png. 운영 before-*.png는 개인정보가 포함되어 로컬에만 보관한다.

## 디자인 색 대비

상대 휘도 공식으로 계획한 기본 색 조합을 계산했다. 아직 실제 렌더링 전체에 대한 완료 판정은 아니다.

| 전경 / 배경 | 대비 |
| --- | --- |
| #17243b / #ffffff | 15.53 |
| #526079 / #f8fafc | 6.07 |
| #2458a6 / #edf3fc | 6.23 |
| #216244 / #edf8f1 | 6.67 |
| #a82d26 / #fff1f0 | 6.22 |
| #b45309 / #ffffff | 5.02 |
| #7b899d / #ffffff | 3.55 |

## 구현 이후 검증 계획 — 초기 기록

대기: 통합 frontend suite/typecheck/build, 실제 브라우저 워크플로와 반응형 측정, 독립 구현 검토, 동일 SHA CI, 독립 배포 준비 검토, 배포 결과와 read-only 운영 확인.

Notion은 동일 문서의 읽기 상태를 확인한 후 동기화할 수 있다. 현재 외부 동기화는 하지 않았으며 로컬 문서가 기준이다.

## 최종 로컬 검증 — 8991a0947d41580454da5fbcf23dcd247c852b45

2026-09-28 부모가 동결 소스를 검사하고 동일 내용을 커밋했다. source worktree는 clean이다. 잘못 중첩 저장된 이전 Plan attempt2 증거는 삭제하지 않고 canonical evidence/preserved-worktree-evidence로 이동했다.

- CWD: D:/Project/Ai ERP/.tmp/worktrees/project-plan-views-20260927/frontend
- D:/nodejs/node.exe node_modules/vitest/vitest.mjs run --reporter=default: exit0, 22files290tests. final-frontend-test.log.
- D:/nodejs/node.exe node_modules/typescript/bin/tsc -b --pretty false: exit0, output empty. final-typecheck.log.
- D:/nodejs/node.exe node_modules/vite/bin/vite.js build: exit0,82modules, assets/index-xrI-AuYg.js and index-DP1MGNVW.css. final-build.log.
- git diff --check: exit0. No API/backend/schema/provider/dependency/infra/workflow changes.
- Plan focused10 and browser50 pass. Actual default-content y: board1440=272,390=447; calendar1440=358.80,390/320=499.80. 320/200percent reflow checks pass. Calendar target/deadline distinction visible.
- Schedule relevant103/headless pass. Calendar1440=322.39, agenda390/320=482.69, page overflow0, view icons18px, visible mobile controls44px minimum, weekcanvas1152px retained.
- Parent directly inspected before/after screenshots, including Plan/calendar/board and Schedule/month/mobile/form/detail. Fictional loopback data only for after images.
- Existing Chrome CUA connection stopped responding after a native confirmation during manual local testing. User was notified. No claim of a completed post-deploy authenticated browser journey; existing headless fixture coverage and screenshot inspection are distinct evidence.
- Independent Astra/high integrated task-review PASS. Release CI and actual deployment remain pending at this record time.

## 최종 배포 검증 — 2026-09-28

- 배포 SHA: `8991a0947d41580454da5fbcf23dcd247c852b45`. PR #21 병합 후 동일 SHA의 main CI `36410002499`와 자동 Deploy Production `36411598922`가 성공했다.
- 운영 blue 컨테이너의 상태는 healthy이다. 실제 image ID, OCI revision, manifest와 active-state가 일치한다. 20초 관찰 및 후속 internal/public smoke가 성공했다.
- 운영에서 제공하는 CSS와 JavaScript의 SHA-256이 검증한 로컬 빌드와 일치한다. 백업 파일의 크기 및 checksum을 확인했다. 기존 migration checksum은 변경되지 않았다.
- 이전 green `2943fb8f127eb8c0f622186dd69cdf49f307d3ce`의 image를 보존했고 해당 컨테이너는 종료됐다. rollback은 필요하지 않았다. 데이터베이스 복구 훈련은 하지 않았다.
- 로컬 미리보기 서버 PID 30108/2552는 command line을 확인한 뒤 종료했다. CIM Terminate 반환값은 각각 0이고 8080/5173의 listener가 남아 있지 않음을 확인했다.
- 운영 smoke는 지정 IP와 Host/SNI를 사용했다. public DNS 및 인증서 신뢰, 로그인 이후의 수동 운영 화면 흐름, 실제 Google 연동까지 검증했다는 뜻은 아니다. 변경 후 UI는 가상 데이터 기반 브라우저 검사와 렌더링 이미지로 확인했다.
- 원본 근거: `.tmp/evidence/schedule-ui-refinement-20260928/release/ci-main-success.json`, `deploy-success.json`, `post-deploy-production.txt`, `post-deploy-production.json`, `served-assets/verification.json`.
- 상세 배포 결과: `docs/releases/2026-09-28-schedule-ui-RELEASE-RESULT.md`.