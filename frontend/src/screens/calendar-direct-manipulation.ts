import type { Schedules } from "../api/client";
import { civilDateBoundary, dateInZone, localDateTimeToUtc, shiftDate, utcToLocalDateTime } from "../time";

export type CalendarSchedule = Pick<Schedules[number], "id" | "projectId" | "title" | "description" | "status" | "startsAt" | "endsAt" | "rowVersion" | "createdBy">;
export type CalendarChange = "move" | "start" | "end";
export type LocalPoint = { day: string; minute: number };

const DAY_MS = 86_400_000;
const MINUTE_MS = 60_000;

export function snapQuarterMinute(minute: number) {
  const clamped = Math.max(0, Math.min(1440, minute));
  return Math.min(1440, Math.floor(clamped / 15 + 0.5) * 15);
}

function localSubminute(instant: string, zone: string) {
  const date = new Date(instant);
  return { local: utcToLocalDateTime(instant, zone), second: date.getUTCSeconds(), millisecond: date.getUTCMilliseconds() };
}

function localWithMinute(day: string, minute: number) {
  const hour = Math.floor(minute / 60);
  const clock = `${String(hour).padStart(2, "0")}:${String(minute % 60).padStart(2, "0")}`;
  return `${day}T${clock}`;
}

function localToUtc(local: string, zone: string, second = 0, millisecond = 0) {
  const base = Date.parse(localDateTimeToUtc(local, zone));
  return new Date(base + second * 1000 + millisecond).toISOString();
}

function civilDayDelta(from: string, to: string) {
  return Math.round((Date.parse(`${to}T12:00:00Z`) - Date.parse(`${from}T12:00:00Z`)) / DAY_MS);
}

function shiftedLocal(instant: string, dayDelta: number, minuteDelta: number, zone: string) {
  const sub = localSubminute(instant, zone);
  const day = sub.local.slice(0, 10);
  const minute = Number(sub.local.slice(11, 13)) * 60 + Number(sub.local.slice(14, 16));
  const total = minute + minuteDelta;
  const carry = Math.floor(total / 1440);
  const nextMinute = ((total % 1440) + 1440) % 1440;
  return localToUtc(localWithMinute(shiftDate(day, dayDelta + carry), nextMinute), zone, sub.second, sub.millisecond);
}

export function eventSpan(schedule: CalendarSchedule, zone: string) {
  const startDay = dateInZone(schedule.startsAt, zone);
  const endDay = dateInZone(new Date(Date.parse(schedule.endsAt) - 1).toISOString(), zone);
  return { startDay, endDay };
}

export function eventDayMinute(schedule: CalendarSchedule, day: string, zone: string) {
  const startLocal = localSubminute(schedule.startsAt, zone);
  const endLocal = localSubminute(schedule.endsAt, zone);
  const localMinute = (value: { local: string; second: number; millisecond: number }) => Number(value.local.slice(11, 13)) * 60 + Number(value.local.slice(14, 16)) + value.second / 60 + value.millisecond / 60_000;
  const start = dateInZone(schedule.startsAt, zone) < day ? 0 : localMinute(startLocal);
  const endDate = dateInZone(schedule.endsAt, zone);
  const end = endDate > day ? 1440 : localMinute(endLocal);
  return { start: Math.max(0, Math.min(1440, start)), end: Math.max(0, Math.min(1440, end)), duration: Math.max(0, end - start) };
}

export function applyMonthChange(schedule: CalendarSchedule, day: string, dropDay: string, change: CalendarChange, zone: string) {
  const delta = civilDayDelta(day, dropDay);
  const start = localSubminute(schedule.startsAt, zone);
  const end = localSubminute(schedule.endsAt, zone);
  let startsAt = schedule.startsAt;
  let endsAt = schedule.endsAt;
  if (change === "move") {
    startsAt = shiftedLocal(schedule.startsAt, delta, 0, zone);
    endsAt = shiftedLocal(schedule.endsAt, delta, 0, zone);
  } else if (change === "start") {
    startsAt = localToUtc(localWithMinute(dropDay, Number(start.local.slice(11, 13)) * 60 + Number(start.local.slice(14, 16))), zone, start.second, start.millisecond);
  } else {
    const span = eventSpan(schedule, zone);
    const endIsBoundary = dateInZone(schedule.endsAt, zone) !== span.endDay && end.local.slice(11) === "00:00";
    endsAt = endIsBoundary ? localToUtc(`${shiftDate(dropDay, 1)}T00:00`, zone) : localToUtc(localWithMinute(dropDay, Number(end.local.slice(11, 13)) * 60 + Number(end.local.slice(14, 16))), zone, end.second, end.millisecond);
  }
  return { startsAt, endsAt };
}

export function applyWeekMove(schedule: CalendarSchedule, origin: LocalPoint, drop: LocalPoint, zone: string) {
  const dayDelta = civilDayDelta(origin.day, drop.day);
  const minuteDelta = snapQuarterMinute(drop.minute) - snapQuarterMinute(origin.minute);
  return { startsAt: shiftedLocal(schedule.startsAt, dayDelta, minuteDelta, zone), endsAt: shiftedLocal(schedule.endsAt, dayDelta, minuteDelta, zone) };
}

export function applyWeekResize(schedule: CalendarSchedule, point: LocalPoint, change: Exclude<CalendarChange, "move">, zone: string) {
  const minute = snapQuarterMinute(point.minute);
  const local = `${minute === 1440 ? shiftDate(point.day, 1) : point.day}T${String(minute === 1440 ? 0 : Math.floor(minute / 60)).padStart(2, "0")}:${String(minute === 1440 ? 0 : minute % 60).padStart(2, "0")}`;
  const value = localToUtc(local, zone);
  return change === "start" ? { startsAt: value, endsAt: schedule.endsAt } : { startsAt: schedule.startsAt, endsAt: value };
}

export function isValidChange(change: { startsAt: string; endsAt: string }, minimumMinutes = 0) {
  const elapsed = Date.parse(change.endsAt) - Date.parse(change.startsAt);
  return Number.isFinite(elapsed) && elapsed > 0 && elapsed >= minimumMinutes * MINUTE_MS;
}

export function editBody(schedule: CalendarSchedule, change: { startsAt: string; endsAt: string }) {
  return { title: schedule.title, description: schedule.description ?? null, startsAt: change.startsAt, endsAt: change.endsAt, rowVersion: schedule.rowVersion };
}

type DayLayout = { schedule: CalendarSchedule; start: number; end: number; lane: number };
function codePointCompare(a: string, b: string) {
  const aa = Array.from(a, ch => ch.codePointAt(0) ?? 0); const bb = Array.from(b, ch => ch.codePointAt(0) ?? 0);
  for (let i = 0; i < Math.min(aa.length, bb.length); i++) if (aa[i] !== bb[i]) return aa[i] - bb[i];
  return aa.length - bb.length;
}

export function layoutWeekDay(rows: CalendarSchedule[], day: string, zone: string) {
  const occurrences = rows.flatMap<DayLayout>(schedule => {
    const span = eventSpan(schedule, zone); if (span.startDay > day || span.endDay < day) return [];
    const position = eventDayMinute(schedule, day, zone); return [{ schedule, start: position.start, end: position.end, lane: -1 }];
  }).sort((a, b) => { const byStart = a.start - b.start; return byStart || a.end - b.end || codePointCompare(a.schedule.title, b.schedule.title) || (a.schedule.id < b.schedule.id ? -1 : a.schedule.id > b.schedule.id ? 1 : 0); });
  const laneEnds: number[] = [];
  for (const occurrence of occurrences) { let lane = laneEnds.findIndex(end => end <= occurrence.start); if (lane < 0) lane = laneEnds.length; occurrence.lane = lane; laneEnds[lane] = occurrence.end; }
  return { occurrences, dayLaneCount: laneEnds.length };
}

export function weekLaneLayout(rows: CalendarSchedule[], days: string[], zone: string) {
  const byDay = days.map(day => ({ day, ...layoutWeekDay(rows, day, zone) }));
  return { byDay, globalLaneCount: Math.max(1, ...byDay.map(item => item.dayLaneCount)) };
}

export function previewText(schedule: CalendarSchedule, change: { startsAt: string; endsAt: string }, zone: string) {
  return `${schedule.title} · ${utcToLocalDateTime(change.startsAt, zone).replace("T", " ")}–${utcToLocalDateTime(change.endsAt, zone).replace("T", " ")}`;
}

export function dayBoundary(day: string, zone: string) { return civilDateBoundary(day, zone); }
