import { vi, expect, afterEach } from "vitest";
let boundaryFailures: unknown[] = [];
afterEach(() => { vi.useRealTimers(); const failures = boundaryFailures; boundaryFailures = []; expect(failures, "HTTP boundary contract failures").toEqual([]); });
export const project = { id: "p1", groupId: "g1", name: "Planning", role: "MANAGER" };
export const schedule = { id: "s1", projectId: "p1", title: "Design review", description: "Keep description", status: "CONFIRMED", rowVersion: 3, businessRevision: 4, startsAt: "2090-09-10T01:00:00Z", endsAt: "2090-09-10T02:00:00Z", createdBy: "u1", participants: [{ memberUserId: "u1", externalEmail: null, acknowledged: false }, { memberUserId: null, externalEmail: "guest@example.test", acknowledged: false }], changes: [{ businessRevision: 4, type: "SCHEDULE_CONFIRMED", changedBy: "u1", createdAt: "2090-09-09T00:00:00Z" }] };
export const invitation = { id: "i1", projectId: "p1", email: "member@example.test", token: "t1", status: "PENDING", expiresAt: "2199-10-01T00:00:00Z" };
export const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { "content-type": status >= 400 ? "application/problem+json" : "application/json" } });
type Handler = (body: any, url: URL) => Response | Promise<Response>;
export function http() {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date("2090-09-10T00:00:00Z"));
  const routes = new Map<string, Handler>();
  const calls: { method: string; url: string; body: any }[] = [];
  const on = (method: string, path: string, handler: Handler) => { routes.set(`${method} ${path}`, handler); };
  on("GET", "/api/v1/system/configuration", () => json({ login: "READY", calendar: "READY", loginUrl: "/oauth2/authorization/google" }));
  on("GET", "/api/v1/me", () => json({ id: "u1", displayName: "김관리자", email: "manager@example.test", authorities: ["ROLE_USER"] }));
  on("GET", "/api/v1/csrf", () => json({ headerName: "X-CSRF-TOKEN", token: "csrf" }));
  on("GET", "/api/v1/projects", () => json([project]));
  on("GET", "/api/v1/projects/creation-options", () => json([]));
  on("GET", "/api/v1/projects/p1/dashboard", () => json({ projectId: "p1", memberCount: 2, scheduleCount: 1, pendingAcknowledgementCount: 1, calendarRiskCount: 1, upcomingSchedules: [schedule], actionQueue: [schedule] }));
  on("GET", "/api/v1/projects/p1/schedules", () => json([schedule]));
  on("GET", "/api/v1/projects/p1/schedules/s1", () => json(schedule));
  on("GET", "/api/v1/projects/p1/schedules/s1/calendar", () => json({ scheduleId: "s1", status: "SYNCED", businessRevision: 4 }));
  on("GET", "/api/v1/projects/p1/members", () => json([{ userId: "u1", displayName: "김관리자", email: "manager@example.test", role: "MANAGER" }, { userId: "u2", displayName: "이구성원", email: "member@example.test", role: "MEMBER" }]));
  on("GET", "/api/v1/notifications", () => json([{ id: "n1", type: "SCHEDULE_CONFIRMED", link: "/projects/p1/schedules/s1", readAt: null, createdAt: "2090-09-09T00:00:00Z" }]));
  on("GET", "/api/v1/calendar/connection", () => json({ status: "NOT_CONNECTED", configurationRequired: false }));
  on("GET", "/api/v1/invitations/t1", () => json(invitation));
  vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const method = init?.method ?? "GET"; const url = new URL(String(input), "http://localhost"); const body = init?.body ? JSON.parse(String(init.body)) : undefined;
    calls.push({ method, url: url.pathname + url.search, body }); expect(init?.credentials).toBe("include");
    if (method !== "GET") expect(new Headers(init?.headers).get("X-CSRF-TOKEN")).toBe("csrf");
    try {
      const handler = routes.get(`${method} ${url.pathname}`);
      if (!handler) throw new Error(`Unexpected HTTP request: ${method} ${url.pathname}`);
      return await handler(body, url);
    } catch (error) {
      boundaryFailures.push(error);
      throw error;
    }
  }));
  return { on, calls };
}
