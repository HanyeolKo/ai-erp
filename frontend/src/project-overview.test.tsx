import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, test, vi } from "vitest";
import App from "./App";
import { http, json } from "./test/http";

const plan = { projectId: "p1", rowVersion: 1, targetStart: "2090-09-01", targetEnd: "2090-09-30", asOfDate: "2090-09-10", items: [], matchedIds: [], summary: { taskCount: 8, doneCount: 3, blockedCount: 0, overdueCount: 0, unplannedCount: 0, unassignedCount: 0, progressPercent: 37.5, forecastStart: "2090-09-01", forecastEnd: "2090-09-30", forecastState: "COMPLETE", outsideTarget: false }, complete: true, totalCount: 0 };
const dashboard = { projectId: "p1", memberCount: 1, scheduleCount: 0, pendingAcknowledgementCount: 0, calendarRiskCount: 0, upcomingSchedules: [], actionQueue: [] };
const schedule = { id: "s1", projectId: "p1", title: "오늘 일정", description: "", status: "CONFIRMED", rowVersion: 1, businessRevision: 1, startsAt: "2090-09-10T01:00:00Z", endsAt: "2090-09-10T02:00:00Z", createdBy: "u1", participants: [], changes: [] };

afterEach(() => { vi.unstubAllGlobals(); window.location.hash = ""; });
test("renders macro progress before work and schedule sections without writes", async () => {
  const server = http();
  server.on("GET", "/api/v1/projects/p1/plan", () => json(plan));
  server.on("GET", "/api/v1/projects/p1/dashboard", () => json(dashboard));
  server.on("GET", "/api/v1/projects/p1/schedules", () => json([schedule]));
  server.on("GET", "/api/v1/projects/p1/schedule-workspace", () => json({ properties: [], views: [], dashboardViewId: null, dashboardRowVersion: 0 }));
  window.location.hash = "#/projects/p1?period=today&zone=Asia%2FSeoul";
  render(<App />);
  expect(await screen.findByRole("heading", { name: "프로젝트 진척" })).toBeInTheDocument();
  expect(screen.getByText("작업 완료율 · 모든 작업 동일 비중 · 취소 제외")).toBeInTheDocument();
  await waitFor(() => expect(screen.getByRole("heading", { name: /프로젝트 일정/ })).toBeInTheDocument());
  expect(server.calls.some(call => /acknowledge|notifications\/.*\/read|\/plan$/.test(call.url) && call.method !== "GET")).toBe(false);
});

test("hides every protected region when a schedule access denial races a late plan success", async () => {
  const server = http();
  let resolvePlan!: (response: Response) => void;
  server.on("GET", "/api/v1/projects/p1/plan", () => new Promise<Response>(resolve => { resolvePlan = resolve; }));
  server.on("GET", "/api/v1/projects/p1/dashboard", () => json(dashboard));
  server.on("GET", "/api/v1/projects/p1/schedules", () => json({ code: "PROJECT_ACCESS_DENIED" }, 403));
  server.on("GET", "/api/v1/projects/p1/schedule-workspace", () => json({ properties: [], views: [], dashboardViewId: null, dashboardRowVersion: 0 }));
  window.location.hash = "#/projects/p1?period=week&zone=Asia%2FSeoul";
  render(<App />);
  expect(await screen.findByRole("alert")).toHaveTextContent("접근할 수 없습니다");
  expect(screen.queryByRole("heading", { name: "프로젝트 진척" })).not.toBeInTheDocument();
  resolvePlan(json(plan));
  await waitFor(() => expect(screen.queryByRole("heading", { name: "프로젝트 진척" })).not.toBeInTheDocument());
  expect(server.calls.filter(call => call.url.includes("/plan")).length).toBe(1);
});

test("hides cached overview regions when the dashboard card query is denied, then recovers on fresh retry", async () => {
  const server = http();
  let cardCalls = 0;
  server.on("GET", "/api/v1/projects/p1/plan", () => json(plan));
  server.on("GET", "/api/v1/projects/p1/dashboard", () => json(dashboard));
  server.on("GET", "/api/v1/projects/p1/schedules", () => json([]));
  server.on("GET", "/api/v1/projects/p1/schedule-workspace", () => json({ properties: [], views: [], dashboardViewId: null, dashboardRowVersion: 0 }));
  server.on("POST", "/api/v1/projects/p1/schedule-workspace/query", () => { cardCalls += 1; return cardCalls === 1 ? json({ code: "PROJECT_ACCESS_DENIED" }, 403) : json({ records: [], total: 0, hasMore: false, page: 0, size: 20, groups: [], queriedAt: "2090-09-10T00:00:00Z" }); });
  window.location.hash = "#/projects/p1";
  render(<App />);
  expect(await screen.findByRole("alert")).toHaveTextContent("접근할 수 없습니다");
  expect(screen.queryByRole("heading", { name: "프로젝트 진척" })).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
  await waitFor(() => expect(screen.getByRole("heading", { name: "프로젝트 진척" })).toBeInTheDocument());
  expect(cardCalls).toBeGreaterThanOrEqual(2);
});

test("keeps a failed membership recovery blocked and retries fresh membership", async () => {
  const server = http();
  server.on("GET", "/api/v1/projects", () => json({ code: "PROJECT_ACCESS_DENIED" }, 403));
  window.location.hash = "#/projects/p1";
  render(<App />);
  expect(await screen.findByRole("alert")).toHaveTextContent("접근할 수 없습니다");
  expect(screen.queryByRole("heading", { name: "프로젝트 진척" })).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
  await waitFor(() => expect(server.calls.filter(call => call.url.startsWith("/api/v1/projects")).length).toBeGreaterThanOrEqual(2));
  expect(screen.queryByRole("heading", { name: "프로젝트 진척" })).not.toBeInTheDocument();
});

test("reuses the loaded week when switching between today and week", async () => {
  const server = http();
  server.on("GET", "/api/v1/projects/p1/plan", () => json(plan));
  server.on("GET", "/api/v1/projects/p1/dashboard", () => json(dashboard));
  server.on("GET", "/api/v1/projects/p1/schedules", () => json([schedule]));
  server.on("GET", "/api/v1/projects/p1/schedule-workspace", () => json({ properties: [], views: [], dashboardViewId: null, dashboardRowVersion: 0 }));
  window.location.hash = "#/projects/p1?period=today&zone=Asia%2FSeoul";
  render(<App />);
  const week = await screen.findByRole("button", { name: "이번 주" });
  const scheduleRequests = () => server.calls.filter(call => call.method === "GET" && call.url.startsWith("/api/v1/projects/p1/schedules?")).length;
  await waitFor(() => expect(screen.getAllByRole("link", { name: /오늘 일정/ }).length).toBeGreaterThan(1));
  expect(scheduleRequests()).toBe(1);
  fireEvent.click(week);
  await waitFor(() => expect(screen.getAllByText("오늘 일정").length).toBeGreaterThan(0));
  expect(scheduleRequests()).toBe(1);
});

test("uses a new same-week request epoch after the Seoul civil midnight timer", async () => {
  const server = http();
  server.on("GET", "/api/v1/projects/p1/plan", () => json(plan));
  server.on("GET", "/api/v1/projects/p1/dashboard", () => json(dashboard));
  server.on("GET", "/api/v1/projects/p1/schedules", () => json([schedule]));
  server.on("GET", "/api/v1/projects/p1/schedule-workspace", () => json({ properties: [], views: [], dashboardViewId: null, dashboardRowVersion: 0 }));
  vi.setSystemTime(new Date("2090-09-10T14:59:59Z"));
  window.location.hash = "#/projects/p1?period=today&zone=Asia%2FSeoul";
  render(<App />);
  await screen.findByRole("heading", { name: "프로젝트 진척" });
  const scheduleRequests = () => server.calls.filter(call => call.method === "GET" && call.url.startsWith("/api/v1/projects/p1/schedules?")).length;
  expect(scheduleRequests()).toBe(1);
  vi.setSystemTime(new Date("2090-09-10T15:00:01Z"));
  await new Promise(resolve => setTimeout(resolve, 1200));
  await waitFor(() => expect(scheduleRequests()).toBe(2));
  expect(window.location.hash).toContain("period=today");
});

test("reschedules the midnight timer when the zone changes within the same civil date", async () => {
  const server = http();
  const timerSpy = vi.spyOn(window, "setTimeout");
  vi.setSystemTime(new Date("2090-09-10T10:00:00Z"));
  server.on("GET", "/api/v1/projects/p1/plan", () => json(plan));
  server.on("GET", "/api/v1/projects/p1/dashboard", () => json(dashboard));
  server.on("GET", "/api/v1/projects/p1/schedules", () => json([schedule]));
  server.on("GET", "/api/v1/projects/p1/schedule-workspace", () => json({ properties: [], views: [], dashboardViewId: null, dashboardRowVersion: 0 }));
  window.location.hash = "#/projects/p1?period=today&zone=America%2FLos_Angeles";
  render(<App />);
  await screen.findByRole("heading", { name: "프로젝트 진척" });
  const scheduleRequests = () => server.calls.filter(call => call.method === "GET" && call.url.startsWith("/api/v1/projects/p1/schedules?")).length;
  await waitFor(() => expect(scheduleRequests()).toBe(1));
  await act(async () => {
    window.location.hash = "#/projects/p1?period=today&zone=America%2FNew_York";
    window.dispatchEvent(new HashChangeEvent("hashchange"));
  });
  await waitFor(() => expect(scheduleRequests()).toBe(2));
  const midnightTimer = timerSpy.mock.calls.find(([, delay]) => Number(delay) > 64_000_000 && Number(delay) < 66_000_000)?.[0] as (() => void) | undefined;
  expect(midnightTimer).toBeDefined();
  vi.setSystemTime(new Date("2090-09-11T04:00:01Z"));
  await act(async () => { midnightTimer!(); });
  await waitFor(() => expect(scheduleRequests()).toBe(3));
  expect(server.calls.some(call => call.url.includes("from=2090-09-11T04%3A00%3A00.000Z"))).toBe(true);
});

test.each(["success", "denial"] as const)("ignores delayed obsolete-zone %s after the new zone is authorized", async outcome => {
  const server = http();
  let settleOld!: (value: Response) => void;
  server.on("GET", "/api/v1/projects/p1/plan", () => json(plan));
  server.on("GET", "/api/v1/projects/p1/dashboard", () => json(dashboard));
  server.on("GET", "/api/v1/projects/p1/schedules", (_, url) => url.searchParams.get("from")?.includes("T15:00") ? new Promise<Response>(resolve => { settleOld = resolve; }) : json([schedule]));
  server.on("GET", "/api/v1/projects/p1/schedule-workspace", () => json({ properties: [], views: [], dashboardViewId: null, dashboardRowVersion: 0 }));
  window.location.hash = "#/projects/p1?period=today&zone=Asia%2FSeoul";
  render(<App />);
  await screen.findByRole("heading", { name: "프로젝트 진척" });
  await act(async () => { window.location.hash = "#/projects/p1?period=today&zone=America%2FNew_York"; window.dispatchEvent(new HashChangeEvent("hashchange")); });
  await screen.findByText(/America\/New_York/);
  await act(async () => settleOld(outcome === "success" ? json([schedule]) : json({ code: "PROJECT_ACCESS_DENIED" }, 403)));
  await waitFor(() => expect(screen.getByRole("heading", { name: "프로젝트 진척" })).toBeInTheDocument());
  expect(screen.queryByText(/보호된 내용을 숨겼습니다/)).not.toBeInTheDocument();
});

test("keeps active configured-card RESOURCE_NOT_FOUND local", async () => {
  const server = http();
  server.on("GET", "/api/v1/projects/p1/plan", () => json(plan)); server.on("GET", "/api/v1/projects/p1/dashboard", () => json(dashboard)); server.on("GET", "/api/v1/projects/p1/schedules", () => json([]));
  server.on("GET", "/api/v1/projects/p1/schedule-workspace", () => json({ properties: [], views: [], dashboardViewId: null, dashboardRowVersion: 0 }));
  server.on("POST", "/api/v1/projects/p1/schedule-workspace/query", () => json({ code: "RESOURCE_NOT_FOUND" }, 404));
  window.location.hash = "#/projects/p1"; render(<App />);
  expect(await screen.findByRole("heading", { name: "프로젝트 진척" })).toBeInTheDocument();
  expect(screen.queryByText(/보호된 내용을 숨겼습니다/)).not.toBeInTheDocument();
});

test("overview reading, period switching, and retry issue no product mutations", async () => {
  const server = http(); let planCalls = 0;
  server.on("GET", "/api/v1/projects/p1/plan", () => ++planCalls === 1 ? json({ code: "TEMPORARY" }, 500) : json(plan));
  server.on("GET", "/api/v1/projects/p1/dashboard", () => json(dashboard)); server.on("GET", "/api/v1/projects/p1/schedules", () => json([schedule])); server.on("GET", "/api/v1/projects/p1/schedule-workspace", () => json({ properties: [], views: [], dashboardViewId: null, dashboardRowVersion: 0 }));
  window.location.hash = "#/projects/p1"; render(<App />);
  await screen.findByText("계획을 불러오지 못했습니다."); fireEvent.click(screen.getByRole("button", { name: "다시 시도" })); await screen.findByRole("heading", { name: "프로젝트 진척" }); fireEvent.click(screen.getByRole("button", { name: "오늘" }));
  expect(server.calls.filter(call => ["POST", "PUT", "PATCH", "DELETE"].includes(call.method) && !call.url.endsWith("/schedule-workspace/query"))).toEqual([]);
});

test.each([
  ["incomplete within target", { forecastState: "INCOMPLETE", outsideTarget: false }, "예측 일정 일부 미정", false],
  ["incomplete outside target", { forecastState: "INCOMPLETE", outsideTarget: true }, "수동 목표 범위 밖 (outsideTarget)", true],
  ["missing forecast bounds", { forecastState: "COMPLETE", forecastStart: null, forecastEnd: null, outsideTarget: false }, "비교할 예측 기간 없음", true],
] as const)("keeps forecast comparison facts independent: %s", async (_label, summary, expected, alsoExpected) => {
  const server = http();
  server.on("GET", "/api/v1/projects/p1/plan", () => json({ ...plan, summary: { ...plan.summary, ...summary } }));
  server.on("GET", "/api/v1/projects/p1/dashboard", () => json(dashboard));
  server.on("GET", "/api/v1/projects/p1/schedules", () => json([]));
  server.on("GET", "/api/v1/projects/p1/schedule-workspace", () => json({ properties: [], views: [], dashboardViewId: null, dashboardRowVersion: 0 }));
  window.location.hash = "#/projects/p1";
  render(<App />);
  await screen.findByRole("heading", { name: "프로젝트 진척" });
  expect(screen.getByText(new RegExp(expected.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")))).toBeInTheDocument();
  if (!alsoExpected) expect(screen.queryByText("수동 목표 안")).not.toBeInTheDocument();
});

test.each(["MANAGER", "MEMBER", "VIEWER"] as const)("preserves %s overview rights and secondary links", async role => {
  const server = http();
  server.on("GET", "/api/v1/projects", () => json([{ ...({ id: "p1", groupId: "g1", name: "Planning", role } as const) }]));
  server.on("GET", "/api/v1/projects/p1/plan", () => json(plan));
  server.on("GET", "/api/v1/projects/p1/dashboard", () => json({ ...dashboard, actionQueue: [schedule], scheduleCount: 1 }));
  server.on("GET", "/api/v1/projects/p1/schedules", () => json([schedule]));
  server.on("GET", "/api/v1/projects/p1/schedule-workspace", () => json({ properties: [], views: [], dashboardViewId: null, dashboardRowVersion: 0 }));
  window.location.hash = "#/projects/p1";
  render(<App />);
  expect(await screen.findByRole("heading", { name: "프로젝트 진척" })).toBeInTheDocument();
  if (role === "VIEWER") {
    expect(screen.getByText("조회 권한에서는 확인 작업을 표시하지 않습니다.")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "일정 만들기" })).not.toBeInTheDocument();
  } else {
    expect(screen.getByRole("link", { name: "일정 만들기" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "내 확인이 필요한 일정" })).toBeInTheDocument();
  }
});
