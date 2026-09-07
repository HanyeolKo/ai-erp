---
name: ai-erp-verify
description: "Verify AI ERP harness structure, required routing, provider parity, and pinned design-skill integrity."
---

# ai-erp-verify

Read `harness/harness-spec.json` and use evaluator `harness-structure`.
Run `python scripts/verify-harness.py` from the project root and preserve actual exit status and output.
Review schema, roles, paths, handoffs, skill parity, explicit model defaults, bounded dispatch limits, escalation artifact integrity, and vendor integrity.
Verify `harness/policies/VERIFICATION.json` and `harness/templates/TASK-RECORD.md`: parent-owned low/standard/high selection, optional standard Sol review, required high Astra review, conditional suite/smoke/preflight triggers, and actual raw evidence fields.
Cold-start from `harness/HARNESS.md`: restore purpose, core profile, phase, next action, and the current task evaluator.
A structural pass does not prove a specialist was invoked for a task or that a screen is usable.
Return findings and checks not run. Verification alone does not authorize mutation.
Verify external dependency policy artifacts and immediate-stop semantics. The saved implementer wrapper defaults to Luna/high; Spark/high is an explicit implementation alternative and does not prove execution without invocation evidence. Missing or unknown required setup must return `BLOCKER-REPORT` rather than continue dependent work.
Verify that executor escalation is parent-owned Luna -> Terra -> Sol, never Astra; Sol-ceiling work returns for Astra orchestrator self-review. Normal mandatory UI/product plan reviews use the read-only reviewer at Sol/medium; high-risk plan/release gates use Astra/high, and low/standard implementation may use parent acceptance under `VERIFICATION.json`. Quota/external blockers cannot trigger escalation or model sweeps.
