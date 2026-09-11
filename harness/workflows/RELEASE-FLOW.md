# Release flow

1. Parent links an accepted increment, implementation contract/result, review, exact SHA, and release assignment.
2. Release-manager prepares `RELEASE-CONTRACT.md` and `INCREMENT-RECORD.md`; parent exposes publication effects and authorization requirements.
3. Reviewer independently runs `release-review` in `readiness` mode; readiness is distinct from deployment completion.
4. Only after independent readiness and complete authorization may the parent dispatch execution of existing commands.
5. Release-manager returns the execution `RELEASE-RESULT.md` to the parent; the independent reviewer then runs `release-review` in `outcome` mode, and the parent records acceptance.
6. Record same-SHA CI, environment, artifact, DB compatibility, backup boundary, smoke scope, active/prior release, manifest/events, cleanup, recovery, and next action.
7. Publishing by push or merge to `main` triggers CI; a successful `main` CI run may trigger production deployment through `.github/workflows/deploy-production.yml`. Manual `workflow_dispatch` does not check CI history itself, so the parent must provide same-SHA successful CI proof before authorization. Preparation must disclose this publication effect before authorization.
8. Mark unresolved recovery, including exit 2, `operator-action-required`; do not mark it deployed or complete.
9. Feedback updates the next bounded plan revision. Planning-only or non-release increments record justified `not-requested`.
