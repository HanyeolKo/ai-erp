---
name: ai-erp-verify
description: "Verify AI ERP harness structure, required routing, provider parity, and pinned design-skill integrity."
---

# ai-erp-verify

Read `harness/harness-spec.json` and use evaluator `harness-structure`.
Run `python scripts/verify-harness.py` from the project root and preserve actual exit status and output.
Review schema, roles, paths, handoffs, skill parity, model inheritance, and vendor integrity.
Cold-start from `harness/HARNESS.md`: restore purpose, core profile, phase, next action, and the current task evaluator.
A structural pass does not prove a specialist was invoked for a task or that a screen is usable.
Return findings and checks not run. Verification alone does not authorize mutation.
