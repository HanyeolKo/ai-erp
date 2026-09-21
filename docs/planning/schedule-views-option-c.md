# Schedule Views Candidate C — Compatible Delivery

- Increment / plan revision: `schedule-views-delivery` / candidate C r1.
- Assignment / contract revision: `docs/planning/schedule-views-delivery-assignment.md` / r1; acknowledged to parent before writing.
- Planner: native `ai-erp-product-planner`, Sol/medium; selected by assignment for bounded product planning.
- Context: assignment, accepted UX r3, current schedule controller/service/entity/repository, V2/V4/V5 migrations, frontend API and schedule screen, `ProjectAccess`, `ScheduleLookup`, and Calendar event consumer; no A/B candidate content read.
- Context budget: selective <=12k-token target; concise output <=100 lines; minimal fork, no reuse; provider usage/cache unavailable (`null`), no escalation.
- Source/base: `main` at `49d79fe553bb4048a72671b503609e2d2c38ce58`; planning files are untracked and preserved.

## Problem, value, and delivery choice

- Users need Calendar, Cards, and List as persisted peer views over one project schedule dataset, including typed custom properties, legends, filters, grouping, sorting, displayed fields, and editable dashboard placements.
- Current reads return at most 20 rows and apply most filters in the browser; this cannot truthfully represent a saved view or its counts.
- Choose an additive compatibility layer: retain `schedule.project_schedule` and existing endpoints, lifecycle, IDs, permissions, acknowledgements, `rowVersion`, `businessRevision`, and Calendar snapshot unchanged.
- Add normalized project property definitions/options and saved-view/dashboard-placement records, plus one typed JSONB custom-value document per schedule keyed by stable property UUID.
- This is the smallest reversible migration: existing rows need no destructive conversion, legacy clients omit the new fields, and rollback can stop new routes while preserving additive tables.

## Current slice and business rules

- Initial property types are Text, Number, Checkbox, Date, and Single select; stable option IDs carry label, order, color, and active/archived state.
- Renames preserve IDs; hide/archive preserves values and saved-view references. Hard delete, bulk replacement, formulas, relations, files, people, multi-select, automation, dependencies, cross-project views, and custom Calendar date mapping are deferred.
- Persist personal and shared saved views of type `CALENDAR`, `CARDS`, or `LIST`; configuration stores validated filters, optional single-select group/legend, ordered sorts, displayed fields, canonical start/end Calendar mapping, and type settings.
- Persist dashboard placements referencing one saved view and a kind (`EDITABLE_VIEW` or `METRIC`); editing a record from a placement uses the same schedule write contract.
- Property/view definitions have independent `metadataRevision`; each schedule value document has `customValueRevision`. Neither changes schedule `businessRevision` nor Google projection state.
- Fixed schedule or participant changes retain existing revision/event behavior. Custom-only or metadata-only writes use audit records outside the schedule-prefixed event path; `CalendarEventConsumer` must not receive them.

## Data and query contract

- Normalize definitions/options for referential stability and governance; store values as a versioned JSONB map with type-tagged scalars and option UUIDs, validated transactionally against the current project definition.
- Compared fairly, normalized value rows provide stronger database type constraints and simpler per-property indexes, but require several sparse typed columns, more joins, and more migration/repository surface for five initial types.
- JSONB reduces write and migration surface and makes atomic value replacement simple; its cost is query complexity and weaker database-native typing. Mitigate with strict service validation, stable encodings, a GIN index, bounded project/date predicates, query-plan integration tests, and an explicit threshold for later normalized-value migration.
- A saved-view query compiles only allow-listed operators and sort keys from stored definitions, applies authorization and all filters in SQL before pagination, and returns records, total/group counts, deterministic order, offset/page, and `hasMore`.
- Concurrent writes may shift offset pages; responses disclose a result revision/time and refresh semantics. The existing 20-row page is never used as the full dataset or as the source of counts/groups.

## API, permissions, and cross-layer impact

- Preserve existing `/api/v1/projects/{projectId}/schedules` contracts; add optional custom values/revision to create, patch, detail, and response without requiring legacy clients to send them.
- Add project-scoped property-definition, saved-view, dashboard-placement, and saved-view-record query resources with optimistic revision checks and bounded limits.
- Every record/value read requires existing project access. Custom-value writes call the same schedule `requireWriter(project, user, createdBy)` rule; Card/List/Calendar and dashboard placement never broaden rights.
- Managers manage definitions/options/shared views/placements. Existing schedule writers may create personal views; personal resources are owner-writable. Viewers remain read-only.
- Backend owns validation, query compilation, pagination/count truth, audit, and projection isolation. Frontend consumes server results and generated contracts; screen structure and interactions remain for `ui-ux-designer`.

## Migration, release, and acceptance

- Use expand-only tables/indexes and optional response fields; seed no copied schedule data. Deploy database, compatible backend, then frontend; retain old routes during observation.
- High risk requires PostgreSQL migration/query integration checks, authorization matrix checks, legacy API regression, concurrent revision checks, Calendar projection isolation regression, independent Astra/high task review, release review, recovery evidence, and observation before completion.
- External mode is `offline-contract-only`; provider/service: existing Google Calendar projection, target current project binding, contract unchanged, managed settings unchanged, owner parent/release manager, evidence date 2026-09-21, safe check regression with fake adapter, expected result zero provider work for metadata/custom-only edits, status unknown for live release and therefore not release-ready.
- Acceptance: existing schedules remain readable/editable with unchanged IDs, lifecycle, rights, acknowledgements, and Google behavior after migration and rollback of application code.
- Acceptance: all three saved view types and editable dashboard placements persist, reopen, and operate on the same record/custom values; fixed and custom edits appear across peer views without duplication.
- Acceptance: five property types, stable options, legends, filters, grouping, multi-key sorting, displayed fields, totals/group counts, and pagination are server-evaluated over the complete authorized result scope.
- Acceptance: rename/archive preserves values and view references; unauthorized cross-project or role-escalating reads/writes fail without disclosing records or values.
- Acceptance: metadata/custom-only changes advance only their own revision/audit evidence and enqueue no Calendar projection; exported-field changes retain existing `businessRevision` behavior.

- Tradeoff: candidate C optimizes compatible delivery and reversibility; JSONB query complexity and eventual scale migration are accepted, measured risks rather than hidden client filtering.
- Screen decisions: deferred to `ui-ux-designer` after accepted `increment-plan-review`, followed by visual planning and independent `ui-plan-review`.
- Revision changes invalidate dependent UI, implementation, test, task-review, and release evidence. Parent owns comparison, review dispatch, implementation contract, Notion archive, and release decision.
