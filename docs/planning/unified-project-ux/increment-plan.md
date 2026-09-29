# Unified Project UX Increment Plan

- Increment / plan revision: unified-project-ux / r1.
- Task / assignment / contract: consolidate-pr-17-18-22 / assignment r1 / implementation contract pending accepted planning and UI reviews.
- Parent and status: root; assignment acknowledged; this plan is ready for independent increment-plan-review and records no verdict.
- Planner: native ai-erp-product-planner, gpt-5.6-sol / medium; canonical role selection, no escalation, provider usage unavailable/null.
- Git evidence: integration base origin/main 8991a0947d41580454da5fbcf23dcd247c852b45; planning HEAD ab76715 on codex/unified-project-ux (docs only); PR17 2cd0b710; PR18 8b601452; PR22 c8c7f3f8. Prior PR18 backend review records backend PASS at 78821a7; frontend final review records eight unresolved findings.
- Context: assignment, harness role/skill/template/rubric/protocol, source diffs, selected PR17/18/22 documents/reviews, and base migration/project-plan evidence; bounded input <=30k tokens, output <=100 lines / <=4k tokens; minimal native dispatch, no fork/reuse.

## Problem, value, and rules

- Project members need one coherent schedule dataset editable through Calendar, Cards, List, and a configured dashboard, with user-managed typed properties, label-plus-color classifications, and saved views.
- Managers govern shared definitions/views; eligible MEMBER writers own personal views and existing record rights; VIEWER users receive complete read-only results without private-view or protected-record disclosure.
- Preserve the independent project-plan domain and its Roadmap, Hierarchy, Tasks, Board, Milestones, and Monthly views. Schedule Calendar is a peer view, not a new data owner.
- Preserve canonical schedule identity/lifecycle, start/end, participants, acknowledgements, creator rights, businessRevision, audit, concurrency, and one-way Google projection.
- Priority: deployed data/behavior preservation, authorization and concurrency, complete query truth, known PR18 defect repair, notification continuity, then releaseability.

## Source disposition

| Source | Adopted, replaced, or deferred |
| --- | --- |
| Base 8991a09 / PR19-21 | Adopt as baseline; retain six plan views, schedule date/filter/navigation, drag/resize/time-edit/recovery, authorization, Google integrations, and user changes. |
| PR17 popup | Adopt popup plus direct-route compatibility, paging, safe links, read state, session guards, and focus recovery. |
| PR17 calendar and stale branch delta | Replace with newer 8991a09 navigation and shared code. Reject stale deletions/reversions of plan, schedule, client, tests, and deployment files. |
| PR18 backend/API/data | Adopt selectively with API r2 invariants and source-proven SV-BE-001..012 repairs; reconfirm on the integrated head. |
| PR18 frontend | Adopt only with SV-FE-001, 003, 006, 007, 009, 010, 011, and 013 repaired; retain already-closed findings and base behavior. |
| PR18 V9 | Replace V9__add_schedule_workspace.sql with the next additive migration after deployed V9__create_project_plan.sql, expected V10. Never edit, rename, checksum-replace, or replay deployed V9. |
| PR18 evidence | Preserve exact-SHA attribution: backend evidence is reusable only for unchanged behavior; frontend FAIL and intermediate failures remain visible until superseded. |
| PR22 four local docs/ux/2026-09-28-* documents | Adopt as canonical planning/history documents. Implement only SCHEDULE_CHANGED label alignment needed for popup correctness. Defer richer notification content/history/filtering, plan density/context, repeated entry, milestone redesign, and broad end-to-end follow-up. Document import is not feature delivery. |

## Current and deferred scope

- Integrate PR17 popup and PR18 workspace onto 8991a09, correct the additive migration, repair known PR18 defects, align the notification change label, and preserve PR22 documents with implemented/deferred markers.
- Deliver TEXT, NUMBER, CHECKBOX, DATE, and SINGLE_SELECT properties with stable IDs. Color always has a label; archive preserves values/references; hard delete and type conversion remain excluded.
- Provide immutable virtual Calendar/Cards/List defaults plus PERSONAL and SHARED saved views. Scope never mutates: MEMBER copies to PERSONAL; only MANAGER manages SHARED definitions and dashboard selection.
- Calendar, Cards, List, dashboard, detail, and forms address the same schedule IDs and permissions. No parallel schedule dataset is created.
- Defer formulas, relations, rollups, people/files, multi-select, automation, dependencies, cross-project views, bulk conversion, custom Calendar date mapping, Google-driven editing, dashboard widgets, and all broader PR22 proposals.

## API, data, permission, and cross-layer requirements

- Retain /api/v1/projects/{projectId}/schedule-workspace semantics: virtual defaults without seed writes; governed properties/views/dashboard; atomic record/custom-value writes; strict typed validation; authorized SQL filters/groups/sorts/counts; bounded results with total, hasMore, and deterministic schedule-ID tie-break.
- The additive successor migration creates only workspace tables, constraints, and indexes; it rewrites no existing schedule/project-plan row and seeds no default view. Existing V9 checksum and project-plan data are invariants.
- Custom-only writes use schedule rowVersion but do not change businessRevision, acknowledgements, schedule events, or Google projection. Fixed-field changes retain existing lifecycle effects.
- Authorize before lookup/query. MANAGER owns definitions/shared views/dashboard; MANAGER or eligible MEMBER owns personal views and permitted values; VIEWER reads only. Personal definitions, counts, and protected records never leak.
- Keep WORKSPACE_* audit actions separate from SCHEDULE events; preserve project -> schedule lock order, fresh role checks, atomic rollback, explicit 409 recovery, and one-response query consistency.
- Backend impact is a selective rebase of workspace domain/API/query/migration/API-doc/deploy-input changes without deleting project-plan code. Frontend impact is popup/workspace composition into current shell/client/state/schedule behavior with guarded sessions and invalidation.
- Screen structure, layout, interactions, and presentation are deferred to ui-ux-designer, then ui-visual-designer, after this plan passes review.

## Dependencies and release prerequisites

- Planning dependency is offline-contract-only: local Git objects for the cited commits and local PR22 documents; owner root; evidence date 2026-09-29; safe check git show/diff; expected exact source/history; status verified for planning.
- GitHub read is ready per assignment. GitHub write, CI, and deployment readiness must be reverified by the parent/release owner before execution; no live outcome is claimed.
- No new provider setup is required. Existing Google settings remain unchanged; fake-adapter regression must show zero provider work for workspace metadata, custom-only values, views, or dashboard changes.
- High-risk evidence must include additive Flyway/PostgreSQL preservation/constraint checks, authorization/query/concurrency tests, legacy schedule/project-plan/Google regressions, generated API compatibility, frontend tests/typecheck/build, focused regressions for each prior PR18 finding, browser accessibility/responsive/state checks, independent Astra/high task review, and release/recovery/observation evidence. Testing must not write production data.

## Observable acceptance

1. Integrated source is based on 8991a09 and retains all six plan views, current schedule navigation/edit/recovery, authorization/concurrency, Google integration, and user data; stale branch deletions are absent.
2. Header notification access opens without changing current hash/page state; direct route, safe navigation, read/paging/session/focus behavior remain valid; SCHEDULE_CHANGED uses the canonical label.
3. Authorized readers receive virtual Calendar/Cards/List defaults; governed saved views, five typed properties, labeled colors, complete server results, and dashboard use the same schedule IDs.
4. The successor migration runs after deployed project-plan V9, preserves its checksum/data, writes no existing rows, and is explicitly covered with V9 by deployment-input checks.
5. Record writes preserve roles, one coherent rowVersion, atomic rollback, stale/cross-project/private denial, and custom-only isolation from businessRevision, acknowledgements, notifications, and Google work.
6. Exhausting the 1,000-row/ten-page Calendar bound or an incomplete later page is visibly partial; authorization/session failure discards protected results and locks all mutations until fresh authorized recovery.
7. MEMBER copy saves PERSONAL and MANAGER shared edit remains SHARED; refetch synchronizes clean drafts/revisions, preserves dirty work, and gives intentional conflict recovery without repeated 409.
8. View/filter changes from a nonzero page persist page 0 in URL context; fractional NUMBER filters are valid; close confirmation preserves option and record-form drafts until explicit discard.
9. Focused tests cover all eight open PR18 frontend findings without weakening retained assertions; integrated backend evidence reconfirms SV-BE-001..012; historical failures remain attributable.
10. The four PR22 documents remain canonical with implemented-now versus deferred status matching this plan; deferred backlog is never reported as implemented, reviewed, or deployed.

- Downstream invalidation: any r1 scope/API/data/permission/acceptance change makes affected UI plans/reviews, implementation contracts/results, tests, task reviews, release reviews, and deployment evidence stale.
- Feedback: docs/planning/unified-project-ux/assignment.md and origin/codex/project-schedule-views:docs/planning/schedule-views-frontend-review-final.md. Return r1 to root for independent Astra/high increment-plan-review and parent acceptance.
- Release impact: additive schema plus authorization/UI integration requires release-manager preparation only after accepted integrated evidence; this plan claims no CI, merge, deployment, rollback readiness, or live integration success.
