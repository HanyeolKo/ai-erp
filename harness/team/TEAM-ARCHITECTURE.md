# Team architecture

`harness/harness-spec.json` owns role IDs, capabilities, domains, handoffs, and evaluator links.

| Role | Responsibility | Output |
| --- | --- | --- |
| `router` | Classify requests, delegate, collect reproducible evidence | Bounded task and evidence bundle |
| `ui-ux-designer` | Plan ERP screens and interactions | Local screen plan with rationale |
| `reviewer` | Independently assess evidence and stable defects | `pass` or `fail` with criteria |

Every screen planning task follows `router -> ui-ux-designer -> reviewer` before implementation.
The requesting orchestrator runs checks and stores evidence; the designer does not approve its own plan.
The reviewer returns findings to the orchestrator. Remediation is governed by the execution loop rather than a reverse normal handoff.
Parallelize independent research or review only within declared limits. Native wrappers inherit the user's model; the `balanced` tier is a provider-neutral cost preference.
