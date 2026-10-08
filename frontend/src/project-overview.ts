import type { PlanItem, PlanSnapshot, Schedules } from "./api/client";
import { PAGE_SIZE, api } from "./api/client";
import { accessRead, isAccessError } from "./state";
import { civilDateBoundary, dateInZone, shiftDate, validZone } from "./time";

export const OVERVIEW_ZONE = "Asia/Seoul";
export const OVERVIEW_PAGE_LIMIT = 50;
export type OverviewPeriod = "today" | "week";
export type OverviewForecast = PlanSnapshot["summary"];

export function overviewContext(search: string, now = new Date()) {
  const params = new URLSearchParams(search.startsWith("?") ? search.slice(1) : search);
  const requestedZone = params.get("zone") ?? OVERVIEW_ZONE;
  const zone = validZone(requestedZone) ? requestedZone : OVERVIEW_ZONE;
  const period: OverviewPeriod = params.get("period") === "today" ? "today" : "week";
  const date = dateInZone(now.toISOString(), zone);
  const weekday = new Date(`${date}T12:00:00Z`).getUTCDay();
  const monday = shiftDate(date, -((weekday + 6) % 7));
  const weekEnd = shiftDate(monday, 7);
  const todayFrom = civilDateBoundary(date, zone);
  const todayTo = civilDateBoundary(shiftDate(date, 1), zone);
  const weekFrom = civilDateBoundary(monday, zone);
  const weekTo = civilDateBoundary(weekEnd, zone);
  return { period, zone, date, monday, weekEnd, todayFrom, todayTo, weekFrom, weekTo };
}

export function updateOverviewContext(period: OverviewPeriod, zone: string) {
  const path = window.location.hash.replace(/^#/, "").split("?", 1)[0];
  const params = new URLSearchParams();
  params.set("period", period);
  params.set("zone", validZone(zone) ? zone : OVERVIEW_ZONE);
  window.location.hash = `${path}?${params.toString()}`;
}

export function overviewWindow(date: string, zone: string) {
  return { from: civilDateBoundary(date, zone), to: civilDateBoundary(shiftDate(date, 1), zone) };
}

export function overviewNextBoundary(now = new Date(), zone = OVERVIEW_ZONE) {
  const context = overviewContext(`?zone=${encodeURIComponent(zone)}`, now);
  return civilDateBoundary(shiftDate(context.date, 1), context.zone);
}

const dateValue = (value: string | null | undefined) => value ? value.slice(0, 10) : null;
const applicableDate = (item: PlanItem) => dateValue(item.deadline) ?? dateValue(item.targetEnd) ?? dateValue(item.targetStart);
const compareDate = (a: string | null, b: string | null) => {
  if (a === b) return 0;
  if (a === null) return 1;
  if (b === null) return -1;
  return a < b ? -1 : 1;
};

export function planPath(items: PlanItem[], item: PlanItem) {
  const byId = new Map(items.map(row => [row.id, row]));
  const path: string[] = [];
  let current: PlanItem | undefined = item;
  while (current?.parentId) {
    const parent = byId.get(current.parentId);
    if (!parent) break;
    path.unshift(parent.title);
    current = parent;
  }
  return path;
}

function isOverdue(item: PlanItem, asOfDate: string) {
  const date = dateValue(item.deadline) ?? dateValue(item.targetEnd);
  return item.state !== "DONE" && item.state !== "CANCELLED" && !!date && date < asOfDate;
}

export type OverviewGoal = PlanItem & { path: string[]; descendantTaskCount: number; descendantDoneCount: number; blocked: boolean; overdue: boolean };
export function selectGoals(snapshot: PlanSnapshot, cap = 3): OverviewGoal[] {
  const goals = snapshot.items.filter(item => item.kind === "EPIC" && item.state !== "CANCELLED");
  return goals.map(item => ({
    ...item,
    path: planPath(snapshot.items, item),
    descendantTaskCount: item.summary.taskCount,
    descendantDoneCount: item.summary.doneCount,
    blocked: item.summary.blockedCount > 0,
    overdue: item.summary.overdueCount > 0,
  })).sort((a, b) => Number(b.blocked) - Number(a.blocked) || Number(b.overdue) - Number(a.overdue) || Number(b.summary.outsideTarget || b.summary.forecastState === "INCOMPLETE") - Number(a.summary.outsideTarget || a.summary.forecastState === "INCOMPLETE") || a.sortOrder - b.sortOrder || a.id.localeCompare(b.id)).slice(0, cap);
}

export type OverviewRisk = PlanItem & { path: string[]; reasons: string[]; overdue: boolean };
export function selectRisks(snapshot: PlanSnapshot, cap = 5): OverviewRisk[] {
  return snapshot.items.filter(item => item.kind === "TASK" && item.state !== "DONE" && item.state !== "CANCELLED").map(item => {
    const overdue = isOverdue(item, snapshot.asOfDate);
    const reasons = [
      item.state === "BLOCKED" || item.blockerIds.length > 0 ? "차단됨" : "",
      overdue ? "기한 지남" : "",
      !item.targetStart || !item.targetEnd ? "계획되지 않음" : "",
      !item.assigneeId ? "담당자 없음" : "",
    ].filter(Boolean);
    return { ...item, path: planPath(snapshot.items, item), reasons, overdue };
  }).filter(item => item.reasons.length > 0).sort((a, b) =>
    Number(b.state === "BLOCKED" || b.blockerIds.length > 0) - Number(a.state === "BLOCKED" || a.blockerIds.length > 0) ||
    Number(b.overdue) - Number(a.overdue) ||
    Number(!b.targetStart || !b.targetEnd) - Number(!a.targetStart || !a.targetEnd) ||
    Number(!b.assigneeId) - Number(!a.assigneeId) || compareDate(applicableDate(a), applicableDate(b)) || a.sortOrder - b.sortOrder || a.id.localeCompare(b.id)
  ).slice(0, cap);
}

export type OverviewMilestone = PlanItem & { date: string | null; path: string[] };
export function selectMilestones(snapshot: PlanSnapshot, cap = 5): OverviewMilestone[] {
  return snapshot.items.filter(item => item.kind === "MILESTONE" && item.state !== "CANCELLED").map(item => ({ ...item, date: applicableDate(item), path: planPath(snapshot.items, item) })).sort((a, b) => compareDate(a.date, b.date) || a.sortOrder - b.sortOrder || a.id.localeCompare(b.id)).slice(0, cap);
}

export type OverviewTask = PlanItem & { path: string[]; overdue: boolean; blockerReason: string | null };
export function selectMyActiveTasks(snapshot: PlanSnapshot, userId: string, cap = 5): OverviewTask[] {
  return snapshot.items.filter(item => item.kind === "TASK" && item.assigneeId === userId && item.state !== "DONE" && item.state !== "CANCELLED").map(item => ({
    ...item,
    path: planPath(snapshot.items, item),
    overdue: isOverdue(item, snapshot.asOfDate),
    blockerReason: item.blockerIds.length || item.state === "BLOCKED" ? "선행 작업 또는 의존성 확인 필요" : null,
  })).sort((a, b) => Number(b.state === "BLOCKED" || b.blockerIds.length > 0) - Number(a.state === "BLOCKED" || a.blockerIds.length > 0) || Number(b.overdue) - Number(a.overdue) || Number(b.state === "IN_PROGRESS") - Number(a.state === "IN_PROGRESS") || compareDate(applicableDate(a), applicableDate(b)) || a.sortOrder - b.sortOrder || a.id.localeCompare(b.id)).slice(0, cap);
}

export function planProgress(snapshot: PlanSnapshot) {
  const summary = snapshot.summary;
  return {
    doneCount: summary.doneCount,
    taskCount: summary.taskCount,
    percent: snapshot.complete && summary.taskCount > 0 ? summary.progressPercent : null,
    complete: snapshot.complete,
    empty: snapshot.complete && summary.taskCount === 0,
  };
}

export type LoadedSchedules = { rows: Schedules; complete: boolean; partial: boolean; pages: number; reason?: string };
export async function readOverviewSchedules(projectId: string, from: string, to: string): Promise<LoadedSchedules> {
  const byId = new Map<string, Schedules[number]>();
  let pages = 0;
  for (let page = 0; page < OVERVIEW_PAGE_LIMIT; page += 1) {
    let rows: Schedules;
    try {
      rows = await accessRead(`overview-schedules:${projectId}:${from}:${to}:${page}`, () => api.schedules(projectId, from, to, page));
    } catch (error) {
      if (page === 0 || isAccessError(error)) throw error;
      return { rows: [...byId.values()], complete: false, partial: true, pages, reason: "추가 일정 페이지를 불러오지 못했습니다." };
    }
    pages += 1;
    const before = byId.size;
    rows.forEach(row => byId.set(row.id, row));
    if (rows.length < PAGE_SIZE) return { rows: [...byId.values()], complete: true, partial: false, pages };
    if (byId.size === before) return { rows: [...byId.values()], complete: false, partial: true, pages, reason: "중복 일정 페이지로 전체 목록을 확인하지 못했습니다." };
  }
  return { rows: [...byId.values()], complete: false, partial: true, pages, reason: "일정이 많아 처음 50페이지까지만 불러왔습니다." };
}

export function scheduleOverlaps(row: Schedules[number], from: string, to: string) {
  return Date.parse(row.endsAt) > Date.parse(from) && Date.parse(row.startsAt) < Date.parse(to);
}
