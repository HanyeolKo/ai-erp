# Schedule Views Delivery — Candidate B

- Increment / revision: `schedule-views-delivery` / candidate B plan r1.
- Assignment / contract: `schedule-views-delivery` candidate assignment r1 / contract r1; parent `/root`.
- Planning runtime: native `ai-erp-product-planner`, Sol/medium; selected by assignment for a bounded product plan. Minimal fork, no reuse or escalation; provider usage/cache availability is unavailable (`null`).
- Source/base: Git `49d79fe553bb4048a72671b503609e2d2c38ce58`; accepted `docs/ux/project-calendar-operating-ux-2026-09-21.md` r3; current schedule controller/service/entity, project access rules, and migrations V2/V4/V5.

## Problem, users, and value

Project members need one operational schedule dataset that can answer when work occurs, what belongs together, and what needs comparison without copying records between tools. Today the bounded Calendar/List fetch and fixed schema cannot persist reusable classifications or complete filtered views. Managers need safe shared configuration; contributors need fast record updates and personal views; viewers need consistent read-only access.

## Product strategy and priorities

Deliver the smallest coherent vertical slice: five bounded custom-property types, persistent saved views, three peer view types, and dashboard reuse of saved views. Usability comes from three project templates (`Calendar`, `Cards`, `List`) created on first use and editable through duplication, so a project is useful before configuration but every resulting view follows one contract.

Priorities are: (1) one record/revision across all views, (2) complete permission-filtered results, (3) durable user choices, (4) reversible schema changes, (5) bounded query and migration cost. Fixed lifecycle, acknowledgement, participants, canonical start/end, optimistic locking, and one-way Google projection remain authoritative.

## Current slice and business rules

- Property definitions are project-scoped with stable IDs, label, type (`TEXT`, `NUMBER`, `CHECKBOX`, `DATE`, `SINGLE_SELECT`), position, state, and revision. A single-select definition owns ordered options with stable IDs, label, color token, position, and active/archived state.
- Property values belong to an existing schedule and reference definition/option IDs. A value mutation participates in that schedule's optimistic row revision but does not change lifecycle/business revision or trigger Google projection. Rename and reorder preserve values and view rules. Archive is reversible, keeps historical values readable, and prevents new selection; hard delete, type conversion, formulas, relations, files, people, multi-select, bulk migration, and cross-project properties are deferred.
- A saved view is project-scoped and persists owner scope (`PERSONAL` or `SHARED`), name, type (`CALENDAR`, `CARDS`, `LIST`), filters, ordered sorts, optional single-select group/legend, displayed fields, canonical date mapping, view revision, and owner. Filters use typed operators validated against the referenced property.
- Calendar, Cards, and List query and mutate the same schedule IDs. Cards are editable summaries and may group by one single-select property; List supports server filtering/sorting; Calendar always uses required canonical start/end in this slice. Group/legend labels and counts accompany color.
- The three templates are versioned application defaults, not separate persisted datasets. First use materializes project-owned saved views from the current template version; later template changes never overwrite customized views. An authorized user may duplicate a template or saved view.
- A project dashboard persists an ordered set of references to saved views plus optional count metrics. A saved-view reference exposes the same authorized records and record edits; a metric is read-only and derived from the referenced view query. Missing, archived, or newly unauthorized references return an explicit unavailable state rather than stale data.
- Personal-state persistence stores the last saved-view ID and supported view context per user/project. Saved view definitions hold filters, group, legend, sort, and fields; transient unsaved changes are not shared.

## API, data, permissions, and audit

- Add project property-definition/option CRUD and archive endpoints; schedule property-value read/write contracts; saved-view CRUD/duplicate/query endpoints; project template initialization; dashboard configuration/read; and a shared query grammar with cursor pagination, total/complete metadata, and deterministic ID tie-breaks.
- View queries are server-evaluated across the complete authorized project dataset. They return definition/view revisions and schedule row versions; stale schema, view, or record writes fail with conflict responses and current revision evidence. Existing schedule endpoints remain compatible while responses gain additive property values where requested.
- Persist normalized definition, option, typed value, saved-view, personal-context, and dashboard-reference tables. Saved-view filter/sort/display configuration may be JSONB but every referenced field/property/option ID is validated on write and query. Add project/property/value and query-support indexes after representative query plans are verified.
- All project members may read active definitions, authorized schedule values, templates, and shared views. `MANAGER` manages definitions/options, shared views, template materialization, and dashboard references. `MEMBER` may manage personal views and set values only when existing schedule writer rules allow that record; `VIEWER` is read-only. Personal views/context are visible and mutable only by their owner. Server authorization applies before filtering, counts, grouping, and metrics.
- Property/view/dashboard mutations write actor, project, aggregate ID, action, revision, and timestamp to the existing audit facility. Custom values cannot override lifecycle, access, acknowledgement, participant, Google projection, record ID, creator, row version, or business revision.

## Cross-layer, migration, and release requirements

- Backend adds repositories/services for definitions, values, views, dashboard references, validation, authorization, complete queries, optimistic concurrency, and audit events. Frontend consumes one saved-view/query contract and existing schedule mutations; detailed screen structure, interaction, accessibility, and visual decisions wait for UI specialists.
- Use expand-only migrations: create nullable-independent tables and indexes, deploy compatible reads/writes, then lazily materialize templates per project. Existing schedule rows require no rewrite and initially have no custom values. Rollback disables new routes/UI while retaining additive tables; no destructive down migration or Google data change.
- Release requires representative migration rehearsal, query-plan/load evidence for filtered/grouped cursor reads, authorization/inference checks, concurrent revision checks, and existing schedule lifecycle/acknowledgement/Calendar projection regression coverage. High-risk implementation requires relevant integration checks plus independent Astra/high task and release reviews.

## Dependencies, assumptions, cost, and tradeoffs

- External readiness: `not-required` for current planning and local implementation; mode `offline-contract-only`; provider/service `Google Calendar projection (existing)`; target `existing configured project integrations`; contract/version and managed settings unchanged; owner parent/release operator; evidence date 2026-09-21 from accepted r3 and source; safe check is regression verification with no provider call; expected result is unchanged projection payload/direction. Live GitHub/deployment readiness remains `unknown` and parent-owned, so it cannot be called release-ready.
- Assumptions resolved for this slice: schedule start/end stay required; existing project roles and writer rules remain; personal views are private; only managers publish shared configuration; custom property changes do not increment schedule business revision unless product implementation explicitly proves participant acknowledgement should change.
- Cost judgment: medium-high backend/data work and medium frontend work. Reusing one query grammar and materialized templates avoids separate Calendar/Card/List stores and a template editor. Normalized typed values cost more than opaque JSON but preserve validation, option identity, filtering, indexing, and reversible rename/archive behavior.
- Deferred: custom-date Calendar mapping and `No date`, advanced properties, automation, formulas, relations, board workflow, cross-project views, hard deletion, bulk conversion, Google-driven editing, and free-form dashboard layout.

## Observable acceptance

1. Editing schedule R or an allowed property value through any peer view changes the same row-versioned record; Calendar, Cards, List, detail, and dashboard saved-view references show R once after refresh, while lifecycle and Google projection behavior remain unchanged.
2. A manager can define each bounded property type and single-select options; users can persist permitted values; property/option rename preserves records, filters, grouping, legends, and colors by stable ID, and archive preserves readable history while blocking new use.
3. An authorized user can create, duplicate, reopen, and query personal or shared Calendar/Cards/List views with persisted filters, group/legend, sorts, and displayed fields; personal configuration is isolated and shared configuration obeys manager governance.
4. Calendar, Cards, List, dashboard references, counts, filters, groups, and metrics operate on the complete authorized result set through deterministic server pagination or explicitly report incomplete/error state; no first-20 result is presented as complete.
5. New and existing projects obtain versioned default Calendar/Cards/List views without copying schedules. Dashboard saved-view references remain editable only under existing record permissions, while metrics remain derived/read-only and inaccessible data is neither counted nor disclosed.
6. Expand-only migration preserves every existing schedule and requires no backfill; disabling the feature leaves legacy schedule APIs and data usable. Permission loss, stale record/view/schema revisions, archived dependencies, and concurrent edits fail safely with no silent overwrite or data erasure.

- Screen decisions: deferred to `ui-ux-designer` after independent `increment-plan-review`; visual planning and `ui-plan-review` precede UI implementation.
- Downstream invalidation: any change to candidate B plan r1 makes derived screen, implementation, test, migration, comparison, and release contracts/reviews stale where affected.
- Review/decision: ready for parent-dispatched independent `increment-plan-review`; no verdict or implementation approval is claimed here.
