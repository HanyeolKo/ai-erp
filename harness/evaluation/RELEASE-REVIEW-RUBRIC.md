# Release review rubric

Runner: `router`. Verdict owner: `reviewer`. Executor: `release-manager`.

Review mode is `readiness` for preparation or `outcome` after execution. Action mode remains `preparation` or `execution`.

| Criterion | Required evidence |
| --- | --- |
| Linkage | Matching increment, plan, implementation, release revisions and accepted implementation review |
| Identity | Exact reviewed 40-hex SHA, artifact identity, target environment, cwd, and same-SHA CI success |
| Authorization | Parent assignment, authorized operation/commands, and publication effect |
| Data safety | Migration inventory/checksum, old-app compatibility, backup/restore boundary, and rollback plan |
| Observation | Preparation records planned observation criteria; execution records active/prior release, manifest/events/output pointers, cleanup, recovery outcome, and next action |
| State | Preparation readiness differs from deployed; operator-action-required blocks deployed/complete |
| Limits | No fabricated evidence; insecure/resolved-IP smoke does not prove public DNS/TLS; app rollback does not restore DB |

Every applicable criterion must pass with independent evidence. Missing, stale, failed, or pending required evidence fails. Planning-only and non-release work records justified N/A.

Required external dependency setup must be target-specific and current. Missing or unknown API, account, permission, OAuth, or managed setting blocks release preparation or execution and requires a BLOCKER-REPORT; mocks and goal-mode continuation cannot relabel it as ready or deployed.
