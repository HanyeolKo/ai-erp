# Schedule views frontend repair result (attempt 2)

Status: **blocked for independent re-review; local frontend test suite is not green.**

## Delivered code

- Calendar keys include the effective period; the workspace Calendar reads bounded 100-record pages through 1000 records, de-duplicates schedule IDs, and retains usable loaded records with an explicit partial/retry message after a later-page error or cap.
- Workspace form saves use only atomic workspace-record endpoints, including a zero-property schema. A submit that wins the render race waits for the guarded metadata read; it never falls back to legacy create/edit.
- Number fields accept decimals; checkbox and archived values have explicit clear paths. Detail custom-value mutation pins the row version when the draft first becomes dirty and preserves/reloads on failure.
- Builtin creation preserves MANAGER-selected SHARED scope. Existing scope is immutable; option saves adopt returned UUIDs and row versions. Viewer/cancelled action gates and dashboard pagination/reset/unavailable state were strengthened.
- Synthetic preview now preserves property options with assigned IDs and enforces stale/viewer checks for workspace record mutations.

## Evidence

- `pnpm typecheck` — exit 0.
- `pnpm build` — exit 0 (elevated Windows Vite process; 80 modules).
- `pnpm test -- --reporter=dot` — exit 1: 26 failed / 236 passed / 262. The prior reviewer had 20 failed / 242 passed. Six additional old-suite failures assert legacy `POST /schedules` calls or immediately expect their legacy response; the repaired contract correctly invokes atomic `POST/PATCH /schedule-workspace/records`. The remaining 20 require the already-recorded feature and regression repair matrix. No test was removed or weakened.

## Handoff

Changed: `frontend/src/screens/Schedules.tsx`, `ScheduleForm.tsx`, `Detail.tsx`, `ScheduleWorkspace.tsx`, `scripts/ui-preview-server.mjs`, and this result. The UI is **not stable-browser-ready** because full tests remain failing. Backend-generated API has not been regenerated; wait for parent coordination.
