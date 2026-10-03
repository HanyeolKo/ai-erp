# F1 repair 3 actual-runtime checklist

This is a parent-run observation checklist for the exact repair 3 candidate. It does not replace independent review. Record candidate source/bundle/helper hashes, commit, Chrome version, viewport, DPR, role, scenario, route, and fixture-only external readiness in every bundle. Store raw output under `D:/onedrive/Documents/ChatGPT/AI ERP/tmp/pm-implementation-2026-10-03/browser/f1-observations/`.

## Minimal execution order

1. **Candidate prerequisite.** Match the final manifest and served JS/CSS before observation. Preserve the R2 files as baseline failure or partial-success evidence only.
2. **MANAGER / `none` / `#/projects/p1/plan`.** At 1280x900, verify initial TASK loading is visible inside the retained heading/filter region, then verify the populated default state. Measure enabled quick-create, filter, default/advanced, row, and overview controls at `>=44px`; single-line TASK rows at 52–60px; persistent visible search/state/assignee labels; no ordinary overflow. In default mode, advanced navigation must have `display:none`, zero rect, and no accessibility-tree exposure. DOM presence or a `hidden` attribute alone is insufficient. Save `r3-default-1280-{snapshot,dom}.json` and screenshot.
3. **Precedence and continuity on the same server.** Open `?mode=default&view=board`, `?mode=default&types=...&scopeId=<valid fixture id>`, and corresponding URLs without `mode`. Expect compact mode whenever `mode=default`, preserved query values, and advanced mode for every valid legacy selector without `mode`. Enter a dirty quick-create title/state, switch default/advanced, leave to `#/projects/p1`, and return. Expect the same session/project draft. Save URL, form state, and request evidence as `r3-mode-continuity.json`.
4. **Recovery variants.** `MEMBER/conflict`: 409 retains the immutable payload and requires deliberate recovery. `MANAGER/lost-response`: the socket is destroyed after apply; capture DevTools POSTs and server stdout to determine whether fetch/browser transparently resends. Do not describe this as a manual retry. `MANAGER/unknown-response`: HTTP 500 leaves the form locked; the visible manual action must replay the same requestId and canonical payload, refetch the applied resource, unlock, and give the next submitted edit a new ID. Save `r3-{conflict,lost-response,unknown-response}-{ui,network,server}.json|log`. Confirm route leave/return while unknown retains the same ID/payload.
5. **Responsive default only.** Repeat affected target/label/visibility/overflow checks with real device emulation reporting `innerWidth=390`, not the earlier clamped 500px resize. Repeat at 640x450 CSS pixels/DPR2 and label it **200%-equivalent**, because native zoom remained unchanged. Save `r3-default-{390,equivalent200}-{dom,screenshot}.json` with the emulation method.
6. **Overview authority.** On `#/projects/p1`, MANAGER and MEMBER show plan-create actions. VIEWER shows work-open plus an adjacent read-only reason and no active create link in either header or TASK region. Save `r3-overview-{manager,member,viewer}.json`.

## Required fixture gaps

The current helper exposes only roles `MANAGER|MEMBER|VIEWER|CREATOR_MEMBER` and scenarios `none|forbidden|conflict|lost-response|unknown-response`. It cannot presently provide the remaining runtime observations below; do not invent variants or infer them from unit tests.

| Stable finding | Required supported fixture and expected observation | Raw artifact |
| --- | --- | --- |
| F1-OVERVIEW-REGION-INDEPENDENCE | dashboard500/plan-success retains TASK with its retry; plan500/dashboard-success retains schedules | `r3-region-independence-*.json` plus request log |
| F1-TASK-EMPTY-STATE | TASK+zero schedules shows work; EPIC/TOPIC-only shows true no-TASK; TASK source with zero matches shows filtered-empty; incomplete source never asserts absence | `r3-task-empty-*.json` |
| F1-BLOCKED-FACTS | explicit BLOCKED/no predecessor is counted as manual blocking; unfinished predecessors are separate; DONE/CANCELLED excluded | `r3-blocked-facts.json` |
| F1-DEFAULT-INITIAL-LOADING | delayed plan response exposes initial pending, retained-data refetch, error/retry, true-empty, and filtered-empty as distinct states | `r3-default-loading-*.json` plus request timing |
| F1-QUICK-CREATE-CONTINUITY | actual session termination/account change clears the prior draft; current-project 403 clears protected server data, freezes affected controls, and retains only the actor's own input | `r3-session-boundary-403.json` |

Do not rerun the unchanged six advanced views or Calendar geometry. Cite their existing R2 raw artifacts only as unaffected reference evidence; final R3 acceptance remains reviewer-owned.
