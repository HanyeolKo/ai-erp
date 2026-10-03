import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, test, vi } from "vitest";
import App from "./App";
import { http, json, planSnapshot, project } from "./test/http";

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
