# Visual change plan

- Task/revision: ui-interaction-completion/r1. Actual handoff: functional /root/ui_completion_functional completed SCREEN-PLAN.md, then native read-only Sol/medium /root/ui_completion_visual returned this proposal and a bounded data-geometry correction. Parent /root persists the response; visual role wrote no files.
- Accepted pattern: ERP-WORKSPACE-01/v2, ../latest-ui-refactor-visual-contract.md, plus accepted subsequent calendar interaction geometry at base 49d79fe. Observed tokens/components: frontend/src/app.css and frontend/src/screens/schedules.css. Production evidence: PRODUCTION-OBSERVATION.md.
- Functional source: SCREEN-PLAN.md. Popup/Today/date-jump behavior is user-authorized functional planning, not a permission expansion by the visual specialist.

| Rule | Pattern mapping | Candidate presentation |
| --- | --- | --- |
| IC-V1 | V2-TOKEN, TYPE, CONTROL, FOCUS | Reuse tokens/typography. Toolbar/action controls min-height 40px desktop, 44px below 768px, radius 8px, existing 3px focus ring unobscured. Controls in one row resolve to equal heights and may expand together for wrapped text at zoom. Never clip/ellipsis/hide labels. |
| IC-V2 | V2-DIALOG, SURFACE, STATE | Notification dialog width min(560px,calc(100vw - 24px)), existing viewport max-height, white surface, radius 12px, 24px desktop/16px mobile padding. Keep v2 loading/empty/error/read/pagination state colors and text. |
| IC-V3 | V2-SPACE, RESP | Notification rows use minmax(0,1fr) auto, gap 16px, vertical padding 16px. Related link/read action may wrap without clipping; below 768px rows stack with 12px gap and actions min-height 44px. |
| IC-V4 | V2-CONTROL, RESP | Header notification trigger matches text-button, transparent surface, accent text, horizontal padding 12px, min-height 40/44px. Remain visible at 320px without displacing/clipping account control. |
| IC-V5 | V2-CONTROL, SPACE | Calendar toolbar is one bounded surface: flex, centered alignment, gaps 8px, vertical padding 16px, divider borders. Month/week form equal-width segmented group. Period controls retain functional order. |
| IC-V6 | V2-RESP | Below 768px toolbar is two logical rows: toggle repeat(2,1fr); navigation 44px minmax(0,1fr) 44px 64px. Previous/next show existing chevrons with persistent accessible names. All controls min-height 44px; row expands together if wrapping needs it. |
| IC-V7 | V2-DIALOG, FORM | Date-jump dialog width min(420px,calc(100vw - 24px)); body-width input min-height 40/44px. Actions right aligned desktop, equal-width mobile, gap 8px. Validation uses existing danger style. |
| IC-V8 | V2-DATA and subsequent accepted geometry | Preserve current 7-column grids, mobile agenda, relative week day canvas height 1152px, runtime --calendar-content-width, global lane count, occurrence order, drag/resize handles, and every event/hour/ghost data-bearing top/height/left/width value/unit. Historical 260px canvas is superseded and must not be restored. |

## Mapping and validation

- Notification popup and direct route: IC-V1 through IC-V4; same content hierarchy/semantics.
- Project schedule toolbar/date jump: IC-V1 and IC-V5 through IC-V8.
- Planned 1440/768/390/375/320 and zoom/reflow checks: equal control heights, stable order, no ordinary horizontal overflow, visible focus, complete labels and 44px mobile targets. Check actual browser zoom separately from CSS-width reflow; do not conflate evidence.
- Keyboard: named trigger/dialog, close/Escape/focus return, background exclusion, visible date-error focus, toggle and previous/period/next/Today order.
- No new visual pattern. Current calendar geometry is an explicit accepted subsequent feature, not a new visual exception. IC-V8 was corrected before review after source inspection showed historical 260px was obsolete.
- Checks not run by visual specialist: browser/screenshots/keyboard/contrast/geometry/tests/typecheck/build. Proposal only, no implementation approval. Parent disposition: accepted as candidate for independent ui-plan-review.
- Local canonical source is this file. Intermediate planning records are consolidated into the eventual reader report; no standalone Notion page for each working artifact.
