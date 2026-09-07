---
name: "ai-erp-implement"
description: "Execute parent-approved implementation contracts with bounded code, tests, and behavior-affecting configuration changes."
---

# ai-erp-implement

1. Read `harness/harness-spec.json` and `harness/team/agents/implementer.md`.
2. This skill does not select a model. The upper caller must delegate to an actual `gpt-5.3-codex-spark`/`high` worker or approved `gpt-5.6-luna`/`high` fallback; if already running as that worker, continue without re-delegating.
3. Before changes, confirm selected model, high reasoning, invocation/session evidence, and fallback reason when Luna is used. If any is unavailable, stop and return the blocker to the parent.
4. Confirm the parent contract is complete and scoped, including allowed paths and acceptance criteria.
5. Execute code/test/config changes only in approved implementation scope.
6. Run requested verification commands, collect actual outputs, and record checks not run.
7. Return changes, diff paths, and blocked assumptions to the parent orchestrator for independent `task-review` by `reviewer`.
8. Never own verdicts, routing, or skill delegation decisions; preserve stable evidence and do not bypass review.
