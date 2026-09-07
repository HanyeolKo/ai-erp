---
name: ai-erp
description: "Route AI ERP requests through the required UI/UX specialist and independent review before screen planning or implementation."
---

# ai-erp

1. Read `harness/harness-spec.json`, `harness/state/state.json`, and `harness/team/agents/router.md`.
2. Every screen planning request MUST delegate to `ui-ux-designer` before drafting the plan or implementing screen behavior. This includes pages, flows, layouts, navigation, forms, tables, dashboards, states, accessibility, and interaction changes.
3. Pass the bounded objective, users, relevant local files, and constraints; enter `harness/skills/ai-erp-ui-ux/SKILL.md`.
4. Collect the specialist plan and actual handoff evidence, then delegate independent review to `reviewer` using `ui-plan-review`.
5. Require a passing review before implementation. Preserve failed criteria and route bounded revisions through the execution loop.
6. Backend-only work without a screen decision follows the ordinary project workflow. If delegation is unavailable, disclose it and leave the prerequisite unresolved.
7. Preserve local evidence, append the verdict, update next action, and follow the reporting policy for substantial documents.
