# Decisions

## D-001: Install bounded UI/UX routing

- Source: user request to create a UI/UX specialist, download and configure related skills, route all screen planning through the specialist, track the setup in Git, and merge its PR.
- Factory: https://github.com/HanyeolKo/harness-factory ; ref `0.3.0`; commit `537ef692042bf9f1895fbd6d3fac48c364307cf4`.
- Mode: `create`; no prior project harness or provider files were found.
- `core`, Codex-only projection, `balanced` abstract tiers, attended operation, and limits of 4 parallel tasks / depth 2 are bounded implementation choices inferred within the requested setup.
- No separate profile-selection answer was elicited. The receipt's `request` source records the setup authorization, not a fabricated response naming `core`.
- Core includes routing, planning, evaluation, verification, and reporting; it omits adaptive memory, self-evaluation, improvement, and governed learning controls.
- Canonical contracts use English; reader reports use Korean with localized prose, matching this conversation.
- All screen planning requires `router -> ui-ux-designer -> reviewer` before implementation; implementation-only backend tasks retain their existing workflow.
- Execution, evidence collection, and verdict ownership are explicitly separated across the three roles.
- Existing user authorization covers this setup, downloads, Git tracking, PR and merge, and the requested Notion archive. Further external writes outside that scope retain approval boundaries.

## D-002: Native model inheritance

Codex agent wrappers omit `model` and `model_reasoning_effort` to inherit the invoking user's configuration. Factory `0.3.0` requires these optional native keys; the project verifier documents this narrow compatibility difference without inserting a dummy model or altering deployed settings.
All other role metadata, instruction bytes, sandbox access, managed skills, path safety, and structural checks remain applicable.

## D-003: Vendor and archive ownership

Upstream UI/UX skills are pinned under `vendor/ux-skills/`; concise project skills select them progressively and adapt paths to the project.
Local files are canonical. Substantial reader documents are then archived to Notion database `AI 생성문서 관리` (`345de20d-f69b-4fe8-b79b-a204c819a7f8`) with their local source and synchronized date.
