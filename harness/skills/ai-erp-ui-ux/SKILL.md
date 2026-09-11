---
name: ai-erp-ui-ux
description: "Plan ERP screens and user flows through the UI/UX designer before implementation, with independent review."
---

# ai-erp-ui-ux

Read `harness/harness-spec.json` and `harness/team/agents/ui-ux-designer.md`.
Every screen planning request MUST execute through the `ui-ux-designer` role, including direct calls to this skill; the orchestrator delegates before planning output or implementation.
When the request is visual-only, the parent dispatches `ui-visual-designer` after this functional plan and before `reviewer`; this skill remains the mandatory first contact.
The native Codex name is `ai-erp-ui-ux-designer`. If already running as that designer, execute the task here without delegating it to another designer.
Inspect relevant local requirements and existing frontend conventions. Use only the applicable project skills:
- `harness/skills/ai-erp-ui-ux-pro-max/SKILL.md` for design-system and UX pattern research.
- `harness/skills/ai-erp-frontend-design/SKILL.md` for visual hierarchy and concrete frontend design.
- `harness/skills/ai-erp-web-design-guidelines/SKILL.md` for accessibility and interaction review.
Save the plan under `docs/ux/` using `harness/templates/SCREEN-PLAN.md`; include flows, permissions, screen states, accessibility, rationale, and acceptance criteria.
Return source and handoff evidence to the read-only `reviewer` via the orchestrator. Normal `ui-plan-review` uses Sol/medium; high-risk screen plans use Astra/high. Evaluator `ui-plan-review` must pass before implementation.
Keep local documents authoritative, then synchronize substantial reader documents under the reporting policy.
