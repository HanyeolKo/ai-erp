import { act, cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MutationCache, QueryClient, type Mutation } from "@tanstack/react-query";
import { afterEach, expect, test, vi } from "vitest";
import App from "./App";
import { beginSessionBoundary, currentSessionGeneration, isSessionActive, SessionTerminatedError } from "./session";
import { api } from "./api/client";
import { accessKey, hasAccessDenial } from "./state";
import { http, invitation, json, schedule } from "./test/http";

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  window.history.replaceState(null, "", "/");
  sessionStorage.clear();
});

const monitorSessionCleanup = () => {
  let client: QueryClient | undefined;
  const retired: Mutation[] = [];
  const removeQueries = QueryClient.prototype.removeQueries;
  const clear = MutationCache.prototype.clear;
  vi.spyOn(QueryClient.prototype, "removeQueries").mockImplementation(function (this: QueryClient, filters) {
    client = this;
    return removeQueries.call(this, filters);
  });
  vi.spyOn(MutationCache.prototype, "clear").mockImplementation(function (this: MutationCache) {
    retired.push(...this.getAll());
    return clear.call(this);
  });
  return { get client() { return client; }, retired };
};

const monitorMutations = () => {
  const mutations: Mutation[] = [];
  const add = MutationCache.prototype.add;
  vi.spyOn(MutationCache.prototype, "add").mockImplementation(function (this: MutationCache, mutation: Mutation) {
    mutations.push(mutation);
    return add.call(this, mutation);
  });
  return mutations;
};

const mount = (path: string) => {
  window.location.hash = `#${path}`;
  const view = render(<App />);
  return { ...view, user: userEvent.setup() };
};

const fillNewSchedule = async () => {
  fireEvent.change(await screen.findByLabelText("제목"), { target: { value: "Late schedule" } });
  fireEvent.change(screen.getByLabelText("시작"), { target: { value: "2090-09-10T10:00" } });
  fireEvent.change(screen.getByLabelText("종료"), { target: { value: "2090-09-10T11:00" } });
};

test("late schedule save cannot navigate or repopulate the old workspace after a competing 401", async () => {
  const server = http();
  const cleanup = monitorSessionCleanup();
  let finishSave!: (response: Response) => void;
  server.on("POST", "/api/v1/projects/p1/schedules", () => new Promise(resolve => { finishSave = resolve; }));
  server.on("GET", "/api/v1/notifications", () => json({ code: "UNAUTHENTICATED" }, 401));
  const { user } = mount("/projects/p1/schedules/new");

  await fillNewSchedule();
  await user.click(screen.getByRole("button", { name: "일정 저장" }));
  await waitFor(() => expect(finishSave).toBeTypeOf("function"));

  await user.click(screen.getByRole("link", { name: "알림" }));
  await screen.findByRole("link", { name: "Google로 로그인" });
  const expiredRoute = window.location.hash;
  await waitFor(() => expect(cleanup.retired).toHaveLength(1));
  const saveMutation = cleanup.retired.find(mutation => (mutation.state.variables as { title?: string } | undefined)?.title === "Late schedule");
  expect(saveMutation).toBeDefined();

  await act(async () => finishSave(json({ ...schedule, id: "s2" })));
  await waitFor(() => expect(saveMutation!.state.status).not.toBe("pending"));
  expect(cleanup.client).toBeDefined();
  expect(cleanup.client!.getQueryCache().getAll().filter(query => query.queryKey[0] !== "configuration" && query.state.data !== undefined)).toHaveLength(0);
  expect(screen.getByRole("link", { name: "Google로 로그인" })).toBeInTheDocument();
  expect(window.location.hash).toBe(expiredRoute);
  expect(screen.queryByRole("heading", { name: "일정 상세" })).not.toBeInTheDocument();
  expect(server.calls.filter(call => call.method === "POST" && call.url === "/api/v1/projects/p1/schedules")).toHaveLength(1);
});

test("late invitation acceptance cannot restore private data after successful logout", async () => {
  const server = http();
  const cleanup = monitorSessionCleanup();
  let finishAccept!: (response: Response) => void;
  server.on("POST", "/api/v1/invitations/t1/accept", () => new Promise(resolve => { finishAccept = resolve; }));
  server.on("POST", "/api/v1/logout", () => new Response(null, { status: 204 }));
  const { user } = mount("/invitations/t1");

  await user.click(await screen.findByRole("button", { name: "초대 수락" }));
  await waitFor(() => expect(finishAccept).toBeTypeOf("function"));

  await user.click(screen.getAllByRole("link", { name: "프로젝트 선택" })[0]);
  await screen.findByRole("heading", { name: "프로젝트 선택" });
  await user.click(screen.getByText("김관리자"));
  await user.click(screen.getByRole("button", { name: "다른 계정으로 로그인" }));
  await screen.findByRole("link", { name: "Google로 로그인" });
  const expiredRoute = window.location.hash;
  await waitFor(() => expect(cleanup.retired.some(mutation => mutation.state.variables === "accept")).toBe(true));
  const acceptMutation = cleanup.retired.find(mutation => mutation.state.variables === "accept");
  expect(acceptMutation).toBeDefined();

  await act(async () => finishAccept(json({ ...invitation, status: "ACCEPTED" })));
  await waitFor(() => expect(acceptMutation!.state.status).not.toBe("pending"));
  expect(cleanup.client).toBeDefined();
  expect(cleanup.client!.getQueryCache().getAll().filter(query => query.queryKey[0] !== "configuration" && query.state.data !== undefined)).toHaveLength(0);
  expect(screen.getByRole("link", { name: "Google로 로그인" })).toBeInTheDocument();
  expect(window.location.hash).toBe(expiredRoute);
  expect(sessionStorage.getItem("ai-erp.invitation")).toBeNull();
  expect(server.calls.filter(call => call.method === "POST" && call.url.endsWith("/accept"))).toHaveLength(1);
});

test("a pending CSRF lookup issues no POST after another request expires the session", async () => {
  const server = http();
  let finishCsrf!: (response: Response) => void;
  server.on("GET", "/api/v1/csrf", () => new Promise(resolve => { finishCsrf = resolve; }));
  server.on("GET", "/api/v1/notifications", () => json({ code: "UNAUTHENTICATED" }, 401));
  const { user } = mount("/projects/p1/schedules/new");

  await fillNewSchedule();
  await user.click(screen.getByRole("button", { name: "일정 저장" }));
  await waitFor(() => expect(finishCsrf).toBeTypeOf("function"));
  await user.click(screen.getByRole("link", { name: "알림" }));
  await screen.findByRole("link", { name: "Google로 로그인" });

  await act(async () => finishCsrf(json({ headerName: "X-CSRF-TOKEN", token: "late-csrf" })));
  await waitFor(() => expect(server.calls.filter(call => call.method === "POST")).toHaveLength(0));
  expect(window.location.hash).toBe("#/notifications");
});

test("successful logout204 ends the session before another pending CSRF lookup can post", async () => {
  const server = http();
  beginSessionBoundary();
  let finishScheduleCsrf!: (response: Response) => void;
  let csrfCalls = 0;
  server.on("GET", "/api/v1/csrf", () => ++csrfCalls === 1 ? new Promise(resolve => { finishScheduleCsrf = resolve; }) : json({ headerName: "X-CSRF-TOKEN", token: "csrf" }));
  server.on("POST", "/api/v1/logout", () => new Response(null, { status: 204 }));
  const generation = currentSessionGeneration();
  const create = api.create("p1", { title: "Pending create", description: "", startsAt: "2090-09-10T01:00:00.000Z", endsAt: "2090-09-10T02:00:00.000Z", memberParticipantIds: [], externalAttendeeEmails: [] });
  const createOutcome = create.then(() => undefined, error => error);
  await waitFor(() => expect(finishScheduleCsrf).toBeTypeOf("function"));
  await api.logout();
  expect(isSessionActive(generation)).toBe(false);

  await act(async () => finishScheduleCsrf(json({ headerName: "X-CSRF-TOKEN", token: "late-csrf" })));
  await expect(createOutcome).resolves.toBeInstanceOf(SessionTerminatedError);
  expect(server.calls.filter(call => call.method === "POST" && call.url === "/api/v1/projects/p1/schedules")).toHaveLength(0);
  expect(server.calls.filter(call => call.method === "POST" && call.url === "/api/v1/logout")).toHaveLength(1);
});

test("a schedule success callback that is waiting for invalidation cannot navigate after expiry", async () => {
  const server = http();
  let finishDetailRefresh!: (response: Response) => void;
  server.on("PATCH", "/api/v1/projects/p1/schedules/s1", () => json(schedule));
  const { user } = mount("/projects/p1/schedules/s1/edit");
  const title = await screen.findByLabelText("제목");
  fireEvent.change(title, { target: { value: "Updated schedule" } });
  await screen.findByText("김관리자");
  server.on("GET", "/api/v1/projects/p1/schedules/s1", () => new Promise(resolve => { finishDetailRefresh = resolve; }));

  await user.click(screen.getByRole("button", { name: "일정 저장" }));
  await waitFor(() => expect(finishDetailRefresh).toBeTypeOf("function"));
  server.on("GET", "/api/v1/notifications", () => json({ code: "UNAUTHENTICATED" }, 401));
  await user.click(screen.getByRole("link", { name: "알림" }));
  await screen.findByRole("link", { name: "Google로 로그인" });
  const expiredRoute = window.location.hash;

  await act(async () => finishDetailRefresh(json(schedule)));
  await waitFor(() => expect(screen.getByRole("link", { name: "Google로 로그인" })).toBeInTheDocument());
  expect(window.location.hash).toBe(expiredRoute);
  expect(screen.queryByRole("heading", { name: "Design review" })).not.toBeInTheDocument();
});

test("a 401 hides protected UI before a stalled error body can resolve", async () => {
  const server = http();
  const stalledBody = new Promise<never>(() => undefined);
  server.on("GET", "/api/v1/notifications", () => ({ status: 401, ok: false, json: () => stalledBody } as unknown as Response));
  const { user } = mount("/projects/p1");
  await screen.findByRole("heading", { name: "프로젝트 개요" });

  await user.click(screen.getByRole("link", { name: "알림" }));
  await waitFor(() => expect(screen.getByRole("link", { name: "Google로 로그인" })).toBeInTheDocument());
  expect(screen.queryByRole("navigation", { name: "주 메뉴" })).not.toBeInTheDocument();
  expect(screen.queryByRole("heading", { name: "프로젝트 개요" })).not.toBeInTheDocument();
});

test("a stale mutation 401 from an older session cannot expire a clean remount", async () => {
  const server = http();
  let finishOldMutation!: (response: Response) => void;
  server.on("POST", "/api/v1/projects/p1/schedules", () => new Promise(resolve => { finishOldMutation = resolve; }));
  const first = mount("/projects/p1/schedules/new");
  await fillNewSchedule();
  await first.user.click(screen.getByRole("button", { name: "일정 저장" }));
  await waitFor(() => expect(finishOldMutation).toBeTypeOf("function"));

  window.history.replaceState(null, "", "/#/");
  // Model a successful clean login/remount before the stale request resolves.
  beginSessionBoundary();
  const second = { ...render(<App />), user: userEvent.setup() };
  await screen.findAllByRole("heading", { name: "프로젝트 선택" });
  await waitFor(() => expect(screen.getByRole("heading", { name: "프로젝트 선택" })).toBeInTheDocument());
  first.unmount();
  expect(screen.queryByRole("link", { name: "Google로 로그인" })).not.toBeInTheDocument();

  await act(async () => finishOldMutation(json({ code: "STALE_UNAUTHENTICATED" }, 401)));
  await waitFor(() => expect(screen.getByRole("heading", { name: "프로젝트 선택" })).toBeInTheDocument());
  expect(screen.queryByRole("link", { name: "Google로 로그인" })).not.toBeInTheDocument();
  await second.user.click(screen.getByText("김관리자"));
  expect(screen.getByRole("button", { name: "다른 계정으로 로그인" })).toBeInTheDocument();
  second.unmount();
});

test("a delayed stale 403 body cannot create a write denial in a clean remount", async () => {
  const server = http();
  const mutations = monitorMutations();
  let finishOldMutation!: (response: Response) => void;
  let finishOldBody!: (value: unknown) => void;
  let bodyStarted!: () => void;
  let mutationCalls = 0;
  const oldBody = new Promise(resolve => { finishOldBody = resolve; });
  server.on("POST", "/api/v1/projects/p1/schedules", () => ++mutationCalls === 1
    ? new Promise(resolve => { finishOldMutation = resolve; })
    : json({ ...schedule, id: "s2" }));
  const first = mount("/projects/p1/schedules/new");
  await fillNewSchedule();
  await first.user.click(screen.getByRole("button", { name: "일정 저장" }));
  await waitFor(() => expect(finishOldMutation).toBeTypeOf("function"));
  const oldMutation = mutations.find(mutation => (mutation.state.variables as { title?: string } | undefined)?.title === "Late schedule");
  expect(oldMutation).toBeDefined();
  expect(oldMutation!.state.status).toBe("pending");

  const bodyRead = new Promise<void>(resolve => { bodyStarted = resolve; });
  await act(async () => finishOldMutation({ status: 403, ok: false, json: () => { bodyStarted(); return oldBody; } } as unknown as Response));
  await bodyRead;

  first.unmount();
  window.history.replaceState(null, "", "/#/projects/p1/schedules/new");
  beginSessionBoundary();
  const second = render(<App />);
  await screen.findByLabelText("제목");

  await act(async () => finishOldBody({ code: "STALE_FORBIDDEN" }));
  await waitFor(() => expect(oldMutation!.state.status).toBe("error"));
  await waitFor(() => expect(screen.getByRole("heading", { name: "일정 만들기" })).toBeInTheDocument());
  expect(hasAccessDenial(accessKey("schedule-write", "p1", "new"))).toBe(false);
  expect(screen.getByLabelText("제목")).toBeInTheDocument();
  second.unmount();
});

test("a stale create failure cannot mark the same group uncertain in a new session", async () => {
  const server = http();
  const mutations = monitorMutations();
  let finishOldCreate!: (response: Response) => void;
  let createCalls = 0;
  server.on("GET", "/api/v1/projects", () => json([]));
  server.on("GET", "/api/v1/projects/creation-options", () => json([{ id: "g1", name: "그룹 1", canCreate: true, reason: null }]));
  server.on("POST", "/api/v1/projects", () => ++createCalls === 1
    ? new Promise(resolve => { finishOldCreate = resolve; })
    : json({ ...schedule, id: "created" }));
  const first = mount("/");
  const firstRoot = within(first.container);
  await first.user.click(await firstRoot.findByRole("button", { name: "새 프로젝트" }));
  await first.user.type(await firstRoot.findByLabelText("프로젝트 이름"), "이전 세션 프로젝트");
  await first.user.click(firstRoot.getByRole("button", { name: "프로젝트 만들기" }));
  await waitFor(() => expect(finishOldCreate).toBeTypeOf("function"));
  const oldMutation = mutations.find(mutation => (mutation.state.variables as { name?: string } | undefined)?.name === "이전 세션 프로젝트");
  expect(oldMutation).toBeDefined();
  expect(oldMutation!.state.status).toBe("pending");

  first.unmount();
  beginSessionBoundary();
  const second = render(<App />);
  const secondRoot = within(second.container);
  const secondUser = userEvent.setup();
  await secondRoot.findByRole("button", { name: "새 프로젝트" });
  await secondRoot.findByRole("heading", { name: "프로젝트 선택" });
  await act(async () => finishOldCreate(json({ code: "STALE_CREATE_FAILED" }, 500)));
  await waitFor(() => expect(oldMutation!.state.status).toBe("error"));
  await secondUser.click(secondRoot.getByRole("button", { name: "새 프로젝트" }));
  expect(await secondRoot.findByLabelText("프로젝트 이름")).toHaveValue("");
  expect(secondRoot.getByRole("button", { name: "프로젝트 만들기" })).toBeEnabled();
  expect(secondRoot.queryByText(/생성 결과를 확인하지 못했습니다/)).not.toBeInTheDocument();
  expect(createCalls).toBe(1);
  second.unmount();
});
