import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { focusManager } from "@tanstack/react-query";
import { afterEach, expect, test, vi } from "vitest";
import App from "./App";
import { http, invitation, json, project, schedule } from "./test/http";

afterEach(() => {
  vi.unstubAllGlobals();
  window.history.replaceState(null, "", "/");
  sessionStorage.clear();
  focusManager.setFocused(undefined);
});

const mount = (path: string) => {
  window.location.hash = `#${path}`;
  render(<App />);
  return userEvent.setup();
};

const refreshStaleQueries = async () => {
  await act(async () => {
    vi.setSystemTime(new Date("2090-09-10T00:01:00Z"));
    focusManager.setFocused(false);
    focusManager.setFocused(true);
  });
};

test("ScheduleForm hides its editor through project 403 then retry 500 and restores it only after 200", async () => {
  const server = http();
  const user = mount("/projects/p1/schedules/s1/edit");
  expect(await screen.findByLabelText("제목")).toHaveValue(schedule.title);

  server.on("GET", "/api/v1/projects", () => json({ code: "PROJECT_FORBIDDEN" }, 403));
  await refreshStaleQueries();
  await screen.findByText("PROJECT_FORBIDDEN");
  expect(screen.queryByLabelText("제목")).not.toBeInTheDocument();

  server.on("GET", "/api/v1/projects", () => json({ code: "PROJECT_RETRY_FAILED" }, 500));
  await user.click(screen.getByRole("button", { name: "다시 시도" }));
  await screen.findByText("PROJECT_RETRY_FAILED");
  expect(screen.queryByLabelText("제목")).not.toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "일정 저장" })).not.toBeInTheDocument();

  server.on("GET", "/api/v1/projects", () => json([project]));
  await user.click(screen.getByRole("button", { name: "다시 시도" }));
  expect(await screen.findByLabelText("제목")).toHaveValue(schedule.title);
});

test("Detail keeps a denied command blocked after project navigation and offers fresh access recovery", async () => {
  const server = http();
  server.on("POST", "/api/v1/projects/p1/schedules/s1/cancel", () => json({ code: "COMMAND_FORBIDDEN" }, 403));
  const user = mount("/projects/p1/schedules/s1");
  await user.click(await screen.findByRole("button", { name: "일정 취소" }));
  await screen.findByText("COMMAND_FORBIDDEN");

  await user.click(screen.getByRole("link", { name: "구성원" }));
  await screen.findByRole("heading", { name: "구성원" });
  let retryRead = false;
  let finishDetail!: (response: Response) => void;
  server.on("GET", "/api/v1/projects/p1/schedules/s1", () => {
    if (!retryRead) return json(schedule);
    return new Promise(resolve => { finishDetail = resolve; });
  });
  await act(async () => {
    window.location.hash = "#/projects/p1/schedules/s1";
    window.dispatchEvent(new HashChangeEvent("hashchange"));
  });
  await screen.findByRole("heading", { name: schedule.title });
  expect(screen.getByRole("button", { name: "일정 취소" })).toBeDisabled();
  const retry = screen.getByRole("button", { name: "접근 상태 다시 확인" });

  retryRead = true;
  await user.click(retry);
  await waitFor(() => expect(finishDetail).toBeTypeOf("function"));
  expect(screen.getByRole("button", { name: "일정 취소" })).toBeDisabled();
  await act(async () => finishDetail(json(schedule)));
  await waitFor(() => expect(screen.getByRole("button", { name: "일정 취소" })).toBeEnabled());
  expect(server.calls.filter(call => call.method === "GET" && call.url === "/api/v1/projects/p1/schedules/s1?historyLimit=100")).toHaveLength(3);
});

test("projection permission denial removes cached Calendar status and retry through failed refresh before 200 recovery", async () => {
  const server = http();
  server.on("GET", "/api/v1/projects/p1/schedules/s1/calendar", () => json({ scheduleId: "s1", status: "FAILED", retryClassification: "TRANSIENT", businessRevision: 4 }));
  const user = mount("/projects/p1/schedules/s1");
  await screen.findByText("연결 확인 필요");
  expect(screen.getByRole("button", { name: "Calendar 동기화 다시 시도" })).toBeEnabled();

  server.on("GET", "/api/v1/projects/p1/schedules/s1/calendar", () => json({ code: "PROJECTION_FORBIDDEN" }, 403));
  await refreshStaleQueries();
  await screen.findByText("PROJECTION_FORBIDDEN");
  expect(screen.queryByText(/연결 확인 필요/)).not.toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "Calendar 동기화 다시 시도" })).not.toBeInTheDocument();

  server.on("GET", "/api/v1/projects/p1/schedules/s1/calendar", () => json({ code: "PROJECTION_RETRY_FAILED" }, 500));
  await user.click(screen.getByRole("button", { name: "Calendar 접근 상태 다시 확인" }));
  await screen.findByText("PROJECTION_RETRY_FAILED");
  expect(screen.queryByText(/연결 확인 필요/)).not.toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "Calendar 동기화 다시 시도" })).not.toBeInTheDocument();

  server.on("GET", "/api/v1/projects/p1/schedules/s1/calendar", () => json({ scheduleId: "s1", status: "FAILED", retryClassification: "TRANSIENT", businessRevision: 4 }));
  await user.click(screen.getByRole("button", { name: "Calendar 접근 상태 다시 확인" }));
  expect(await screen.findByText("연결 확인 필요")).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Calendar 동기화 다시 시도" })).toBeEnabled();
  expect(server.calls.filter(call => call.method === "GET" && call.url === "/api/v1/projects/p1/schedules/s1/calendar")).toHaveLength(4);
});

test("network project creation keeps the exact request until the same request is resolved", async () => {
  const server = http();
  server.on("GET", "/api/v1/projects", () => json([]));
  const routedFetch = globalThis.fetch as typeof fetch;
  let attempted = 0;
  const bodies: any[] = [];
  server.on("POST", "/api/v1/projects", () => json(project));
  vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = new URL(String(input), "http://localhost");
    if ((init?.method ?? "GET") === "POST" && url.pathname === "/api/v1/projects") {
      attempted += 1;
      if (attempted === 1) {
        bodies.push(JSON.parse(String(init?.body)));
        throw new TypeError("network down");
      }
    }
    return routedFetch(input, init);
  }));
  const user = mount("/");
  await user.click(await screen.findByRole("button", { name: "새 프로젝트" }));
  await user.type(await screen.findByLabelText("프로젝트 이름"), "네트워크 확인");
  await user.click(screen.getByRole("button", { name: "프로젝트 만들기" }));
  await screen.findByRole("button", { name: "같은 요청 결과 확인" });
  expect(screen.getByRole("button", { name: "결과 확인 필요" })).toBeDisabled();
  expect(attempted).toBe(1);
  await user.click(screen.getByRole("button", { name: "같은 요청 결과 확인" }));
  await waitFor(() => expect(attempted).toBe(2));
  expect(bodies[0].name).toBe("네트워크 확인");
  expect(bodies[0].groupId).toBeUndefined();
  expect(bodies[0].requestId).toMatch(/^[0-9a-f-]{36}$/i);
});

test("invalid project response remains blocked across dialog close and reopen", async () => {
  const server = http();
  server.on("GET", "/api/v1/projects", () => json([]));
  server.on("POST", "/api/v1/projects", () => json({ code: "MALFORMED_PROJECT" }));
  const user = mount("/");
  await user.click(await screen.findByRole("button", { name: "새 프로젝트" }));
  await user.type(await screen.findByLabelText("프로젝트 이름"), "결과 확인");
  await user.click(screen.getByRole("button", { name: "프로젝트 만들기" }));
  await screen.findByRole("button", { name: "같은 요청 결과 확인" });
  expect(server.calls.filter(call => call.method === "POST" && call.url === "/api/v1/projects")).toHaveLength(1);

  await user.click(screen.getByRole("button", { name: "취소" }));
  await user.click(screen.getByRole("button", { name: "새 프로젝트" }));
  expect(screen.getByRole("button", { name: "결과 확인 필요" })).toBeDisabled();
  expect(screen.getByRole("button", { name: "같은 요청 결과 확인" })).toBeInTheDocument();
  expect(server.calls.filter(call => call.method === "POST" && call.url === "/api/v1/projects")).toHaveLength(1);
});

test("share invitation denial survives a stale project read, close, and remount", async () => {
  const server = http();
  let finishStaleProjectRead!: (response: Response) => void;
  let readPhase: "initial" | "stale" | "fresh" = "initial";
  server.on("GET", "/api/v1/projects", () => readPhase === "initial" ? json([project]) : readPhase === "stale"
    ? new Promise(resolve => { finishStaleProjectRead = resolve; }) : json([project]));
  server.on("GET", "/api/v1/projects/p1/members", () => json([{ userId: "u1", displayName: "김관리자", email: "manager@example.test", role: "MANAGER" }]));
  server.on("GET", "/api/v1/projects/p1/share-invitation", () => json({ state: "NOT_CREATED", code: null, expiresAt: null }));
  server.on("POST", "/api/v1/projects/p1/share-invitation", () => json({ code: "SHARE_FORBIDDEN" }, 403));
  const user = mount("/projects/p1/members");
  await user.click(await screen.findByRole("button", { name: "구성원 초대" }));
  await screen.findByRole("button", { name: "초대 만들기" });
  readPhase = "stale";
  await refreshStaleQueries();
  await waitFor(() => expect(finishStaleProjectRead).toBeTypeOf("function"));
  await user.click(screen.getByRole("button", { name: "초대 만들기" }));
  await screen.findByText("SHARE_FORBIDDEN");
  finishStaleProjectRead(json([project]));
  await screen.findByRole("button", { name: "초대 상태 다시 확인" });
  await user.click(screen.getByRole("button", { name: "닫기" }));
  await act(async () => { window.location.hash = "#/notifications"; window.dispatchEvent(new HashChangeEvent("hashchange")); });
  await screen.findByRole("heading", { name: "알림" });
  await act(async () => { window.location.hash = "#/projects/p1/members"; window.dispatchEvent(new HashChangeEvent("hashchange")); });
  await user.click(await screen.findByRole("button", { name: "구성원 초대" }));
  expect(screen.queryByDisplayValue(/7K3M/)).not.toBeInTheDocument();
  expect(screen.getByRole("button", { name: "초대 상태 다시 확인" })).toBeInTheDocument();
  readPhase = "fresh";
  server.on("GET", "/api/v1/projects/p1/share-invitation", () => json({ state: "ACTIVE", code: "7K3M9F2D6R8TWX4C", expiresAt: "2199-10-01T00:00:00Z" }));
  await user.click(screen.getByRole("button", { name: "초대 상태 다시 확인" }));
  await screen.findByDisplayValue("7K3M-9F2D-6R8T-WX4C");
  expect(server.calls.filter(call => call.method === "POST" && call.url.endsWith("share-invitation"))).toHaveLength(1);
});

test("Invite keeps an accept denial through a stale GET, route remount, and failed retry until a fresh 200", async () => {
  const server = http();
  let readPhase: "initial" | "stale" | "remount" | "fresh" = "initial";
  let finishStaleRead!: (response: Response) => void;
  let finishRemountRead!: (response: Response) => void;
  server.on("GET", "/api/v1/invitations/t1", () => readPhase === "initial" ? json(invitation)
    : readPhase === "stale" ? new Promise(resolve => { finishStaleRead = resolve; })
    : readPhase === "remount" ? new Promise(resolve => { finishRemountRead = resolve; })
    : json(invitation));
  server.on("POST", "/api/v1/invitations/t1/accept", () => json({ code: "ACCEPT_FORBIDDEN" }, 403));
  const user = mount("/invitations/t1");
  await screen.findByText(/member@example\.test/);

  readPhase = "stale";
  await refreshStaleQueries();
  await waitFor(() => expect(finishStaleRead).toBeTypeOf("function"));
  await user.click(screen.getByRole("button", { name: "초대 수락" }));
  await screen.findByText("ACCEPT_FORBIDDEN");
  finishStaleRead(json(invitation));
  await waitFor(() => expect(screen.queryByRole("button", { name: "초대 수락" })).not.toBeInTheDocument());

  await act(async () => { window.location.hash = "#/notifications"; window.dispatchEvent(new HashChangeEvent("hashchange")); });
  await screen.findByRole("heading", { name: "알림" });
  readPhase = "remount";
  await act(async () => { window.location.hash = "#/invitations/t1"; window.dispatchEvent(new HashChangeEvent("hashchange")); });
  await waitFor(() => expect(finishRemountRead).toBeTypeOf("function"));
  expect(screen.queryByRole("button", { name: "초대 수락" })).not.toBeInTheDocument();
  finishRemountRead(json({ code: "INVITE_RETRY_FAILED" }, 500));
  await screen.findByText("INVITE_RETRY_FAILED");
  expect(screen.queryByRole("button", { name: "초대 수락" })).not.toBeInTheDocument();

  readPhase = "fresh";
  await user.click(screen.getByRole("button", { name: "최신 초대 불러오기" }));
  await waitFor(() => expect(screen.getByRole("button", { name: "초대 수락" })).toBeEnabled());
});

test.each([
  { mode: "denial", response: () => json({ code: "SHARE_A_FORBIDDEN" }, 403) },
  { mode: "unknown", response: () => json({ code: "SHARE_A_UNKNOWN" }, 500) },
])("project A share $mode remains isolated while project B succeeds", async ({ mode, response }) => {
  const server = http();
  const projectB = { ...project, id: "p2", name: "Reporting" };
  server.on("GET", "/api/v1/projects", () => json([project, projectB]));
  server.on("GET", "/api/v1/projects/p1/members", () => json([{ userId: "u1", displayName: "김관리자", email: "manager@example.test", role: "MANAGER" }]));
  server.on("GET", "/api/v1/projects/p2/members", () => json([{ userId: "u1", displayName: "김관리자", email: "manager@example.test", role: "MANAGER" }]));
  server.on("GET", "/api/v1/projects/p1/share-invitation", () => json({ state: "NOT_CREATED", code: null, expiresAt: null }));
  server.on("GET", "/api/v1/projects/p2/share-invitation", () => json({ state: "NOT_CREATED", code: null, expiresAt: null }));
  server.on("POST", "/api/v1/projects/p1/share-invitation", response);
  server.on("POST", "/api/v1/projects/p2/share-invitation", () => json({ state: "ACTIVE", code: "B7K3M9F2D6R8TWX4", expiresAt: "2199-10-01T00:00:00Z" }));
  const user = mount("/projects/p1/members");
  await user.click(await screen.findByRole("button", { name: "구성원 초대" }));
  await user.click(await screen.findByRole("button", { name: "초대 만들기" }));
  await screen.findByText(`SHARE_A_${mode === "denial" ? "FORBIDDEN" : "UNKNOWN"}`);
  await act(async () => { window.location.hash = "#/projects/p2/members"; window.dispatchEvent(new HashChangeEvent("hashchange")); });
  await user.click(await screen.findByRole("button", { name: "구성원 초대" }));
  await user.click(await screen.findByRole("button", { name: "초대 만들기" }));
  await screen.findByDisplayValue("B7K3-M9F2-D6R8-TWX4");
  await act(async () => { window.location.hash = "#/projects/p1/members"; window.dispatchEvent(new HashChangeEvent("hashchange")); });
  await user.click(await screen.findByRole("button", { name: "구성원 초대" }));
  expect(screen.getByRole("button", { name: "초대 상태 다시 확인" })).toBeInTheDocument();
});

test("unknown create result stays blocked while the lobby changes project list page", async () => {
  const server = http();
  const pageZero = Array.from({ length: 100 }, (_, index) => ({ ...project, id: `p${index}`, name: `Project ${index}` }));
  server.on("GET", "/api/v1/projects", (_body, url) => json(url.searchParams.get("page") === "1" ? [{ ...project, id: "p100", name: "Project 100" }] : pageZero));
  server.on("POST", "/api/v1/projects", () => json({ code: "CREATE_UNKNOWN" }, 500));
  const user = mount("/");
  await user.click(await screen.findByRole("button", { name: "새 프로젝트" }));
  await user.type(screen.getByLabelText("프로젝트 이름"), "페이지 유지");
  await user.click(screen.getByRole("button", { name: "프로젝트 만들기" }));
  await screen.findByRole("button", { name: "같은 요청 결과 확인" });
  await user.click(screen.getByRole("button", { name: "취소" }));
  await user.click(screen.getByRole("button", { name: "다음 프로젝트" }));
  await screen.findByRole("link", { name: /Project 100/ });
  await user.click(screen.getByRole("button", { name: "새 프로젝트" }));
  expect(screen.getByRole("button", { name: "결과 확인 필요" })).toBeDisabled();
  expect(screen.getByRole("button", { name: "같은 요청 결과 확인" })).toBeInTheDocument();
});

test("an unknown create result is not cleared by a project-list read that started before the create failed", async () => {
  const server = http();
  let finishInitialProjects!: (response: Response) => void;
  server.on("GET", "/api/v1/projects", () => {
    return new Promise(resolve => { finishInitialProjects = resolve; });
  });
  server.on("POST", "/api/v1/projects", () => json({ code: "MALFORMED_PROJECT" }, 500));
  const user = mount("/");
  await user.click(await screen.findByRole("button", { name: "새 프로젝트" }));
  await user.type(await screen.findByLabelText("프로젝트 이름"), "순서 확인");
  await waitFor(() => expect(finishInitialProjects).toBeTypeOf("function"));
  await user.click(screen.getByRole("button", { name: "프로젝트 만들기" }));
  await screen.findByRole("button", { name: "같은 요청 결과 확인" });
  expect(screen.getByRole("button", { name: "결과 확인 필요" })).toBeDisabled();
  await act(async () => finishInitialProjects(json([])));
  await waitFor(() => expect(screen.getByRole("button", { name: "같은 요청 결과 확인" })).toBeInTheDocument());
  expect(server.calls.filter(call => call.method === "POST" && call.url === "/api/v1/projects")).toHaveLength(1);
});

test.each([
  { mode: "denial", response: () => json({ code: "CREATION_FORBIDDEN" }, 403) },
  { mode: "unknown", response: () => json({ code: "MALFORMED_PROJECT" }, 500) },
])("creation $mode remains blocked after route leave and remount", async ({ response }) => {
  const server = http();
  server.on("GET", "/api/v1/projects", () => json([]));
  server.on("POST", "/api/v1/projects", response);
  const user = mount("/");
  await user.click(await screen.findByRole("button", { name: "새 프로젝트" }));
  await user.type(await screen.findByLabelText("프로젝트 이름"), "경로 유지");
  await user.click(screen.getByRole("button", { name: "프로젝트 만들기" }));
  await screen.findByRole("button", { name: "같은 요청 결과 확인" });

  await act(async () => {
    window.location.hash = "#/notifications";
    window.dispatchEvent(new HashChangeEvent("hashchange"));
  });
  await screen.findByRole("heading", { name: "알림" });
  await act(async () => {
    window.location.hash = "#/";
    window.dispatchEvent(new HashChangeEvent("hashchange"));
  });
  await user.click(await screen.findByRole("button", { name: "새 프로젝트" }));
  await screen.findByLabelText("프로젝트 이름");
  expect(screen.getByRole("button", { name: "결과 확인 필요" })).toBeDisabled();
  expect(screen.getByRole("button", { name: "같은 요청 결과 확인" })).toBeInTheDocument();
});

test("Projects hides a cached name through read 403 and retry 500, then restores it after 200", async () => {
  const server = http();
  const user = mount("/");
  await screen.findByRole("link", { name: /Planning/ });

  server.on("GET", "/api/v1/projects", () => json({ code: "PROJECTS_FORBIDDEN" }, 403));
  await refreshStaleQueries();
  await screen.findByText("PROJECTS_FORBIDDEN");
  expect(screen.queryByRole("link", { name: /Planning/ })).not.toBeInTheDocument();

  server.on("GET", "/api/v1/projects", () => json({ code: "PROJECTS_RETRY_FAILED" }, 500));
  await user.click(screen.getByRole("button", { name: "다시 시도" }));
  await screen.findByText("PROJECTS_RETRY_FAILED");
  expect(screen.queryByRole("link", { name: /Planning/ })).not.toBeInTheDocument();

  server.on("GET", "/api/v1/projects", () => json([project]));
  await user.click(screen.getByRole("button", { name: "다시 시도" }));
  expect(await screen.findByRole("link", { name: /Planning/ })).toBeInTheDocument();
});

test("Invite hides protected email and accept CTA through read 403 and retry 500, then restores them after 200", async () => {
  const server = http();
  const user = mount("/invitations/t1");
  await screen.findByText(/member@example\.test/);

  server.on("GET", "/api/v1/invitations/t1", () => json({ code: "INVITE_FORBIDDEN" }, 403));
  await refreshStaleQueries();
  await screen.findByText("INVITE_FORBIDDEN");
  expect(screen.queryByText(/member@example\.test/)).not.toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "초대 수락" })).not.toBeInTheDocument();
  expect(screen.queryByRole("link", { name: "프로젝트 열기" })).not.toBeInTheDocument();

  server.on("GET", "/api/v1/invitations/t1", () => json({ code: "INVITE_RETRY_FAILED" }, 500));
  await user.click(screen.getByRole("button", { name: "다시 시도" }));
  await screen.findByText("INVITE_RETRY_FAILED");
  expect(screen.queryByText(/member@example\.test/)).not.toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "초대 수락" })).not.toBeInTheDocument();

  server.on("GET", "/api/v1/invitations/t1", () => json({ ...invitation }));
  await user.click(screen.getByRole("button", { name: "다시 시도" }));
  expect(await screen.findByText(/member@example\.test/)).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "초대 수락" })).toBeEnabled();
});

test("share invitation keeps a failed change blocked through remount and recovers from a fresh GET", async () => {
  const server = http();
  let shareReads = 0;
  server.on("GET", "/api/v1/projects/p1/members", () => json([{ userId: "u1", displayName: "김관리자", email: "manager@example.test", role: "MANAGER" }]));
  server.on("GET", "/api/v1/projects/p1/share-invitation", () => {
    shareReads += 1;
    return shareReads <= 1 ? json({ state: "NOT_CREATED", code: null, expiresAt: null }) : json({ code: "SHARE_RETRY_FAILED" }, 500);
  });
  server.on("POST", "/api/v1/projects/p1/share-invitation", () => json({ code: "SHARE_CHANGE_FORBIDDEN" }, 403));
  const user = mount("/projects/p1/members");
  await user.click(await screen.findByRole("button", { name: "구성원 초대" }));
  await user.click(await screen.findByRole("button", { name: "초대 만들기" }));
  await screen.findByText("SHARE_CHANGE_FORBIDDEN");
  await user.click(screen.getByRole("button", { name: "닫기" }));
  await act(async () => { window.location.hash = "#/notifications"; window.dispatchEvent(new HashChangeEvent("hashchange")); });
  await screen.findByRole("heading", { name: "알림" });
  await act(async () => { window.location.hash = "#/projects/p1/members"; window.dispatchEvent(new HashChangeEvent("hashchange")); });
  await user.click(await screen.findByRole("button", { name: "구성원 초대" }));
  await screen.findByText("SHARE_RETRY_FAILED");
  expect(screen.getByRole("button", { name: "초대 상태 다시 확인" })).toBeInTheDocument();
  server.on("GET", "/api/v1/projects/p1/share-invitation", () => json({ state: "ACTIVE", code: "7K3M9F2D6R8TWX4C", expiresAt: "2199-10-01T00:00:00Z" }));
  await user.click(screen.getByRole("button", { name: "초대 상태 다시 확인" }));
  await screen.findByDisplayValue("7K3M-9F2D-6R8T-WX4C");
});
