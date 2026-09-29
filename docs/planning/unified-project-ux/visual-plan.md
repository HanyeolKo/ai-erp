# Unified visual reconciliation r1
- Task consolidate-pr-17-18-22; functional handoff ui-plan.md r1 from /root/unified_ui. Actual visual specialist /root/unified_visual, native ai-erp-ui-visual-designer Sol/medium; read-only proposal returned2026-09-29, parent persists exact operative rules. No escalation, usage null; bounded minimal dispatch.
- Candidate pattern UNIFIED-PROJECT-UX-01/r1 extends accepted ERP-WORKSPACE-01/v2 and SCHEDULE-VIEWS-01/v1 SV01-11, sources docs/ux/latest-ui-refactor-visual-contract.md and schedule-views-visual-plan.md. Parent adoption pending independent UI review.
- Preserve main shell/navigation/header, six plan views, routes/actions/API/state/permission/validation/content/field order, keyboard/focus semantics and enabled conditions; no new dependency, motion/shadow/color system or added clicks.
- UV01 popup: existing white V2 Dialog/overlay maxwidth560px and desktop maxheight calc(100vh - 48px). Existing divider/header20px24px/body16px24px24px. No width exception.
- UV02 rows: server order; minmax(0,1fr) auto;16px gap/padding/divider, wrapping labels/link,13px secondary time; actions wrap in source order, read button text intact.
- UV03 below768: dialog bounded calc(100vw - 24px)/calc(100vh - 24px);padding16px; singlecolumn row/gap12px; actions44px targets; paging in flow/wrapping; no horizontal page overflow.
- UV04 workspace: exactly one saved-view peer selector Calendar/Cards/List plus authorized views perSV02; Calendar month/week subordinate. Current main shell/header/result hierarchy/dashboard order/plan six views preserved.
- UV05 states/focus: existing V2/SV state colors+words, labels, non-color meaning, Dialog trap/Escape/trigger return, DOM order and3px unclipped focus. No action hiding.

| Screen | Mapping | Before/after acceptance |
| --- | --- | --- |
| Shell popup | V2 Dialog/Control/Focus/State/Responsive;UV01-03,05 | Route-only before; same ordered/read/link/paging states in bounded popup after, long content wraps, no underlying layout shift. |
| Direct notifications | V2 Surface/Control/Responsive;UV02,05 | Existing page hierarchy/order; popup width not applied to full route. |
| Calendar | SV01-03,08-11;UV04-05 | One peer selector, month/week/date subcontrols; seven columns/event order/hitareas/inline top,minHeight,left,width/mobile agenda and current1152 time canvas retained. |
| Cards/List/settings/form/detail/dashboard | Existing SV mapping;UV04-05 | Retain grid/table/dialog/field/action order and behavior. |

- Exact retained exceptions only: SV06 settings760px/schema800px; SV11 actual current1152 week geometry supersedes obsoleteV2 260example. Popup remains560px.
- Acceptance:1280/768/390/320px and200%zoom; long labels/link; overflow<=1px; popup loading/empty/error/read/paging; one selector; keyboardopen/actions/paging/Escape/link/focus return; computed calendar geometry before/after.
- Specialist checks: source/doc read only. No code/test/config/browser/contrast/keyboard/geometry/build/external checks run. These remain implementation verification requirements, not a visual plan verdict.

- Parent root acceptance2026-09-29: independent /root/unified_plan_review Astra/high pattern-stage PASS received. Adopt exact UNIFIED-PROJECT-UX-01/r1 UV01-05 with unchanged screen mapping and existing SV06/SV11 exceptions. This adoption authorizes screen-stage review, not implementation before final UI PASS.

