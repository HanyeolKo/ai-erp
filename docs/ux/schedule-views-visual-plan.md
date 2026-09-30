# Schedule views visual contract and screen mapping

- Task/revision: `schedule-views-delivery` visual r1. Actual read-only specialist `/root/views_visual`, native `ai-erp-ui-visual-designer`, Sol/medium; ACK then proposal returned through parent; parent persists this artifact.
- Functional handoff: `schedule-views-functional-plan.md` r1, API r2; accepted product selected r1. Extension `SCHEDULE-VIEWS-01/v1` to accepted `ERP-WORKSPACE-01/v2` passed independent pattern-stage review by `/root/views_increment_review` Astra/high; parent `/root` adopts rules SV-01–11 and the identical screen mapping below, including SV-06/SV-11 exceptions, on 2026-09-21. Combined screen-stage verdict is recorded separately.
- Accepted source: `latest-ui-refactor-visual-contract.md`; observed `frontend/src/app.css`, `screens/schedules.css`, Schedules.tsx. Existing local font/palette/spacing/control/focus/mobile conventions retained. Pinned guidance excerpts inherited from functional designer.
- Preserve routes, actions, handlers, API/state/version rules, permission/visibility, validation/content/columns, accessible names, DOM/keyboard behavior, status wording and dashboard order. No hidden action menu, added clicks, framework/dependency, marketing treatment, gradient, new motion or shadow system.

| Rule | Proposed presentation |
| --- | --- |
| SV-01 hierarchy | Existing header → view selector/actions → config summary → results → pagination, vertical gaps24/16/32px. White semantic surfaces on existing page token. |
| SV-02 selector | Builtins/shared/personal in one wrapping control, functional order, targets40px desktop/44px mobile, gap8px. Selected accent/white plus selected semantics; others white/control-border. Settings, Duplicate, Properties, Create visible and wrap without reorder. |
| SV-03 filters | Labelled summary chips wrap, minheight32px and gap8px, explicit Remove with accessible target, accent-soft/accent/divider. Text shows operator/value; color never sole encoding. |
| SV-04 cards | 3 equal columns>=1100px;2 at768–1099;1 below768. Gap16px desktop/12 mobile; padding20px desktop/16 mobile; divider1px, radius12px, no shadow. Title, configured label/value rows gap8, then actions. Group heading/full count spans grid; legend wraps. |
| SV-05 list | Semantic desktop table>=768, white/divider1px/radius12px, cells12px16px, left-aligned surface-subtle nonsticky header. Title→configuredfields→actions. Text wraps; bounded table overflow only if necessary, never page overflow. Mobile labelled record blocks preserve same field/action order, padding16/gap12. |
| SV-06 dialogs | View settings maxwidth760px; schema editor800px, extending old560px. Under768 width/height bounded calc(100vw - 24px)/calc(100vh - 24px). Existing overlay elevation only, white/radius12, padding24desktop/16mobile, sectiongap24/rowgap12. Footer in flow; actions wrap source order; mobile one column. |
| SV-07 fields | Existing labelled controls. Repeated filters/options semantic bordered groups padding16/radius12; desktop minmax(160px,1fr), mobile one column. Move/archive/Save/Cancel text controls with44px mobile targets. Field errors adjacent. |
| SV-08 legend | 16px solid swatches gray#64748B,blue#2563EB,green#15803D,amber#B45309,red#B91C1C,purple#7E22CE,pink#BE185D,teal#0F766E;1px text-color border. Pair label/count/Unset/Archived text; labels use existing text token. Verify boundary>=3:1 and text>=4.5:1. Fixed lifecycle colors independent. |
| SV-09 focus/state | Reuse V2 danger/success/amber/loading. Visible3px focus ring offset2px, unclipped including selected controls. Preserve dialog trap/Escape/return, first-error focus, route heading focus, skip link and polite loading/partial announcements. |
| SV-10 responsive |1280:3card columns/inline controls/table.768:2columns/table/wrappingcontrols.390/320:16pxpagegutter/1column/labelledlist/onecolumndialogs/44pxtargets.200%zoom reflow; globaloverflow<=1px. |
| SV-11 geometry | Preserve current seven-column month/week, mobile agenda, inline top/height/minHeight/left/width values and units, event order and hit areas. Observed current weekcanvas1152px supersedes obsolete oldV2 numericexample260px; preserve current computed behavior, no visual rewrite of data geometry. |

| Screen | Rules | Required before/after behavior |
| --- | --- | --- |
| Schedule Calendar |01–03,08–11,V2-DATA|Add viewselector/legend while preserving month/week/directedit/timeDialog/partialstate/agenda and measured geometry.|
| Schedule Cards |01–04,08–10|Grouped server-ordered grid; same record IDs/fields/actions/counts/paging and inlineSaveCancel.|
| Schedule List |01–03,05,08–10|Desktop semantic table/mobile labelled blocks with identical fields/order/actions.|
| Settings/archive/conflict dialogs |03,06,07,09–10|Sectioned surface preserves dirtydraft/400/409/scope/caps/actionorder.|
| Properties dialog |06–10|Typed schema/optionrows preserve immutability/version/position/move/archive/confirmation.|
| Record form/detail |07–10,V2form/state|Property fieldset/section matches task surfaces, atomic submit/archivedvalues/rights/conflict preserved.|
| Dashboard |04,08–10,V2header/surface|Existing sections ordered; compact selected-view cards, visible managerselector/reset, unavailable state.|

- Proposed exceptions: SV-06 wider configuration dialogs; SV-11 preserve current1152px data geometry instead of stale numericexample. No other base exception.
- Acceptance evidence needed: scope diff; action/field order and visibility; screenshots1280/768/390/320 plus200%zoom, longvalues, overflow<=1px; loading/empty/partial/error/disabled/conflict/archive/unavailable androle states; swatch/text/control contrast; keyboard/dialog focus; calendar computed geometry beforeafter.
- Specialist checks: read-only Get-Content/rg source inspection exit0. Not run by specialist: source edits/tests/build/browser/contrast/keyboard/geometry/database/liveGoogle/deploy/Notion. Parent baseline screenshots under `schedule-views-evidence/` are separate observed source, not specialist runtime proof.
