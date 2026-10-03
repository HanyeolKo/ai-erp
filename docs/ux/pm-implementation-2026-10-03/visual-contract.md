# Visual design contract

- Task/revision: ERP-PM-IMPLEMENT-20261003-VIS r1; native ui-visual-designer proposal.
- Proposed shared set: ERP-PM-EXECUTION-01/v1. Candidate pending independent pattern-only ui-plan-review and parent acceptance; per-screen mapping N/A until then.
- Functional input: screen-plan.md r2 and architecture-contract.md r1; baseline b6603269800352073e68c6e76fe4bedf516c5ba6. Parent owns adoption. Previous synthesis-visual.md r2 is planning precedent, not current implementation approval.
- Observed source: app.css has Segoe UI/Malgun Gothic/system, 15px body, 28/20/16/13px hierarchy, navy/blue/light/amber/red/green palette, 8/12px radii, 40px desktop/44px mobile controls, 224px sidebar and 3px focus outline. ui.tsx supplies Shell/Link/Notice/Loading/QueryState/RetryButton/ProjectMissing/native Dialog with focus return. App.tsx provides hash routes and heading focus. schedules.css preserves seven-column month/week, 1152px week canvas, computed event geometry and mobile agenda.
- Consumers: Account, Dashboard, ProjectPlan, ScheduleWorkspace, Schedules, ScheduleForm, Detail, ProjectFiles; new MyWork, ProjectSettings, WeeklyReview. Pinned design guidance hashes/selective reuse are in tmp/pm-review-2026-10-02/guidance/ui-r2-guidance-evidence.md.
- Functional invariants: retain accepted actions/handlers/routes/APIs/state/permissions/validation/data/columns/content/DOM and keyboard semantics/accessible names/visibility conditions. This visual contract proposes presentation only. The accepted functional plan separately authorizes the new simple default and routes; visual work cannot hide actions, add interaction steps or recouple the four states.

## Shared rules

- PME-01 Shell/header/actions: preserve 72px top bar, 224px desktop sidebar, mobile menu, heading focus and content widths. Order heading/context, one primary permitted action, remaining source-ordered actions, filters, summary, records, pagination. Do not hide actions or add steps.
- PME-02 Type/spacing/density: existing font stack and 28/20/16/15/13px scale; 4/8/12/16/20/24/32/40px rhythm. Compact default project/TASK rows 52-60px, expanded editing at least64px. Avoid decorative card grids.
- PME-03 Palette/surfaces: existing named colors, 1px divider/control borders, 8px control/12px surface radii. White surfaces bound filters/records/forms/dialogs/recovery; keep light background and restrained existing elevation.
- PME-04 Filters/tables/lists: separate URL-backed filters from summaries/records. Semantic tables/aligned lists at1280 with stable headers, identity and tabular numbers. Incomplete results explicitly mean observed query scope; no implied totals/capacity.
- PME-05 Forms/read-edit: definition lists/flat rows in read mode. Label/control/help/error vertically grouped in edit mode followed by source-ordered actions. VIEWER reads values with adjacent permission reason; visibility follows functional plan.
- PME-06 Dialog/recovery: native trap/Escape/caller return. Distinct headings/text for403/409/unknown/partial/disabled states; no color-only meaning.
- PME-07 Four-state grammar: separately labelled TASK state, schedule state, current-user acknowledgment, Calendar projection, in that order. Axis label plus exact token and localized meaning; never one combined badge.
- PME-08 Relationships: separate Primary TASK and Related TASK/MILESTONE groups. Row order relationship type,target,TASK state,schedule state,acknowledgment,Calendar projection,permission reason,allowed action. Static labels do not resemble controls.
- PME-09 Facts/judgments: neutral stored facts; outlined warning System observation; distinct Manager judgment for health/milestone decisions. Reason, reference zone/date/time and source links. No invented score/KPI/progress visualization.
- PME-10 Stage variants: S1 compact default TASK plus secondary Advanced planning; S2 same rules for independent definition/target regions, execution metadata, MyWork and context; S3 grouped weekly observations and separate criterion/evidence/decision regions.
- PME-11 Responsive/overflow:390px16px gutters and single-column labelled records; all axes and relationships remain distinct. At200%, DOM/visual order aligned, actions wrap in source order, ordinary pages no horizontal overflow beyond1px.
- PME-12 Calendar boundary: retain seven columns, month/week semantics, mobile agenda, day-canvas height, event order/hit areas and computed top,height/minHeight,left,width. Shared tokens may change only typography/color/border/non-semantic spacing.

## Exceptions and required verification

- PME-E1: existing mobile-shell project title may ellipsize on one line only with full accessible/title context.
- PME-E2: bounded desktop week-calendar horizontal scroll permitted; page-level overflow is not.
- PME-E3: errors/permission/blocking/zone/evidence/user content wrap without truncation.
- At1280/390/200% verify action visibility/order, labelled axes/relationship groups, target size, dialog bounds and ordinary overflow<=1px. Keyboard shell/navigation -> heading/actions -> filters -> summary -> records -> pagination; visible3px focus, dialog trap/Escape/return and focusable error summary. Contrast text>=4.5:1, large text>=3:1, UI/focus>=3:1. Exercise long names/tokens/reasons/zones/unbroken identifiers. Identical Calendar fixtures before/after must retain seven columns,1152px canvas,event order/hit areas and computed geometry.
- Unexecuted: code/tests/browser/screenshots/contrast/keyboard/screen-reader/1280/390/200%/overflow/Calendar comparison/live ERP/Google/production capture. No claimed executed acceptance. Usage null.
