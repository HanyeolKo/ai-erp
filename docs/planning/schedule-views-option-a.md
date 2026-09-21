# Schedule Views Delivery — Option A: Schema-first reusable model

## Contract

- Task/increment: `schedule-views-delivery`; candidate plan r1; assignment contract r1; owner `/root`.
- Planner: native `ai-erp-product-planner`, Sol/medium; chosen for bounded cross-layer planning. Provider token/cache usage is unavailable (`null`); no escalation or fork/reuse.
- Context: assignment, accepted UX r3, `ScheduleController`, `ScheduleService`, V2/V4/V5/V6 migrations, `ProjectAccess`, `ScheduleRepository`, and generated/frontend API types; selective input stayed within the assigned 12k-token target.
- Mode: `offline-contract-only`; existing Google integration is preserved. Live GitHub/deployment readiness is parent-owned and unknown, so this plan claims no release readiness.

## Outcome and priorities

- Keep one project-owned schedule row as the record of truth; Calendar, Cards, List, and dashboard embeddings query and edit that record rather than copying it.
- Prioritize permission/data integrity, deterministic complete queries, stable property/view references, reversible rollout, then configuration breadth.
- Initial property types are `TEXT`, `NUMBER`, `CHECKBOX`, `DATE`, and `SINGLE_SELECT`; fixed lifecycle, acknowledgement, identity, revisions, participants, and projection metadata are not properties.
- Initial saved-view types are `CALENDAR`, `CARDS`, and `LIST`, with filters, one optional group/legend property, ordered sorts, and displayed fields. Calendar uses canonical `startsAt`/`endsAt` only.
- Shared dashboard items reference a shared saved view as either `EDITABLE_VIEW` or `METRIC`; they never persist schedule copies or provider data.

## Data contract

- Add project-scoped `property_definition(id, project_id, type, label, position, state, schema_version, row_version)` and `property_option(id, property_id, label, color_token, position, state, row_version)` tables; IDs survive rename/reorder and `state=ARCHIVED` preserves references and values.
- Add `schedule_property_value(project_id, schedule_id, property_id, text_value, number_value, boolean_value, date_value, option_id, row_version)` with one row per schedule/property, typed-slot checks, composite project ownership FKs, and an option-to-property FK; absence means unset.
- Add `saved_schedule_view(id, project_id, owner_user_id, scope, type, name, group_property_id, legend_property_id, row_version)` plus normalized `view_filter`, `view_sort`, and `view_field` children. Each field source is exactly one allow-listed fixed field or project property ID; operands use typed columns/options.
- Add `project_dashboard_item(id, project_id, saved_view_id, kind, position, row_version)`; only `scope=SHARED` views may be referenced. Database constraints and service validation reject cross-project references.
- Index values by `(project_id, property_id, typed_value, schedule_id)`, options by `(property_id,state,position)`, shared/personal views by project and owner, and always terminate result ordering with schedule ID.
- Initial safety caps are 50 active properties/project, 100 active options/select property, 10 filters, 3 sorts, 30 displayed fields/view, and 100 shared plus 100 personal views/project/user; caps are API-visible validation, not silent truncation.

## API and query contract

- Add project property-definition/option collection and item APIs; list is available to every project reader, while create/rename/reorder/archive and option replacement require `MANAGER`.
- Extend schedule create/patch responses with a property-value map keyed by stable property ID. Writes accept explicit value upserts/unsets plus expected schedule `rowVersion` and remain one transaction with fixed-field edits.
- A value-only write locks/touches the schedule so `rowVersion` advances, but does not change `businessRevision`, acknowledgement eligibility, event payload for Calendar, or projection work; lifecycle rules and existing creator-based write permission still apply.
- Add saved-view collection/item APIs. Any project member reads shared views; owners read/write their personal views; `MANAGER` writes shared views and dashboard items. VIEWER cannot change records or shared configuration.
- Add `GET .../schedule-views/{viewId}/records?cursor&limit&from&to`; the server validates stored configuration, applies project/record permissions, filters/groups/sorts, and returns values, group counts, `nextCursor`, and `resultComplete`.
- Cursor keys include all configured sort values plus schedule ID. No client may label a partial page as a full result; aggregate metrics and group counts execute across the complete authorized match set.
- All write APIs use CSRF/session protection and optimistic versions; stale schedule, property, option, view, or dashboard writes return the existing conflict shape with the latest safe representation.

## Cross-layer effects and delivery

- Backend adds schema ownership validation, typed query compilation, cursor encoding, quotas, conflict handling, definition-impact counts, and audit/change types for property/view/schema changes. Query construction must use bound parameters and an allow-list, never persisted SQL fragments.
- Frontend generated types/client gain definitions, values, saved views, query metadata, and dashboard items; later UI contracts decide presentation and interactions. Cached records are keyed by project plus schedule ID and invalidated across all peer views after writes.
- Google export continues to read only fixed schedule fields and `businessRevision`; property/view/dashboard changes neither alter sync direction nor create provider calls.
- Expand migration creates new tables/indexes without rewriting schedule rows, then idempotently seeds Calendar, Cards, and List shared defaults for every existing project; new-project creation seeds the same defaults transactionally.
- Roll out schema, compatible backend, default backfill verification, then reviewed frontend/dashboard behavior. Keep the legacy schedule list endpoint during the transition; rollback points clients to it and leaves additive tables dormant.
- Before release, verify migration on a production-shaped copy, query plans at cap-sized cardinality, seed counts/uniqueness, permission isolation, optimistic conflicts, projection non-regression, and backup/restore. Release-manager owns commit/CI/environment/recovery evidence and observation.

## Observable acceptance

- Every existing schedule appears once in each seeded peer view; editing fixed fields or a property through any consumer returns the same schedule ID and next `rowVersion`, and all consumers show it after refresh.
- Rename/reorder preserves values, filters, groups, legends, and fields by ID; archive preserves readable historical values, blocks new selection, and reports affected record/view counts before a mapped replacement or clear.
- MANAGER, MEMBER-owner, MEMBER-nonowner, VIEWER, personal-view owner, and nonowner cases match the stated matrix; cross-project IDs return no data and cannot mutate state.
- Queries spanning more than 20 records page without duplicates/omissions under stable data, expose incompleteness while a cursor remains, and compute full-scope group counts/metrics.
- Value-only edits increment `rowVersion` but leave `businessRevision`, acknowledgements, and Calendar projection rows/events unchanged; fixed/lifecycle edits retain current behavior.
- Migration preserves all pre-existing schedule/participant/change/acknowledgement/projection rows, creates exactly three defaults per project, and supports application rollback without destructive down-migration.

## Cost, deferrals, and handoff

- Cost is higher than JSON-only configuration: roughly eight additive tables, typed query/compiler work, cursor/aggregation tests, migration/backfill, and generated-client expansion. It buys referential integrity, impact analysis, reusable definitions, and indexable queries.
- Defer formulas, relations, rollups, people/files, multi-select, automation, dependencies, hard delete/bulk conversion, cross-project views, nullable canonical times, custom-Date Calendar mapping, and Google-driven edits.
- Main risk is dynamic-query performance and lock contention on record revision; enforce caps, inspect query plans, cap page size, monitor latency/conflicts, and abort frontend activation if correctness or performance gates fail.
- Required next step: parent sends this r1 candidate for independent Astra/high comparison and increment review. Any accepted plan requiring screens then routes to UI/UX and visual specialists before implementation; this plan makes no screen decisions or verdict.
- Notion archive synchronization is parent-owned after candidate selection; local file remains canonical.
