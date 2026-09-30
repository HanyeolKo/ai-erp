import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { focusManager } from "@tanstack/react-query";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, test, vi } from "vitest";
import App from "./App";
import { civilDateBoundary } from "./time";
import { http, json, project, schedule } from "./test/http";

afterEach(() => { vi.unstubAllGlobals(); window.location.hash = ""; sessionStorage.clear(); focusManager.setFocused(undefined); });

const queryResult = (body: any, nextSchedule = schedule, hasMore = false) => json({ records: [{ schedule: nextSchedule, values: {} }], total: hasMore ? 101 : 1, hasMore, page: body.page, size: body.size, groups: [], queriedAt: "2090-11-05T00:00:00Z" });

test("Calendar recovery reuses the exact nondefault-zone explicit range and complete page traversal", async () => {
  const server = http();
  let phase: "normal" | "denied" | "recovered" = "normal";
  const config = { type: "CALENDAR", filters: [], sorts: [{ field: "startsAt", direction: "ASC" }], groupBy: null, legendBy: null, visibleFields: ["title", "startsAt", "endsAt", "status"] };
  server.on("POST", "/api/v1/projects/p1/schedule-workspace/query", body => {
    if (phase === "normal") return queryResult(body);
    if (body.page === 0) return queryResult(body, schedule, true);
    return phase === "denied" ? json({ code: "FORBIDDEN" }, 403) : queryResult(body);
  });
  window.location.hash = "#/projects/p1/schedules?view=builtin-calendar&page=0&date=2090-11-05&mode=week&zone=America%2FNew_York&after=2090-11-05&before=2090-11-06&text=Design";
  render(<App />);
  const user = userEvent.setup();
  await user.click(await screen.findByRole("button", { name: "보기 설정" }));
  const save = within(screen.getByRole("dialog", { name: "보기 설정" })).getByRole("button", { name: "보기 저장" });
  expect(screen.getByLabelText("표시 시간대")).toHaveValue("America/New_York");
  expect(screen.getByLabelText("날짜 이후")).toHaveValue("2090-11-05");
  expect(screen.getByLabelText("날짜 이전")).toHaveValue("2090-11-06");
  phase = "denied";
  await act(async () => { vi.setSystemTime(new Date("2090-11-05T00:01:00Z")); focusManager.setFocused(false); focusManager.setFocused(true); });
  await screen.findByText(/일정 접근 권한을 다시 확인해야 합니다/);
  expect(save).toBeDisabled();
  expect(screen.getByLabelText("표시 시간대")).toHaveValue("America/New_York");
  expect(new URLSearchParams(window.location.hash.split("?")[1]).get("zone")).toBe("America/New_York");
  const from = civilDateBoundary("2090-11-05", "America/New_York");
  const to = civilDateBoundary("2090-11-07", "America/New_York");
  const beforeFailedRecovery = server.calls.length;
  await user.click(screen.getByRole("button", { name: "접근 상태 다시 확인" }));
  await screen.findByText("접근 상태를 확인하지 못해 보호된 작업을 잠갔습니다.");
  const failedBodies = server.calls.slice(beforeFailedRecovery).filter(call => call.method === "POST" && call.url.endsWith("/schedule-workspace/query")).map(call => call.body);
  expect(failedBodies).toEqual([{ config, from, to, page: 0, size: 100 }, { config, from, to, page: 1, size: 100 }]);
  expect(save).toBeDisabled();
  phase = "recovered";
  const beforeSuccess = server.calls.length;
  await user.click(screen.getByRole("button", { name: "접근 상태 다시 확인" }));
  await waitFor(() => expect(save).toBeEnabled());
  const successBodies = server.calls.slice(beforeSuccess).filter(call => call.method === "POST" && call.url.endsWith("/schedule-workspace/query")).map(call => call.body);
  expect(successBodies).toEqual([{ config, from, to, page: 0, size: 100 }, { config, from, to, page: 1, size: 100 }]);
  expect(screen.getByLabelText("표시 시간대")).toHaveValue("America/New_York");
  expect(screen.getByLabelText("날짜 이후")).toHaveValue("2090-11-05");
  expect(screen.getByLabelText("날짜 이전")).toHaveValue("2090-11-06");
});

test("List time save preserves an untouched subminute instant exactly", async () => {
  const server = http();
  const precise = { ...schedule, startsAt: "2090-09-10T01:00:12.125Z", endsAt: "2090-09-10T02:00:37.250Z" };
  let write: any;
  server.on("POST", "/api/v1/projects/p1/schedule-workspace/query", body => queryResult(body, precise));
  server.on("PATCH", "/api/v1/projects/p1/schedules/s1", body => { write = body; return json({ ...precise, ...body, rowVersion: 4 }); });
  window.location.hash = "#/projects/p1/schedules?view=builtin-list&page=0";
  render(<App />);
  const user = userEvent.setup();
  await user.click(await screen.findByRole("button", { name: "시간 변경: Design review" }));
  const dialog = screen.getByRole("dialog", { name: "시간 변경" });
  fireEvent.change(within(dialog).getByLabelText("시작"), { target: { value: "2090-09-10T10:30" } });
  await user.click(within(dialog).getByRole("button", { name: "변경 저장" }));
  await waitFor(() => expect(write).toBeTruthy());
  expect(write.startsAt).toBe("2090-09-10T01:30:00.000Z");
  expect(write.endsAt).toBe("2090-09-10T02:00:37.250Z");
});

test.each([
  ["committed", true],
  ["uncommitted", false],
] as const)("List uncertain %s outcome is resolved without replay and requires fresh re-entry", async (_label, committed) => {
  const server = http();
  let intended: any;
  let writes = 0;
  server.on("PATCH", "/api/v1/projects/p1/schedules/s1", body => { writes += 1; intended = body; return json({ code: "TEMPORARY" }, 500); });
  server.on("GET", "/api/v1/projects/p1/schedules/s1", () => json(committed && intended ? { ...schedule, ...intended, rowVersion: 4 } : schedule));
  window.location.hash = "#/projects/p1/schedules?view=builtin-list&page=0";
  render(<App />);
  const user = userEvent.setup();
  await user.click(await screen.findByRole("button", { name: "시간 변경: Design review" }));
  const dialog = screen.getByRole("dialog", { name: "시간 변경" });
  fireEvent.change(within(dialog).getByLabelText("시작"), { target: { value: "2090-09-10T10:30" } });
  await user.click(within(dialog).getByRole("button", { name: "변경 저장" }));
  await waitFor(() => expect(screen.queryByRole("dialog", { name: "시간 변경" })).not.toBeInTheDocument());
  expect(writes).toBe(1);
  expect(await screen.findByRole("status")).toHaveTextContent(committed ? "이미 저장된 것을 확인했습니다" : "저장되지 않은 것을 확인했습니다");
  expect(writes).toBe(1);
});

test("List uncertain outcome locks writes when the fresh project role loses edit access", async () => {
  const server = http();
  let writes = 0;
  let roleLost = false;
  server.on("PATCH", "/api/v1/projects/p1/schedules/s1", () => { writes += 1; roleLost = true; return json({ code: "TEMPORARY" }, 500); });
  server.on("GET", "/api/v1/projects", () => json([{ ...project, role: roleLost ? "VIEWER" : "MANAGER" }]));
  window.location.hash = "#/projects/p1/schedules?view=builtin-list&page=0";
  render(<App />);
  const user = userEvent.setup();
  await user.click(await screen.findByRole("button", { name: "시간 변경: Design review" }));
  const dialog = screen.getByRole("dialog", { name: "시간 변경" });
  fireEvent.change(within(dialog).getByLabelText("시작"), { target: { value: "2090-09-10T10:30" } });
  await user.click(within(dialog).getByRole("button", { name: "변경 저장" }));
  await screen.findByText(/일정 접근 권한을 다시 확인해야 합니다/);
  expect(screen.queryByRole("button", { name: "시간 변경: Design review" })).not.toBeInTheDocument();
  expect(writes).toBe(1);
});

test("saving a filter from page two atomically writes page zero to the canonical URL", async () => {
  const server = http();
  server.on("POST", "/api/v1/projects/p1/schedule-workspace/views", body => json({ id: "filtered", ownerId: "u1", rowVersion: 1, archived: false, ...body }));
  window.location.hash = "#/projects/p1/schedules?view=builtin-list&page=2&text=Design";
  render(<App />);
  const user = userEvent.setup();
  await user.click(await screen.findByRole("button", { name: "보기 설정" }));
  const dialog = screen.getByRole("dialog", { name: "보기 설정" });
  await user.click(within(dialog).getByRole("button", { name: "필터 추가" }));
  fireEvent.change(within(dialog).getByLabelText("값"), { target: { value: "Design" } });
  await user.click(within(dialog).getByRole("button", { name: "보기 저장" }));
  await waitFor(() => expect(new URLSearchParams(window.location.hash.split("?")[1]).get("page")).toBe("0"));
  const write = server.calls.find(call => call.method === "POST" && call.url.endsWith("/schedule-workspace/views"));
  expect(write?.body.config.filters).toEqual([{ field: "title", operator: "CONTAINS", value: "Design" }]);
});

test("property writes stay locked through failed recovery and re-enable only after fresh project metadata and target reads", async () => {
  const server = http();
  const property = { id: "priority", name: "우선순위", type: "SINGLE_SELECT", position: 0, archived: false, rowVersion: 1, options: [{ id: "high", label: "높음", color: "red", archived: false }] };
  let denied = false;
  let propertyWrites = 0;
  server.on("GET", "/api/v1/projects/p1/schedule-workspace", () => json({ properties: [property], views: [], dashboardViewId: null, dashboardRowVersion: 0 }));
  server.on("POST", "/api/v1/projects/p1/schedule-workspace/query", body => denied ? json({ code: "FORBIDDEN" }, 403) : queryResult(body));
  server.on("PATCH", "/api/v1/projects/p1/schedule-workspace/properties/priority", body => { propertyWrites += 1; return json({ ...property, ...body, rowVersion: 2, options: property.options }); });
  window.location.hash = "#/projects/p1/schedules?view=builtin-list&page=0";
  render(<App />);
  const user = userEvent.setup();
  await user.click(await screen.findByRole("button", { name: "속성" }));
  const dialog = screen.getByRole("dialog", { name: "프로젝트 속성" });
  denied = true;
  await act(async () => { vi.setSystemTime(new Date("2090-09-10T00:01:00Z")); focusManager.setFocused(false); focusManager.setFocused(true); });
  await screen.findByText(/일정 접근 권한을 다시 확인해야 합니다/);
  const save = within(dialog).getByRole("button", { name: "저장" });
  expect(save).toBeDisabled();
  await user.click(screen.getByRole("button", { name: "접근 상태 다시 확인" }));
  await screen.findByText("접근 상태를 확인하지 못해 보호된 작업을 잠갔습니다.");
  expect(save).toBeDisabled(); expect(propertyWrites).toBe(0);
  denied = false;
  await user.click(screen.getByRole("button", { name: "접근 상태 다시 확인" }));
  await waitFor(() => expect(save).toBeEnabled());
  await user.click(save);
  await waitFor(() => expect(propertyWrites).toBe(1));
});

test("dashboard writes stay locked through failed recovery and use fresh values after successful recovery", async () => {
  const server = http();
  const property = { id: "notes", name: "메모", type: "TEXT", position: 0, archived: false, rowVersion: 1, options: [] };
  const view = { id: "shared", name: "공유 카드", scope: "SHARED", ownerId: "u1", rowVersion: 1, archived: false, config: { type: "CARDS", filters: [], sorts: [], groupBy: null, legendBy: null, visibleFields: ["title", "property:notes"] } };
  let denied = false;
  let version = 3;
  let value = "old";
  const writes: any[] = [];
  server.on("GET", "/api/v1/projects/p1/schedule-workspace", () => json({ properties: [property], views: [view], dashboardViewId: view.id, dashboardRowVersion: 1 }));
  server.on("POST", "/api/v1/projects/p1/schedule-workspace/query", body => denied ? json({ code: "FORBIDDEN" }, 403) : json({ records: [{ schedule: { ...schedule, rowVersion: version }, values: { notes: value } }], total: 1, hasMore: false, page: body.page, size: body.size, groups: [], queriedAt: "2090-09-10T00:00:00Z" }));
  server.on("PATCH", "/api/v1/projects/p1/schedule-workspace/records/s1/values", body => { writes.push(body); return json({ schedule: { ...schedule, rowVersion: version + 1 }, values: body.values }); });
  window.location.hash = "#/projects/p1";
  render(<App />);
  const user = userEvent.setup();
  await user.click(await screen.findByRole("button", { name: "속성 바로 수정" }));
  denied = true;
  await act(async () => { vi.setSystemTime(new Date("2090-09-10T00:01:00Z")); focusManager.setFocused(false); focusManager.setFocused(true); });
  await screen.findByText(/대시보드 접근 권한을 다시 확인해야 합니다/);
  expect(screen.queryByRole("button", { name: "속성 저장" })).not.toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "접근 상태 다시 확인" }));
  await screen.findByText("접근 상태를 확인하지 못해 보호된 대시보드 작업을 잠갔습니다.");
  expect(writes).toHaveLength(0);
  denied = false; version = 5; value = "fresh";
  await user.click(screen.getByRole("button", { name: "접근 상태 다시 확인" }));
  await screen.findByText("접근 상태를 다시 확인했습니다.");
  await user.click(await screen.findByRole("button", { name: "속성 바로 수정" }));
  const input = screen.getByLabelText("메모");
  expect(input).toHaveValue("fresh");
  await user.clear(input); await user.type(input, "saved");
  await user.click(screen.getByRole("button", { name: "속성 저장" }));
  await waitFor(() => expect(writes).toEqual([{ rowVersion: 5, values: { notes: "saved" } }]));
});
