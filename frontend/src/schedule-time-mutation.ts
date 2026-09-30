import { ApiError } from "./api/client";
import { localDateTimeToUtc } from "./time";
import type { CalendarSchedule } from "./screens/calendar-direct-manipulation";

export type ScheduleTimeChange = { startsAt: string; endsAt: string };

export function preserveScheduleTimeInstants(schedule: CalendarSchedule, start: string, end: string, originalStart: string, originalEnd: string, zone: string): ScheduleTimeChange {
  return {
    startsAt: start === originalStart ? schedule.startsAt : localDateTimeToUtc(start, zone),
    endsAt: end === originalEnd ? schedule.endsAt : localDateTimeToUtc(end, zone),
  };
}

type MutationOptions<Role, Schedule extends CalendarSchedule> = {
  schedule: Schedule;
  change: ScheduleTimeChange;
  write: (change: ScheduleTimeChange) => Promise<Schedule>;
  readLatest: () => Promise<Schedule>;
  readRole: () => Promise<Role>;
  refreshTarget: () => Promise<unknown>;
  canEdit: (role: Role, latest: Schedule) => boolean;
};

export type ScheduleTimeMutationResult<Schedule extends CalendarSchedule> =
  | { kind: "saved"; latest: Schedule }
  | { kind: "rejected"; error: unknown }
  | { kind: "recovered"; latest: Schedule; committed: boolean; canEdit: boolean; cause: "conflict" | "uncertain" }
  | { kind: "locked"; error: unknown; targetError?: unknown };

export async function executeScheduleTimeMutation<Role, Schedule extends CalendarSchedule>(options: MutationOptions<Role, Schedule>): Promise<ScheduleTimeMutationResult<Schedule>> {
  try {
    return { kind: "saved", latest: await options.write(options.change) };
  } catch (error) {
    if (error instanceof ApiError && error.status >= 400 && error.status < 500 && error.status !== 409) return { kind: "rejected", error };
    const [latest, role, target] = await Promise.allSettled([options.readLatest(), options.readRole(), options.refreshTarget()]);
    if (latest.status !== "fulfilled" || role.status !== "fulfilled" || target.status !== "fulfilled") return {
      kind: "locked",
      error: latest.status === "rejected" ? latest.reason : role.status === "rejected" ? role.reason : target.status === "rejected" ? target.reason : error,
      ...(target.status === "rejected" ? { targetError: target.reason } : {}),
    };
    const committed = Date.parse(latest.value.startsAt) === Date.parse(options.change.startsAt)
      && Date.parse(latest.value.endsAt) === Date.parse(options.change.endsAt);
    return {
      kind: "recovered",
      latest: latest.value,
      committed,
      canEdit: options.canEdit(role.value, latest.value),
      cause: error instanceof ApiError && error.status === 409 ? "conflict" : "uncertain",
    };
  }
}
