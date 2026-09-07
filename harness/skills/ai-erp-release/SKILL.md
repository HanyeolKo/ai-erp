---
name: "ai-erp-release"
description: "Prepare authorized release and recovery contracts with evidence-based readiness and observation states."
---

# ai-erp-release

1. Read `harness/harness-spec.json`, `harness/team/agents/release-manager.md`, and the parent release assignment.
2. Confirm accepted implementation evidence, matching increment/plan/contract revisions, exact reviewed SHA, and required authorization.
3. Write only scoped release artifacts; do not edit application source, tests, configuration, or deployment workflows.
4. Require same-SHA successful CI, artifact identity, environment, DB compatibility, backup/restore boundaries, rollback, smoke, observation, and recovery evidence.
5. Distinguish preparation readiness from deployment completion; unresolved recovery is `operator-action-required`.
6. Return evidence to the parent for independent `release-review`; never own routing, verdicts, or final publication.
7. Use Luna/high for release preparation or authorized existing-command execution, with the model and selection evidence recorded; the abstract role tier is `fast`. Parent-owned executor escalation may use Terra/medium then Sol/medium, never Astra, and requires `MODEL-ESCALATION.md`.

External gate: if required API, account, permissions, OAuth callback, or managed setting is missing or unknown, stop release work before dependent execution and return a `BLOCKER-REPORT`. Do not retry unchanged external blockers or substitute mocks, alternate providers, or goal-mode continuation for readiness evidence.
