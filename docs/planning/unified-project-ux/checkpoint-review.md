# Frontend checkpoint independent review

- Reviewer: actual /root/unified_plan_review Astra/high, read-only checkpoint task-review2026-09-29. Source: frozen uncommitted frontend above47dac12.
- Verdict: FAIL / CHANGES_REQUESTED. Contract/specialist gates PASS; functional acceptance/main preservation/verification FAIL. Parent accepts findings and returns implementation for bounded correction.
- Raw checks: frontend-checkpoint-full.log224PASS74FAIL/298,67uncaught; frontend-checkpoint-typecheck.log14errors. Reviewer inspected source/main diff/logs, did not rerun tests/browser/build.

| Stable ID | Priority | Finding and required correction |
|---|---|---|
| SV-FE-006 | P1 | Schedules.tsx346-348 converts later-page401/403/404 to partial success. Workspace138 unlocks before target query;145 renders Records despite denial. Propagate access errors, hide protected data and lock all writes until fresh role+workspace+target read succeeds; failed recovery remains locked. |
| SV-FE-007 | P1 | Workspace155-156 discards dirty draft on409 and excludes clean editing drafts from refetch sync. Preserve dirty/stale draft, sync clean values+version, explicit reload/re-entry and Cancel adopting latest values/version. |
| SV-FE-010 | P1 | Form142-149 omits custom valuesDirty from guard; Detail96-100,125 custom editor has no departure guard. Cover custom-only Cancel/back/route with Continue preservation and deliberate Discard. Option draft mount improved but untested. |
| UPUX-FE-001 | P1 | Workspace144,146 undefined date-jump state crashes screen. Remove unapproved popup entirely and restore accepted main inline date control. |
| UPUX-FE-002 | P1 | Schedules486 suppresses workspace mobile Agenda while CSS also suppresses mobile Calendar, leaving no schedule display. Restore mobile agenda, actions and permission behavior. |
| UPUX-FE-003 | P1 | Workspace118 list time-change button is inert; edit link loses return context. Connect existing time Dialog/recovery and context-preserving return. |
| UPUX-FE-004 | P2 | Workspace18,137 legacy week/list views fall back incorrectly. Normalize week to builtin-calendar+modeweek and list to builtin-list while preserving date/filter/context. |
| SV-FE-013 | P1 | Six workspace and two popup tests omit required denial/recovery,409/refetch,overlapping pagination,MEMBER copy,page reset,custom dirty and fractional filter regressions. Add meaningful checks; retain baseline behavioral assertions. |

SV-FE-001/003/009/011 have source improvements but remain unclosed without focused regression proof. No frontend acceptance or release approval. Backend accepted evidence remains valid and untouched.
