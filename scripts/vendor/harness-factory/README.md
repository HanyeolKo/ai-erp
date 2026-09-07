# Harness Factory verification snapshot

This directory contains the unmodified validator and provider registries from the project owner's [Harness Factory 0.3.0](https://github.com/HanyeolKo/harness-factory/tree/537ef692042bf9f1895fbd6d3fac48c364307cf4).
`SOURCE.json` records the commit and SHA-256 values; verification needs no installed plugin or network access.

`scripts/verify-harness.py` subclasses only the Codex agent required-key check: `model` and `model_reasoning_effort` may be omitted, as supported by the [current Codex agent contract](https://learn.chatgpt.com/docs/agent-configuration/subagents). The installed agents inherit the parent model. All other upstream validation runs unchanged. The upstream file itself remains byte-identical.

This snapshot is from the user's own repository. The upstream commit contains no license file; this note does not invent a third-party license grant. Review upstream changes and update `SOURCE.json` together when refreshing the snapshot.
