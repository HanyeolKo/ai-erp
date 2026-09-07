# Integration contract

- Task: absorb-superpowers-integration; revision: 1; parent: /root; status: ready.
- Objective: resolve rebase of 48956e8 onto origin/main 1c94f45, preserving upstream expanded harness and our reviewed execution/Notion additions.
- Source evidence: active Git rebase conflicts only in loop, state, and contract template. Upstream adds tiered verification, planning/release roles, external gates, model policy and compact records.
- Risk: high, conservative because execution-loop integration touches guard-adjacent text; independent Astra/high review required. No UI plan: no screen change. No deployment requested; Git PR delivery is parent-owned.
- Executor: explicit gpt-5.6-luna/high; generic dispatch canonical role harness/team/agents/implementer.md; no escalation.
- Context: read current AGENTS, relevant loop/templates, upstream versions via git show origin/main:<path>, and conflict stages. Do not scan unrelated app code; return concise result under 1200 words. Token/cache usage unavailable: null.
- Exact allowed edits: harness/loops/EXECUTION-LOOP.md, harness/state/state.json, harness/templates/IMPLEMENTATION-CONTRACT.md; integration result/check log files under harness/maintenance/runs/absorb-superpowers/ named INTEGRATION-* only.
- Preserve automatic merges in AGENTS.md, HARNESS.md, RESULT template and all existing files. No Git index/commit/rebase operations: parent owns those. No provider/skill/code/test/config policy changes.
- Decisions: keep origin/main execution steps and external/retry gates, append concise absorbed execution practices without reinstating mandatory full contracts/reviews for low/standard tasks. Preserve upstream state/queue/next_action unchanged (drop our stale next_action edit). Contract template includes BOTH external readiness and absorbed verification fields.
- Acceptance: (1) conflict markers removed from three files; (2) upstream routing/tier/compact-record/model/external/attempt policies preserved; (3) original conditional root-cause/regression/evidence/feedback/ownership practices and Notion queue retained; (4) structural and verifier tests pass with recorded output. No unresolved design decisions.
- External readiness: GitHub authentication and origin fetch succeeded; mode live-integration for parent Git delivery, worker local integration only. Plugin already uninstalled; no plugin dependency.
- Checks: python -B scripts/verify-harness.py; python -B scripts/test_verify_harness.py; git diff --check. Expected exit 0; note staged conflict metadata until parent stages resolution. Parent runs --require-tracked after staging; CI covers full repo. UX smoke already passed and UX runtime unchanged, so reuse it.
- Worker acknowledges assignment and returns ready-for-review with actual model/task invocation and check outputs. Parent will request independent reviewer after rebase completion and owns acceptance/push/PR/merge.
