# External dependency gate

Declare task mode as `none`, `offline-contract-only`, or `live-integration`. For every dependency record provider/service, account/tenant/project, API contract/version, externally managed enablement/permissions/OAuth callback or other settings, configuration/key names without secret values, external owner, evidence date, safe check, expected result, and status `verified`, `missing`, `unknown`, or `not-required`.

`unknown` is never ready. `not-required` requires a scope-based reason. A placeholder, mock response, local test, or environment-variable name does not prove external readiness. Separate code work, explicitly scoped offline-contract-only tests, and live activation; offline work may proceed only when it does not depend on missing facts and is never presented as integration success.

If a required external prerequisite is missing or unknown, stop the affected task before dependent edits or execution and return a BLOCKER-REPORT to the parent.

At most three total attempts apply only to repairable implementation defects, including the initial attempt and any escalation. External blockers stop immediately, consume no retry attempt, and cannot authorize model escalation or a model sweep. Do not retry an unchanged external blocker. Do not invent credentials, accounts, tenant IDs, OAuth settings, permissions, provider responses, or alternate providers. Do not bypass authentication or validation. Goal-mode continuation does not override external prerequisites; a blocked task remains blocked until evidence resolves the blocker.

The blocker names the external owner, current affected scope and diff, granted and ungranted actions, concrete resolution options, and exact resume checks. Options are: the external owner supplies the missing setup; use a verified authorized existing integration; or obtain an explicit scoped deferral/offline alternative. Resume requires current target-specific evidence, updated affected plan/contract revisions, applicable policy reviews, and a parent reissued or confirmed assignment.
