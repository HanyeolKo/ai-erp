# Team architecture

`harness/harness-spec.json` owns role IDs, capabilities, handoffs, and evaluator links.

| Role | Responsibility | Output |
| --- | --- | --- |
| `router` | Route bounded tasks and collect reproducible evidence | Routing assignment and evidence bundle |
| `ui-ux-designer` | Plan ERP screens and interactions | Local plan under `docs/ux/` with rationale |
| `ui-visual-designer` | Propose shared visual patterns after the functional UI/UX plan | Read-only visual contract and screen change plan |
| `product-planner` | Plan bounded increments and cross-layer requirements | Parent-scoped increment plan |
| `release-manager` | Prepare authorized release and recovery evidence | Release contract/result and increment record |
| `librarian` | Persist, retrieve, and archive parent-approved substantive documents | Document result with source/status/revision and archive evidence |
| `reviewer` | Independently review evidence and issue verdicts | Pass/fail result with stable defects |
| `implementer` | Execute parent-approved implementation contracts and verification | `IMPLEMENTATION-RESULT.md` and executed checks |

Ordinary UI implementation follows `router -> ui-ux-designer -> reviewer -> implementer` after the parent completes the contract. Visual planning adds `ui-visual-designer` between the functional designer and reviewer.
Product planning follows `router -> product-planner -> reviewer`; accepted plans needing screens then enter the UI specialist route. Use `TASK-ASSIGNMENT.md` and `DELEGATION-PROTOCOL.md` for parent-controlled assignment and return states.
Release preparation follows accepted implementation evidence -> `release-manager` -> `release-review`; the parent owns authorization, execution, and acceptance.
Substantive document management follows `router -> librarian` after a complete parent assignment. The librarian writes only exact parent-authorized documentation paths, preserves accepted meaning and Notion reading status, and returns to the parent. Documents may mention or quote technical work; changes to executable code, tests, behavior configuration, screen-decision meaning, release permissions/commands, or verdicts stay with their existing owner and gate.
Non-UI implementation follows `router -> implementer` after a complete parent contract; results return to the parent, who applies `harness/policies/VERIFICATION.json` (parent acceptance for low/standard, required Astra review for high).
These are artifact prerequisites controlled by the upper orchestrator, not nested spawn edges. No specialist shortcut or reverse implementation edge is part of the required route.
Reviewer verdicts are never delegated to `implementer`.
Conditional behavior evidence keeps ownership explicit: the parent prepares bounded scenarios and expected outcomes, `router` observes actual results read-only, `implementer` owns code/test changes and raw checks, and `reviewer` independently verifies evidence and issues the verdict. Use `harness/templates/BEHAVIOR-EVIDENCE.md` only for cross-screen/state/recovery risk or an unresolved behavior concern; proportional checks and justified `N/A` remain valid for other work. Fixtures never prove live integration. Security review remains reviewer-owned for authentication, authorization, and sensitive data; accounting, payroll, and tax guidance is conditional on actual domain rules and parent scope.
External dependencies are checked in the parent assignment before dependent work. Missing or unknown required setup returns a blocker to the parent; goal-mode continuation cannot authorize retries or substitute test doubles for live evidence.

Native model defaults are explicit: Astra/high at the root and final reviewer, Sol/medium for `router`, `product-planner`, `ui-ux-designer`, `ui-visual-designer`, and `librarian`, and Luna/high for `implementer` and `release-manager`. The release-manager abstract tier is `fast`; Spark/high is an explicitly selected implementation alternative. Parent-owned executor escalation is Luna/high -> Terra/medium -> Sol/medium, never Astra execution. Sol-ceiling failures return to the Astra orchestrator for self-review. Dispatch is bounded to two active workers and depth one.
The parent may assign bounded concurrent implementer instances with disjoint file ownership; do not create permanent FE, BE, DB, or test implementation roles.
