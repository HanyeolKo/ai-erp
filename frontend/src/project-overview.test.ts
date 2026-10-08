import { afterEach, expect, test, vi } from "vitest";
import { ApiError, api, type PlanItem, type PlanSnapshot } from "./api/client";
import { overviewContext, overviewNextBoundary, overviewWindow, planProgress, readOverviewSchedules, scheduleOverlaps, selectGoals, selectMilestones, selectMyActiveTasks, selectRisks } from "./project-overview";

const item = (overrides: Partial<PlanItem>): PlanItem => ({ id: "i", projectId: "p1", parentId: null, kind: "TASK", title: "작업", description: null, assigneeId: null, state: "READY", targetStart: null, targetEnd: null, deadline: null, sortOrder: 0, labels: [], rowVersion: 1, predecessorIds: [], successorIds: [], blockerIds: [], summary: { taskCount: 1, doneCount: 0, blockedCount: 0, overdueCount: 0, unplannedCount: 0, unassignedCount: 0, progressPercent: 0, forecastStart: null, forecastEnd: null, forecastState: "EMPTY", outsideTarget: false }, ...overrides });
const snapshot = (items: PlanItem[], complete = true): PlanSnapshot => ({ projectId: "p1", rowVersion: 1, targetStart: "2090-09-01", targetEnd: "2090-09-30", asOfDate: "2090-09-10", items, matchedIds: items.map(row => row.id), summary: { taskCount: 8, doneCount: 3, blockedCount: 0, overdueCount: 0, unplannedCount: 0, unassignedCount: 0, progressPercent: 37.5, forecastStart: "2090-09-01", forecastEnd: "2090-09-30", forecastState: "COMPLETE", outsideTarget: false }, complete, totalCount: items.length });

afterEach(() => vi.restoreAllMocks());

test("selects capped goals, risks, and active assigned work with accepted ordering", () => {
  const epic = item({ id: "goal", kind: "EPIC", title: "목표", summary: { taskCount: 2, doneCount: 1, blockedCount: 1, overdueCount: 0, unplannedCount: 0, unassignedCount: 0, progressPercent: 50, forecastStart: null, forecastEnd: null, forecastState: "INCOMPLETE", outsideTarget: false } });
  const rows = [epic, ...Array.from({ length: 7 }, (_, index) => item({ id: `risk-${index}`, title: `위험 ${index}`, deadline: "2090-09-01", blockerIds: index === 0 ? ["dep"] : [], assigneeId: index < 6 ? "u1" : null }))];
  expect(selectGoals(snapshot(rows))).toHaveLength(1);
  expect(selectRisks(snapshot(rows))).toHaveLength(5);
  expect(selectMyActiveTasks(snapshot(rows), "u1")).toHaveLength(5);
});

test("treats either missing target bound as unplanned and ranks blockers before state/date", () => {
  const outside = item({ id: "outside", kind: "EPIC", summary: { ...item({}).summary, outsideTarget: true } });
  const inside = item({ id: "inside", kind: "EPIC" });
  expect(selectGoals(snapshot([inside, outside])).map(row => row.id)).toEqual(["outside", "inside"]);
  const blocker = item({ id: "blocker", assigneeId: "u1", state: "READY", blockerIds: ["dependency"], targetStart: "2090-09-10", targetEnd: "2090-09-11" });
  const inProgress = item({ id: "progress", assigneeId: "u1", state: "IN_PROGRESS", targetStart: "2090-09-10", targetEnd: "2090-09-11" });
  const missingEnd = item({ id: "missing-end", assigneeId: "u1", state: "READY", targetStart: "2090-09-10", targetEnd: null, deadline: "2090-09-12" });
  const risks = selectRisks(snapshot([missingEnd]));
  expect(risks[0].reasons).toContain("계획되지 않음");
  expect(selectMyActiveTasks(snapshot([inProgress, blocker]), "u1").map(row => row.id)).toEqual(["blocker", "progress"]);
});

test("normalizes invalid URL zone and derives Monday week and civil windows", () => {
  const context = overviewContext("?period=today&zone=invalid", new Date("2090-09-10T00:00:00Z"));
  expect(context.zone).toBe("Asia/Seoul");
  expect(context.period).toBe("today");
  expect(context.monday).toBe("2090-09-04");
  const window = overviewWindow("2026-03-08", "America/New_York");
  expect(Date.parse(window.to) - Date.parse(window.from)).toBe(23 * 60 * 60 * 1000);
  const week = overviewContext("?period=week&zone=Asia%2FSeoul", new Date("2090-09-10T12:00:00Z"));
  expect(week.monday).toBe("2090-09-04");
  expect(week.weekEnd).toBe("2090-09-11");
  expect(Date.parse(week.weekTo) - Date.parse(week.weekFrom)).toBe(7 * 24 * 60 * 60 * 1000);
  expect(overviewNextBoundary(new Date("2090-09-09T14:55:00Z"), "Asia/Seoul")).toBe("2090-09-09T15:00:00.000Z");
  expect(overviewNextBoundary(new Date("2026-11-01T05:55:00Z"), "America/New_York")).toBe("2026-11-02T05:00:00.000Z");
});

test("deduplicates pages and stops at the first short page", async () => {
  const rows = (ids: string[]) => ids.map(id => ({ id, projectId: "p1", title: id, description: "", status: "CONFIRMED", rowVersion: 1, businessRevision: 1, startsAt: "2090-09-10T01:00:00Z", endsAt: "2090-09-10T02:00:00Z", createdBy: "u1", participants: [], changes: [] }));
  vi.spyOn(api, "schedules").mockResolvedValueOnce(rows(Array.from({ length: 20 }, (_, index) => `s${index}`)) as never).mockResolvedValueOnce(rows(["s19", "s20"]) as never);
  const result = await readOverviewSchedules("p1", "2090-09-08T00:00:00Z", "2090-09-15T00:00:00Z");
  expect(result.pages).toBe(2);
  expect(result.complete).toBe(true);
  expect(result.rows).toHaveLength(21);
});

test("keeps complete and partial progress honest and applies half-open overlap", () => {
  const complete = snapshot([], true);
  complete.summary = { ...complete.summary, taskCount: 8, doneCount: 3, progressPercent: 37.5 };
  expect(planProgress(complete)).toMatchObject({ doneCount: 3, taskCount: 8, percent: 37.5, empty: false });
  const partial = { ...complete, complete: false };
  expect(planProgress(partial).percent).toBeNull();
  const zero = { ...complete, complete: true, summary: { ...complete.summary, taskCount: 0, doneCount: 0, progressPercent: null } };
  expect(planProgress(zero).empty).toBe(true);
  const row = { id: "s", projectId: "p1", title: "s", description: "", status: "CONFIRMED", rowVersion: 1, businessRevision: 1, startsAt: "2090-09-10T01:00:00Z", endsAt: "2090-09-10T02:00:00Z", createdBy: "u1", participants: [], changes: [] } as never;
  expect(scheduleOverlaps(row, "2090-09-10T00:00:00Z", "2090-09-10T01:00:00Z")).toBe(false);
  expect(scheduleOverlaps(row, "2090-09-10T02:00:00Z", "2090-09-10T03:00:00Z")).toBe(false);
  expect(scheduleOverlaps(row, "2090-09-10T01:59:59Z", "2090-09-10T02:00:00Z")).toBe(true);
});

test("proves paging ceiling, no-progress, overlap deduplication, and failure boundaries", async () => {
  const rows = (ids: string[]) => ids.map(id => ({ id, projectId: "p1", title: id, description: "", status: "CONFIRMED", rowVersion: 1, businessRevision: 1, startsAt: "2090-09-10T01:00:00Z", endsAt: "2090-09-10T02:00:00Z", createdBy: "u1", participants: [], changes: [] }));
  const fullPage = Array.from({ length: 20 }, (_, index) => `s${index}`);
  const schedules = vi.spyOn(api, "schedules").mockImplementation(async (_id, _from, _to, page) => rows(Array.from({ length: 20 }, (_, index) => `s${(page ?? 0) * 20 + index}`)) as never);
  const ceiling = await readOverviewSchedules("p1", "2090-09-08T00:00:00Z", "2090-09-15T00:00:00Z");
  expect(schedules).toHaveBeenCalledTimes(50);
  expect(ceiling).toMatchObject({ pages: 50, complete: false, partial: true });
  schedules.mockReset().mockResolvedValueOnce(rows(fullPage) as never).mockResolvedValueOnce(rows(fullPage) as never);
  const noProgress = await readOverviewSchedules("p1", "2090-09-08T00:00:00Z", "2090-09-15T00:00:00Z");
  expect(noProgress).toMatchObject({ pages: 2, complete: false, partial: true });
  expect(noProgress.rows).toHaveLength(20);
  schedules.mockReset().mockResolvedValueOnce(rows(fullPage) as never).mockResolvedValueOnce(rows(["s19", "s20"]) as never);
  const dedup = await readOverviewSchedules("p1", "2090-09-08T00:00:00Z", "2090-09-15T00:00:00Z");
  expect(dedup).toMatchObject({ pages: 2, complete: true, partial: false });
  expect(dedup.rows).toHaveLength(21);
  schedules.mockReset().mockResolvedValueOnce(rows(fullPage) as never).mockRejectedValueOnce(new ApiError(500, {}));
  await expect(readOverviewSchedules("p1", "2090-09-08T00:00:00Z", "2090-09-15T00:00:00Z")).resolves.toMatchObject({ pages: 1, complete: false, partial: true });
  schedules.mockReset().mockResolvedValueOnce(rows(fullPage) as never).mockRejectedValueOnce(new ApiError(403, {}));
  await expect(readOverviewSchedules("p1", "2090-09-08T00:00:00Z", "2090-09-15T00:00:00Z")).rejects.toMatchObject({ status: 403 });
  schedules.mockReset().mockRejectedValueOnce(new ApiError(500, {}));
  await expect(readOverviewSchedules("p1", "2090-09-08T00:00:00Z", "2090-09-15T00:00:00Z")).rejects.toMatchObject({ status: 500 });
});

test("orders milestone and active-work selectors while retaining all forecast states", () => {
  const states = ["EMPTY", "UNDATED", "INCOMPLETE", "COMPLETE"] as const;
  const rows = states.map((forecastState, index) => item({ id: `m${index}`, kind: "MILESTONE", targetEnd: index === 0 ? null : `2090-09-${String(index + 10).padStart(2, "0")}`, summary: { ...item({}).summary, forecastState } }));
  const active = item({ id: "active", assigneeId: "u1", state: "IN_PROGRESS" });
  const result = selectMilestones(snapshot([...rows, active]));
  expect(result.map(item => item.id)).toEqual(["m1", "m2", "m3", "m0"]);
  expect(selectMyActiveTasks(snapshot([...rows, active]), "u1").map(item => item.id)).toEqual(["active"]);
});
