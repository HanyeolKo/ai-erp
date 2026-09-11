# Team architecture

`harness/harness-spec.json` owns role IDs, capabilities, handoffs, and evaluator links.

| Role | Responsibility | Output |
| --- | --- | --- |
| `router` | Route bounded tasks and collect reproducible evidence | Routing assignment and evidence bundle |
| `ui-ux-designer` | Plan ERP screens and interactions | Local plan under `docs/ux/` with rationale |
| `ui-visual-designer` | Propose shared visual patterns after the functional UI/UX plan | Read-only visual contract and screen change plan |
| `product-planner` | Plan bounded increments and cross-layer requirements | Parent-scoped increment plan |
| `release-manager` | Prepare authorized release and recovery evidence | Release contract/result and increment record |
| `reviewer` | Independently review evidence and issue verdicts | Pass/fail result with stable defects |
| `implementer` | Execute parent-approved implementation contracts and verification | `IMPLEMENTATION-RESULT.md` and executed checks |

Ordinary UI implementation follows `router -> ui-ux-designer -> reviewer -> implementer` after the parent completes the contract. Visual planning adds `ui-visual-designer` between the functional designer and reviewer.
Product planning follows `router -> product-planner -> reviewer`; accepted plans needing screens then enter the UI specialist route. Use `TASK-ASSIGNMENT.md` and `DELEGATION-PROTOCOL.md` for parent-controlled assignment and return states.
Release preparation follows accepted implementation evidence -> `release-manager` -> `release-review`; the parent owns authorization, execution, and acceptance.
Non-UI implementation follows `router -> implementer` after a complete parent contract; results return to the parent, who applies `harness/policies/VERIFICATION.json` (parent acceptance for low/standard, required Astra review for high).
These are artifact prerequisites controlled by the upper orchestrator, not nested spawn edges. No specialist shortcut or reverse implementation edge is part of the required route.
Reviewer verdicts are never delegated to `implementer`.
External dependencies are checked in the parent assignment before dependent work. Missing or unknown required setup returns a blocker to the parent; goal-mode continuation cannot authorize retries or substitute test doubles for live evidence.

Native model defaults are explicit: Astra/high at the root and final reviewer, Sol/medium for `router`, `product-planner`, `ui-ux-designer`, and `ui-visual-designer`, and Luna/high for `implementer` and `release-manager`. The release-manager abstract tier is `fast`; Spark/high is an explicitly selected implementation alternative. Parent-owned executor escalation is Luna/high -> Terra/medium -> Sol/medium, never Astra execution. Sol-ceiling failures return to the Astra orchestrator for self-review. Dispatch is bounded to two active workers and depth one.
