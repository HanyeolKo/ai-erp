import { useQuery, type QueryClient } from "@tanstack/react-query";
import { api, type Project, type Schedule, type Schedules, type Connection, type Projection } from "./api/client";
export const keys = {
    configuration: ["configuration"] as const, me: ["me"] as const, projects: ["projects"] as const,
    dashboard: (p: string) => ["dashboard", p] as const,
    schedules: (p: string) => ["schedules", p] as const,
    detail: (p: string, s: string) => ["schedule", p, s] as const,
    projection: (p: string, s?: string) => s ? ["projection", p, s] as const : ["projection", p] as const,
    members: (p: string) => ["members", p] as const,
    invitation: (t: string) => ["invite", t] as const, notifications: ["notifications"] as const,
    connection: ["connection"] as const
};
export const useMe = () => useQuery({ queryKey: keys.me, queryFn: api.me });
export const useProject = (id: string) => useQuery({
    queryKey: [...keys.projects, "lookup", id],
    queryFn: async () => {
        for (let page = 0; page <= 10000; page++) {
            const rows = await api.projects(page);
            const project = rows.find(p => p.id === id);
            if (project)
                return project;
            if (rows.length < 100)
                return null;
        }
        return null;
    }
});
export function refreshSchedule(qc: QueryClient, p: string, s?: string) {
    return Promise.all([keys.schedules(p), keys.dashboard(p), keys.projection(p), keys.connection, keys.notifications, ...(s ? [keys.detail(p, s)] : [])].map(queryKey => qc.invalidateQueries({ queryKey })));
}
type ScheduleState = Pick<Schedules[number], "participants" | "businessRevision" | "status" | "createdBy">;
export function acknowledgement(s: ScheduleState, userId: string) {
    const member = s.participants.find(p => p.memberUserId === userId && !p.externalEmail);
    if (!member || s.businessRevision <= 0)
        return "NOT_REQUIRED";
    return member.acknowledged ? "ACKNOWLEDGED" : "PENDING";
}
export function capabilities(project: Project, userId: string, schedule?: Schedule, projection?: Projection) {
    const create = project.role === "MANAGER" || project.role === "MEMBER";
    const edit = !!schedule && schedule.status !== "CANCELLED" && (project.role === "MANAGER" || (project.role === "MEMBER" && schedule.createdBy === userId));
    return { create, edit, confirm: edit && schedule?.status === "DRAFT", cancel: edit && (schedule?.status === "DRAFT" || schedule?.status === "CONFIRMED"),
        ack: create && !!schedule && acknowledgement(schedule, userId) === "PENDING",
        retry: project.role === "MANAGER" && projection?.status === "FAILED" && projection.retryClassification === "TRANSIENT" };
}
export function calendarStatus(connection?: Connection, projection?: Projection) {
    const order = ["REAUTH_REQUIRED", "FAILED", "PENDING", "SYNCED", "NOT_CONNECTED"];
    return order.find(s => s === connection?.status || s === projection?.status);
}
