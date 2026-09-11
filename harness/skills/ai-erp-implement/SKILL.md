---
name: "ai-erp-implement"
description: "Execute parent-approved implementation contracts with bounded code, tests, and behavior-affecting configuration changes."
---

# ai-erp-implement

1. Read `harness/harness-spec.json` and `harness/team/agents/implementer.md`.
2. This skill does not select a model. The upper caller must delegate to an actual `gpt-5.6-luna`/`high` worker by default or an explicitly selected `gpt-5.3-codex-spark`/`high` alternative; if already running as that worker, continue without re-delegating.
3. Before changes, confirm the selected authorized model/effort rung, invocation/session evidence, and reason/availability evidence for an alternative or escalation. The default is Luna/high; Spark/high is an explicit alternative, while parent-authorized Terra/medium or Sol/medium is valid only with `MODEL-ESCALATION.md` evidence. If the selected rung is unavailable, stop and return the blocker to the parent.
4. Parent-owned escalation may reassign a demonstrated capability/comprehension failure through Luna/high -> Terra/medium -> Sol/medium, at most three total attempts including the initial attempt. A Sol-ceiling failure returns to the Astra orchestrator for self-review; never select Astra as executor and never self-escalate. Record `MODEL-ESCALATION.md`.
4. Confirm the parent contract is complete and scoped, including allowed paths and acceptance criteria; for bounded low/standard work, the compact `TASK-RECORD.md` is the single assignment/contract/result record.
5. Execute code/test/config changes only in approved implementation scope.
6. For visual UI implementation, require the reviewed `VISUAL-DESIGN-CONTRACT.md` and `VISUAL-CHANGE-PLAN.md` in the parent contract and preserve their behavior and data-encoding invariants.
7. Follow the parent-selected tier in `harness/policies/VERIFICATION.json`: run applicable quick checks for low, changed-area tests for standard, and relevant integration checks for high. Record exact raw outputs and checks not run in `TASK-RECORD.md` or the high-risk detailed artifacts.
8. Return changes, diff paths, and blocked assumptions to the parent orchestrator for parent acceptance or the applicable review gate.
9. Never own verdicts, routing, or skill delegation decisions; preserve stable evidence and do not bypass review.
10. Return release needs to the parent; do not deploy or hand off directly to `release-manager`.

External gate: verify assigned dependencies before dependent edits or execution. If required setup is missing or unknown, stop and return a `BLOCKER-REPORT` with the external owner, concrete resolution options, and exact resume checks. Explicitly scoped offline-contract-only tests may proceed without live setup but never prove live integration.
