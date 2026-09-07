# Team architecture

`harness/harness-spec.json` owns role IDs, capabilities, handoffs, and evaluator links.

| Role | Responsibility | Output |
| --- | --- | --- |
| `router` | Route bounded tasks and collect reproducible evidence | Routing assignment and evidence bundle |
| `ui-ux-designer` | Plan ERP screens and interactions | Local plan under `docs/ux/` with rationale |
| `reviewer` | Independently review evidence and issue verdicts | Pass/fail result with stable defects |
| `implementer` | Execute parent-approved implementation contracts and verification | `IMPLEMENTATION-RESULT.md` and executed checks |

UI implementation follows `router -> ui-ux-designer -> reviewer -> implementer` after the parent completes the contract.
Non-UI implementation follows `router -> implementer` after a complete parent contract; results return to the parent, who requests independent review.
These are artifact prerequisites controlled by the upper orchestrator, not nested spawn edges. No specialist shortcut or reverse implementation edge is part of the required route.
Reviewer verdicts are never delegated to `implementer`.
