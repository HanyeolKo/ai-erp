# Project management redesign — backend execution contract

- Task: project-management-redesign / backend r1.
- Base: `1e2435b7b88fdb91e234e74122e5a4b430d3f50c`, branch `codex/project-management-redesign`.
- Parent decision owner: `/root`; execution: actual Spark/high or documented Luna/high fallback; independent verdict: reviewer.
- Status: READY. Specialist plan `docs/ux/2026-09-07-project-management-redesign-plan.md` completed by `/root/project_redesign_designer`; independent `/root/ui_plan_reviewer` issued ui-plan-review PASS for all nine required criteria and found no backend-contract blocker. Parent `/root` authorizes execution of this exact scope. Reader review evidence: `docs/ux/2026-09-07-project-management-redesign-review.md`.

## Objective and evidence

The user explicitly rejects PR8's dead-end group requirement, administrator request copying, flat global navigation, and ambiguous invitation flow. They explicitly selected shared code/link invitations: recipients sign in and join. A project must be directly creatable before inviting members from within that project. Existing group and project authorization remain separate.

Current evidence: ProjectController requires groupId and GroupAccess OWNER/ADMIN; no group creation entry exists. InvitationService requires GroupMember plus Project MANAGER yet acceptance adds only ProjectMember. Email-bound ProjectInvitationEntity is single-use and cannot implement shared membership. Members and current-user responses expose UUIDs without human names. changeRole lacks last-manager concurrency protection.

## Allowed changes

- `backend/src/main/java/com/aierp/project/**`: project creation, roles/members responses, legacy invitation permission consistency; new ProjectShareInvitationEntity/Repository/Service and API controller.
- `backend/src/main/java/com/aierp/group/api/GroupAccess.java`: module-owned atomic group bootstrap only.
- `backend/src/main/java/com/aierp/identity/api/CurrentUserController.java` and new `IdentityProfiles.java`: bounded internal profile read DTOs and additive current-user display fields.
- `backend/src/main/resources/db/migration/V7__add_project_share_invitations.sql`: additive share-invitation and project-creation-request tables only. Never edit V1–V6.
- Backend test/integration sources directly covering these contracts; existing REST Docs/OpenAPI tests must match additive fields/endpoints. No unrelated test deletion.
- Deployment migration whitelist/checksum code and directly affected tests under `scripts/`: register V7 with the same strict validation as V6, never weaken guards.
- Generated API outputs through existing commands; no handwritten generated TypeScript.
- No frontend implementation, production data, credentials, git publication, deployment, architecture/harness changes, or external messages.

## Direct creation

1. `POST /api/v1/projects` accepts `{name, groupId?, requestId?}`. Validate trimmed name 1–200 before any writes. requestId is an optional UUID for backward compatibility; new UI always supplies one.
2. Missing/null groupId means explicit direct creation: call group module public method to save a new group (name matches project), assign only its creator OWNER, then save project and creator MANAGER in the caller's single transaction. Group method requires MANDATORY transaction; no REQUIRES_NEW. Any failure rolls back all four records.
3. Supplied groupId uses existing OWNER/ADMIN authorization with its lock. Unknown/unauthorized supplied groupId must fail; never silently create a new group or elevate an existing member. Existing creation-options remains compatible.
4. Preserve existing ProjectResponse shape and list pagination. If requestId is present, obtain a PostgreSQL transaction advisory lock namespaced by userId and requestId, using the already established `select 1 from pg_advisory_xact_lock(hashtextextended(:scope, 0))` pattern. Read an additive project.project_creation_request mapping with unique(actor_id,request_id), normalized name, requested_group_id nullable, project_id FK and UUID primary key. A same-user/key replay with matching name/requested group returns the existing project after checking current membership and actual role; a mismatched payload returns409. Save the mapping in the same transaction as all creation records; failed creation leaves no mapping or orphan group. Never deduplicate by name alone or across users. Legacy absent-requestId calls remain supported but have no retry guarantee. UI performs no automatic POST retries.

## Shared invitation data and interfaces

5. Separate table in project schema, one row per project: project_id PK/FK, code unique varchar(16), invited_by UUID, created_at timestamptz, expires_at timestamptz, revoked_at nullable timestamptz. Keep existing email invitations intact.
6. Code is 16 independently uniform Crockford characters `0123456789ABCDEFGHJKMNPQRSTVWXYZ` from SecureRandom (80 bits). Never UUID substring, timestamp, Math.random, or 6-digit code. API accepts upper/lower case, ASCII spaces and hyphen grouping, then enforces exactly 16 canonical chars and a small input length bound. UI displays 4 groups of 4. The same secret powers the share URL and pasted code.
7. `GET /api/v1/projects/{projectId}/share-invitation`: current MANAGER only, no writes. Return `{state,code,expiresAt}` with state NOT_CREATED/ACTIVE/EXPIRED/REVOKED; code/expiry nullable only when NOT_CREATED. Expired/revoked are not usable.
8. `POST` same URL explicitly creates or rotates the code for seven days from server time; replacement invalidates the old code. Return ACTIVE DTO. `DELETE` same URL revokes idempotently and returns 204. Never send email or share externally.
9. `GET /api/v1/project-invitations/{code}`: authenticated verified Google user only. Unknown/rotated code returns 404. Return `{state,projectId,projectName,inviterName,role,expiresAt,alreadyMember}`. ACTIVE includes name of the manager who most recently generated this code, role MEMBER for new joins, and alreadyMember. Missing/blank/email-shaped (contains @) inviter display name is replaced by `프로젝트 관리자`; never return inviter email before membership. EXPIRED/REVOKED have no projectId/name/inviter/role fields (null), only safe status/expiry and alreadyMember=false. Reading never adds membership.
10. `POST /api/v1/project-invitations/{code}/join`: revalidate code and time under lock; inactive returns 409. Save exactly one new MEMBER and INVITATION_ACCEPTED event only for a new membership; return existing ProjectResponse. Existing MANAGER/MEMBER/VIEWER keeps its role and succeeds without another membership/event. No GroupMember is created on join and no existing group role changes.
11. Auth/CSRF/400/401/403/404/409 problem contracts stay intact. All new endpoints protected; no token/profile-list public HTTP endpoint. Never log invitation secrets.

No new distributed rate limiter is in this scope: verified authentication, 80-bit random codes, bounded parsing and non-disclosing invalid responses are the selected bounds. Do not claim unimplemented throttling. Shared POST rotation is deliberately not automatically retried; UI recovers an ambiguous response via current-code GET before enabling another explicit rotation.

## Permission and concurrency

12. Project row lock precedes reading current acting MANAGER, target member, or current share invitation for all rotate/revoke/join/role changes and legacy email invite creation. Join first obtains scalar projectId by code, then locks project, then reloads code row; code could have rotated while waiting. Recheck actual code, revocation and expiry after lock.
13. Project MANAGER governs project invitations regardless of GroupMember; remove legacy invite's extra group membership restriction while preserving groupId/project match and current MANAGER check.
14. Role update locks project, reloads actor and target, prevents demoting the last MANAGER (409), allows no-op, and serializes concurrent demotions. No global admin bypass. Role mutation never changes GroupRole.
15. Internal `IdentityProfiles` reads at most 200 distinct IDs through identity-owned repository; returns immutable public module DTOs. No cross-schema repository/Entity access or joins. Only authorized project members/current user/valid-code preview receive necessary profiles. MemberResponse adds displayName and email (nullable fallback), CurrentUserResponse adds displayName and email while preserving existing fields. Missing profile displays a neutral name rather than exposing another account's data.

## Required observable acceptance and validation

- B01: zero-group verified user creates project, owned group, OWNER and MANAGER atomically; rollback proves no orphan records. Same-user/key reply-loss replay and concurrent replay return one project/group; payload mismatch409; same key on different users does not collide.
- B02: supplied unauthorized/null-role/member group cannot be used or elevated; existing OWNER/ADMIN path still succeeds.
- B03: MANAGER without GroupMember can issue project invitation; MEMBER/VIEWER/nonmember cannot issue/read/revoke; wrong legacy groupId denied.
- B04: real secure code format, seven-day expiry, rotation invalidation, revoke and GET no-side-effects; valid preview contains human project/inviter names, invalid status no protected metadata.
- B05: different accounts reuse a valid code; repeated same-user join is idempotent and preserves existing roles. Concurrent joins persist once. Expiry/revoke/rotation race cannot admit after invalidation.
- B06: concurrent last-manager demotions retain at least one manager; demoted actor cannot rotate/revoke after lock wait.
- B07: unauthenticated and missing-CSRF writes fail; bounded identity profile reads and module verification pass; no unrelated account enumeration.
- B08: V6→V7 preserves legacy invitations/roles/projects; V7 deployment whitelist/checksum and missing/extra/modified SQL guards remain strict.
- B09: REST Docs generates new endpoints and additive fields; all existing backend behavior tests pass with new expectations only where explicitly changed here.
- Commands: Java25 `backend/gradlew.bat clean test openapi3 bootJar compileIntegrationTestJava`; `pnpm api:generate`. Run meaningful focused tests first. Real PostgreSQL integration and Linux deployment suite require Docker/Linux CI; report local unavailability honestly. Root will run full CI after delivery approval.
- Result must enumerate actual model/high invocation evidence, fallback reason, changed files, commands/exit codes, AC evidence, checks not run, deviations, and ready-for-review (not PASS). Return any missing decision to parent before implementing it.
## Parent addendum: bounded participant identities (revision 2)

The approved human identity requirement also applies to participants outside the first page of project members. Enrich only an authorized single-schedule detail response with nullable participant `displayName` and `email`. Resolve the schedule within the requested project after checking access, then call the existing `IdentityProfiles.find` once with actual stored internal participant IDs (at most 200). Do not scan all project members, add a lookup endpoint, enrich list/dashboard unions, or change the database. Missing profiles and external participants have null new fields; existing `externalEmail`, IDs, revisions and acknowledgements remain unchanged.

Additional permitted paths are `backend/src/main/java/com/aierp/schedule/ScheduleService.java`, `schedule/api/ScheduleController.java`, directly related backend tests and REST Docs, and normal generated OpenAPI artifacts. Frontend schedule implementation owns `Detail.tsx`, `ScheduleForm.tsx` and related tests/fixture. Detail consumes its authorized response fields instead of the first members page; stored selections in the editor retain both IDs and available names. Participant privacy follows the detail access boundary, including cached success followed by 403 and then 500.

Required evidence includes identities at positions 101 and 200, one exact bounded profile lookup, missing/external profile handling, zero profile calls for denied/wrong-project/missing schedules, no list/dashboard enrichment, and preserved acknowledgement/selection behavior. The complete temporary execution slice is `tmp/project-redesign-participant-identity-contract.md`. The parent approved this as completion of the existing specialist plan after independent contract analysis; the implementer still does not own the acceptance verdict.

Actual model routing: explicit Spark/high executions were attempted after usage became available and terminated with quota errors; continued execution uses the documented Luna/high fallback. Preserve both invocation evidence and checks not run in execution receipts. Last archive synchronization: 2026-09-07 (Asia/Seoul).
