# AI ERP harness

Read in order: `harness-spec.json`, `state/state.json`, the entry skill, and the relevant role.

## Routing

All screen planning requests must pass through `ui-ux-designer` before any design output or UI code decisions.

All code, test, and behavior-affecting configuration requests must pass through `implementer` with an explicit parent-owned contract using `IMPLEMENTATION-CONTRACT.md`.
Allowed implementation domains are frontend, backend, scripts, infra, .github, and root build/config files plus tests and behavior-affecting configuration.
Implementation that touches screens still requires the specialist plan, a passing `ui-plan-review`, and contract evidence.

## Control flow

- Router constructs the bounded assignment and collects evidence.
- Designer writes planning/docs only for UI work.
- Implementer executes the approved contract and verification commands.
- Reviewer is the sole independent verdict owner.
- UI artifact order is `router -> ui-ux-designer -> reviewer -> implementer`; non-UI artifact order is `router -> implementer`, followed by parent-requested independent review. No designer shortcut or reverse implementation edge exists.

## Scope and evidence

This `core` profile installs routing, execution, task evaluation, structural verification, and short reporting.
It does not install memory, self-evaluation, automated improvement, or Learning Gate.
Use `templates/SCREEN-PLAN.md`, `templates/IMPLEMENTATION-CONTRACT.md`, `templates/IMPLEMENTATION-RESULT.md`, `templates/UI-REVIEW.md`, and `evaluation/TASK-REVIEW-RUBRIC.md` as appropriate. The native implementer defaults to Spark/high; Luna/high is a documented invocation fallback with evidence.
Run `python scripts/verify-harness.py` for harness changes.

## Reporting and permissions

Follow `policies/reporting.json`: update local source first, then synchronize substantial reader documents to Notion.
Use Korean for reader reports and concise English for canonical contracts, preserve exact machine identifiers.
Respect spec approval gates and reuse existing authorization.
