# Increment plan review rubric

Runner: `router`. Verdict owner: `reviewer`. Executor: `product-planner`.

| Criterion | Required evidence |
| --- | --- |
| Assignment | Matching task, increment, plan, contract revision, parent, and source/base Git evidence |
| Product scope | Problem, users/value, priorities, business rules, current slice, and deferred scope |
| Cross-layer impact | API, data, permissions, frontend, backend, dependencies, and resolved current assumptions |
| Acceptance | Observable numbered criteria and feedback/revision links |
| Boundaries | No screen/layout/interaction, code, test, release, routing, or verdict ownership |
| Revision safety | Changed plan revision identifies affected downstream evidence as stale |

Every applicable criterion must pass with independent evidence. Missing, stale, failed, or pending required evidence fails. Screen decisions require `ui-plan-review` separately.

External dependency readiness records mode, provider/service, target account or tenant, contract/version, managed settings, owner, evidence date, safe check, expected result, and status. Unknown is never ready; unresolved required setup is blocked for current implementation and may remain only as a bounded future planning dependency.
