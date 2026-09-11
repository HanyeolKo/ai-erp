# AI ERP harness

Read in order: `harness-spec.json`, `state/state.json`, the entry skill, the relevant role, and `loops/EXECUTION-LOOP.md`.

## Routing

All product planning requests pass through `product-planner`; accepted plans requiring screens then pass through `ui-ux-designer` before any design output or UI code decisions.

Visual planning continues through the read-only `ui-visual-designer` after the functional `ui-ux-designer` plan, then reaches `reviewer` for independent `ui-plan-review`. The visual specialist proposes shared patterns and presentation changes only.

All code, test, and behavior-affecting configuration requests must pass through `implementer` with an explicit parent-owned contract. High/complex work uses `IMPLEMENTATION-CONTRACT.md`; bounded low/standard work may use the compact `TASK-RECORD.md` as its assignment/contract/result record. Verification tier and checks follow `policies/VERIFICATION.json`.
Allowed implementation domains are frontend, backend, scripts, infra, .github, and root build/config files plus tests and behavior-affecting configuration.
Implementation that touches screens still requires the specialist plan, a passing `ui-plan-review`, and contract evidence.

## Control flow

- Router constructs the bounded assignment and collects evidence.
- Parent-controlled assignments use `TASK-ASSIGNMENT.md` and `DELEGATION-PROTOCOL.md`; each worker acknowledges and returns evidence to the parent.
- Product-planner owns bounded product increments and cross-layer requirements; reviewer independently evaluates plans with `increment-plan-review`.
- Release-manager prepares authorized release/recovery evidence; reviewer independently evaluates it with `release-review`. Readiness is distinct from deployment completion.
- Designer writes planning/docs only for UI work.
- Visual designer writes no production files and returns bounded proposals to the parent.
- Implementer executes the approved contract and verification commands.
- Reviewer is the independent verdict owner when the selected policy gate requires review; parent acceptance owns low/standard completion, while high-risk completion requires reviewer pass.
- Ordinary UI artifact order is `router -> ui-ux-designer -> reviewer -> implementer`; visual artifact order is `router -> ui-ux-designer -> ui-visual-designer -> reviewer -> implementer`. Product planning is `router -> product-planner -> reviewer`, then accepted screen work may enter the UI route. Release preparation uses accepted implementation evidence -> `release-manager` -> `release-review`. Non-UI artifact order is `router -> implementer`, followed by the applicable policy gate. No designer shortcut or reverse implementation edge exists.

## Scope and evidence

This `core` profile installs routing, execution, task evaluation, structural verification, and short reporting.
It does not install memory, self-evaluation, automated improvement, or Learning Gate.
Use the compact `templates/TASK-RECORD.md` for bounded low/standard implementation work and the detailed `TASK-ASSIGNMENT.md`, `IMPLEMENTATION-CONTRACT.md`, `IMPLEMENTATION-RESULT.md`, `MODEL-USAGE-RECORD.md`, and `MODEL-ESCALATION.md` artifacts for high/complex work or actual capability escalation. Planning, UI, and release templates remain required by their specialist gates. Astra owns top-level orchestration and final review; normal plan reviews may use Sol/medium; high-risk plan/release review uses Astra/high; Luna/high owns implementation and release execution; Terra/medium and Sol/medium are parent-owned executor escalation rungs; Astra is never an executor fallback.
Run `python scripts/verify-harness.py` for harness changes.

External prerequisites are declared per assignment and contract. Missing or unknown required setup stops dependent work and returns `BLOCKER-REPORT`; explicitly scoped offline-contract-only work may continue only when independent of that setup. Goal-mode continuation does not override a blocked task.

## Reporting and permissions

Follow `policies/reporting.json`: update local source first, then synchronize substantial reader documents to Notion.
Use Korean for reader reports and concise English for canonical contracts, preserve exact machine identifiers.
Respect spec approval gates and reuse existing authorization.
