# Unified Project UX Integration Screen Plan

## Request and specialist handoff

- Task / revision / date: `consolidate-pr-17-18-22` / UI plan r1 / 2026-09-29; canonical local path `docs/planning/unified-project-ux/ui-plan.md`.
- Actual specialist: `/root/unified_ui`, native `ai-erp-ui-ux-designer`, gpt-5.6-sol / medium; assignment acknowledged, no delegation, escalation, production edit, or verdict; provider usage unavailable/null.
- Input gate: accepted increment plan r1 and independent Astra/high PASS in `plan-review.md`; base `8991a0947d41580454da5fbcf23dcd247c852b45`; PR17 `2cd0b710`; PR18 `8b601452`; PR22 `c8c7f3f8`; offline-contract-only Git evidence ready.
- Users/goal/entry: MANAGER, eligible MEMBER, and VIEWER enter `/projects/:id/schedules` or the project shell notification trigger to act on one project schedule dataset through Calendar, Cards, List, dashboard, form, and detail.

## Current evidence and constraints

- Preserve `schedule-views-functional-plan.md` r1 and visual plan r1, API r2 except its migration name is overridden by additive V10, and the eight open findings in `schedule-views-frontend-review-final.md`.
- Main owns the six independent plan views (Roadmap, Hierarchy, Tasks, Board, Milestones, Monthly), current schedule date/filter/month/week/list navigation, drag/move/resize, time-change Dialog and recovery, create defaults/return context, authorization/concurrency/Google behavior, `unsaved-changes.ts`, and corrected `Dialog` focus lifecycle. Integration must not replace or fork them.
- Adopt PR17 popup behavior without its stale App/Schedule/Plan/client/CSS deletions; adopt PR18 workspace only where composed onto main behavior. PR22 contributes four canonical documents and the `SCHEDULE_CHANGED` label fix only; all richer notification, density, repeated-entry, milestone, and broad validation proposals remain backlog.
- Pinned guidance applied: existing dense ERP hierarchy and explicit action names; semantic controls/table, persistent labels, inline errors, polite async status, URL state, visible focus, keyboard alternatives to drag, dirty-navigation warning, long-content/reflow coverage. Existing V2 palette/type/spacing remains authority; no framework, dependency, motion, shadow, or visual-system expansion.

## Screen ownership and navigation

- Keep one peer selector for immutable builtin Calendar/Cards/List plus authorized shared and actor-owned personal views. Do not render the old month/week/list selector as a second peer selector and do not nest a second page heading/filter shell inside Calendar.
- Canonical query ownership is `view=<builtin-or-authorized-id>` for the saved view, `mode=month|week` and `date=YYYY-MM-DD` for Calendar substate, and `page>=0` for Cards/List. Calendar alone renders the existing month/week toggle and date controls; Cards/List alone render workspace pagination.
- Normalize legacy URLs once: `view=month|week` becomes `view=builtin-calendar&mode=<value>`; `view=list` becomes `view=builtin-list`. Preserve valid date/filters/return context. Peer switching retains the last valid Calendar `mode/date`, resets `page=0`, and never lets hash synchronization restore an old page.
- Calendar delegates records and interaction to the current main implementation with API-r2 workspace config/legend metadata; Cards/List query and render the same schedule IDs. All create/edit/detail links carry validated return context, and Cancel/success returns restore it.
- The shell notification button opens PR17 content in the current main `Dialog` without changing the hash, date, mode, filters, page, or dirty editor state. Close button/Escape restores trigger focus; safe record/project link navigation closes the Dialog and uses the existing route guard. `/notifications` remains a direct, pageable route using the same content/read/session logic.
- Popup rows retain event label, localized time, safe link, read state, loading/empty/error/paging states, and guarded invalidation. Map canonical `SCHEDULE_CHANGED` to “일정 변경”; unknown types use the generic label. No PR22 content-contract expansion is introduced.

## Eight required repair flows

1. `SV-FE-001`: Calendar pagination deduplicates schedule IDs, but if ten pages are exhausted while server `hasMore=true`, mark the result partial even below 1,000 unique rows; retain loaded rows, disable misleading completeness, and offer refresh/narrower range.
2. `SV-FE-003`: MEMBER opening a SHARED view sees “Save personal copy”; Save POSTs `PERSONAL` with a new ID. MANAGER editing SHARED PATCHes SHARED; scope is immutable, and explicit Duplicate remains a new copy.
3. `SV-FE-006`: a protected Calendar page, metadata/config, dashboard, or mutation returning an access/session failure escapes partial success, removes protected results, announces unavailable access, and locks every workspace mutation. Unlock only after fresh project-role plus target read succeeds; failed recovery remains locked.
4. `SV-FE-007`: external dashboard/value refetch updates displayed server values and `rowVersion` whenever the draft is clean. Dirty drafts remain intact and labelled stale; Cancel adopts both latest values and version. A 409 offers Reload latest and intentional re-entry/duplicate, never automatic replay or a repeated stale save.
5. `SV-FE-009`: switching any peer view or successfully saving filter/group/legend/sort/visible-field configuration writes `page=0` into the URL atomically; hash listeners cannot restore the prior page. Calendar `mode/date` remain independent.
6. `SV-FE-010`: view settings owns name, config, and nested option drafts through one dirty boundary. Close/Escape/route change opens Discard/Continue; Continue preserves add/rename/reorder option drafts, while Discard clears all. Record create/edit and inline detail edits use main `useUnsavedChanges` with the same protection.
7. `SV-FE-011`: NUMBER filter/value controls accept finite fractional input with `step="any"`, retain a persistent label and nearby validation, and serialize the exact valid number; invalid/empty values remain draft errors or explicit null per API r2.
8. `SV-FE-013`: focused regressions cover overlapping ten-page partial data, MEMBER copy/MANAGER edit, denial plus failed recovery on all four surfaces, external refetch/cancel/conflict, nonzero-page reset, nested option and record dirty exits, and fractional NUMBER input without weakening retained calendar/list/Dialog tests.

## Workflow, permissions, and states

- Keep API-r2 settings, property/option archive, fixed/custom atomic record forms, hidden-after-save, dashboard selection, conflict, cap, and unavailable-reference flows exactly as retained plans specify; V10 changes deployment ordering only, not the wire or screen language.
- MANAGER manages schema, SHARED views, and dashboard selection; eligible MEMBER manages own PERSONAL views and records allowed by existing creator rules; VIEWER and non-owner MEMBER read only. Disabled/hidden actions explain role, archived target, cancelled record, pending write, cap, or locked recovery without exposing another actor’s personal view.
- Initial/loading/empty/stale/partial/error/409/permission/success/disabled states keep prior safe results only when authorization remains valid. Use `aria-busy`, polite status for progress/success, alert for blocking failure, first-error focus, and no color-only legend/status meaning.
- Keyboard order is shell trigger, peer selector/actions, Calendar subcontrols or view configuration, results, pagination. Preserve 3px visible unclipped focus, Dialog trap/Escape/caller return, semantic table and mobile labelled blocks, button alternatives for reorder/drag, Calendar time Dialog, 44px mobile targets, 200% zoom, reduced motion, and no global horizontal overflow.

## Visual handoff excerpt

- Reuse SV-01–11: existing header → selector/actions → summary → results → pagination; 40px desktop/44px mobile controls; wrapping selector/chips; three/two/one-card grid; semantic desktop List and labelled mobile blocks; existing allowed label-plus-color swatches; current 1,152px week geometry.
- Popup is the existing white semantic Dialog/overlay, sized for readable notification rows and bounded to `calc(100vw - 24px)` / `calc(100vh - 24px)` under 768px; actions wrap in source order, long titles wrap, and paging remains in flow. The visual specialist may tune only spacing/width within V2 tokens and must preserve the functional behavior above.
- No visual rework of plan views, Calendar geometry, drag hit areas, form field order, dashboard order, or PR22 backlog surfaces.

## Acceptance and evidence

- AC1: popup open/close/read/page/safe-link/direct-route/session flows preserve the current schedule URL and restore focus; `SCHEDULE_CHANGED` never falls back to the generic label.
- AC2: one selector and canonical URL mapping provide Calendar/Cards/List peers; legacy links normalize; current date/month/week/drag/time/create/list behavior and six plan views remain unchanged.
- AC3: all eight named defects reproduce before and pass the specified flows after integration, with protected results removed and writes locked after denial.
- AC4: API-r2 roles, typed properties, saved views, atomic records, dashboard, partial bounds, conflicts, return context, and visual SV-01–11 remain observable under the V10 migration override.
- AC5: keyboard-only and 1280/768/390/320px plus 200% zoom checks cover popup, selector, Calendar, Cards, List, settings, form/detail, and recovery without clipped focus or page overflow.
- Planned evidence: focused tests per defect; retained suites; typecheck/build; screenshots/reflow/contrast; Dialog and dirty-route keyboard checks; API generation; high-risk PostgreSQL/auth/concurrency regressions and independent Astra/high task review after implementation.
- Checks not run by this specialist: code edits/diff validation, unit/integration/PostgreSQL/Flyway, generated client, typecheck/build, browser/screenshots, keyboard/contrast, live GitHub/CI/Google/deployment, production data, or Notion synchronization. This is an offline planning artifact and claims no live integration success.
- Reviewer target: `docs/planning/unified-project-ux/ui-review.md`; any change to increment r1, API-r2 behavior, V10 override, eight-defect list, or preserved main invariants makes this plan/review stale.

## Archive

- Local source: `C:/Users/USER/.codex/worktrees/unified-project-ux/AI ERP/docs/planning/unified-project-ux/ui-plan.md`.
- Notion archive: deferred to parent after review/adoption and required same-context unread-page/status verification; no external write performed by this specialist.
