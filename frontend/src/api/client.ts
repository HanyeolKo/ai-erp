import type { paths } from "./generated";
import { currentSessionGeneration, isSessionActive, SessionTerminatedError, terminateSession } from "../session";
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
export type CreationOptions = ResponseOf<paths["/api/v1/projects/creation-options"]["get"]>;
type ProjectCreateBody = BodyOf<paths["/api/v1/projects"]["post"]>;
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
    constructor(readonly status: number, readonly problem: Problem, readonly sessionGeneration?: number) { super(problem.code ?? `HTTP_${status}`); }
}
async function parse<T>(response: Response, expectedGeneration?: number, allowTerminatedSuccess = false, protectedRequest = true): Promise<T> {
    if (response.status === 401) {
        if (expectedGeneration === undefined || isSessionActive(expectedGeneration))
            terminateSession(expectedGeneration);
        throw new ApiError(401, {}, expectedGeneration);
    }
    if (protectedRequest && expectedGeneration !== undefined && !isSessionActive(expectedGeneration) && !(allowTerminatedSuccess && response.ok))
        throw new SessionTerminatedError();
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
        if (protectedRequest && expectedGeneration !== undefined && !isSessionActive(expectedGeneration))
            throw new SessionTerminatedError();
        throw new ApiError(response.status, problem, expectedGeneration);
    }
    if (protectedRequest && expectedGeneration !== undefined && !allowTerminatedSuccess && !isSessionActive(expectedGeneration))
        throw new SessionTerminatedError();
    if (response.status === 204)
        return undefined as T;
    const value = await response.json() as T;
    if (protectedRequest && expectedGeneration !== undefined && !allowTerminatedSuccess && !isSessionActive(expectedGeneration))
        throw new SessionTerminatedError();
    return value;
}
const isPublicPath = (path: string) => path === "/api/v1/system/configuration";
export async function read<T>(path: string, expectedGeneration = currentSessionGeneration()): Promise<T> {
    const protectedRequest = !isPublicPath(path);
    const response = await fetch(path, { credentials: "include" });
    return parse<T>(response, expectedGeneration, false, protectedRequest);
}
async function mutate<T, B = never>(path: string, method = "POST", body?: B): Promise<T> {
    const expectedGeneration = currentSessionGeneration();
    const csrf = await read<Csrf>("/api/v1/csrf", expectedGeneration);
    if (!isSessionActive(expectedGeneration))
        throw new SessionTerminatedError();
    const response = await fetch(path, { method, credentials: "include", headers: { "content-type": "application/json", [csrf.headerName]: csrf.token }, body: body === undefined ? undefined : JSON.stringify(body) });
    const isLogout = path === "/api/v1/logout";
    const ownLogoutSuccess = isLogout && response.ok && isSessionActive(expectedGeneration);
    if (ownLogoutSuccess)
        terminateSession(expectedGeneration);
    return parse<T>(response, expectedGeneration, ownLogoutSuccess, true);
}
const segment = encodeURIComponent;
const base = (p: string) => `/api/v1/projects/${segment(p)}`;
const item = (p: string, s: string) => `${base(p)}/schedules/${segment(s)}`;
export const PAGE_SIZE = 20;
export const api = {
    configuration: () => read<Configuration>("/api/v1/system/configuration"),
    me: () => read<Me>("/api/v1/me"),
    logout: () => mutate<void>("/api/v1/logout"),
    projects: (page = 0) => read<Projects>(`/api/v1/projects?page=${page}&limit=100`),
    creationOptions: (page = 0) => read<CreationOptions>(`/api/v1/projects/creation-options?page=${page}&limit=100`),
    createProject: (body: ProjectCreateBody) => mutate<Project, ProjectCreateBody>("/api/v1/projects", "POST", body),
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
