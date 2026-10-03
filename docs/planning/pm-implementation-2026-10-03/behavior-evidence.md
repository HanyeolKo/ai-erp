# Behavior evidence ERP-PM-IMPLEMENT-20261003 r1

- Parent root; architecture r1; accepted screen-plan r3, visual-contract r1 and visual-change-plan r2; independent UI review passed. Risk high; required because cross-screen authorization, ambiguous saves, conflict/draft recovery and new default navigation affect usability.
- Candidate revision: pending implementation commit; do not use baseline/planning screenshots as actual results. Target: built candidate frontend with clearly labelled synthetic own-API fixtures, local unit/API tests and actual GitHub PostgreSQL CI. Live Google not-required for independent offline-contract-only work; no provider usability claim. Docker-local execution unavailable.
- Router observes read-only; implementer writes all fixtures/tests and records commands/cwd/exits/raw outputs; reviewer independently verifies and owns verdict. Parent accepts. Observed column must stay pending until actually exercised.

| Scenario | Trigger/precondition | Expected | Observed/evidence/revision/environment |
| --- | --- | --- | --- |
| Default and advanced | Existing legacy project TASK/EPIC/TOPIC/MILESTONE and all six views; open bare plan then explicit legacy view URL | Compact TASK default; advanced all views/kinds/history/filters remain, original date-only/ID/state data intact | pending |
| Authoritative roles | MANAGER, creator MEMBER, other MEMBER, VIEWER; unrelated project and revoked current membership | Shared plan permissions preserved; definition/decision manager only; primary schedule creator/manager only, related links shared plan writer; viewer readable/denied editing; counts exclude unreadable projects | pending |
| S1 unknown create | Network response lost after server applied requestId; retry identical payload | Same request resolves without duplicate, draft remains usable and next edit gets new requestId | pending |
| New unknown write | Definition/meta/link/criteria write may be applied; receipt present or404 | Identical canonical replay only, applied receipt followed by resource refresh;404 stays unknown; no duplicate/blind overwrite | pending |
| Conflict then edit | Concurrent version update causes409 while local draft changed | Latest server values and draft retained, user deliberately reconciles/saves using current version, then can edit/save again | pending |
| Partial create/save | Project created; optional definition or original targets fail independently | Created project and successful region remain; failed region draft/retry retained; no automatic delete or repeated project creation | pending |
| Evidence loss | Linked target/ref removed or TASK/MILESTONE cancelled after criteria/decision saved | Identity/history/decision retained; missing target shown without stale metadata/provider permission claim; required evidence reduces current readiness | pending |
| Time/date boundary | IANA zone, local midnight/DST, overdue/date-only interval and actual schedule instants | Date-only unchanged, actual zone/date/time shown; same-day/week filter deterministic; mismatch observation causes no automatic edit | pending |
| Incomplete results | Composed region fails or bounded underlying snapshot incomplete | Successful regions stay readable; retry failed region; observed subset marked, no full totals/denominator/capacity | pending |
| Disabled flag | Existing/new data then config disabled | Legacy operation/advanced routes retained, new data readable, new and replayed writes denied; no data erased | pending |
| Actual visibility | Actual built candidate at1280/390 and200%, long names/reasons/tokens | Labelled reflow and four state axes, relationship distinction,44px applicable targets, ordinary overflow<=1px, primary actions visible | pending |
| Keyboard/dialog | Tab/ShiftTab navigation, route changes, edit/recovery dialog and Escape | DOM order aligned, visible focus, heading focus, native dialog trap/Escape/caller return and focusable error feedback | pending |
| Calendar preservation | Identical schedule fixtures before/after for month/week/mobile agenda | Seven columns,1152px week canvas and computed geometry/event ordering/hit areas preserved | pending |

- N/A: Google activation/file permissions/live provider receipts, payroll/tax/accounting, capacity/critical path/two-way sync, persisted timezone preference history. These are outside scope; synthetic mocks are never live evidence.
- Required unavailable check: actual PostgreSQL fresh/populated legacy migration, concurrent transactions and module integration execute in existing CI, exact candidate SHA required before final high-risk acceptance.
- Final observations and native reviewer verdict: pending; deviations return to parent rather than fabricate outcomes.

## F1 observation prerequisites, 2026-10-03

- Parent Chrome DevTools instance is connected; worker instances cannot launch the same profile concurrently. Parent captures actual browser outputs and the router independently reads these outputs. No browser/configuration process is killed or changed to work around this conflict.
- Initial actual navigation to `http://127.0.0.1:4173/#/projects/p1/plan` rendered the existing login-configuration-required screen. The synthetic helper supplied a non-contract configuration response; the existing production auth gate behaved correctly. Implementer owns the helper-only correction. Product behavior observations remain pending.
- F1 unit suite: 341 tests passed; typecheck/build passed using the verified exact-base CI OpenAPI artifact. These checks do not substitute for browser observations, current PostgreSQL execution or independent task acceptance.
