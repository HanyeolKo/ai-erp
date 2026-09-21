# Project Schedule Dataset and Saved Views

## Request and specialist handoff

- Task / revision / local canonical path: `project-calendar-operating-ux`, plan r3 / contract r2, 2026-09-21, `docs/ux/project-calendar-operating-ux-2026-09-21.md`.
- Handoff: parent `/root` assigned native `ai-erp-ui-ux-designer` (Sol/medium); ACK returned before this rewrite. Parent-dispatched independent `ui-plan-review` passed r3; parent accepted the conceptual proposal.
- Resolved user model: Calendar is one way to view and edit a project's schedules. Card and list are peer views of the same records, and user-defined properties provide Notion-like classification without reproducing Notion or requiring tasks, milestones, or a board.
- Scope: project schedule dataset, saved views, property behavior, create/edit continuity, governance proposal, states, accessibility, and bounded capability. No visual styling, production code, test, configuration, external integration, or permission-policy change.

## Current evidence and constraints

- `frontend/src/App.tsx`, `ui.tsx`: schedules are project-owned and navigation is project-scoped; no saved-view model exists.
- `frontend/src/screens/Schedules.tsx:273-435`: month/week, agenda, filters, and list are one screen over one paged result; view state is local and resets across detail/form routes.
- `frontend/src/api/client.ts:241,255-257`: the page size is 20, so every view needs a completeness contract rather than treating one page as the full dataset.
- `ScheduleForm.tsx` and `calendar-direct-manipulation.ts`: records already share create/edit APIs; Calendar supports direct time changes plus an accessible dialog alternative.
- `backend/.../schedule/api/ScheduleController.java`: write/response contracts expose fixed schedule fields only; no custom-property definition or saved-view contract was found in frontend/backend source. These are new capabilities, not UI-only rearrangement.
- Accepted historical context: `docs/ux/2026-09-07-project-management-redesign-plan.md` and `tmp/reassessment-2026-09-21-original-sources.txt`. Earlier r1/r2 model reviews are historical only; the user's resolved r3 model supersedes their A/B/C decision.
- Fixed server-authoritative permissions, lifecycle, acknowledgement, and one-way Calendar projection remain unchanged. Work is `offline-contract-only`; no live browser or Google facts are required.

## Workflow and screen structure

### One dataset, peer views

- A schedule has one stable record ID and one project. Editing title, time, participants, lifecycle, or custom property in Calendar, Card, List, form, or detail updates that record; every authorized view reflects the saved revision.
- Project Schedules opens a saved-view bar. `Calendar`, `Cards`, and `List` are view types, not separate stores. Each saved view records type, name, filters, grouping, sorting, visible fields, and type-specific settings.
- Calendar answers “when”: the initial view maps the required fixed schedule start/end to the grid. Custom Date mapping is deferred; when introduced, records missing the chosen source must appear in a labeled `No date` tray/count and remain editable rather than disappear.
- Cards answer “what belongs together”: cards show configured fields and may group by one single-select property. Cards are editable record summaries, not mandatory tasks or workflow columns.
- List answers “find and compare”: rows expose configured fields and server-backed filtering/sorting across the authorized result set.
- On a project dashboard, an **editable saved-view card** embeds records from one named view and opens that view for full editing. An **aggregate metric widget** shows a computed count/status only and never looks editable; each is labeled by kind and source view.
- View context (project, saved-view ID, date/selection, filters, group, sort, and list position) is restorable through detail, edit, save, cancel, browser back, and project switching where applicable.

### Custom properties and legend

- Custom properties are separate from fixed fields. They cannot replace or redefine project access, schedule lifecycle, acknowledgement, Calendar sync/projection metadata, record ID, creator, or row/business revision.
- Bounded initial types: Text, Number, Checkbox, Date, and Single select. Formula, relation, rollup, person, file, multi-select, dependencies, automation, and cross-project properties are deferred.
- Property definitions have stable IDs, labels, types, and project scope. Single-select options have stable option IDs, labels, order, and colors. Values store IDs, so renaming a property or option updates presentation without changing records, filters, or grouping.
- A view may choose one single-select property as its legend/grouping. Color supplements the option label, count, and accessible text; no meaning relies on color alone.
- Example: `Category` options `Design`, `Development`, `Operations`; `Priority` options `High`, `Medium`, `Low`. Saved view `Launch Calendar` maps schedule start/end, filters active records, colors/labels by Category, and shows Priority. `Operations Cards` filters Category=Operations, groups by Priority, and shows a fixed participant summary plus dates.

### Create, edit, and safe schema change

- Creating from a Calendar date/slot prefills fixed start/end. Creating inside a Card group defaults the grouping option. Equality filters may supply defaults only when writable and nonconflicting.
- If grouping/filter defaults conflict, the create form names the conflict and does not silently invent values. If a saved record no longer matches the current view, feedback says `Created, but hidden by this view` and offers `Show record` and `Clear relevant filters`.
- Rename preserves stable IDs. Hide is reversible and preserves values. Removing an option first shows affected-record/view counts; the safe default archives it, keeps existing values readable, and prevents new selection. Replacement or value clearing requires an explicit mapped action.
- Removing a property first shows affected records/views and offers Hide/Archive. Initial scope has no hard delete; permanent deletion and bulk migration are deferred.
- Recommended governance proposal: MANAGER manages project-shared property definitions, options, and shared views; existing schedule editors may set values and create personal saved views; VIEWER reads authorized records/views. Personal preferences (last view, density, field widths) and personal views do not alter shared schema. This governance needs an explicit implementation contract; it does not change current rights by itself.

### Bounded initial increment

- Initial: project-scoped properties of the five types, stable select options, personal and shared saved Calendar/Card/List views, filters/group/sort/visible fields, canonical start/end Calendar mapping, context-preserving navigation, and dashboard embedding of one saved view or metric.
- Deferred: custom Date Calendar mapping and its `No date` tray, nullable canonical schedule dates, cross-project aggregate views, advanced property types, automation, formulas, board-specific workflow, dependencies, bulk migration/hard delete, and Google-driven editing. These date behaviors are explicit new contracts because current schedules require start/end; any cross-project view requires a complete authorized aggregate read model.

## State and accessibility coverage

- Loading/partial/error: each view keeps its name/configuration visible, states whether records are complete, and never presents the first 20 as the full result. Retry preserves view context.
- Empty states distinguish empty project, empty saved view, and no matching filters; authorized users receive a context-aware create action. The deferred custom-date mode adds a distinct `No date` state.
- Permission loss removes schema/value/edit controls after refresh, preserves safe read context, and never leaks hidden project records or property values.
- Concurrent record changes use existing conflict recovery. Concurrent view/schema edits show the latest definition and require an explicit retry; no silent overwrite.
- Keyboard order covers saved views, filter/group/sort, record create/open, field edit, `No date`, and recovery. Calendar direct manipulation remains optional; dialog/form editing provides equivalent keyboard/touch behavior and returns focus.
- Every property control has a semantic label, selected options are text-readable, validation is field-associated, success/error is announced, and mobile Card/List/agenda retain the same records and actions without horizontal-grid dependence.

## Decisions and acceptance criteria

- Editing record R in any peer view changes the same revision; Calendar, Cards, List, detail, and embedded editable view show the update after refresh without duplicate records.
- All view types enforce identical record/value permissions and either load the complete authorized scope or disclose incomplete/error state.
- Canonical start/end mapping, group-create defaults, conflicting filters, and post-create hidden records behave as specified and are keyboard operable; a later custom-date contract must add the specified `No date` behavior.
- Renaming property/option preserves filters, groups, legends, and stored values by stable ID; archive/hide never erases existing values.
- Fixed lifecycle, permissions, acknowledgement, and sync metadata cannot be converted into or overridden by custom properties.
- Editable saved-view cards and aggregate widgets are distinguishable by label, available actions, and focus behavior.
- Static checks completed by designer: template coverage, 75 lines before parent review record, assigned-file-only changes, and whitespace check. Checks not run: browser/runtime, viewport/assistive technology, API/app tests, live Google/OAuth, or implementation verification.

## Independent review and parent acceptance

- `/root/project_calendar_ux_review`, native `ai-erp-reviewer` (Astra/high), directly read r3 / contract r2 and returned `PASS` on 2026-09-21; no blocking defects. r1/r2 verdicts were not reused as r3 evidence.
- `/root` accepts r3 as the functional concept matching the clarified user direction. Recommended governance and increment boundaries remain proposals; this verdict is not implementation or runtime verification.

## Archive

- Local source: `docs/ux/project-calendar-operating-ux-2026-09-21.md`
- Notion archive URL / synchronized date: parent-owned; not synchronized by this specialist.
