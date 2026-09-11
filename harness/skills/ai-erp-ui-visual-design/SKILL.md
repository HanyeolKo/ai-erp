---
name: "ai-erp-ui-visual-design"
description: "Plan shared visual patterns and bounded screen presentation changes after the functional UI/UX gate."
---

# ai-erp-ui-visual-design

Read `harness/harness-spec.json` and `harness/team/agents/ui-visual-designer.md`.
This skill is entered through `ui-ux-designer`; the parent dispatches the visual specialist only after the functional plan defines invariants.
Visual planning follows `router -> ui-ux-designer -> ui-visual-designer -> reviewer`; the existing ordinary UI route remains available for non-visual plans.
The `ui-ux-designer` selects and invokes the three pinned project skills, then passes bounded excerpts and evidence to this specialist: `ai-erp-frontend-design` for hierarchy, `ai-erp-ui-ux-pro-max` for candidate tokens and patterns, and `ai-erp-web-design-guidelines` for accessibility and responsive regression checks.

Inventory existing tokens, components, documentation, and current screen conventions. Label each finding `observed`, `candidate`, or `accepted`, and cite source paths.
Reuse an accepted pattern set by ID/version. If none exists or sources conflict, propose one coherent shared `VISUAL-DESIGN-CONTRACT.md` for independent review and parent acceptance before per-screen plans; pattern-contract-only review may mark screen mapping `N/A`, then later map screens after acceptance. Never choose page-specific styles in isolation.
The shared contract covers shell, page header, action placement, filter toolbar, table, form, dialog, status, density, typography, spacing, colors, responsive rules, variants, and reviewed exceptions.

Limit scope to spacing, alignment, placement, sizing, typography, color tokens, borders, surfaces, density, and responsive presentation.
Treat layout values that encode data (for example calendar `top`, `minHeight`, `left`, or `width`) as functional presentation. Preserve action meaning, handlers, routes, APIs, state, permissions, validation, data and columns, content, keyboard/DOM semantics, accessible names, and enabled/visible conditions; do not hide actions in menus or add clicks. Return any behavior-affecting layout decision to the parent for separate scope.
Use `harness/templates/VISUAL-CHANGE-PLAN.md` to map every screen to shared rule IDs and record before/after visual acceptance, responsive checks, keyboard checks, and unexecuted checks.
Use `ui-plan-review` through the independent reviewer. This skill does not edit production code, execute implementation, own verdicts, or mutate external systems.
