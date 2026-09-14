# Task assignment

- Task/increment: ui-interaction-completion; revision: r1.
- Parent/decision owner: /root. Risk: standard frontend interaction correction; release remains a separate high-risk gate.
- Cwd/base: D:/onedrive/Documents/ChatGPT/AI ERP/tmp/ui-interaction-completion; 49d79fe553bb4048a72671b503609e2d2c38ce58; branch codex/ui-interaction-completion.
- User request: complete missing UI refactor behavior. Production notification entry still navigates to a separate page instead of a popup; calendar date navigation and UI alignment remain unsatisfactory.
- Existing evidence: PR 13 merged CSS-only styling and ERP-WORKSPACE-01/v2; latest production workflow 34794956458 succeeded at base SHA. Source still routes /notifications to Account.Notifications and renders calendar period buttons with a native date field. Parent is inspecting production read-only.
- Required route: functional ui-ux-designer -> read-only ui-visual-designer -> independent ui-plan-review -> parent-approved implementer. No screen implementation before review.
- Current recipient: ui-ux-designer, native Sol/medium; no delegation or escalation. Acknowledge assignment and return to parent.
- Permitted reads: project instructions, relevant role/skills, frontend source/tests, existing docs/ux/latest-ui-refactor-* and templates.
- Permitted writes for functional specialist: docs/ux/ui-interaction-completion/SCREEN-PLAN.md only. No code/tests/config/Git/external writes.
- Deliverable: bounded functional plan using SCREEN-PLAN template; distinguish preservation of business behavior from user-authorized changes to presentation and navigation. Identify concrete source/test paths and checks for popup, date navigation, and layout consistency.
- Acceptance: (1) notification entry opens a popup while preserving current page context and existing read/link/pagination semantics; direct notification URL compatibility is explicitly considered; (2) calendar date navigation is coherent and aligned across desktop/mobile, preserving timezone, filters, valid date ranges and recent drag/resize behavior; (3) retain existing backend/API/auth/permission contracts and ERP-WORKSPACE-01/v2; (4) keyboard/focus/close/loading/empty/error states covered; (5) no fabricated production observations.
- External mode: offline-contract-only implementation planning; no external provider integration required. GitHub read evidence ready 2026-09-14; parent owns authenticated production UI read-only inspection. No production data edits, notification read mutations, OAuth or mail operations during diagnosis.
- Source ownership: workers are not alone; do not revert others. Main checkout remains untouched. Two active workers maximum, depth one.
- Context: this assignment, relevant source and existing pattern; minimal fork. Return budget: 250 words plus artifact path. Provider token/cache usage: null; model escalation: N/A.
- Validation: source inspection only for planning; parent will assign changed-area tests, typecheck/build and actual browser smoke after approved implementation. Not run checks must be explicit.
- Review owner: parent dispatches independent reviewer after visual proposal. Parent ready-to-dispatch: ready. Release mode: not-requested in planning; prior correction publication authorization retained for later concrete release gate.
- Stop/return: unresolved functional ambiguity or external prerequisite returns to parent; never self-approve implementation.
