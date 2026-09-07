# Vendored UI/UX skills

These files are immutable upstream snapshots for the AI ERP project. Project skill entrypoints supply local execution paths and ERP-specific guidance; the upstream `SKILL.md` files are preserved without edits.

`skills.lock.json` records each upstream commit and maps every vendored destination to its upstream source path, SHA-256 digest, Git blob ID, and byte count. Paths in the lock are relative to the repository root. Snapshot date: 2026-09-07.

| Skill or reference | Source | License evidence |
| --- | --- | --- |
| `frontend-design/` | [Anthropic skills](https://github.com/anthropics/skills/tree/41bbe19d1a1a7eaab5e7bb9050a417e5c6cffc8f/skills/frontend-design) | Apache-2.0; `frontend-design/LICENSE.txt` |
| `ui-ux-pro-max/` | [Next Level Builder UI UX Pro Max](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill/tree/4aad0584d92131626b16d4ff4d77f0455385013c/.claude/skills/ui-ux-pro-max) | MIT; `ui-ux-pro-max/LICENSE` copied from the upstream repository root |
| `web-design-guidelines/` | [Vercel agent skills](https://github.com/vercel-labs/agent-skills/tree/063bee94c3f4df8453406c830b0a7df0f2860278/skills/web-design-guidelines) | Upstream `README.md` declares MIT; preserved as `web-design-guidelines/README.upstream.md`. This repository has no standalone license file at the pinned commit. |
| `web-design-guidelines/references/web-interface-guidelines/` | [Vercel Web Interface Guidelines](https://github.com/vercel-labs/web-interface-guidelines/tree/e3d624baaf29dc1fc645aff3e38f03e564d2d6b1) | MIT; the reference directory includes its own upstream `LICENSE` |

The Vercel reference license belongs to the reference repository. It is not presented as a missing license file for the separate agent-skills repository.

The UI UX Pro Max data directory includes upstream provenance and font-license metadata. Preserve those files when updating this snapshot. Fonts and icons referenced by the data are not bundled font binaries or a blanket grant for third-party assets.

UI UX Pro Max's search runtime consists of `scripts/search.py`, `scripts/core.py`, `scripts/design_system.py`, and `scripts/reasoning_contract.py`. It uses Python's standard library and local data. Ordinary searches return output; `--persist` writes design-system files under the explicitly supplied output directory, and `--force` permits replacement. Upstream tests and the validator are retained for provenance, but some tests expect the original repository layout and are not the project smoke-test contract.

The upstream Pro Max entrypoint uses Claude plugin paths. Project entrypoints must resolve `vendor/ux-skills/ui-ux-pro-max/scripts/search.py` from the current project location. The upstream Vercel entrypoint fetches live guidelines; project review entrypoints may use the pinned local reference and identify that snapshot. Updates should review upstream changes, replace the relevant snapshot as a unit, and regenerate the lock.
