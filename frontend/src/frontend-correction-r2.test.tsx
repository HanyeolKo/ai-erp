import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { focusManager } from "@tanstack/react-query";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, test, vi } from "vitest";
import App from "./App";
import { http, json, schedule } from "./test/http";

afterEach(() => { vi.unstubAllGlobals(); window.location.hash = ""; sessionStorage.clear(); focusManager.setFocused(undefined); });

test("MANAGER preserves an explicitly selected SHARED scope when creating from a builtin view", async () => {
  const server = http();
  server.on("POST", "/api/v1/projects/p1/schedule-workspace/views", body => json({ id: "shared-new", ownerId: "u1", rowVersion: 1, archived: false, ...body }));
  window.location.hash = "#/projects/p1/schedules?view=builtin-list&page=0";
  render(<App />);
  const user = userEvent.setup();
  await user.click(await screen.findByRole("button", { name: "보기 설정" }));
  const dialog = screen.getByRole("dialog", { name: "보기 설정" });
  await user.selectOptions(within(dialog).getByLabelText("공유 범위"), "SHARED");
  await user.click(within(dialog).getByRole("button", { name: "보기 저장" }));
  await waitFor(() => expect(server.calls.some(call => call.method === "POST" && call.url === "/api/v1/projects/p1/schedule-workspace/views" && call.body.scope === "SHARED")).toBe(true));
});

test.each([
  ["month", "builtin-calendar", "month"],
  ["week", "builtin-calendar", "week"],
  ["list", "builtin-list", null],
] as const)("legacy %s context normalizes once and preserves date and filters", async (legacy, canonical, mode) => {
  http();
  window.location.hash = `#/projects/p1/schedules?view=${legacy}&page=2&date=2090-09-10&text=review&status=CONFIRMED`;
  render(<App />);
  await screen.findByRole("heading", { name: "프로젝트 일정" });
  await waitFor(() => expect(new URLSearchParams(window.location.hash.split("?")[1]).get("view")).toBe(canonical));
  const params = new URLSearchParams(window.location.hash.split("?")[1]);
  expect(params.get("page")).toBe("0");
  expect(params.get("date")).toBe("2090-09-10");
  expect(params.get("text")).toBe("review");
  expect(params.get("status")).toBe("CONFIRMED");
  expect(params.get("mode")).toBe(mode);
  expect(screen.queryByText("선택한 보기를 사용할 수 없어 기본 캘린더로 이동했습니다.")).not.toBeInTheDocument();
});

test("List time edit guards dirty Cancel and uses a freshly recovered version after 409", async () => {
  const server = http();
  let writes = 0;
  server.on("PATCH", "/api/v1/projects/p1/schedules/s1", body => {
    writes += 1;
    if (writes === 1) return json({ code: "ROW_VERSION_CONFLICT" }, 409);
    expect(body.rowVersion).toBe(7);
    return json({ ...schedule, ...body, rowVersion: 8 });
  });
  server.on("GET", "/api/v1/projects/p1/schedules/s1", () => json({ ...schedule, rowVersion: 7 }));
  window.location.hash = "#/projects/p1/schedules?view=builtin-list&page=0";
  render(<App />);
  const user = userEvent.setup();
  const region = await screen.findByRole("region", { name: "일정 목록" });
  await user.click(within(region).getByRole("button", { name: "시간 변경: Design review" }));
  const dialog = screen.getByRole("dialog", { name: "시간 변경" });
  fireEvent.change(within(dialog).getByLabelText("시작"), { target: { value: "2090-09-10T11:00" } });
  fireEvent.change(within(dialog).getByLabelText("종료"), { target: { value: "2090-09-10T12:00" } });
  vi.stubGlobal("confirm", vi.fn(() => false));
  await user.click(within(dialog).getByRole("button", { name: "취소" }));
  expect(screen.getByRole("dialog", { name: "시간 변경" })).toBeInTheDocument();
  await user.click(within(dialog).getByRole("button", { name: "변경 저장" }));
  expect(await within(dialog).findByRole("alert")).toHaveTextContent("다음 저장은 최신 버전을 사용합니다");
  await user.click(within(dialog).getByRole("button", { name: "변경 저장" }));
  await waitFor(() => expect(writes).toBe(2));
  expect(screen.queryByRole("dialog", { name: "시간 변경" })).not.toBeInTheDocument();
});

test("validated return context survives edit Cancel and successful save", async () => {
  const server = http();
  server.on("PATCH", "/api/v1/projects/p1/schedule-workspace/records/s1", body => json({ schedule: { ...schedule, ...body.schedule, rowVersion: 4 }, values: body.values }));
  const encoded = encodeURIComponent("view=builtin-list&page=2&text=review");
  window.location.hash = `#/projects/p1/schedules/s1/edit?return=${encoded}`;
  render(<App />);
  const user = userEvent.setup();
  const cancel = await screen.findByRole("link", { name: "취소" });
  expect(cancel).toHaveAttribute("href", `#/projects/p1/schedules/s1?return=${encoded}`);
  await user.click(screen.getByRole("button", { name: "일정 저장" }));
  await waitFor(() => expect(window.location.hash).toBe(`#/projects/p1/schedules/s1?return=${encoded}`));
});

test("custom-only Detail Cancel asks before discarding and keeps the draft when declined", async () => {
  const server = http();
  const property = { id: "notes", name: "메모", type: "TEXT", position: 0, archived: false, rowVersion: 1, options: [] };
  server.on("GET", "/api/v1/projects/p1/schedule-workspace", () => json({ properties: [property], views: [], dashboardViewId: null, dashboardRowVersion: 0 }));
  server.on("GET", "/api/v1/projects/p1/schedule-workspace/records/s1", () => json({ schedule, values: { notes: "server" } }));
  window.location.hash = "#/projects/p1/schedules/s1";
  render(<App />);
  const user = userEvent.setup();
  const input = await screen.findByLabelText("메모");
  await user.clear(input); await user.type(input, "draft");
  const confirm = vi.fn(() => false); vi.stubGlobal("confirm", confirm);
  await user.click(screen.getByRole("button", { name: "취소" }));
  expect(confirm).toHaveBeenCalledTimes(1);
  expect(input).toHaveValue("draft");
  confirm.mockReturnValue(true);
  await user.click(screen.getByRole("button", { name: "취소" }));
  expect(input).toHaveValue("server");
});

test("custom-only Form route departure keeps its draft when declined", async () => {
  const server = http();
  const property = { id: "notes", name: "메모", type: "TEXT", position: 0, archived: false, rowVersion: 1, options: [] };
  server.on("GET", "/api/v1/projects/p1/schedule-workspace", () => json({ properties: [property], views: [], dashboardViewId: null, dashboardRowVersion: 0 }));
  server.on("GET", "/api/v1/projects/p1/schedule-workspace/records/s1", () => json({ schedule, values: { notes: "server" } }));
  window.location.hash = "#/projects/p1/schedules/s1/edit?return=view%3Dbuiltin-list%26page%3D0";
  render(<App />);
  const user = userEvent.setup();
  const input = await screen.findByLabelText("메모");
  await user.clear(input); await user.type(input, "draft");
  vi.stubGlobal("confirm", vi.fn(() => false));
  await act(async () => { window.location.hash = "#/projects/p1/schedules"; window.dispatchEvent(new HashChangeEvent("hashchange")); });
  await waitFor(() => expect(window.location.hash).toContain("/s1/edit"));
  expect(screen.getByLabelText("메모")).toHaveValue("draft");
});

test("dashboard denial locks an already-open value editor", async () => {
  const server = http();
  const property = { id: "notes", name: "메모", type: "TEXT", position: 0, archived: false, rowVersion: 1, options: [] };
  const view = { id: "shared-cards", name: "공유 카드", scope: "SHARED", ownerId: "u1", rowVersion: 1, archived: false, config: { type: "CARDS", filters: [], sorts: [], groupBy: null, legendBy: null, visibleFields: ["title", "property:notes"] } };
  let denied = false;
  server.on("GET", "/api/v1/projects/p1/schedule-workspace", () => json({ properties: [property], views: [view], dashboardViewId: view.id, dashboardRowVersion: 1 }));
  server.on("POST", "/api/v1/projects/p1/schedule-workspace/query", body => denied ? json({ code: "FORBIDDEN" }, 403) : json({ records: [{ schedule, values: { notes: "server" } }], total: 1, hasMore: false, page: body.page, size: body.size, groups: [], queriedAt: "2090-09-10T00:00:00Z" }));
  window.location.hash = "#/projects/p1";
  render(<App />);
  const user = userEvent.setup();
  await user.click(await screen.findByRole("button", { name: "속성 바로 수정" }));
  denied = true;
  await act(async () => { vi.setSystemTime(new Date("2090-09-10T00:01:00Z")); focusManager.setFocused(false); focusManager.setFocused(true); });
  await screen.findByText(/대시보드 접근 권한을 다시 확인해야 합니다/);
  expect(screen.queryByRole("button", { name: "속성 저장" })).not.toBeInTheDocument();
  expect(server.calls.filter(call => call.method === "PATCH" && call.url.endsWith("/values"))).toHaveLength(0);
});

test("Calendar denial locks an already-open property editor", async () => {
  const server = http();
  const property = { id: "priority", name: "우선순위", type: "SINGLE_SELECT", position: 0, archived: false, rowVersion: 1, options: [{ id: "high", label: "높음", color: "red", archived: false }] };
  let denied = false;
  server.on("GET", "/api/v1/projects/p1/schedule-workspace", () => json({ properties: [property], views: [], dashboardViewId: null, dashboardRowVersion: 0 }));
  server.on("POST", "/api/v1/projects/p1/schedule-workspace/query", body => denied && body.page === 1 ? json({ code: "FORBIDDEN" }, 403) : json({ records: [{ schedule, values: {} }], total: denied ? 101 : 1, hasMore: denied && body.page === 0, page: body.page, size: body.size, groups: [], queriedAt: "2090-09-10T00:00:00Z" }));
  window.location.hash = "#/projects/p1/schedules?view=builtin-calendar&date=2090-09-10&mode=month&page=0";
  render(<App />);
  const user = userEvent.setup();
  await user.click(await screen.findByRole("button", { name: "속성" }));
  const dialog = screen.getByRole("dialog", { name: "프로젝트 속성" });
  denied = true;
  await act(async () => { vi.setSystemTime(new Date("2090-09-10T00:01:00Z")); focusManager.setFocused(false); focusManager.setFocused(true); });
  await screen.findByText(/일정 접근 권한을 다시 확인해야 합니다/);
  expect(within(dialog).getByRole("button", { name: "옵션 저장" })).toBeDisabled();
  expect(within(dialog).getByRole("button", { name: "저장" })).toBeDisabled();
});

test.each([403, 404] as const)("Calendar later-page %s locks an already-open settings write until the exact ranged traversal recovers", async status => {
  const server = http();
  let denied = false;
  server.on("POST", "/api/v1/projects/p1/schedule-workspace/query", body => {
    if (denied && body.page === 1) return json({ code: status === 403 ? "FORBIDDEN" : "NOT_FOUND" }, status);
    return json({ records: [{ schedule, values: {} }], total: denied ? 101 : 1, hasMore: denied && body.page === 0, page: body.page, size: body.size, groups: [], queriedAt: "2090-09-10T00:00:00Z" });
  });
  window.location.hash = "#/projects/p1/schedules?view=builtin-calendar&date=2090-09-10&mode=month&page=0";
  render(<App />);
  const user = userEvent.setup();
  await user.click(await screen.findByRole("button", { name: "보기 설정" }));
  const save = within(screen.getByRole("dialog", { name: "보기 설정" })).getByRole("button", { name: "보기 저장" });
  denied = true;
  await act(async () => { vi.setSystemTime(new Date("2090-09-10T00:01:00Z")); focusManager.setFocused(false); focusManager.setFocused(true); });
  await screen.findByText(/일정 접근 권한을 다시 확인해야 합니다/);
  expect(save).toBeDisabled();
  await user.click(screen.getByRole("button", { name: "접근 상태 다시 확인" }));
  await screen.findByText("접근 상태를 확인하지 못해 보호된 작업을 잠갔습니다.");
  expect(save).toBeDisabled();
  denied = false;
  await user.click(screen.getByRole("button", { name: "접근 상태 다시 확인" }));
  await waitFor(() => expect(save).toBeEnabled());
});

test("Calendar later-page 401 retires the protected workspace and cannot send the open settings write", async () => {
  const server = http();
  let denied = false;
  server.on("POST", "/api/v1/projects/p1/schedule-workspace/query", body => denied && body.page === 1 ? json({ code: "UNAUTHENTICATED" }, 401) : json({ records: [{ schedule, values: {} }], total: denied ? 101 : 1, hasMore: denied && body.page === 0, page: body.page, size: body.size, groups: [], queriedAt: "2090-09-10T00:00:00Z" }));
  window.location.hash = "#/projects/p1/schedules?view=builtin-calendar&date=2090-09-10&mode=month&page=0";
  render(<App />);
  const user = userEvent.setup();
  await user.click(await screen.findByRole("button", { name: "보기 설정" }));
  denied = true;
  await act(async () => { vi.setSystemTime(new Date("2090-09-10T00:01:00Z")); focusManager.setFocused(false); focusManager.setFocused(true); });
  expect(await screen.findByRole("link", { name: "Google로 로그인" })).toBeInTheDocument();
  expect(server.calls.filter(call => call.method === "POST" && call.url.endsWith("/schedule-workspace/views"))).toHaveLength(0);
});
