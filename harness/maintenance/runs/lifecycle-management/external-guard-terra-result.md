# External guard Terra result

Status: ready-for-review

The parent-authorized Terra/medium correction changes only `scripts/verify-harness.py` and `scripts/test_verify_harness.py`. `has_unnegated_rule` now accepts the external rules only at a genuine sentence, list, heading, semicolon-clause, or validated inline policy-label boundary. It retains local directive checks, so a rule introduced by `Do not enforce`, `Ignore`, or `Disregard` is non-operative. This prevents arbitrary non-operative prefixes such as `Treat as optional:` without treating all policy prose as invalid.

Acceptance evidence:

1. Baseline verification passes, including shared-paragraph references and an independent preceding sentence.
2. Focused mutations fail for `Do not enforce this rule:`, `Ignore this rule:`, `Disregard the requirement:`, and `Treat as optional:`.
3. Regression batches passed 77 tests total; structural verifier, UI/UX smoke, provider-path preflight, and `git diff --check` passed.

External dependency mode is `none`; no live integration, external write, deployment, or configuration change was performed. Detailed raw check evidence is in `checks.json`. Independent task review remains parent-owned.
