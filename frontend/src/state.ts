import { useQuery, type QueryClient } from "@tanstack/react-query";
import { useEffect, useSyncExternalStore } from "react";
import {
  api,
  ApiError,
  type Project,
  type Schedule,
  type Schedules,
  type Connection,
  type Projection,
} from "./api/client";
export { SESSION_EXPIRED_EVENT } from "./session";
export const isAccessError = (error: unknown) =>
  error instanceof ApiError && (error.status === 403 || error.status === 404);
const denials = new Map<string, number>();
const accessVersions = new Map<string, number>();
const uncertainCreations = new Set<string>();
type CreateAttempt = {
  name: string;
  requestId: string;
  uncertain: boolean;
  pending?: boolean;
};
const createAttempts = new Map<string, CreateAttempt>();
type ShareAttempt = {
  pending: boolean;
  uncertain: boolean;
  revision: number;
  completed?: boolean;
  completedReadRevision?: number;
};
const shareAttempts = new Map<string, ShareAttempt>();
const shareAttemptRevisions = new Map<string, number>();
const shareReadRevisions = new Map<string, number>();
const shareRecoveries = new Map<string, number>();
const listeners = new Set<() => void>();
const readRevisions = new Map<string, number>();
const denialRevision = (key: string) => accessVersions.get(key) ?? 0;
export const accessKey = (...parts: string[]) => parts.join(":");
export function recordAccessDenial(key: string) {
  const revision = denialRevision(key) + 1;
  accessVersions.set(key, revision);
  denials.set(key, revision);
  listeners.forEach((listener) => listener());
}
export function clearAccessDenial(key: string) {
  if (!denials.has(key)) return;
  denials.delete(key);
  readRevisions.delete(key);
  listeners.forEach((listener) => listener());
}
export const hasAccessDenial = (key: string) => denials.has(key);
export function beginAccessRead(key: string) {
  return denialRevision(key);
}
export function completeAccessRead(key: string, revision: number) {
  if (denialRevision(key) === revision) readRevisions.set(key, revision);
}
export const accessReadCanClear = (key: string) =>
  readRevisions.get(key) === denialRevision(key) && hasAccessDenial(key);
export async function accessRead<T>(key: string, reader: () => Promise<T>) {
  const revision = beginAccessRead(key);
  try {
    const value = await reader();
    completeAccessRead(key, revision);
    return value;
  } catch (error) {
    if (isAccessError(error)) recordAccessDenial(key);
    throw error;
  }
}
export function useAccessDenied(key: string) {
  return useSyncExternalStore(
    (listener) => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    () => hasAccessDenial(key),
    () => false,
  );
}
export function resetAccessState() {
  denials.clear();
  accessVersions.clear();
  readRevisions.clear();
  uncertainCreations.clear();
  createAttempts.clear();
  shareAttempts.clear();
  shareAttemptRevisions.clear();
  shareReadRevisions.clear();
  shareRecoveries.clear();
  listeners.forEach((listener) => listener());
}
export const getCreateAttempt = (session: string) =>
  createAttempts.get(session);
export const setCreateAttempt = (session: string, attempt: CreateAttempt) => {
  createAttempts.set(session, attempt);
  listeners.forEach((listener) => listener());
};
export const clearCreateAttempt = (session: string) => {
  createAttempts.delete(session);
  listeners.forEach((listener) => listener());
};
export const useCreateAttempt = (session: string) =>
  useSyncExternalStore(
    (listener) => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    () => createAttempts.get(session),
    () => undefined,
  );
export const getShareAttempt = (projectId: string) =>
  shareAttempts.get(projectId);
export const beginShareRead = (projectId: string) => {
  const revision = (shareReadRevisions.get(projectId) ?? 0) + 1;
  shareReadRevisions.set(projectId, revision);
  return revision;
};
export const getShareReadRevision = (projectId: string) =>
  shareReadRevisions.get(projectId) ?? 0;
export const setShareAttempt = (projectId: string, attempt: ShareAttempt) => {
  const revision = attempt.revision;
  shareAttemptRevisions.set(projectId, revision);
  shareAttempts.set(projectId, attempt);
  listeners.forEach((listener) => listener());
};
export const clearShareAttempt = (projectId: string) => {
  shareAttempts.delete(projectId);
  listeners.forEach((listener) => listener());
};
const nextShareAttemptRevision = (projectId: string) => {
  const revision = (shareAttemptRevisions.get(projectId) ?? 0) + 1;
  shareAttemptRevisions.set(projectId, revision);
  return revision;
};
export const startShareAttempt = (projectId: string) => {
  const revision = nextShareAttemptRevision(projectId);
  return revision;
};
export const beginShareRecovery = (projectId: string) => {
  if (shareRecoveries.has(projectId)) return undefined;
  const revision = nextShareAttemptRevision(projectId);
  shareRecoveries.set(projectId, revision);
  listeners.forEach((listener) => listener());
  return revision;
};
export const isCurrentShareRecovery = (projectId: string, revision: number) => {
  return shareRecoveries.get(projectId) === revision;
};
export const clearShareRecovery = (projectId: string, revision: number) => {
  if (shareRecoveries.get(projectId) !== revision) return;
  shareRecoveries.delete(projectId);
  listeners.forEach((listener) => listener());
};
export const invalidateShareRecovery = (projectId: string) => {
  if (!shareRecoveries.delete(projectId)) return;
  listeners.forEach((listener) => listener());
};
export const useShareRecovery = (projectId: string) =>
  useSyncExternalStore(
    (listener) => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    () => shareRecoveries.get(projectId),
    () => undefined,
  );
export const useShareAttempt = (projectId: string) =>
  useSyncExternalStore(
    (listener) => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    () => shareAttempts.get(projectId),
    () => undefined,
  );
export function recordCreationUncertainty(groupId: string) {
  uncertainCreations.add(groupId);
  listeners.forEach((listener) => listener());
}
export function clearCreationUncertainty(groupId: string) {
  if (uncertainCreations.delete(groupId))
    listeners.forEach((listener) => listener());
}
export function useCreationUncertainty(groupId: string) {
  return useSyncExternalStore(
    (listener) => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    () => uncertainCreations.has(groupId),
    () => false,
  );
}
export const keys = {
  configuration: ["configuration"] as const,
  me: ["me"] as const,
  projects: ["projects"] as const,
  dashboard: (p: string) => ["dashboard", p] as const,
  schedules: (p: string) => ["schedules", p] as const,
  detail: (p: string, s: string) => ["schedule", p, s] as const,
  projection: (p: string, s?: string) =>
    s ? (["projection", p, s] as const) : (["projection", p] as const),
  members: (p: string) => ["members", p] as const,
  invitation: (t: string) => ["invite", t] as const,
  notifications: ["notifications"] as const,
  connection: ["connection"] as const,
};
export const useMe = () => useQuery({ queryKey: keys.me, queryFn: api.me });
export const useProject = (id: string) => {
  const denialKey = accessKey("project", id);
  const denied = useAccessDenied(denialKey);
  const query = useQuery({
    queryKey: [...keys.projects, "lookup", id],
    staleTime: denied ? 0 : 15000,
    queryFn: async () => {
      const revision = beginAccessRead(denialKey);
      for (let page = 0; page <= 10000; page++) {
        try {
          const rows = await api.projects(page);
          const project = rows.find((p) => p.id === id);
          if (project) {
            completeAccessRead(denialKey, revision);
            return project;
          }
          if (rows.length < 100) {
            completeAccessRead(denialKey, revision);
            return null;
          }
        } catch (error) {
          if (isAccessError(error)) recordAccessDenial(denialKey);
          throw error;
        }
      }
      completeAccessRead(denialKey, revision);
      return null;
    },
  });
  useEffect(() => {
    if (query.isSuccess && !query.isFetching && accessReadCanClear(denialKey))
      clearAccessDenial(denialKey);
  }, [denialKey, query.isFetching, query.isSuccess]);
  return query;
};
export function refreshSchedule(qc: QueryClient, p: string, s?: string) {
  return Promise.all(
    [
      keys.schedules(p),
      keys.dashboard(p),
      keys.projection(p),
      keys.connection,
      keys.notifications,
      ...(s ? [keys.detail(p, s)] : []),
    ].map((queryKey) => qc.invalidateQueries({ queryKey })),
  );
}
type ScheduleState = Pick<
  Schedules[number],
  "participants" | "businessRevision" | "status" | "createdBy"
>;
export function acknowledgement(s: ScheduleState, userId: string) {
  const member = s.participants.find(
    (p) => p.memberUserId === userId && !p.externalEmail,
  );
  if (!member || s.businessRevision <= 0) return "NOT_REQUIRED";
  return member.acknowledged ? "ACKNOWLEDGED" : "PENDING";
}
export function capabilities(
  project: Project,
  userId: string,
  schedule?: Schedule,
  projection?: Projection,
) {
  const create = project.role === "MANAGER" || project.role === "MEMBER";
  const edit =
    !!schedule &&
    schedule.status !== "CANCELLED" &&
    (project.role === "MANAGER" ||
      (project.role === "MEMBER" && schedule.createdBy === userId));
  return {
    create,
    edit,
    confirm: edit && schedule?.status === "DRAFT",
    cancel:
      edit &&
      (schedule?.status === "DRAFT" || schedule?.status === "CONFIRMED"),
    ack:
      create && !!schedule && acknowledgement(schedule, userId) === "PENDING",
    retry:
      project.role === "MANAGER" &&
      projection?.status === "FAILED" &&
      projection.retryClassification === "TRANSIENT",
  };
}
export function calendarStatus(
  connection?: Connection,
  projection?: Projection,
) {
  const order = [
    "REAUTH_REQUIRED",
    "FAILED",
    "PENDING",
    "SYNCED",
    "NOT_CONNECTED",
  ];
  return order.find(
    (s) => s === connection?.status || s === projection?.status,
  );
}
