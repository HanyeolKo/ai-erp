---
name: ai-erp-ui-ux-pro-max
description: "Use pinned UI UX Pro Max design-system and stack research for ERP planning with project-relative Python paths."
---

# ai-erp-ui-ux-pro-max

Read `harness/harness-spec.json` and enter through `ui-ux-designer` with `harness/skills/ai-erp-ui-ux/SKILL.md`.
Load `vendor/ux-skills/ui-ux-pro-max/SKILL.md` only when design-system or UX pattern research is useful.
Override upstream `${CLAUDE_PLUGIN_ROOT}` and `.claude/skills/ui-ux-pro-max` paths with `vendor/ux-skills/ui-ux-pro-max`; do not install into a global provider directory.
Run project-local searches from the repository root, for example:
```powershell
python vendor/ux-skills/ui-ux-pro-max/scripts/search.py "B2B SaaS dashboard" --design-system -p "AI ERP"
python vendor/ux-skills/ui-ux-pro-max/scripts/search.py "form validation keyboard navigation" --domain ux
```
Use results as design candidates, never as design authority. Check existing React/TypeScript conventions and current task needs before adopting them.
Reject marketing hero/CTA layouts or decorative suggestions when they do not support the internal ERP task; prioritize tables, filters, forms, permissions, and screen states.
If the product result is irrelevant, retry once with a narrower task query, then record the mismatch instead of adopting unrelated recommendations.
Keep generated output task-scoped; do not persist or overwrite a design system without the user's task requiring it.
Record the query, selected evidence, and rationale; evaluator `ui-plan-review` still requires independent review before implementation.
