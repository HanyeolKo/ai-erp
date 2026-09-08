# Implementation contract

- Task: absorb-superpowers; revision: 2; owner: parent /root.
- Base: a7f9b329d676a5babd6feae4b95043b5010a506d with existing user-authorized Notion queue edits in AGENTS.md preserved.
- Objective: retain useful Superpowers execution principles in existing AI ERP guidance before parent uninstalls the plugin.
- Evidence: execution loop already provides contracts, independent review, verification output, and three bounded retries; source skills add root-cause debugging, meaningful regression checks, current-revision evidence, and critical review handling.
- Design: extend existing execution guidance and templates; keep core profile, role routing, evaluator semantics, permissions, models, and provider files unchanged.
- Permitted edits: AGENTS.md (one pointer within managed block); harness/HARNESS.md (one execution-loop pointer); harness/loops/EXECUTION-LOOP.md; harness/templates/IMPLEMENTATION-CONTRACT.md; harness/templates/IMPLEMENTATION-RESULT.md; harness/state/state.json (next_action only); harness/maintenance/runs/absorb-superpowers/IMPLEMENTATION-RESULT.md and check logs in that same run directory.
- Exclusions: no code, tests, vendor, skills, role definitions, native adapters, dependencies, commits, external writes, or plugin removal by worker. Do not revert other work.
- Required principles: reproduce/inspect evidence before fixing; explicit testable hypothesis and one focused change; reassess after repeated failures within existing retry bound; behavior-focused regression red/green when warranted and feasible; no new tests for reversible low-impact changes or implementation-mirroring assertions; relevant existing checks still run; fresh evidence tied to reviewed revision and rerun affected checks after edits; assess review feedback against requirements/code with evidence-based disagreement; keep scope small and preserve user edits; delegate only independent bounded work with explicit ownership when authorized; worktree only when isolation is needed.
- Existing authorizations are reused; do not introduce blanket approval, mandatory worktree/TDD, new reporting artifacts per ordinary task, or extra review stages.
- Review correction AS-001: configuration-only defects still require diagnostic evidence and a cause hypothesis. Limit N/A to non-defect maintenance. If reproduction is infeasible, record the reason and alternative evidence; do not guess or bypass diagnosis. Apply in loop and contract template, then rerun the same checks and update result to revision 2.
- Acceptance criteria:
  1. Principles above are concise, actionable, conditional, and reachable from cold-start/AGENTS guidance without Superpowers installed; English canonical text, under 100 lines per guidance file.
  2. Existing UI gates, implementer model rules, independent reviewer authority, core profile, provider parity, and Notion reading-queue rules are preserved.
  3. Contract/result templates capture conditional regression evidence or rationale and the reviewed revision/evidence validity without mandatory extra artifacts.
  4. Required verification succeeds and actual command/exit/output evidence plus model invocation are recorded.
- Validation: python scripts/verify-harness.py; python -B scripts/test_verify_harness.py; python scripts/smoke-ux-skills.py; git diff --check. Expected all exit zero. Baseline verify-harness passed before edits.
- UI prerequisite: no; workflow-only guidance, no screens or product behavior.
- Unresolved decisions: none. Parent ready-to-implement determination: ready.
- Worker returns ready-for-review, never final approval. Parent separately requests reviewer and removes plugin after pass.
