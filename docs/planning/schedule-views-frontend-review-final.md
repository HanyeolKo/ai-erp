# Frontend final re-review

- Verdict: **FAIL / CHANGES_REQUESTED**, 2026-09-22; independent `/root/views_frontend_review`, Astra/high. Exact frontend SHA: `966e6f48e511026c0a71898997c080018d6271ff`.
- ACK: implementation r1/API r2/functional+visual r1; approved Sol/medium attempt3. Scope and routing PASS. Functional acceptance and coverage FAIL; parent acceptance pending. No production/test/config edits.
- Reused: parent/executor 267/267 tests, typecheck/build/API-generation/syntax PASS; browser README records shared creation, successive option saves, name-draft preservation, dashboard editing, focus/context and mobile checks. These do not cover the remaining paths below. Old `.vitest-final-baseline.json` is failed intermediate evidence, not the reported final run.

## Remaining stable findings

Locations abbreviated `SW=frontend/src/screens/ScheduleWorkspace.tsx`, `SC=frontend/src/screens/Schedules.tsx`. Reproductions are source-proven unless otherwise stated.

| ID / severity | Reproduction, actual versus required, regression |
| --- | --- |
| SV-FE-001 / P2 | SC330–342: ten pages containing duplicate IDs can exhaust the loop with `hasMore:true` but fewer than1000 unique records; `partial` remains false. Mark every exhausted incomplete traversal partial. Test overlapping pages at the cap. |
| SV-FE-003 / P1 | SW38,44,67: MEMBER opens shared settings; immutable selector remains SHARED and Save POSTs SHARED, rejected403. Required personal-copy flow must POST PERSONAL. Test member-copy versus manager-edit. |
| SV-FE-006 / P1 | SC336–345: page1 succeeds, page2 returns403/404; catch returns successful editable partial data, so denial callback never fires. Authorization/session failures must escape, hide protected results and lock writes. SW44,83–96,135–136,159 also leave mutation controls enabled after403. Test denial followed by failed recovery for calendar, configuration and dashboard. |
| SV-FE-007 / P1 | SW154–161: DashboardCard initializes draft/version once. Refetch after another writer leaves old displayed values beside updated legend; Cancel updates values but not version, so subsequent save repeatedly409s. Synchronize clean drafts/revisions and provide intentional conflict recovery. Test external update→refetch→edit/cancel/re-entry. |
| SV-FE-009 / P2 | SW134,144: from Cards page2, switch view; setPage(0) is undone by hashchange because setContext preserves page2. Saving changed filters also preserves page. Write page0 into URL on view/config change. Test switch/filter-save from nonzero page. |
| SV-FE-010 / P1 | SW87–92: change an option, Close→Continue editing. Discard branch unmounts OptionEditor; remount reads server options, losing draft. Existing test covers parent-owned name only. Preserve child draft through confirmation; test unsaved option add/rename/order. Record form/detail navigation also lacks dirty-discard protection. |
| SV-FE-011 / P2 | SW60: NUMBER filter input lacks step=any;1.5 prevents form submission. Independent jsdom command exit0 confirmed `valid:false,stepMismatch:true`. Test fractional filter save; record-number controls are repaired. |
| SV-FE-013 / P1 | Six workspace tests omit the above denial/conflict/paging cases; no1000/partial regression exists. Passing267 is valid bounded evidence, not complete contract coverage. Add the named regressions without weakening retained assertions. |

Closed original findings: SV-FE-002,004,005,008,012. Other findings are partially repaired, with remaining behavior precisely bounded above. Eight unresolved IDs: five P1, three P2.

No full-suite rerun; no browser/backend/PostgreSQL/CI/deployment/Notion execution here. Parent owns bounded corrections, acceptance and release decisions; preserve prior reviews and failed evidence.
