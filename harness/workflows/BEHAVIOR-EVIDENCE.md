# Behavior evidence workflow

Behavior evidence is a conditional handoff for implementation work whose risk depends on cross-screen/state/recovery behavior or an unresolved behavior concern. The parent decides applicability from the actual change scope and records the decision in the contract or compact task record.

When applicable, the parent prepares bounded scenarios before execution. Each scenario states the task and contract revision, target environment, external-readiness status, trigger, expected outcome, and required evidence. The parent records required dimensions and justified `N/A` dimensions; a small edit keeps proportional checks without a mandatory specialist document.

The router observes results read-only and records the actual revision, environment, evidence source, and observed outcome beside the expected outcome. The router does not edit implementation files, invent production results, or issue the verdict.

The implementer owns all code and test changes and runs the contracted checks. It records exact commands, exits, output paths, and the revision/environment used. Fixtures and mocks can support offline checks, but they never count as live integration evidence. Missing or unknown required external readiness stops dependent checks and returns a `BLOCKER-REPORT`.

The reviewer independently verifies the scenario evidence and issues the required verdict. It checks that expected and observed outcomes are distinct, revision and environment are current, and any unavailable live prerequisite is reported as blocked rather than passed. The parent retains acceptance.

When applicable, cover these dimensions:

- authorization refusal or revoked access;
- failed save or conflict handling;
- recovery followed by another edit;
- actual affected-screen visibility and keyboard behavior.

Security checks remain reviewer-owned for authentication, authorization, or sensitive-data changes. Accounting, payroll, and tax guidance is conditional on actual domain rules and parent scope; it does not create a permanent role or bypass existing authority. No new specialist role is required.
