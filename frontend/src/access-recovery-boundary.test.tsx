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

const allowedGroup = { id: "g-a", name: "그룹 A", canCreate: true, reason: null } as const;

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

test("Detail keeps a denied command blocked after Calendar navigation and offers fresh access recovery", async () => {
  const server = http();
  server.on("POST", "/api/v1/projects/p1/schedules/s1/cancel", () => json({ code: "COMMAND_FORBIDDEN" }, 403));
  const user = mount("/projects/p1/schedules/s1");
  await user.click(await screen.findByRole("button", { name: "일정 취소" }));
  await screen.findByText("COMMAND_FORBIDDEN");

  await user.click(screen.getByRole("link", { name: "Calendar" }));
  await screen.findByRole("heading", { name: "Google Calendar" });
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
  await screen.findByText("Calendar FAILED");
  expect(screen.getByRole("button", { name: "Calendar 재시도" })).toBeEnabled();

  server.on("GET", "/api/v1/projects/p1/schedules/s1/calendar", () => json({ code: "PROJECTION_FORBIDDEN" }, 403));
  await refreshStaleQueries();
  await screen.findByText("PROJECTION_FORBIDDEN");
  expect(screen.queryByText(/투영: FAILED/)).not.toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "Calendar 재시도" })).not.toBeInTheDocument();

  server.on("GET", "/api/v1/projects/p1/schedules/s1/calendar", () => json({ code: "PROJECTION_RETRY_FAILED" }, 500));
  await user.click(screen.getByRole("button", { name: "Calendar 접근 상태 다시 확인" }));
  await screen.findByText("PROJECTION_RETRY_FAILED");
  expect(screen.queryByText(/투영: FAILED/)).not.toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "Calendar 재시도" })).not.toBeInTheDocument();

  server.on("GET", "/api/v1/projects/p1/schedules/s1/calendar", () => json({ scheduleId: "s1", status: "FAILED", retryClassification: "TRANSIENT", businessRevision: 4 }));
  await user.click(screen.getByRole("button", { name: "Calendar 접근 상태 다시 확인" }));
  expect(await screen.findByText("Calendar FAILED")).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Calendar 재시도" })).toBeEnabled();
  expect(server.calls.filter(call => call.method === "GET" && call.url === "/api/v1/projects/p1/schedules/s1/calendar")).toHaveLength(4);
});

test("network project creation failure blocks resubmission until a successful project-list check", async () => {
  const server = http();
  server.on("GET", "/api/v1/projects", () => json([]));
  server.on("GET", "/api/v1/projects/creation-options", () => json([allowedGroup]));
  const routedFetch = globalThis.fetch as typeof fetch;
  let attempted = 0;
  vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = new URL(String(input), "http://localhost");
    if ((init?.method ?? "GET") === "POST" && url.pathname === "/api/v1/projects") {
      attempted += 1;
      throw new TypeError("network down");
    }
    return routedFetch(input, init);
  }));
  const user = mount("/");
  await user.type(await screen.findByLabelText("프로젝트 이름"), "네트워크 확인");
  await user.click(screen.getByRole("button", { name: "프로젝트 만들기" }));
  await screen.findByRole("button", { name: "프로젝트 목록 새로고침" });
  expect(screen.getByRole("button", { name: "프로젝트 만들기" })).toBeDisabled();
  expect(attempted).toBe(1);

  await user.click(screen.getByRole("button", { name: "프로젝트 목록 새로고침" }));
  await waitFor(() => expect(screen.getByRole("button", { name: "프로젝트 만들기" })).toBeEnabled());
  expect(attempted).toBe(1);
});

test("invalid successful project response remains blocked when project-list refresh fails, then recovers after 200", async () => {
  const server = http();
  server.on("GET", "/api/v1/projects", () => json([]));
  server.on("GET", "/api/v1/projects/creation-options", () => json([allowedGroup]));
  server.on("POST", "/api/v1/projects", () => json({ code: "MALFORMED_PROJECT" }));
  const user = mount("/");
  await user.type(await screen.findByLabelText("프로젝트 이름"), "결과 확인");
  await user.click(screen.getByRole("button", { name: "프로젝트 만들기" }));
  await screen.findByRole("button", { name: "프로젝트 목록 새로고침" });
  expect(server.calls.filter(call => call.method === "POST" && call.url === "/api/v1/projects")).toHaveLength(1);

  server.on("GET", "/api/v1/projects", () => json({ code: "PROJECT_LIST_FAILED" }, 500));
  await user.click(screen.getByRole("button", { name: "프로젝트 목록 새로고침" }));
  await screen.findByText("PROJECT_LIST_FAILED");
  expect(screen.getByRole("button", { name: "프로젝트 만들기" })).toBeDisabled();

  server.on("GET", "/api/v1/projects", () => json([]));
  await user.click(screen.getByRole("button", { name: "프로젝트 목록 새로고침" }));
  await waitFor(() => expect(screen.getByRole("button", { name: "프로젝트 만들기" })).toBeEnabled());
  expect(server.calls.filter(call => call.method === "POST" && call.url === "/api/v1/projects")).toHaveLength(1);
});

test("InviteCreate keeps a 403 denial after a project read that started before the mutation, then recovers on a newer read", async () => {
  const server = http();
  let finishStaleProjectRead!: (response: Response) => void;
  let readPhase: "initial" | "stale" | "fresh" = "initial";
  server.on("POST", "/api/v1/groups/g1/invitations", () => json({ code: "INVITE_FORBIDDEN" }, 403));
  server.on("GET", "/api/v1/projects", () => readPhase === "initial" ? json([project]) : readPhase === "stale"
    ? new Promise(resolve => { finishStaleProjectRead = resolve; })
    : json([project]));
  const user = mount("/projects/p1/invitations/new");
  await screen.findByLabelText("이메일");
  readPhase = "stale";
  await refreshStaleQueries();
  await waitFor(() => expect(finishStaleProjectRead).toBeTypeOf("function"));

  await user.type(screen.getByLabelText("이메일"), "member@example.test");
  await user.click(screen.getByRole("button", { name: "초대 만들기" }));
  await screen.findByText("INVITE_FORBIDDEN");
  finishStaleProjectRead(json([project]));
  await waitFor(() => expect(screen.getByRole("button", { name: "접근 상태 다시 확인" })).toBeInTheDocument());

  await act(async () => {
    window.location.hash = "#/calendar";
    window.dispatchEvent(new HashChangeEvent("hashchange"));
  });
  await screen.findByRole("heading", { name: "Google Calendar" });
  readPhase = "fresh";
  await act(async () => {
    window.location.hash = "#/projects/p1/invitations/new";
    window.dispatchEvent(new HashChangeEvent("hashchange"));
  });
  await screen.findByLabelText("이메일");
  expect(screen.getByRole("button", { name: "초대 만들기" })).toBeDisabled();
  const retry = screen.getByRole("button", { name: "접근 상태 다시 확인" });
  await user.click(retry);
  await waitFor(() => expect(screen.getByRole("button", { name: "초대 만들기" })).toBeEnabled());
  expect(screen.getByLabelText("이메일")).toHaveValue("");
  expect(server.calls.filter(call => call.method === "POST" && call.url.endsWith("/invitations")).length).toBe(1);
});

test.each([
  { mode: "denial", response: () => json({ code: "CREATION_FORBIDDEN" }, 403), recovery: "생성 권한 다시 확인" },
  { mode: "unknown", response: () => json({ code: "MALFORMED_PROJECT" }), recovery: "프로젝트 목록 새로고침" },
])("group A $mode state survives switching to eligible B and back", async ({ mode, response, recovery }) => {
  const server = http();
  server.on("GET", "/api/v1/projects", () => json([]));
  server.on("GET", "/api/v1/projects/creation-options", () => json([
    { ...allowedGroup },
    { id: "g-b", name: "그룹 B", canCreate: true, reason: null },
  ]));
  server.on("POST", "/api/v1/projects", response);
  const user = mount("/");
  const group = await screen.findByLabelText("그룹");
  await user.type(await screen.findByLabelText("프로젝트 이름"), `그룹 ${mode}`);
  await user.click(screen.getByRole("button", { name: "프로젝트 만들기" }));
  await screen.findByRole("button", { name: recovery });

  await user.selectOptions(group, "g-b");
  expect(screen.getByRole("button", { name: "프로젝트 만들기" })).toBeEnabled();
  await user.selectOptions(group, "g-a");
  expect(screen.getByRole("button", { name: "프로젝트 만들기" })).toBeDisabled();
  expect(screen.getByRole("button", { name: recovery })).toBeInTheDocument();

  await user.click(screen.getByRole("button", { name: recovery }));
  await waitFor(() => expect(screen.getByRole("button", { name: "프로젝트 만들기" })).toBeEnabled());
  expect(server.calls.filter(call => call.method === "POST" && call.url === "/api/v1/projects")).toHaveLength(1);
});

test("creation denial on page 0 does not poison eligible page 1, while page 0 stays denied until refreshed", async () => {
  const server = http();
  const pageZero = [
    { ...allowedGroup },
    ...Array.from({ length: 99 }, (_, index) => ({ id: `g-${index + 1}`, name: `그룹 ${index + 1}`, canCreate: false, reason: "GROUP_ROLE_REQUIRED" as const })),
  ];
  server.on("GET", "/api/v1/projects", () => json([]));
  server.on("GET", "/api/v1/projects/creation-options", (_body, url) => Number(url.searchParams.get("page")) === 0
    ? json(pageZero)
    : json([{ id: "g-b", name: "그룹 B", canCreate: true, reason: null }]));
  server.on("POST", "/api/v1/projects", () => json({ code: "CREATION_FORBIDDEN" }, 403));
  const user = mount("/");
  await user.type(await screen.findByLabelText("프로젝트 이름"), "페이지 분리");
  await user.click(screen.getByRole("button", { name: "프로젝트 만들기" }));
  await screen.findByRole("button", { name: "생성 권한 다시 확인" });

  await user.click(screen.getByRole("button", { name: "다음 그룹" }));
  await user.type(await screen.findByLabelText("프로젝트 이름"), "B 생성");
  expect(screen.getByRole("button", { name: "프로젝트 만들기" })).toBeEnabled();

  await user.click(screen.getByRole("button", { name: "이전 그룹" }));
  await screen.findByRole("button", { name: "생성 권한 다시 확인" });
  expect(screen.getByRole("button", { name: "프로젝트 만들기" })).toBeDisabled();
  await user.click(screen.getByRole("button", { name: "생성 권한 다시 확인" }));
  await waitFor(() => expect(screen.getByRole("button", { name: "프로젝트 만들기" })).toBeEnabled());
});

test("an unknown create result is not cleared by a project-list read that started before the create failed", async () => {
  const server = http();
  let finishInitialProjects!: (response: Response) => void;
  let finishNewerProjects: ((response: Response) => void) | undefined;
  let reads = 0;
  server.on("GET", "/api/v1/projects", () => {
    reads += 1;
    if (reads === 1) return new Promise(resolve => { finishInitialProjects = resolve; });
    return new Promise(resolve => { finishNewerProjects = resolve; });
  });
  server.on("GET", "/api/v1/projects/creation-options", () => json([allowedGroup]));
  server.on("POST", "/api/v1/projects", () => json({ code: "MALFORMED_PROJECT" }));
  const user = mount("/");
  await user.type(await screen.findByLabelText("프로젝트 이름"), "순서 확인");
  await waitFor(() => expect(finishInitialProjects).toBeTypeOf("function"));
  await user.click(screen.getByRole("button", { name: "프로젝트 만들기" }));
  const refresh = await screen.findByRole("button", { name: "프로젝트 목록 새로고침" });
  await user.click(refresh);
  await act(async () => finishInitialProjects(json([])));
  await waitFor(() => expect(screen.getByRole("button", { name: "프로젝트 목록 새로고침" })).toBeInTheDocument());
  expect(screen.getByRole("button", { name: "프로젝트 만들기" })).toBeDisabled();
  expect(server.calls.filter(call => call.method === "POST" && call.url === "/api/v1/projects")).toHaveLength(1);

  if (finishNewerProjects) await act(async () => finishNewerProjects?.(json([])));
  else {
    server.on("GET", "/api/v1/projects", () => json([]));
    await user.click(screen.getByRole("button", { name: "프로젝트 목록 새로고침" }));
  }
  await waitFor(() => expect(screen.getByRole("button", { name: "프로젝트 만들기" })).toBeEnabled());
});

test.each([
  { mode: "denial", response: () => json({ code: "CREATION_FORBIDDEN" }, 403), recovery: "생성 권한 다시 확인" },
  { mode: "unknown", response: () => json({ code: "MALFORMED_PROJECT" }), recovery: "프로젝트 목록 새로고침" },
])("creation $mode remains blocked after route leave and remount", async ({ response, recovery }) => {
  const server = http();
  server.on("GET", "/api/v1/projects", () => json([]));
  server.on("GET", "/api/v1/projects/creation-options", () => json([allowedGroup]));
  server.on("POST", "/api/v1/projects", response);
  const user = mount("/");
  await user.type(await screen.findByLabelText("프로젝트 이름"), "경로 유지");
  await user.click(screen.getByRole("button", { name: "프로젝트 만들기" }));
  await screen.findByRole("button", { name: recovery });

  await act(async () => {
    window.location.hash = "#/notifications";
    window.dispatchEvent(new HashChangeEvent("hashchange"));
  });
  await screen.findByRole("heading", { name: "알림" });
  await act(async () => {
    window.location.hash = "#/";
    window.dispatchEvent(new HashChangeEvent("hashchange"));
  });
  await screen.findByLabelText("프로젝트 이름");
  expect(screen.getByRole("button", { name: "프로젝트 만들기" })).toBeDisabled();
  expect(screen.getByRole("button", { name: recovery })).toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: recovery }));
  await waitFor(() => expect(screen.getByRole("button", { name: "프로젝트 만들기" })).toBeEnabled());
});

test("Projects hides a cached name through read 403 and retry 500, then restores it after 200", async () => {
  const server = http();
  const user = mount("/");
  await screen.findByRole("link", { name: project.name });

  server.on("GET", "/api/v1/projects", () => json({ code: "PROJECTS_FORBIDDEN" }, 403));
  await refreshStaleQueries();
  await screen.findByText("PROJECTS_FORBIDDEN");
  expect(screen.queryByRole("link", { name: project.name })).not.toBeInTheDocument();

  server.on("GET", "/api/v1/projects", () => json({ code: "PROJECTS_RETRY_FAILED" }, 500));
  await user.click(screen.getByRole("button", { name: "다시 시도" }));
  await screen.findByText("PROJECTS_RETRY_FAILED");
  expect(screen.queryByRole("link", { name: project.name })).not.toBeInTheDocument();

  server.on("GET", "/api/v1/projects", () => json([project]));
  await user.click(screen.getByRole("button", { name: "다시 시도" }));
  expect(await screen.findByRole("link", { name: project.name })).toBeInTheDocument();
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

test("Invite keeps an accept denial through a stale GET, cached remount, and failed retry until a fresh 200", async () => {
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
  await waitFor(() => expect(screen.getByRole("button", { name: "초대 수락" })).toBeDisabled());

  await act(async () => {
    window.location.hash = "#/calendar";
    window.dispatchEvent(new HashChangeEvent("hashchange"));
  });
  await screen.findByRole("heading", { name: "Google Calendar" });
  readPhase = "remount";
  await act(async () => {
    window.location.hash = "#/invitations/t1";
    window.dispatchEvent(new HashChangeEvent("hashchange"));
  });
  await screen.findByText(/member@example\.test/);
  await waitFor(() => expect(finishRemountRead).toBeTypeOf("function"));
  expect(screen.getByRole("button", { name: "초대 수락" })).toBeDisabled();
  finishRemountRead(json({ code: "INVITE_RETRY_FAILED" }, 500));
  await screen.findByText("INVITE_RETRY_FAILED");
  expect(screen.getByRole("button", { name: "초대 수락" })).toBeDisabled();

  readPhase = "fresh";
  await user.click(screen.getByRole("button", { name: "최신 초대 불러오기" }));
  await waitFor(() => expect(screen.getByRole("button", { name: "초대 수락" })).toBeEnabled());
});
