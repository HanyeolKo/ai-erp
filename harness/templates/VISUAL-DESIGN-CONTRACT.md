# Visual design contract

Complete this contract before per-screen visual planning. The parent owns acceptance; `ui-visual-designer` supplies read-only proposals and the `reviewer` owns `ui-plan-review`.

- Task and revision:
- Functional `ui-ux-designer` plan and handoff evidence:
- Parent decision owner and adoption status: observed / candidate / accepted (parent records acceptance after independent review)
- Existing source inventory (tokens, components, docs, screens):
- Accepted pattern set ID/version and source paths, or one proposed shared set with coherence rationale:
- Shared shell, page header, action placement, filter toolbar, table, form, dialog, status, and density rules:
- Typography, spacing, color, border, surface, responsive, and variant rules:
- Explicit exceptions, rationale, affected screens, and reviewer/parent acceptance:
- Functional invariants: preserve actions, handlers, routes, APIs, state, permissions, validation, data/columns, content, DOM/keyboard semantics, accessible names, and visibility conditions.
- Data-encoding presentation boundaries (such as calendar geometry) and validation method:
- Measurable visual acceptance and responsive/keyboard regression checks:
- Unexecuted checks:
- Pattern-contract-only review: screen mapping `N/A` until this contract is accepted.

Visual scope covers presentation only. Do not hide actions in menus, add clicks, or change data-encoding geometry without a separately approved parent scope.
