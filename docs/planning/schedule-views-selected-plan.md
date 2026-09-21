# Schedule Views Delivery — Selected Hybrid Plan

- Increment id and plan revision: `schedule-views-delivery` / selected plan r1.
- Task assignment and contract revision: `docs/planning/schedule-views-delivery-assignment.md` / contract r2.
- Planner: native `ai-erp-product-planner`, Sol/medium; parent-authorized consolidation, no escalation.
- Source/base: `main` at `49d79fe553bb4048a72671b503609e2d2c38ce58`; accepted UX concept r3 and candidate plans A/B/C r1.
- Candidate provenance: A supplied normalized typed values and stable schema integrity; B supplied JSON saved-view configuration and operational defaults; C supplied additive compatibility, virtual defaults, simple complete-scope paging, and projection isolation.
- Selection status: parent proposal awaiting independent Astra/high `increment-plan-review`; this document records no verdict.
- Context: reused candidate evidence and cross-review only; <=12k-token assignment budget, concise <=90-line output, minimal fork, provider usage/cache unavailable (`null`).

## Problem, users, value, and priorities

- Projects need one schedule dataset that users can manage through persisted Calendar, Cards, and List views without copying records or weakening existing rights.
- Managers need safe shared schema/views, schedule writers need editable records and personal views, and VIEWER users need consistent read-only results.
- Priority order is data/permission consistency, complete server-side query truth, backward compatibility, reversible delivery, then extensibility.
- Fixed lifecycle, participants, acknowledgements, canonical start/end, creator ownership, `businessRevision`, and one-way Google projection remain authoritative.

## Current release scope and business rules

- Deliver five property types: `TEXT`, `NUMBER`, `CHECKBOX`, `DATE`, and `SINGLE_SELECT`; property and option IDs remain stable across rename/reorder.
- Archive is reversible, preserves stored values and view references, and blocks new use; hard delete and type conversion are excluded.
- Persist personal and shared saved views of type `CALENDAR`, `CARDS`, or `LIST` with filters, one optional group/legend property, ordered sorts, and displayed fields.
- Calendar uses canonical schedule start/end. Cards and List edit the same schedule IDs under the same record permissions.
- Provide virtual default Calendar, Cards, and List views for every project; persist a view only when an authorized user saves or customizes it. No migration backfill of default view rows is required.
- Store no separate personal-context table in this release; unsaved last-view, position, density, and width preferences are not durable product data.
- Dashboard configuration contains one nullable reference to a shared saved view. It exposes that view's authorized editable records and does not introduce a widget/layout engine.
- Deferred: custom Calendar date mapping, formulas, relations, rollups, people/files, multi-select, automation, dependencies, cross-project views, bulk conversion, hard delete, Google-driven editing, and dashboard widgets.

## Data and API requirements

- Add normalized project property-definition and single-select option tables with state, ordering, and optimistic row versions.
- Add normalized typed schedule-value rows keyed by project, schedule, and property; exactly one type-compatible value is populated and select values reference an option owned by that property.
- Persist each saved view as identity/governance columns plus one validated JSONB configuration document; referenced fixed fields, property IDs, option IDs, operators, and sort directions are allow-listed.
- Persist one project dashboard schedule configuration row with a nullable shared-view ID; reject cross-project, personal, archived, or unauthorized references.
- Keep existing schedule CRUD request and behavior compatible. Add a project/schedule custom-values endpoint that requires the expected schedule `rowVersion`, applies upserts/unsets atomically, and advances that same schedule row version.
- A custom-value-only write does not change `businessRevision`, acknowledgement state, or Calendar export fields and emits no existing schedule event.
- Add definition/option, saved-view, dashboard-selection, and saved-view-query resources with bounded limits and optimistic conflict responses.
- Saved-view queries authorize first, validate configuration, execute all filters/grouping/sorts and total/group counts in backend SQL before paging, and terminate deterministic order with schedule ID.
- Use bounded offset/page results with `total`, group counts, and `hasMore`; concurrent changes use explicit refresh semantics. The current 20-row client page is never treated as complete.

## Permissions, audit, and cross-layer effects

- Every project member may read active definitions, authorized values, virtual defaults, and shared views; no query, count, group, or dashboard result may disclose unauthorized records.
- `MANAGER` manages definitions/options, shared views, and dashboard selection. Existing schedule writers manage personal views and values only where current `requireWriter(project,user,createdBy)` permits the record. `VIEWER` remains read-only.
- Personal views are private and owner-writable; shared views are project-readable and manager-writable.
- Property, option, custom-value, view, and dashboard changes write direct audit evidence with `PROPERTY_*`, `CUSTOM_VALUE_*`, `VIEW_*`, or `DASHBOARD_VIEW_*` actions; no action/event uses the `SCHEDULE` prefix.
- Backend owns typed validation, cross-project integrity, SQL query compilation, paging/count truth, optimistic locking, audit, and Calendar isolation.
- Frontend consumes generated contracts and invalidates the same schedule across all peer queries after writes. Screen structure, interaction, accessibility, and visual treatment remain undecided.

## Migration, dependencies, and release impact

- Use expand-only tables, constraints, and indexes; rewrite no existing schedule row and seed no default views. Deploy schema, compatible backend, then reviewed frontend activation.
- Application rollback disables new routes/UI while leaving additive data dormant; no destructive down migration is required.
- External dependency mode is `offline-contract-only`; existing Google Calendar projection target/contract/settings remain unchanged, owner is parent/release manager, evidence date 2026-09-21.
- Safe external check: regression with the fake adapter; expected result is zero provider work for property, custom-value, view, or dashboard-only changes. Live GitHub/deployment readiness remains `unknown` and cannot be called ready.
- High-risk release requires PostgreSQL migration and query-plan integration checks, permission/inference matrix, stale/concurrent revision checks, legacy CRUD regression, Calendar event/projection isolation regression, independent Astra/high task and release reviews, recovery evidence, and observation.

## Observable acceptance

1. Existing schedule CRUD clients remain compatible and all pre-existing schedule, participant, acknowledgement, change, and projection data remains intact.
2. An authorized user can save/reopen personal or shared Calendar, Cards, and List views with the five property types, stable options, legends, filters, group, sorts, and displayed fields.
3. Every peer view and the selected dashboard view returns and edits the same schedule ID; custom-value writes require and advance schedule `rowVersion` while leaving `businessRevision`, acknowledgements, schedule events, and Google projection unchanged.
4. Results beyond 20 records are filtered, grouped, counted, sorted, and paged by backend SQL over the complete authorized match set, with deterministic ID tie-breaking and truthful `total`/`hasMore`.
5. Rename/reorder/archive preserves values and saved-view references; cross-project references and role-escalating reads/writes fail without data disclosure.
6. Virtual defaults require no persisted backfill; saving a customization creates one governed saved view, and dashboard selection accepts only one valid shared view or null.

- Screen decisions: deferred to `ui-ux-designer` after an accepted increment review, then visual planning and independent `ui-plan-review` before UI implementation.
- Downstream invalidation: any selected-plan revision change makes affected UI, implementation, test, task-review, and release evidence stale.
- Reviews, feedback, and parent decision: selected plan r1 is ready for `/root/views_increment_review`; parent retains acceptance and implementation-contract authority.
- Release ownership: release-manager prepares or executes only after accepted implementation evidence and parent assignment; this plan does not claim release readiness.
