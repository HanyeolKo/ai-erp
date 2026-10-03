# F1 browser observations

- Task: `ERP-PM-IMPLEMENT-20261003-F1-BEHAVIOR`; observation revision `r1`.
- Observer: router, read-only. Risk remains `high`; this record is evidence, not a verdict.
- Candidate base: `b6603269800352073e68c6e76fe4bedf516c5ba6`, with uncommitted F1 source and built bundle identified by `tmp/pm-implementation-2026-10-03/browser/f1-candidate-manifest.json`.
- Fixed fixture SHA-256: `5E0E36F9A170D887499BA17F7F9FB8CEF4E01F06448F4BA784C6DAA919EF14C5` from `owned-servers.json`.
- Environment: Chrome `154.0.0.0`, Windows, `1280x900`, DPR `1`; observed at `2026-10-03T09:06:59.690Z` (`18:06:59 KST`). Synthetic own API only; no ERP, Google, credentials, or provider claim.

## Prerequisite

The first candidate load showed the configured-login screen, recorded in `prerequisite-auth-mismatch.json` and `prerequisite-environment.json`. After the parent corrected the fixture/auth prerequisite, the same hash route loaded the authenticated MANAGER fixture. The prerequisite result is not counted as candidate product behavior.

## Default and advanced boundary at 1280px

Expected: bare `/projects/p1/plan` opens the compact TASK default. The six legacy views, kind selector, advanced filters, history, dependencies, and legacy editing remain available after the explicit `고급 계획 보기` entry. Explicit legacy view URLs open the advanced surface.

Observed at `http://127.0.0.1:4173/#/projects/p1/plan`:

- The compact default content is present: heading `작업`, title/state quick-create, summary, and a seven-column TASK table with two legacy TASK rows.
- The explicit `고급 계획 보기` link is present and points to `?view=roadmap`.
- The same default page also visibly renders the complete six-view navigation (`로드맵`, `계층`, `작업`, `보드`, `Milestone`, `월간`) and the advanced search/kind/state/assignee filter bar.
- The DOM evidence reports the advanced navigation at `414.15625x44` and the filter region at `992x58`; both are visible. This is a concrete default-versus-advanced separation deviation. The advanced controls are not confined to the explicit advanced entry.
- Source inspection explains the observation without changing source: `ProjectPlan.tsx` applies the HTML `hidden` attribute when `defaultMode` is true, but the actual computed DOM reports both elements visible. The browser result is authoritative for this record.

## Additional measured observations

- Ordinary page horizontal overflow is `0px` at 1280px.
- The H1 is visible at `28px`; the project sidebar is present and its navigation controls measure `44px` high.
- Several TASK actions are below the planned 44px applicable-target check: TASK title buttons measure `32px` high and `세부 정보` buttons and status selects measure `40px` high. Reviewer determines applicability and severity.
- The observed TASK facts remain distinct: assignee, exact six-state control, date-only target/deadline, blocking explanation, and detail action. No source data mutation was performed for this observation.

## Evidence

- `D:/onedrive/Documents/ChatGPT/AI ERP/tmp/pm-implementation-2026-10-03/browser/f1-observations/default-1280-snapshot.json`
- `D:/onedrive/Documents/ChatGPT/AI ERP/tmp/pm-implementation-2026-10-03/browser/f1-observations/default-1280-dom.json`
- `D:/onedrive/Documents/ChatGPT/AI ERP/tmp/pm-implementation-2026-10-03/browser/f1-observations/prerequisite-auth-mismatch.json`
- `D:/onedrive/Documents/ChatGPT/AI ERP/tmp/pm-implementation-2026-10-03/browser/f1-observations/prerequisite-environment.json`

## Pending observations

The following remain pending and must not be inferred from the 1280px default evidence: all six advanced view contents and legacy URL preservation; dialog/deep-link close and edit; VIEWER and scoped denial; 409 draft preservation and deliberate recovery; lost-response replay/new request behavior and server request logs; 390px, native 200% zoom, keyboard/focus/dialog behavior, contrast, long-text wrapping, and 44px mobile targets; candidate-versus-baseline Calendar month/week/mobile agenda and exact geometry. Backend API, Google, and real PostgreSQL are outside this F1 browser fixture; final CI evidence remains pending.

No tests, code, configuration, source checks, Git operations, or provider actions were run by the observer. Usage is `null`.
