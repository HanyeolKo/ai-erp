# UI/UX specialist and lightweight implementation setup

This report records the project-owned four-role harness extension for AI ERP.

- `ai-erp-router`, `ai-erp-ui-ux-designer`, `ai-erp-reviewer`, and `ai-erp-implementer` are the native roles. The upper agent analyzes scope, requirements, architecture, permissions, UX, and acceptance criteria; the implementer executes only its approved contract.
- Every screen planning request must follow `router -> ui-ux-designer -> reviewer`. UI implementation adds `implementer` only after the specialist plan, independent `ui-plan-review`, and an explicit upper-agent contract. Planning-only work ends after review without implementation.
- Non-UI code, tests, and behavior-affecting configuration follow `router -> implementer` after the upper-agent contract. The upper agent independently reviews the result and owns final approval; the implementer never owns routing, verdicts, or merge decisions.
- The implementer defaults to `gpt-5.3-codex-spark` with `model_reasoning_effort=high`; `gpt-5.6-luna`/`high` is the documented fallback only when Spark is unavailable or quota-limited. Future comparable lightweight models may be adopted after upper-agent feasibility assessment and a matching policy and verifier update.
- Three pinned UX skill sources remain under `vendor/ux-skills/`, preserving the original 80 files, provenance, licenses, and Git lock records. The harness, projections, and verifier are Git-managed.
- The bounded `core` profile and the known Harness Factory `0.3.0` optional-model-key compatibility shim remain intact. The project verifier relaxes only those inherited-wrapper keys; the factory validator is not modified.
- Local reader documents remain canonical and are archived to Notion `AI 생성문서 관리` after local completion, with the local path and synchronization date when practical.

Spark execution was interrupted by quota; the parent record at `harness/maintenance/runs/add-spark-implementer/execution-evidence.json` records the actual Luna selection and execution evidence. Final local validation and independent assessment are recorded under `harness/maintenance/runs/add-spark-implementer/`; CI and merge state are available at https://github.com/HanyeolKo/ai-erp/pull/7.

Final local structure validation, all 22 regression tests, offline UX search smoke, and whitespace checks passed. The independent task-review passed for harness structure and delegation-policy installation. Application code was unchanged; this verdict does not certify screen behavior. Git delivery and archive evidence are maintained by the parent, with current CI and merge results linked above.
