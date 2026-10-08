# Project Progress Overview Visual Design Contract and Change Plan

## Status and provenance

- Task: `PROJECT-OVERVIEW-20261008`
- Revision: `visualr1`
- Date: `2026-10-08`
- Specialist: `/root/overview_visual`, native `ai-erp-ui-visual-designer`, `gpt-5.6-sol` / `medium`
- Dispatch: acknowledged; no fork history, subdelegation, or provider usage (`null`)
- Functional source: `docs/ux/project-progress-overview-functional.md`, revision `functionalr2`, SHA-256 `30EFE547BAA44A0AA2994790982FFDB3ED3527A53F45A96182336358FB73BFF8`
- Product source: revision `r4`, independently accepted `PASS`, SHA-256 `502DDBD34FAD5F8C05752044DD11C2350B8C08049121CD6A0C07C78B3ACA39FF`, selected main `b660`
- Status: candidate pending independent `ui-plan-review` and parent acceptance
- Scope: planning only, `offline-contract-only`; no implementation approval

## Accepted pattern inventory

- `ERP-WORKSPACE-01/v2` in `docs/ux/latest-ui-refactor-visual-contract.md`: `V2-HEADER`, `V2-TYPE`, `V2-SPACE`, `V2-SURFACE`, `V2-CONTROL`, `V2-FOCUS`, `V2-STATE`, `V2-RESP`.
- `SCHEDULE-VIEWS-01/v1` in `docs/ux/schedule-views-visual-plan.md`: `SV-04`, `SV-09`, `SV-10`.
- `UNIFIED-PROJECT-UX-01/r1` in `docs/planning/unified-project-ux/visual-plan.md`: `UV02`, `UV03`, `UV05`.

Observed implementation evidence includes the local font, pale page and white surface palette, 4–40 px spacing, 8 px controls, 12 px surfaces, 40 px desktop and 44 px mobile controls, and 3 px focus treatment in `app.css`; the Overview header, actions, queue, signals, and cards in `Schedules.tsx`; and the matching header, section, row, state, responsive, and card patterns in `schedules.css`. This contract introduces the mapping and candidate exception below.

## Visual contract

Preserve the existing shell, header, project eyebrow, `h1`, lead, invite and create actions, functional DOM, and permission order. Below 768 px, stack actions without menus or hidden controls.

Use this canonical DOM and visual order: header → Project progress → Goals → Critical risks → Milestones → My active tasks → Project schedules → Needs my confirmation → existing signals and cards.

Project progress is the only headline metric. Show the completed/task ratio at 28 px, 1.2 line height, bold, with tabular numerals. Place the exact API percentage beside it at 20 px and 1.4 line height, with the explicit label `unweighted TASK completion`. For unavailable data, show a plain status; do not introduce an indeterminate or health meter.

Render target and forecast as compact definition facts beneath the ratio, with separate labels and equal visual weight. Preserve all four states, known incomplete bounds, and the literal `outsideTarget` wording. Never reinterpret these facts as health or traffic-light states.

Place progress, goals, risks, and milestones in one shared white macro surface with 1 px structural dividers and a 12 px radius. Use meaningful headings and dividers rather than a marketing hero, chart, or interchangeable metric grid. Domain counts appear only in section headings, use tabular numerals, and distinguish complete, partial, and unavailable states in text.

Use compact, read-first lists following `UV02`: title first, facts below or beside it, 13 px metadata, 12 px row gaps, 12 px vertical and 16 px horizontal padding, and 1 px dividers. Preserve source order and allow actions to wrap. Item titles are 15 px/1.5 bold, `h2` headings are 20 px/1.4, and body text follows the accepted 15 px/1.6 treatment. Long Korean text, paths, dates, zones, and IDs use `overflow-wrap: anywhere` and `min-width: 0`; do not ellipsize or clamp them.

Period selectors remain pressed-state buttons using existing controls. Destination actions remain links and retry actions remain buttons. Keep every action visible. Existing signals and configured cards remain secondary under `SV-04` and retain their meaning. Add no new palette, font, shadow, gradient, motion, or icon-only status.

### `POV-E01` candidate exception

The new Goals, Critical risks, and Milestones regions use three equal columns at widths of at least 1100 px within one macro surface, with 16 px gaps and 1 px structural dividers. From 769–1099 px, use two columns in DOM order with the last region in the next grid cell. At 768 px and below, use one column.

This reuses the `SV-04` 3/2/1 breakpoints while keeping one shared surface. It supersedes the old audit item `OV-C01`, whose My Tasks/Schedules two-column layout is not reused. This is a presentation-only candidate and requires independent review and parent acceptance.

## Functional invariants

Preserve every action, handler, route, query, API, state, permission, validation rule, data value, count, cap, label, visibility rule, accessible name, DOM relationship, keyboard behavior, and focus behavior from `functionalr2`. Preserve zero Overview writes, the current selected main behavior, and notifications.

Calendar geometry is out of scope. Keep the seven-column layout, mobile agenda, event order, hit areas, `top`, `minHeight`, `left`, `width`, and 1152 px week canvas unchanged.

## Measured acceptance targets

- At 1440×900, show the page heading plus each heading and the first record or state for Project progress, Goals, Critical risks, and Milestones without scrolling. Make ratio, percentage, target, and forecast distinguishable without color.
- At 768 px and below, use one column in DOM order.
- At 390 px and 320 px, show the page heading plus the ratio or zero/unavailable state within the first viewport with 16 px gutters.
- At 320 px, 390 px, and 200% zoom equivalent, keep overflow at or below 1 px and wrap long copy without clipping.
- On mobile, every interactive target is at least 44×44 px and focus remains unclipped.
- Keyboard order follows the visual DOM. Period, destination, retry, and header controls remain reachable with unchanged accessible names.
- Meet contrast ratios of 4.5:1 for normal text, 3:1 for large text, and 3:1 for controls and focus. Every state remains understandable without color.

## Visual change plan

| Surface | Accepted patterns | Presentation | Preserved behavior and data | Responsive and accessibility evidence | Exception |
| --- | --- | --- | --- | --- | --- |
| `/projects/:id` header and macro group | `V2-HEADER`, `V2-TYPE`, `V2-SPACE`, `V2-SURFACE`, `V2-FOCUS`, `V2-STATE`, `V2-RESP`, `SV-09`, `SV-10`, `UV03`, `UV05` | Macro-first progress band structurally divided from goals, risks, and milestones; at 1440 px, show macro headings and first records | Preserve `functionalr2` exact order, facts, counts, caps, partial and error states, routes, and zero-write behavior | DOM-preserving 3/2/1 layout, 16 px gutter, long-text wrapping, contrast and focus requirements, overflow ≤1 px | `POV-E01` candidate |
| `/projects/:id` tasks, schedules, and confirmation | `V2-TYPE`, `V2-SPACE`, `V2-CONTROL`, `V2-FOCUS`, `V2-STATE`, `V2-RESP`, `SV-09`, `SV-10`, `UV02`, `UV03`, `UV05` | Sequential full-width sections after the macro surface; compact rows, period controls, range and zone copy, and visible links | Preserve caps, fields, today/week URL, zone-midnight behavior, `VIEWER` queue behavior, and all source meanings | Source-order wrapping, 44 px targets, `aria-pressed`, retry names, focus return, and long-copy coverage at 320/390/768 px and 200% zoom | No exception |
| `/projects/:id` signals and configured cards | `V2-SURFACE`, `V2-STATE`, `V2-RESP`, `SV-04`, `SV-09`, `SV-10`, `UV05` | Preserve existing secondary placement and grid | Preserve data, labels, order, role, notifications, and schedule meanings | Existing 3/2/1 configured-card behavior, focus, wrapping, and no overflow | Unchanged authority |

## Verification and handoff

Read-only checks completed: inspection of the functional plan, visual templates, accepted pattern documents, `Schedules.tsx`, `app.css`, and `schedules.css`.

Not run: browser or screenshot checks; viewport, zoom, screen reader, keyboard, contrast, or focus checks; tests; API or database checks; typecheck or build; exact selected-main revalidation; PR or CI checks; provider or production deployment checks; Notion synchronization.

Handoff `visualr1` with `functionalr2` to independent `ui-plan-review`. Implementation remains blocked until `PASS` and parent acceptance. This local file is canonical. Notion synchronization is deferred with no external mutation because the already verified `entitlement_required` free-block quota is exhausted today; do not retry while that readiness evidence is unchanged.
