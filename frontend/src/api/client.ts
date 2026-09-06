import type { paths } from "./generated";
type ResponseOf<T> = T extends {
    responses: {
        200: {
            content: {
                "application/json": infer R;
            };
        };
    };
} ? R : never;
type BodyOf<T> = T extends {
    requestBody?: {
        content: {
            "application/json;charset=UTF-8": infer B;
        };
    };
} ? B : never;
export type Configuration = ResponseOf<paths["/api/v1/system/configuration"]["get"]>;
export type Me = ResponseOf<paths["/api/v1/me"]["get"]>;
export type Projects = ResponseOf<paths["/api/v1/projects"]["get"]>;
export type Project = Projects[number];
export type Dashboard = ResponseOf<paths["/api/v1/projects/{projectId}/dashboard"]["get"]>;
export type Schedules = ResponseOf<paths["/api/v1/projects/{projectId}/schedules"]["get"]>;
export type Schedule = ResponseOf<paths["/api/v1/projects/{projectId}/schedules/{id}"]["get"]>;
export type CreateBody = BodyOf<paths["/api/v1/projects/{projectId}/schedules"]["post"]>;
export type EditBody = BodyOf<paths["/api/v1/projects/{projectId}/schedules/{id}"]["patch"]>;
type CreateResult = ResponseOf<paths["/api/v1/projects/{projectId}/schedules"]["post"]>;
type EditResult = ResponseOf<paths["/api/v1/projects/{projectId}/schedules/{id}"]["patch"]>;
type ConfirmBody = BodyOf<paths["/api/v1/projects/{projectId}/schedules/{id}/confirm"]["post"]>;
type ConfirmResult = ResponseOf<paths["/api/v1/projects/{projectId}/schedules/{id}/confirm"]["post"]>;
type CancelBody = BodyOf<paths["/api/v1/projects/{projectId}/schedules/{id}/cancel"]["post"]>;
type CancelResult = ResponseOf<paths["/api/v1/projects/{projectId}/schedules/{id}/cancel"]["post"]>;
type AckBody = BodyOf<paths["/api/v1/projects/{projectId}/schedules/{id}/acknowledge"]["post"]>;
type AckResult = ResponseOf<paths["/api/v1/projects/{projectId}/schedules/{id}/acknowledge"]["post"]>;
type InviteBody = BodyOf<paths["/api/v1/groups/{groupId}/invitations"]["post"]>;
type InviteResult = ResponseOf<paths["/api/v1/groups/{groupId}/invitations"]["post"]>;
export type Invitation = ResponseOf<paths["/api/v1/invitations/{token}"]["get"]>;
type AcceptResult = ResponseOf<paths["/api/v1/invitations/{token}/accept"]["post"]>;
type RejectResult = ResponseOf<paths["/api/v1/invitations/{token}/reject"]["post"]>;
type Notifications = ResponseOf<paths["/api/v1/notifications"]["get"]>;
type ReadResult = ResponseOf<paths["/api/v1/notifications/{id}/read"]["post"]>;
export type Members = ResponseOf<paths["/api/v1/projects/{projectId}/members"]["get"]>;
type RoleBody = BodyOf<paths["/api/v1/projects/{projectId}/members/{userId}"]["patch"]>;
type RoleResult = ResponseOf<paths["/api/v1/projects/{projectId}/members/{userId}"]["patch"]>;
export type Connection = ResponseOf<paths["/api/v1/calendar/connection"]["get"]>;
type ReconnectResult = ResponseOf<paths["/api/v1/calendar/reconnect"]["post"]>;
export type Projection = ResponseOf<paths["/api/v1/projects/{projectId}/schedules/{id}/calendar"]["get"]>;
type RetryResult = ResponseOf<paths["/api/v1/projects/{projectId}/schedules/{id}/calendar/retry"]["post"]>;
type Csrf = ResponseOf<paths["/api/v1/csrf"]["get"]>;
type ProblemContract = paths["/api/v1/projects/{projectId}/members/{userId}"]["patch"]["responses"][400]["content"]["application/problem+json"];
export type Problem = Partial<ProblemContract>;
export class ApiError extends Error {
    constructor(readonly status: number, readonly problem: Problem) { super(problem.code ?? `HTTP_${status}`); }
}
async function parse<T>(response: Response): Promise<T> {
    if (!response.ok) {
        let problem: Problem = {};
        try {
            const value: unknown = await response.json();
            if (value && typeof value === "object") {
                if ("code" in value && typeof value.code === "string")
                    problem.code = value.code;
                if ("traceId" in value && typeof value.traceId === "string")
                    problem.traceId = value.traceId;
                if ("fieldErrors" in value && Array.isArray(value.fieldErrors))
                    problem.fieldErrors = value.fieldErrors.filter((f): f is ProblemContract["fieldErrors"][number] => !!f && typeof f === "object" && typeof f.field === "string" && typeof f.message === "string");
            }
        }
        catch { /* A proxy may return an empty or non-JSON error. */ }
        throw new ApiError(response.status, problem);
    }
    return response.status === 204 ? undefined as T : response.json() as Promise<T>;
}
export async function read<T>(path: string): Promise<T> { return parse<T>(await fetch(path, { credentials: "include" })); }
async function mutate<T, B = never>(path: string, method = "POST", body?: B): Promise<T> {
    const csrf = await read<Csrf>("/api/v1/csrf");
    return parse<T>(await fetch(path, { method, credentials: "include", headers: { "content-type": "application/json", [csrf.headerName]: csrf.token }, body: body === undefined ? undefined : JSON.stringify(body) }));
}
const segment = encodeURIComponent;
const base = (p: string) => `/api/v1/projects/${segment(p)}`;
const item = (p: string, s: string) => `${base(p)}/schedules/${segment(s)}`;
export const PAGE_SIZE = 20;
export const api = {
    configuration: () => read<Configuration>("/api/v1/system/configuration"),
    me: () => read<Me>("/api/v1/me"),
    projects: (page = 0) => read<Projects>(`/api/v1/projects?page=${page}&limit=100`),
    dashboard: (p: string) => read<Dashboard>(`${base(p)}/dashboard`),
    schedules: (p: string, from: string, to: string, page = 0) => read<Schedules>(`${base(p)}/schedules?page=${page}&limit=${PAGE_SIZE}&from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`),
    detail: (p: string, s: string) => read<Schedule>(`${item(p, s)}?historyLimit=100`),
    create: (p: string, body: CreateBody) => mutate<CreateResult, CreateBody>(`${base(p)}/schedules`, "POST", body),
    edit: (p: string, s: string, body: EditBody) => mutate<EditResult, EditBody>(item(p, s), "PATCH", body),
    confirm: (p: string, s: string, body: ConfirmBody) => mutate<ConfirmResult, ConfirmBody>(`${item(p, s)}/confirm`, "POST", body),
    cancel: (p: string, s: string, body: CancelBody) => mutate<CancelResult, CancelBody>(`${item(p, s)}/cancel`, "POST", body),
    acknowledge: (p: string, s: string, body: AckBody) => mutate<AckResult, AckBody>(`${item(p, s)}/acknowledge`, "POST", body),
    invite: (group: string, body: InviteBody) => mutate<InviteResult, InviteBody>(`/api/v1/groups/${segment(group)}/invitations`, "POST", body),
    invitation: (token: string) => read<Invitation>(`/api/v1/invitations/${segment(token)}`),
    resolve: (token: string, action: "accept" | "reject") => action === "accept" ? mutate<AcceptResult>(`/api/v1/invitations/${segment(token)}/accept`) : mutate<RejectResult>(`/api/v1/invitations/${segment(token)}/reject`),
    notifications: (page = 0) => read<Notifications>(`/api/v1/notifications?page=${page}&limit=100`),
    readNotification: (id: string) => mutate<ReadResult>(`/api/v1/notifications/${segment(id)}/read`),
    members: (p: string, page = 0) => read<Members>(`${base(p)}/members?page=${page}&limit=100`),
    role: (p: string, user: string, body: RoleBody) => mutate<RoleResult, RoleBody>(`${base(p)}/members/${segment(user)}`, "PATCH", body),
    calendar: () => read<Connection>("/api/v1/calendar/connection"),
    reconnect: () => mutate<ReconnectResult>("/api/v1/calendar/reconnect"),
    projection: (p: string, s: string) => read<Projection>(`${item(p, s)}/calendar`),
    retryProjection: (p: string, s: string) => mutate<RetryResult>(`${item(p, s)}/calendar/retry`)
};
