#!/usr/bin/env node

// Offline-only synthetic fixture for visual inspection. Every record is fictional,
// held in memory, and served on loopback; this file never calls Google or persists data.

import { createServer } from "node:http";

const HOST = "127.0.0.1";
const DEFAULT_PORT = 8081;
const toInt = (value, fallback) => {
    const candidate = Number.parseInt(String(value ?? ""), 10);
    return Number.isFinite(candidate) && candidate > 0 ? candidate : fallback;
};
const PORT = toInt(process.env.UI_PREVIEW_PORT, DEFAULT_PORT);
const scenarioInput = String(process.env.UI_PREVIEW_SCENARIO ?? "populated").toLowerCase();
const scenarios = ["populated", "empty", "error", "viewer", "login", "unconfigured", "google-partial", "unknown"];
const scenario = scenarios.includes(scenarioInput) ? scenarioInput : "populated";
const displayName = String(process.env.UI_PREVIEW_DISPLAY_NAME ?? "김관리자").trim() || "김관리자";
const accountEmail = String(process.env.UI_PREVIEW_EMAIL ?? "manager@example.com").trim() || "manager@example.com";

const parseJson = async (stream) => {
    const chunks = [];
    for await (const chunk of stream)
        chunks.push(chunk);
    if (!chunks.length)
        return undefined;
    const body = Buffer.concat(chunks).toString("utf8");
    if (!body.trim())
        return undefined;
    return JSON.parse(body);
};

const buildResponse = (payload, status = 200) => {
    const text = JSON.stringify(payload);
    return {
        status,
        headers: {
            "content-type": status >= 400 ? "application/problem+json" : "application/json",
            "cache-control": "no-store",
            "access-control-allow-origin": `http://${HOST}:${PORT}`,
            "access-control-allow-headers": "content-type,x-csrf-token",
            "access-control-allow-methods": "GET,POST,PATCH,DELETE,OPTIONS",
        },
        body: text,
    };
};

const problem = (code, status = 500, extra) => buildResponse({ code, ...extra }, status);
const ok = (value, status = 200) => buildResponse(value, status);
const notFound = (path) => problem("NOT_FOUND", 404, { path });

const now = new Date("2026-09-10T00:00:00.000Z");
const projectId = "p1";
const groupId = "g1";
const userId = "u1";
const sequence = {
    schedule: 2,
};
const nextScheduleId = () => `s${++sequence.schedule}`;
const nextInvitationToken = () => `t${Date.now().toString(36).slice(-4)}`;
const nowIso = () => new Date(now.getTime() + sequence.schedule * 60000).toISOString();

const baseMembers = [
    { userId: "u1", role: "MANAGER" },
    { userId: "u2", role: "MEMBER" },
    { userId: "u3", role: "VIEWER" },
];

const scheduleTemplate = {
    id: "s1",
    projectId,
    title: "9월 운영 일정 확정",
    description: "주간 예측 리뷰 및 위험 대응회의",
    status: "CONFIRMED",
    rowVersion: 3,
    businessRevision: 4,
    startsAt: "2026-09-10T01:00:00.000Z",
    endsAt: "2026-09-10T02:00:00.000Z",
    createdBy: "u1",
    participants: [
        { memberUserId: "u1", externalEmail: null, acknowledged: false },
        { memberUserId: "u2", externalEmail: null, acknowledged: true },
        { externalEmail: "partner@example.com", memberUserId: null, acknowledged: false },
    ],
    changes: [
        { businessRevision: 2, type: "DRAFT", changedBy: "u2", createdAt: "2026-09-09T12:00:00.000Z" },
        { businessRevision: 4, type: "CONFIRMED", changedBy: "u1", createdAt: "2026-09-10T00:00:00.000Z" },
    ],
};
const secondarySchedule = {
    ...scheduleTemplate,
    id: "s2",
    title: "9월 고객 동기화 점검",
    description: "외부 참석자 대상 일정 동기화",
    status: "DRAFT",
    rowVersion: 1,
    businessRevision: 0,
    startsAt: "2026-09-11T01:00:00.000Z",
    endsAt: "2026-09-11T02:00:00.000Z",
    createdBy: "u2",
    participants: [
        { memberUserId: "u2", externalEmail: null, acknowledged: false },
        { memberUserId: null, externalEmail: "external@example.org", acknowledged: false },
    ],
    changes: [],
};
const baseInvitation = {
    id: "i1",
    projectId,
    email: "new.member@example.com",
    token: "t1",
    status: "PENDING",
    expiresAt: "2026-12-31T00:00:00.000Z",
};
const baseNotifications = [
    {
        id: "n1",
        type: "SCHEDULE_CONFIRMED",
        link: "/projects/p1/schedules/s1",
        readAt: null,
        createdAt: "2026-09-09T00:00:00.000Z",
    },
];

const baseDriveFiles = [
    { id: "file-roadmap", name: "서울 운영 로드맵.pdf", mimeType: "application/pdf", url: "https://drive.google.com/open?id=file-roadmap", modifiedTime: "2026-09-09T03:00:00.000Z" },
    { id: "file-brief", name: "고객 브리핑 문서", mimeType: "application/vnd.google-apps.document", url: "https://drive.google.com/open?id=file-brief", modifiedTime: "2026-09-08T06:00:00.000Z" },
];
const baseProjectFiles = [
    { id: "ref-roadmap", fileId: "file-roadmap", name: "서울 운영 로드맵.pdf", mimeType: "application/pdf", url: "https://drive.google.com/open?id=file-roadmap", attachedBy: "김관리자", attachedAt: "2026-09-09T04:00:00.000Z", canRemove: true },
];
const baseMailMessages = [
    { id: "mail-1", subject: "9월 운영 일정 안내", from: "partner@example.com", to: ["manager@example.com"], snippet: "이번 주 운영 일정을 확인해 주세요.", internalDate: "2026-09-09T08:00:00.000Z", unread: true },
    { id: "mail-2", subject: "프로젝트 브리핑", from: "manager@example.com", to: ["partner@example.com"], snippet: "프로젝트 진행 현황을 공유합니다.", internalDate: "2026-09-08T08:00:00.000Z", unread: false },
];
const baseMailDetails = {
    "mail-1": { id: "mail-1", subject: "9월 운영 일정 안내", from: "partner@example.com", to: ["manager@example.com"], cc: [], date: "2026-09-09T08:00:00.000Z", bodyText: "이번 주 운영 일정을 확인해 주세요.", truncated: false },
    "mail-2": { id: "mail-2", subject: "프로젝트 브리핑", from: "manager@example.com", to: ["partner@example.com"], cc: [], date: "2026-09-08T08:00:00.000Z", bodyText: "프로젝트 진행 현황을 공유합니다.", truncated: false },
};

const makeState = () => {
    const role = scenario === "viewer" ? "VIEWER" : "MANAGER";
    const loginReady = scenario !== "unconfigured";
    const loginUrl = scenario === "unconfigured" ? null : "/oauth2/authorization/google";
    const base = {
        scenario,
        csrf: { headerName: "X-CSRF-TOKEN", token: "csrf-token-local" },
        configuration: {
            login: loginReady ? "READY" : "CONFIGURATION_REQUIRED",
            calendar: loginReady ? "READY" : "CONFIGURATION_REQUIRED",
            loginUrl,
        },
        me: scenario === "login" ? undefined : { id: userId, displayName, email: accountEmail, authorities: ["ROLE_USER"] },
        projectBaseRole: role,
        connection: { status: "CONNECTED", configurationRequired: false },
        googleConnection: { configurationRequired: false, accountEmail, drive: { status: "CONNECTED" }, gmail: { status: "CONNECTED" }, calendar: { status: "CONNECTED" } },
        invite: { ...baseInvitation },
        membersByProject: { [projectId]: baseMembers.map((member) => ({ ...member })) },
        schedulesByProject: { [projectId]: [ { ...scheduleTemplate }, { ...secondarySchedule } ] },
        notifications: [...baseNotifications],
        creationOptions: [{ id: groupId, name: "서울 운영 그룹", canCreate: scenario !== "viewer", reason: scenario === "viewer" ? "프로젝트 생성 권한이 없습니다." : null }],
        projectFilesByProject: { [projectId]: scenario === "empty" ? [] : baseProjectFiles.map((file) => ({ ...file })) },
        projectCalendars: { [projectId]: { status: "NOT_BOUND", calendarName: null, ownerName: null, isOwner: scenario !== "viewer", canManage: scenario !== "viewer", backfillPending: false } },
        shareInvitation: { state: "NOT_CREATED", code: null, expiresAt: null },
        sendReceipts: new Map(),
        sendReceiptReads: new Map(),
    };
    if (scenario === "empty")
        base.membersByProject[projectId] = [];
    if (scenario === "unconfigured") {
        base.connection = { status: "NOT_CONNECTED", configurationRequired: true };
        base.googleConnection = { configurationRequired: true, accountEmail: null, drive: { status: "NOT_CONNECTED" }, gmail: { status: "NOT_CONNECTED" }, calendar: { status: "NOT_CONNECTED" } };
    }
    if (scenario === "google-partial") {
        base.googleConnection = { configurationRequired: false, accountEmail, drive: { status: "CONNECTED" }, gmail: { status: "PERMISSION_REQUIRED" }, calendar: { status: "REAUTH_REQUIRED" } };
    }
    return base;
};

const state = makeState();
const createAttempts = new Map();
const projectCalendarFor = (id) => state.projectCalendars[id] ?? { status: "NOT_BOUND", calendarName: null, ownerName: null, isOwner: scenario !== "viewer", canManage: scenario !== "viewer", backfillPending: false };
const googleConnected = (feature) => state.googleConnection[feature.toLowerCase()]?.status === "CONNECTED";
const googleAccessProblem = (feature) => problem(state.googleConnection[feature.toLowerCase()]?.status === "REAUTH_REQUIRED" ? "GOOGLE_REAUTH_REQUIRED" : "GOOGLE_PERMISSION_REQUIRED", 403);

if (scenario === "empty") {
    state.schedulesByProject = { [projectId]: [] };
    state.configuration = {
        ...state.configuration,
        login: "READY",
        calendar: "READY",
    };
}

const ensureRole = (project) => {
    if (scenario === "viewer" && project.role === "MANAGER")
        return { ...project, role: "VIEWER" };
    if (scenario === "populated")
        return project;
    return { ...project, role: project.role };
};

state.projects = scenario === "empty" ? [] : [{ id: projectId, groupId, name: "서울 스프린트 운영", role: state.projectBaseRole }];
state.projects = state.projects.map((project) => ensureRole(project));

const stateProjectRole = (project) => scenario === "viewer" ? "VIEWER" : "MANAGER";

const getProject = (id) => state.projects.find((project) => project.id === id);
const getSchedules = (id) => state.schedulesByProject[id] ?? [];
const getMembers = (id) => state.membersByProject[id] ?? [];
const getProjection = (id, sid) => {
    if (id !== projectId)
        return undefined;
    const schedules = getSchedules(id);
    const schedule = schedules.find((value) => value.id === sid);
    if (!schedule)
        return undefined;
    return {
        scheduleId: sid,
        status: schedule.status === "CANCELLED" ? "FAILED" : (sid === "s1" ? "SYNCED" : "NOT_CONNECTED"),
        businessRevision: schedule.businessRevision,
        retryClassification: schedule.status === "CANCELLED" ? "TRANSIENT" : null,
    };
};
const projectionKey = (projectIdValue, scheduleId) => `${projectIdValue}:${scheduleId}`;
const projectionOverrides = new Map([
    [projectionKey(projectId, "s1"), { status: "SYNCED", retryClassification: null, businessRevision: 4 }],
    [projectionKey(projectId, "s2"), { status: "NOT_CONNECTED", retryClassification: null, businessRevision: 0 }],
]);

const recalcProjection = (projectIdValue, scheduleId) => {
    const schedule = getSchedules(projectIdValue).find((value) => value.id === scheduleId);
    const key = projectionKey(projectIdValue, scheduleId);
    if (!schedule)
        return undefined;
    const current = projectionOverrides.get(key);
    if (current)
        return { scheduleId, status: current.status, businessRevision: schedule.businessRevision ?? 0, retryClassification: current.retryClassification };
    return { scheduleId, status: "SYNCED", businessRevision: schedule.businessRevision ?? 0 };
};

const dashboard = (projectIdValue) => {
    const schedules = getSchedules(projectIdValue);
    const members = getMembers(projectIdValue);
    const pendingAcknowledgementCount = schedules.filter((schedule) => schedule.businessRevision > 0 && schedule.participants.some((entry) => !entry.externalEmail && entry.memberUserId && !entry.acknowledged)).length;
    const calendarRiskCount = schedules.filter((schedule) => {
        const projection = getProjection(projectIdValue, schedule.id);
        return projection?.status === "FAILED" || projection?.status === "REAUTH_REQUIRED";
    }).length;
    const limitRows = schedules.slice(0, 2);
    return {
        projectId: projectIdValue,
        memberCount: members.length,
        scheduleCount: schedules.length,
        pendingAcknowledgementCount,
        calendarRiskCount,
        upcomingSchedules: limitRows.map((schedule) => schedule),
        actionQueue: limitRows.filter((schedule) => schedule.status !== "CANCELLED"),
    };
};

const updateParticipantsAck = (schedule, userIdValue) => {
    const next = schedule.participants.map((entry) => entry.memberUserId === userIdValue && !entry.externalEmail ? { ...entry, acknowledged: true } : entry);
    return next;
};

const emitDashboard = () => buildResponse(dashboard(projectId));

const partsOf = (path) => path.split("/");
const scheduleIdFromProjectPath = (path) => partsOf(path)[6];
const memberUserIdFromProjectPath = (path) => partsOf(path)[6];
const notificationIdFromPath = (path) => partsOf(path)[4];

const handleErrorMode = (pathname) => {
    if (!pathname.startsWith("/api/v1"))
        return false;
    if (scenario !== "error")
        return false;
    if (pathname === "/api/v1/system/configuration" || pathname === "/api/v1/csrf" || pathname === "/api/v1/me")
        return false;
    return true;
};

const send = (response, payload) => {
    response.statusCode = payload.status;
    for (const [name, value] of Object.entries(payload.headers))
        response.setHeader(name, value);
    response.end(payload.body);
};

const readProjects = (url) => {
    const page = Math.max(0, Number.parseInt(url.searchParams.get("page") ?? "0", 10));
    const limit = Number.parseInt(url.searchParams.get("limit") ?? "100", 10);
    const start = page * limit;
    const data = state.projects.slice(start, start + limit);
    return ok(data, 200);
};

const readCreationOptions = (url) => {
    const page = Math.max(0, Number.parseInt(url.searchParams.get("page") ?? "0", 10));
    const limit = Number.parseInt(url.searchParams.get("limit") ?? "100", 10);
    return ok(state.creationOptions.slice(page * limit, page * limit + limit));
};

const createProjectFinder = (pathname, expectedProject) => {
    if (!expectedProject)
        return notFound(pathname);
    return null;
};

const applyMutation = (payload, projectIdValue, scheduleId) => {
    const schedules = getSchedules(projectIdValue);
    const index = schedules.findIndex((value) => value.id === scheduleId);
    if (index < 0)
        return null;
    const schedule = { ...schedules[index] };
    return { index, schedule, schedules };
};

const routeRequest = async (request, body) => {
    const method = request.method ?? "GET";
    const url = new URL(request.url ?? "/", `http://${HOST}:${PORT}`);
    const path = url.pathname;

    if (method === "OPTIONS")
        return ok({}, 204);

    if (handleErrorMode(path))
        return problem("TEMPORARY", 503, { message: "Preview API is in error scenario" });

    if (method === "GET" && path === "/api/v1/system/configuration")
        return ok(state.configuration);
    if (method === "GET" && path === "/api/v1/csrf")
        return ok(state.csrf);
    if (method === "GET" && path === "/api/v1/me") {
        if (scenario === "login")
            return problem("UNAUTHENTICATED", 401);
        return ok(state.me);
    }
    if (method === "GET" && path === "/api/v1/projects")
        return readProjects(url);
    if (method === "GET" && path === "/api/v1/projects/creation-options")
        return readCreationOptions(url);
    if (method === "POST" && path === "/api/v1/projects") {
        const requestId = body?.requestId;
        if (scenario === "unknown" && requestId && !createAttempts.has(requestId)) {
            createAttempts.set(requestId, { name: body?.name ?? "", status: "UNKNOWN" });
            return problem("RESULT_UNKNOWN", 500);
        }
        const id = state.projects.length ? `p${state.projects.length + 1}` : projectId;
        const project = { id, groupId, name: body?.name ?? "새 프로젝트", role: "MANAGER" };
        state.projects.push(project);
        state.membersByProject[id] = baseMembers.map((member) => ({ ...member }));
        state.schedulesByProject[id] = [];
        state.projectFilesByProject[id] = [];
        state.projectCalendars[id] = { status: "NOT_BOUND", calendarName: null, ownerName: null, isOwner: true, canManage: true, backfillPending: false };
        return ok(project);
    }
    if (method === "GET" && path === `/api/v1/projects/${projectId}/dashboard`)
        return ok(dashboard(projectId));
    if (method === "GET" && path === `/api/v1/projects/${projectId}/schedules`)
        return ok(getSchedules(projectId).slice());
    if (method === "GET" && path === `/api/v1/projects/${projectId}/schedules/${url.pathname.split("/").at(-1)}`)
        return (() => {
            const scheduleId = path.split("/").at(-1);
            const schedule = getSchedules(projectId).find((value) => value.id === scheduleId);
            if (!schedule)
                return notFound(path);
            const withHistory = { ...schedule, businessRevision: schedule.businessRevision + 1, changes: schedule.changes.map((entry) => ({ ...entry })) };
            return ok(withHistory);
        })();
    if (method === "GET" && path.startsWith(`/api/v1/projects/${projectId}/schedules/`) && path.endsWith("/calendar"))
        return (() => {
            const scheduleId = scheduleIdFromProjectPath(path);
            const schedule = getSchedules(projectId).find((value) => value.id === scheduleId);
            if (!schedule)
                return notFound(path);
            const projection = recalcProjection(projectId, scheduleId);
            return ok(projection);
        })();
    if (method === "GET" && path === `/api/v1/projects/${projectId}/members`)
        return ok(getMembers(projectId).slice());
    if (method === "POST" && path === `/api/v1/groups/${groupId}/invitations`)
        return (() => {
            const next = {
                ...state.invite,
                token: nextInvitationToken(),
                status: "PENDING",
                expiresAt: "2026-12-31T00:00:00.000Z",
                email: body?.email,
                projectId,
            };
            state.invite = next;
            return ok(next);
        })();
    if (method === "GET" && path === `/api/v1/projects/${projectId}/share-invitation`)
        return ok({ ...state.shareInvitation });
    if (method === "POST" && path === `/api/v1/projects/${projectId}/share-invitation`) {
        state.shareInvitation = { state: "ACTIVE", code: "7K3M9F2D6R8TWX4C", expiresAt: "2026-12-31T00:00:00.000Z" };
        return ok({ ...state.shareInvitation });
    }
    if (method === "DELETE" && path === `/api/v1/projects/${projectId}/share-invitation`) {
        state.shareInvitation = { state: "REVOKED", code: null, expiresAt: null };
        return ok({}, 204);
    }
    if (method === "GET" && path.startsWith("/api/v1/project-invitations/")) {
        const code = path.split("/").at(-1);
        return ok({ alreadyMember: false, state: "ACTIVE", projectName: "서울 스프린트 운영", projectId, expiresAt: "2026-12-31T00:00:00.000Z", inviterName: "김관리자", role: "MEMBER", code });
    }
    if (method === "POST" && path.startsWith("/api/v1/project-invitations/") && path.endsWith("/join"))
        return ok({ id: projectId, groupId, name: "서울 스프린트 운영", role: "MEMBER" });
    if (method === "GET" && path === `/api/v1/invitations/${state.invite.token}`)
        return ok({ ...state.invite, projectId });
    if (method === "POST" && (path === `/api/v1/invitations/${state.invite.token}/accept` || path === `/api/v1/invitations/${state.invite.token}/reject`)) {
        state.invite = {
            ...state.invite,
            status: path.endsWith("/accept") ? "ACCEPTED" : "REJECTED",
        };
        return ok(state.invite);
    }
    if (method === "GET" && path === "/api/v1/notifications")
        return ok(state.notifications.map((value) => ({ ...value })));
    if (method === "POST" && path === "/api/v1/calendar/reconnect")
        return (() => {
            state.connection = { ...state.connection, status: "PENDING", configurationRequired: false };
            return ok(state.connection);
        })();
    if (method === "GET" && path === "/api/v1/calendar/connection")
        return ok(state.connection);
    if (method === "GET" && path === "/api/v1/google/connection")
        return ok(state.googleConnection);
    if (method === "POST" && path === "/api/v1/google/connect") {
        const feature = String(body?.feature ?? "").toUpperCase();
        if (!["DRIVE", "GMAIL", "CALENDAR"].includes(feature)) return problem("INVALID_FEATURE", 400);
        const key = feature.toLowerCase();
        state.googleConnection[key] = { status: "CONNECTED" };
        state.googleConnection.accountEmail = accountEmail;
        return ok({ authorizationUrl: `/oauth2/authorization/google?feature=${feature}` });
    }
    if (method === "DELETE" && path === "/api/v1/google/connection") {
        state.googleConnection = { configurationRequired: false, accountEmail: null, drive: { status: "NOT_CONNECTED" }, gmail: { status: "NOT_CONNECTED" }, calendar: { status: "NOT_CONNECTED" } };
        return ok({}, 204);
    }
    if (method === "GET" && path === "/api/v1/google/drive/files") {
        if (!googleConnected("DRIVE")) return googleAccessProblem("DRIVE");
        const query = (url.searchParams.get("query") ?? "").trim().toLowerCase();
        const files = baseDriveFiles.filter((file) => !query || file.name.toLowerCase().includes(query));
        return ok({ files, nextPageToken: null });
    }
    if (method === "GET" && path === "/api/v1/google/mail/messages") {
        if (!googleConnected("GMAIL")) return googleAccessProblem("GMAIL");
        const query = (url.searchParams.get("query") ?? "").trim().toLowerCase();
        const messages = baseMailMessages.filter((mail) => !query || `${mail.subject} ${mail.from} ${mail.snippet}`.toLowerCase().includes(query));
        return ok({ messages, nextPageToken: null });
    }
    if (method === "GET" && path.startsWith("/api/v1/google/mail/messages/")) {
        if (!googleConnected("GMAIL")) return googleAccessProblem("GMAIL");
        const detail = baseMailDetails[path.split("/").at(-1)];
        return detail ? ok(detail) : notFound(path);
    }
    if (method === "POST" && path === "/api/v1/google/mail/send") {
        if (!googleConnected("GMAIL")) return googleAccessProblem("GMAIL");
        const requestId = String(body?.requestId ?? "");
        if (!requestId) return problem("MISSING_REQUEST_ID", 400);
        const existing = state.sendReceipts.get(requestId);
        if (existing) return ok(existing);
        const status = scenario === "unknown" ? "UNKNOWN" : "SENT";
        const receipt = { requestId, status, messageId: status === "SENT" ? `sent-${requestId.slice(0, 8)}` : null };
        state.sendReceipts.set(requestId, receipt);
        state.sendReceiptReads.set(requestId, 0);
        return ok(receipt);
    }
    if (method === "GET" && path.startsWith("/api/v1/google/mail/sends/")) {
        if (!googleConnected("GMAIL")) return googleAccessProblem("GMAIL");
        const requestId = path.split("/").at(-1);
        const receipt = state.sendReceipts.get(requestId);
        if (!receipt) return notFound(path);
        const reads = (state.sendReceiptReads.get(requestId) ?? 0) + 1;
        state.sendReceiptReads.set(requestId, reads);
        if (scenario === "unknown" && receipt.status === "UNKNOWN" && reads > 1) {
            const sent = { ...receipt, status: "SENT", messageId: `sent-${requestId.slice(0, 8)}` };
            state.sendReceipts.set(requestId, sent);
            return ok(sent);
        }
        return ok(receipt);
    }
    if (method === "GET" && path === "/api/v1/google/calendars") {
        if (!googleConnected("CALENDAR")) return googleAccessProblem("CALENDAR");
        return ok({ calendars: [{ id: "cal-work", name: "서울 운영 일정" }, { id: "cal-team", name: "팀 공유 Calendar" }], nextPageToken: null });
    }
    if (method === "GET" && path === `/api/v1/projects/${projectId}/files`) {
        return ok({ files: (state.projectFilesByProject[projectId] ?? []).map((file) => ({ ...file })), hasNext: false });
    }
    if (method === "POST" && path === `/api/v1/projects/${projectId}/files`) {
        const file = baseDriveFiles.find((value) => value.id === body?.fileId);
        if (!file) return notFound(path);
        const reference = { id: `ref-${file.id}`, fileId: file.id, name: file.name, mimeType: file.mimeType, url: file.url, attachedBy: "김관리자", attachedAt: nowIso(), canRemove: true };
        state.projectFilesByProject[projectId] = [...(state.projectFilesByProject[projectId] ?? []).filter((value) => value.fileId !== file.id), reference];
        return ok(reference);
    }
    if (method === "DELETE" && path.startsWith(`/api/v1/projects/${projectId}/files/`)) {
        const referenceId = path.split("/").at(-1);
        state.projectFilesByProject[projectId] = (state.projectFilesByProject[projectId] ?? []).filter((value) => value.id !== referenceId);
        return ok({}, 204);
    }
    if (method === "GET" && path === `/api/v1/projects/${projectId}/calendar`)
        return ok({ ...projectCalendarFor(projectId) });
    if (method === "POST" && path === `/api/v1/projects/${projectId}/calendar`) {
        if (!state.projectCalendars[projectId]?.canManage) return problem("CALENDAR_FORBIDDEN", 403);
        const calendar = [{ id: "cal-work", name: "서울 운영 일정" }, { id: "cal-team", name: "팀 공유 Calendar" }].find((item) => item.id === body?.calendarId);
        if (!calendar) return problem("CALENDAR_NOT_FOUND", 404);
        state.projectCalendars[projectId] = { status: "BOUND", calendarName: calendar.name, ownerName: "김관리자", isOwner: true, canManage: true, backfillPending: true };
        return ok({ ...state.projectCalendars[projectId] });
    }
    if (method === "DELETE" && path === `/api/v1/projects/${projectId}/calendar`) {
        state.projectCalendars[projectId] = { ...projectCalendarFor(projectId), status: "NOT_BOUND", calendarName: null, ownerName: null, backfillPending: false };
        return ok({}, 204);
    }

    if (method === "POST" && path === `/api/v1/projects/${projectId}/schedules`) {
        const schedule = {
            ...scheduleTemplate,
            id: nextScheduleId(),
            projectId,
            title: body?.title ?? "무제",
            description: body?.description ?? null,
            startsAt: body?.startsAt ?? nowIso(),
            endsAt: body?.endsAt ?? nowIso(),
            rowVersion: 1,
            businessRevision: 1,
            createdBy: userId,
            participants: [
                ...(body?.memberParticipantIds ?? []).map((member) => ({ memberUserId: member, externalEmail: null, acknowledged: false })),
                ...(body?.externalAttendeeEmails ?? []).map((email) => ({ memberUserId: null, externalEmail: email, acknowledged: false })),
            ],
            changes: [{ businessRevision: 1, type: "DRAFT", changedBy: userId, createdAt: nowIso() }],
        };
        state.schedulesByProject[projectId] = [schedule, ...getSchedules(projectId)];
        projectionOverrides.delete(projectionKey(projectId, schedule.id));
        return ok(schedule);
    }
    if (method === "PATCH" && path.startsWith(`/api/v1/projects/${projectId}/schedules/`) && !path.endsWith("/confirm") && !path.endsWith("/cancel") && !path.endsWith("/acknowledge") && !path.endsWith("/calendar/retry")) {
        const scheduleId = scheduleIdFromProjectPath(path);
        const found = applyMutation(body, projectId, scheduleId);
        if (!found)
            return notFound(path);
        const schedule = found.schedule;
        const changed = { ...schedule, ...body, id: schedule.id, projectId: schedule.projectId, businessRevision: schedule.businessRevision + 1, rowVersion: schedule.rowVersion + 1, changes: [...schedule.changes, { businessRevision: schedule.businessRevision + 1, type: "UPDATED", changedBy: userId, createdAt: nowIso() }] };
        if (changed.startsAt && changed.endsAt && changed.endsAt <= changed.startsAt)
            return problem("INVALID_TIME", 400);
        found.schedules[found.index] = changed;
        projectionOverrides.set(projectionKey(projectId, scheduleId), { status: "SYNCED", retryClassification: null, businessRevision: changed.businessRevision });
        return ok(changed);
    }
    if (method === "POST" && path.endsWith("/confirm")) {
        const scheduleId = scheduleIdFromProjectPath(path);
        const found = applyMutation(body, projectId, scheduleId);
        if (!found)
            return notFound(path);
        const schedule = { ...found.schedule, status: "CONFIRMED", rowVersion: found.schedule.rowVersion + 1 };
        found.schedules[found.index] = schedule;
        return ok(schedule);
    }
    if (method === "POST" && path.endsWith("/cancel")) {
        const scheduleId = scheduleIdFromProjectPath(path);
        const found = applyMutation(body, projectId, scheduleId);
        if (!found)
            return notFound(path);
        const schedule = { ...found.schedule, status: "CANCELLED", rowVersion: found.schedule.rowVersion + 1 };
        found.schedules[found.index] = schedule;
        return ok(schedule);
    }
    if (method === "POST" && path.endsWith("/acknowledge")) {
        const scheduleId = scheduleIdFromProjectPath(path);
        const found = applyMutation(body, projectId, scheduleId);
        if (!found)
            return notFound(path);
        const schedule = { ...found.schedule, participants: updateParticipantsAck(found.schedule, body?.expectedBusinessRevision ? body.expectedBusinessRevision ? "u1" : "u1" : userId), businessRevision: found.schedule.businessRevision + 1 };
        found.schedules[found.index] = schedule;
        return ok(schedule);
    }
    if (method === "POST" && path.endsWith("/calendar/retry")) {
        const scheduleId = scheduleIdFromProjectPath(path);
        const found = applyMutation(body, projectId, scheduleId);
        if (!found)
            return notFound(path);
        const current = getProjection(projectId, scheduleId);
        if (!current)
            return notFound(path);
        const next = {
            ...current,
            status: "SYNCED",
            retryClassification: null,
        };
        projectionOverrides.set(projectionKey(projectId, scheduleId), next);
        return ok(next);
    }
    if (method === "POST" && path.startsWith(`/api/v1/notifications/`) && path.endsWith("/read")) {
        const notificationId = notificationIdFromPath(path);
        const index = state.notifications.findIndex((item) => item.id === notificationId);
        if (index < 0)
            return notFound(path);
        state.notifications[index] = { ...state.notifications[index], readAt: nowIso() };
        return ok(state.notifications[index]);
    }
    if (method === "PATCH" && path.startsWith(`/api/v1/projects/${projectId}/members/`)) {
        const user = memberUserIdFromProjectPath(path);
        const members = getMembers(projectId);
        const index = members.findIndex((item) => item.userId === user);
        if (index < 0)
            return notFound(path);
        if (!body?.role)
            return problem("MISSING_FIELD", 400);
        members[index] = { ...members[index], role: body.role };
        state.membersByProject[projectId] = members;
        return ok(members[index]);
    }

    return null;
};

const requestHandler = async (request, response) => {
    const requestInfo = new URL(request.url ?? "/", `http://${HOST}:${PORT}`);
    const pathname = requestInfo.pathname;
    try {
        const body = await parseJson(request);
        const route = await routeRequest({ ...request, url: request.url }, body);
        const payload = route ?? (() => {
            if (scenario === "error" && pathname.startsWith("/api/v1"))
                return problem("UNSUPPORTED", 404, { path: pathname });
            return notFound(pathname);
        })();
        send(response, payload);
    }
    catch (error) {
        send(response, problem("INTERNAL", 500, { message: error instanceof Error ? error.message : "internal" }));
    }
};

if (scenarioInput !== scenario)
    console.log(`[ui-preview] unknown scenario '${scenarioInput}', falling back to '${scenario}'`);
console.log(`[ui-preview] starting fictional preview API server | scenario=${scenario} port=${PORT}`);
console.log("[ui-preview] mode=fixture data only; no external calls");

const server = createServer(requestHandler);
server.once("error", (error) => {
    const code = error?.code ?? "";
    if (code === "EADDRINUSE") {
        console.error(`[ui-preview] port ${PORT} already in use on ${HOST}. Refusing to stop existing services.`);
        process.exit(1);
    }
    console.error("[ui-preview] server failed to start", error);
    process.exit(1);
});
server.listen(PORT, HOST, () => {
    console.log(`[ui-preview] listening at http://${HOST}:${PORT}`);
});
