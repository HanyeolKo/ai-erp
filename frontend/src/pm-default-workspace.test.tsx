import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient } from "@tanstack/react-query";
import { afterEach, expect, test, vi } from "vitest";
import App from "./App";
import { currentSessionGeneration } from "./session";
import { http, json, planSnapshot, project, taskSummary } from "./test/http";

afterEach(() => { vi.restoreAllMocks(); window.location.hash = ""; sessionStorage.clear(); });
const mount = (path: string) => { window.location.hash = `#${path}`; render(<App />); return userEvent.setup(); };

test("default plan route opens the compact TASK workspace", async () => {
  const server = http();
  server.on("GET", "/api/v1/projects/p1/plan", () => json(planSnapshot));
  mount("/projects/p1/plan");
  expect(await screen.findByRole("heading", { name: "작업" })).toBeInTheDocument();
  expect(await screen.findByRole("form", { name: "TASK 빠른 추가" })).toBeInTheDocument();
  expect(screen.getByRole("link", { name: "고급 계획 보기" })).toHaveAttribute("href", "#/projects/p1/plan?view=roadmap");
  expect(screen.queryByRole("navigation", { name: "계획 보기" })).not.toBeInTheDocument();
  expect(server.calls.some((call) => call.url === "/api/v1/projects/p1/plan?kind=TASK")).toBe(true);
});

test("explicit legacy view keeps the advanced six-view workspace", async () => {
  const server = http();
  server.on("GET", "/api/v1/projects/p1/plan", () => json(planSnapshot));
  mount("/projects/p1/plan?view=roadmap&state=READY");
  expect(await screen.findByRole("heading", { name: "계획" })).toBeInTheDocument();
  expect(screen.getByRole("navigation", { name: "계획 보기" })).toBeInTheDocument();
  expect(screen.getByRole("link", { name: "로드맵" })).toHaveAttribute("aria-current", "page");
  expect(screen.queryByRole("link", { name: "고급 계획 보기" })).not.toBeInTheDocument();
  expect(server.calls.some((call) => call.url.includes("kind=EPIC%2CTOPIC%2CTASK%2CMILESTONE"))).toBe(true);
});

test("legacy kind and scope selectors without view remain advanced", async () => {
  const server = http();
  server.on("GET", "/api/v1/projects/p1/plan", () => json(planSnapshot));
  mount("/projects/p1/plan?kind=EPIC&scopeId=task-1");
  expect(await screen.findByRole("heading", { name: "계획" })).toBeInTheDocument();
  expect(screen.getByRole("navigation", { name: "계획 보기" })).toBeInTheDocument();
  expect(server.calls.some((call) => call.url.includes("kind=EPIC") && call.url.includes("scopeId=task-1"))).toBe(true);
});

test("TASK deep link opens once and closing clears the requested item", async () => {
  const server = http();
  server.on("GET", "/api/v1/projects/p1/plan", () => json(planSnapshot));
  mount("/projects/p1/plan?itemId=task-1");
  expect(await screen.findByRole("dialog", { name: "실행 TASK" })).toBeInTheDocument();
  await userEvent.setup().click(screen.getByRole("button", { name: "닫기" }));
  expect(screen.queryByRole("dialog", { name: "실행 TASK" })).not.toBeInTheDocument();
  expect(window.location.hash).not.toContain("itemId=task-1");
  expect(server.calls.some((call) => call.url === "/api/v1/projects/p1/plan?kind=TASK")).toBe(true);
});

test("VIEWER cannot submit the compact TASK create form", async () => {
  const server = http();
  server.on("GET", "/api/v1/projects", () => json([{ ...project, role: "VIEWER" }]));
  server.on("GET", "/api/v1/projects/p1/plan", () => json(planSnapshot));
  mount("/projects/p1/plan");
  await screen.findByRole("form", { name: "TASK 빠른 추가" });
  expect(screen.getByLabelText("제목")).toBeDisabled();
  expect(screen.getAllByLabelText("상태").some((control) => (control as HTMLSelectElement).disabled)).toBe(true);
  expect(screen.getByRole("button", { name: "TASK 만들기" })).toBeDisabled();
});

test("compact TASK creation retains the draft after an unknown failure", async () => {
  const server = http();
  server.on("GET", "/api/v1/projects/p1/plan", () => json(planSnapshot));
  server.on("POST", "/api/v1/projects/p1/plan/items", () => json({ code: "TEMPORARY" }, 500));
  const user = mount("/projects/p1/plan");
  await user.type(await screen.findByLabelText("제목"), "복구할 TASK");
  await user.click(screen.getByRole("button", { name: "TASK 만들기" }));
  expect(await screen.findByText("저장 결과를 확인하지 못했습니다. 같은 요청 결과를 확인한 뒤 새 입력을 시작하세요.")).toBeInTheDocument();
  expect(screen.getByLabelText("제목")).toHaveValue("복구할 TASK");
  expect(server.calls.some((call) => call.method === "POST" && call.url === "/api/v1/projects/p1/plan/items")).toBe(true);
});

test("compact TASK creation rechecks the same request after an unknown outcome", async () => {
  const server = http();
  let first = true;
  let firstBody: unknown;
  server.on("GET", "/api/v1/projects/p1/plan", () => json(planSnapshot));
  server.on("POST", "/api/v1/projects/p1/plan/items", (body) => {
    if (first) { first = false; firstBody = body; return json({ code: "UNKNOWN_OUTCOME" }, 500); }
    expect(body).toEqual(firstBody);
    return json(planSnapshot.items[0]);
  });
  const user = mount("/projects/p1/plan");
  const title = await screen.findByLabelText("제목");
  await user.type(title, "재확인할 TASK");
  await user.click(screen.getByRole("button", { name: "TASK 만들기" }));
  expect(await screen.findByRole("button", { name: "같은 요청 결과 확인" })).toBeInTheDocument();
  expect(title).toBeDisabled();
  await user.click(screen.getByRole("button", { name: "같은 요청 결과 확인" }));
  expect(await screen.findByText("TASK를 만들었습니다.")).toBeInTheDocument();
  expect(title).toHaveValue("");
  expect(server.calls.filter((call) => call.method === "POST")).toHaveLength(2);
  const postCalls = server.calls.filter((call) => call.method === "POST");
  expect(postCalls[0].body.requestId).toBe(postCalls[1].body.requestId);
});

test("quick create reconciles after a real TASK update mutation in the same cache", async () => {
  const server = http();
  server.on("PATCH", "/api/v1/projects/p1/plan/items/task-1", () => json({ ...planSnapshot.items[0], state: "IN_PROGRESS" }));
  server.on("POST", "/api/v1/projects/p1/plan/items", (body) => json({ ...planSnapshot.items[0], title: body.title }));
  const user = mount("/projects/p1/plan");
  const state = await screen.findByLabelText("실행 TASK 상태");
  await user.selectOptions(state, "IN_PROGRESS");
  await screen.findByText("계획 항목을 저장했습니다.");
  const title = screen.getByLabelText("제목");
  await user.type(title, "연속 생성 TASK");
  await user.click(screen.getByRole("button", { name: "TASK 만들기" }));
  await screen.findByText("TASK를 만들었습니다.");
  expect(screen.getByRole("heading", { name: "작업" })).toBeInTheDocument();
  expect(server.calls.filter((call) => call.method === "PATCH" && call.url.endsWith("/plan/items/task-1"))).toHaveLength(1);
  expect(server.calls.filter((call) => call.method === "POST" && call.url.endsWith("/plan/items"))).toHaveLength(1);
  expect(screen.queryByText("Cannot read properties of undefined")).not.toBeInTheDocument();
});

test("remounted unknown retry stays locked while the new attempt is pending", async () => {
  const server = http(); let first = true; let finish!: (response: Response) => void;
  server.on("POST", "/api/v1/projects/p1/plan/items", () => { if (first) { first = false; return json({ code: "UNKNOWN_OUTCOME" }, 500); } return new Promise(resolve => { finish = resolve; }); });
  const user = mount("/projects/p1/plan"); const title = await screen.findByLabelText("제목"); await user.type(title, "재시도 잠금 TASK"); await user.click(screen.getByRole("button", { name: "TASK 만들기" }));
  expect(await screen.findByRole("button", { name: "같은 요청 결과 확인" })).toBeInTheDocument();
  vi.spyOn(window, "confirm").mockReturnValue(true); window.location.hash = "#/projects/p1/schedules"; await screen.findByRole("heading", { name: "프로젝트 일정" }); window.location.hash = "#/projects/p1/plan";
  const remountedTitle = await screen.findByLabelText("제목"); expect(remountedTitle).toBeDisabled(); await user.click(screen.getByRole("button", { name: "같은 요청 결과 확인" }));
  await waitFor(() => expect(server.calls.filter((call) => call.method === "POST" && call.url.endsWith("/plan/items"))).toHaveLength(2));
  expect(screen.getByRole("button", { name: "저장 중…" })).toBeDisabled(); expect(screen.getByLabelText("제목")).toBeDisabled();
  finish(json(planSnapshot.items[0])); await waitFor(() => expect(screen.getByLabelText("제목")).toHaveValue(""));
});

test("in-flight compact TASK remains locked across a route remount and resolves once", async () => {
  const server = http(); let finish!: (response: Response) => void;
  server.on("GET", "/api/v1/projects/p1/plan", () => json(planSnapshot));
  server.on("POST", "/api/v1/projects/p1/plan/items", () => new Promise(resolve => { finish = resolve; }));
  const user = mount("/projects/p1/plan");
  const title = await screen.findByLabelText("제목"); await user.type(title, "왕복 중 TASK"); await user.click(screen.getByRole("button", { name: "TASK 만들기" }));
  await waitFor(() => expect(server.calls.filter(call => call.method === "POST")).toHaveLength(1));
  const requestId = server.calls.find(call => call.method === "POST")!.body.requestId;
  vi.spyOn(window, "confirm").mockReturnValue(true);
  window.location.hash = "#/projects/p1/plan?view=roadmap"; await screen.findByRole("heading", { name: "계획" });
  window.location.hash = "#/projects/p1/plan";
  const remountedTitle = await screen.findByLabelText("제목"); expect(remountedTitle).toBeDisabled(); expect(remountedTitle).toHaveValue("왕복 중 TASK");
  finish(json(planSnapshot.items[0])); await waitFor(() => expect(screen.getByLabelText("제목")).toHaveValue(""));
  expect(server.calls.filter(call => call.method === "POST").map(call => call.body.requestId)).toEqual([requestId]);
});

test("dirty compact draft guards a real route departure", async () => {
  http(); const user = mount("/projects/p1/plan"); const title = await screen.findByLabelText("제목"); await user.type(title, "이탈 보호 TASK");
  vi.spyOn(window, "confirm").mockReturnValue(false); window.location.hash = "#/projects/p1/schedules";
  await waitFor(() => expect(window.location.hash).toBe("#/projects/p1/plan")); expect(screen.getByLabelText("제목")).toHaveValue("이탈 보호 TASK");
});

test("partial compact results retain authoritative off-page blockers and scope label", async () => {
  const server = http(); const partial = { ...planSnapshot, complete: false, items: [{ ...planSnapshot.items[0], predecessorIds: ["off-page"], blockerIds: ["off-page"] }] };
  server.on("GET", "/api/v1/projects/p1/plan", () => json(partial)); mount("/projects/p1/plan");
  expect(await screen.findByText("관측된 TASK 범위")).toBeInTheDocument(); expect(screen.getByText("차단 원인 1건")).toBeInTheDocument(); expect(screen.getByText(/일부 TASK만 관측/)).toBeInTheDocument();
});

test("overview keeps schedule-empty copy separate from TASK onboarding", async () => {
  const server = http(); server.on("GET", "/api/v1/projects/p1/dashboard", () => json({ projectId: "p1", memberCount: 2, scheduleCount: 0, pendingAcknowledgementCount: 0, calendarRiskCount: 0, upcomingSchedules: [], actionQueue: [] }));
  mount("/projects/p1"); expect(await screen.findByText("예정된 일정이 없습니다.")).toBeInTheDocument(); expect(screen.queryByText("구성원을 초대하고 첫 작업을 만들어보세요.")).not.toBeInTheDocument();
});

test("current TASK plan denial freezes cached-role create actions", async () => {
  const server = http(); server.on("GET", "/api/v1/projects/p1/plan", () => json({ code: "PLAN_FORBIDDEN" }, 403)); mount("/projects/p1");
  expect(await screen.findByText("PLAN_FORBIDDEN")).toBeInTheDocument(); expect(screen.queryByRole("link", { name: "TASK 만들기" })).not.toBeInTheDocument();
});

test("compact TASK input keeps the complete ASCII value synchronously", async () => {
  http(); mount("/projects/p1/plan"); const title = await screen.findByLabelText("제목");
  fireEvent.change(title, { target: { value: "R4 durable draft a12345" } });
  expect(title).toHaveValue("R4 durable draft a12345");
});

test("known TASK conflict lets edited recovery send a new UUID and payload", async () => {
  const server = http(); let first = true;
  server.on("POST", "/api/v1/projects/p1/plan/items", (body) => { if (first) { first = false; return json({ code: "CONFLICT" }, 409); } return json({ ...planSnapshot.items[0], title: body.title }); });
  const user = mount("/projects/p1/plan"); const title = await screen.findByLabelText("제목");
  await user.type(title, "초기 제목"); await user.click(screen.getByRole("button", { name: "TASK 만들기" }));
  expect(await screen.findByText("다른 변경이 먼저 저장되었습니다. 입력을 확인한 뒤 다시 시도하세요.")).toBeInTheDocument();
  await user.clear(title); await user.type(title, "수정된 제목"); await user.click(screen.getByRole("button", { name: "TASK 만들기" }));
  await screen.findByText("TASK를 만들었습니다.");
  const posts = server.calls.filter((call) => call.method === "POST");
  expect(posts[1].body.title).toBe("수정된 제목"); expect(posts[1].body.requestId).not.toBe(posts[0].body.requestId);
});

test("successful TASK creation allocates a new UUID for the next creation", async () => {
  const server = http(); server.on("POST", "/api/v1/projects/p1/plan/items", (body) => json({ ...planSnapshot.items[0], title: body.title }));
  const user = mount("/projects/p1/plan"); const title = await screen.findByLabelText("제목");
  await user.type(title, "첫 TASK"); await user.click(screen.getByRole("button", { name: "TASK 만들기" })); await screen.findByText("TASK를 만들었습니다.");
  await user.type(title, "둘째 TASK"); await user.click(screen.getByRole("button", { name: "TASK 만들기" })); await waitFor(() => expect(server.calls.filter((call) => call.method === "POST")).toHaveLength(2));
  const posts = server.calls.filter((call) => call.method === "POST"); expect(posts[1].body.requestId).not.toBe(posts[0].body.requestId);
});

test("compact TASK draft survives route return after more than five minutes", async () => {
  http(); const user = mount("/projects/p1/plan"); const title = await screen.findByLabelText("제목"); await user.type(title, "장기 보존 TASK");
  vi.spyOn(window, "confirm").mockReturnValue(true); vi.advanceTimersByTime(5 * 60 * 1000 + 1); window.location.hash = "#/projects/p1/schedules"; await act(async () => { await Promise.resolve(); });
  expect(window.location.hash).toBe("#/projects/p1/schedules");
  vi.useFakeTimers({ toFake: ["setTimeout", "clearTimeout"] }); vi.advanceTimersByTime(5 * 60 * 1000 + 1); window.location.hash = "#/projects/p1/plan"; await act(async () => { await Promise.resolve(); });
  expect(screen.getByLabelText("제목")).toHaveValue("장기 보존 TASK");
});

test("current TASK denial rechecks project membership and preserves the own draft namespace", async () => {
  const server = http(); let projectReads = 0;
  server.on("GET", "/api/v1/projects", () => { projectReads += 1; return json([project]); });
  server.on("GET", "/api/v1/projects/p1/plan", () => json({ code: "PLAN_FORBIDDEN" }, 403));
  mount("/projects/p1"); await screen.findByText("PLAN_FORBIDDEN"); await waitFor(() => expect(projectReads).toBeGreaterThanOrEqual(2));
});

test("cached TASK success followed by overview 403 clears protected caches and keeps the own draft", async () => {
  const server = http();
  let forbidden = false;
  let projectReads = 0;
  let client: QueryClient | undefined;
  const removedKeys: unknown[][] = [];
  const removeQueries = QueryClient.prototype.removeQueries;
  vi.spyOn(QueryClient.prototype, "removeQueries").mockImplementation(function (this: QueryClient, filters) {
    client = this;
    if (filters?.queryKey) removedKeys.push([...filters.queryKey]);
    return removeQueries.call(this, filters);
  });
  server.on("GET", "/api/v1/projects", () => { projectReads += 1; return json([project]); });
  server.on("GET", "/api/v1/projects/p1/plan", () => forbidden ? json({ code: "PLAN_FORBIDDEN" }, 403) : json(planSnapshot));
  const user = mount("/projects/p1/plan");
  const title = await screen.findByLabelText("제목");
  await user.type(title, "내 보존 TASK");
  const state = within(screen.getByRole("form", { name: "TASK 빠른 추가" })).getByRole("combobox");
  await user.selectOptions(state, "IN_PROGRESS");
  const generation = currentSessionGeneration();
  forbidden = true;
  vi.spyOn(window, "confirm").mockReturnValue(true);
  vi.advanceTimersByTime(15_001);
  window.location.hash = "#/projects/p1";
  await screen.findByRole("heading", { name: "프로젝트 개요" });
  await screen.findByText("PLAN_FORBIDDEN");
  await waitFor(() => expect(projectReads).toBeGreaterThanOrEqual(2));
  await waitFor(() => expect(client).toBeDefined());
  expect(removedKeys).toEqual(expect.arrayContaining([["project-plan", "p1", ""], ["members", "p1"]]));
  expect(client!.getQueryData(["project-task-draft", generation, "p1"])).toMatchObject({ title: "내 보존 TASK", state: "IN_PROGRESS" });
  expect(screen.queryByRole("link", { name: "TASK 만들기" })).not.toBeInTheDocument();
  expect(screen.getAllByRole("link", { name: "작업 열기" })).not.toHaveLength(0);
});

test("TASK zero keeps schedule dashboard sections when a schedule exists", async () => {
  const server = http(); const empty = { ...planSnapshot, totalCount: 0, matchedIds: [], items: [], summary: { ...taskSummary, taskCount: 0, progressPercent: null, forecastState: "EMPTY" } };
  server.on("GET", "/api/v1/projects/p1/plan", () => json(empty)); mount("/projects/p1");
  expect(await screen.findByRole("heading", { name: "예정된 일정" })).toBeInTheDocument(); expect(await screen.findByRole("heading", { name: "선택한 일정 카드" })).toBeInTheDocument();
});

test("partial TASK with an unobserved predecessor reports unknown status", async () => {
  const server = http(); const partial = { ...planSnapshot, complete: false, items: [{ ...planSnapshot.items[0], predecessorIds: ["off-page"], blockerIds: [] }] };
  server.on("GET", "/api/v1/projects/p1/plan", () => json(partial)); mount("/projects/p1/plan");
  expect(await screen.findByText("선행 상태 확인 필요")).toBeInTheDocument(); expect(screen.queryByText("차단 없음")).not.toBeInTheDocument();
});

test("plan 500 remains independent while dashboard schedules remain visible", async () => {
  const server = http(); server.on("GET", "/api/v1/projects/p1/plan", () => json({ code: "PLAN_TEMPORARY" }, 500)); mount("/projects/p1");
  expect(await screen.findByText("PLAN_TEMPORARY")).toBeInTheDocument(); expect(screen.getByRole("heading", { name: "예정된 일정" })).toBeInTheDocument();
});
