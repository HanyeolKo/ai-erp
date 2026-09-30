import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { focusManager } from "@tanstack/react-query";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, test, vi } from "vitest";
import App from "./App";
import { http, json, schedule } from "./test/http";

afterEach(() => {
  vi.unstubAllGlobals();
  window.location.hash = "";
  sessionStorage.clear();
  focusManager.setFocused(undefined);
});

test("workspace views query typed property filters and expose real card records", async () => {
  const server = http();
  const workspace = {
    properties: [{ id: "priority", name: "우선순위", type: "SINGLE_SELECT", position: 0, archived: false, rowVersion: 1, options: [{ id: "high", label: "높음", color: "red", archived: false }] }],
    views: [], dashboardViewId: null, dashboardRowVersion: 0,
  };
  server.on("GET", "/api/v1/projects/p1/schedule-workspace", () => json(workspace));
  let lastQuery: any;
  server.on("POST", "/api/v1/projects/p1/schedule-workspace/query", body => {
    lastQuery = body;
    return json({ records: [{ schedule, values: { priority: "high" } }], total: 1, hasMore: false, page: 0, size: 20, groups: [], queriedAt: "2090-09-10T00:00:00Z" });
  });
  let lastView: any;
  server.on("POST", "/api/v1/projects/p1/schedule-workspace/views", body => { lastView = body; return json({ id: "view-high", name: body.name, scope: "PERSONAL", ownerId: "u1", rowVersion: 1, archived: false, config: body.config }); });
  window.location.hash = "#/projects/p1/schedules";
  render(<App />);
  const user = userEvent.setup();
  await screen.findByRole("heading", { name: "프로젝트 일정" });
  await user.click(screen.getByRole("button", { name: "카드 보기" }));
  expect(await screen.findByRole("link", { name: "Design review" })).toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "보기 설정" }));
  const dialog = screen.getByRole("dialog");
  await user.click(within(dialog).getByRole("button", { name: "필터 추가" }));
  const fields = within(dialog).getAllByLabelText("필드");
  await user.selectOptions(fields[0], "property:priority");
  await user.selectOptions(within(dialog).getByLabelText("연산자"), "EQ");
  await user.selectOptions(within(dialog).getByLabelText("값"), "high");
  expect(within(dialog).getByRole("button", { name: "보기 저장" })).toBeEnabled();
  await user.click(within(dialog).getByRole("button", { name: "보기 저장" }));
  expect(lastView.config.filters).toEqual([{ field: "property:priority", operator: "EQ", value: "high" }]);
  expect(lastQuery.config.type).toBe("CARDS");
});

test("workspace restores validated view context from browser history and announces a missing-view fallback", async () => {
  http();
  window.location.hash = "#/projects/p1/schedules?view=builtin-cards&page=0";
  render(<App />);
  expect(await screen.findByRole("button", { name: "카드 보기" })).toHaveAttribute("aria-pressed", "true");
  window.location.hash = "#/projects/p1/schedules?view=builtin-list&page=0";
  window.dispatchEvent(new HashChangeEvent("hashchange"));
  await waitFor(() => expect(screen.getByRole("button", { name: "목록 보기" })).toHaveAttribute("aria-pressed", "true"));
  window.location.hash = "#/projects/p1/schedules?view=removed-view&page=-8";
  window.dispatchEvent(new HashChangeEvent("hashchange"));
  expect(await screen.findByText("선택한 보기를 사용할 수 없어 기본 캘린더로 이동했습니다.")).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "캘린더 보기" })).toHaveAttribute("aria-pressed", "true");
  expect(window.location.hash).toContain("page=0");
});

test("single-select option IDs and versions remain stable across two saves", async () => {
  const server = http();
  const property = { id: "priority", name: "우선순위", type: "SINGLE_SELECT", position: 0, archived: false, rowVersion: 1, options: [{ id: "high", label: "높음", color: "red", archived: false }] };
  server.on("GET", "/api/v1/projects/p1/schedule-workspace", () => json({ properties: [property], views: [], dashboardViewId: null, dashboardRowVersion: 0 }));
  const writes: any[] = [];
  server.on("PATCH", "/api/v1/projects/p1/schedule-workspace/properties/priority", body => {
    writes.push(body);
    const options = body.options.map((option: any, index: number) => ({ ...option, id: option.id ?? `server-${index}` }));
    return json({ ...property, rowVersion: body.rowVersion + 1, options });
  });
  window.location.hash = "#/projects/p1/schedules";
  render(<App />);
  const user = userEvent.setup();
  await user.click(await screen.findByRole("button", { name: "속성" }));
  const dialog = screen.getByRole("dialog");
  await user.click(within(dialog).getByRole("button", { name: "옵션 추가" }));
  const labels = within(dialog).getAllByLabelText("레이블");
  await user.clear(labels[1]); await user.type(labels[1], "중간");
  await user.click(within(dialog).getByRole("button", { name: "옵션 저장" }));
  await waitFor(() => expect(writes).toHaveLength(1));
  const savedLabels = within(dialog).getAllByLabelText("레이블");
  await user.clear(savedLabels[1]); await user.type(savedLabels[1], "보통");
  await user.click(within(dialog).getByRole("button", { name: "옵션 저장" }));
  await waitFor(() => expect(writes).toHaveLength(2));
  expect(writes[0].rowVersion).toBe(1);
  expect(writes[0].options[1].id).toBeUndefined();
  expect(writes[1]).toMatchObject({ rowVersion: 2, options: [{ id: "high" }, { id: "server-1", label: "보통" }] });
});

test("nested option dirty close uses an accessible discard dialog and preserves add and rename drafts while editing continues", async () => {
  const server = http();
  server.on("GET", "/api/v1/projects/p1/schedule-workspace", () => json({ properties: [{ id: "priority", name: "우선순위", type: "SINGLE_SELECT", position: 0, archived: false, rowVersion: 1, options: [{ id: "high", label: "높음", color: "red", archived: false }, { id: "low", label: "낮음", color: "blue", archived: false }] }], views: [], dashboardViewId: null, dashboardRowVersion: 0 }));
  window.location.hash = "#/projects/p1/schedules";
  render(<App />);
  const user = userEvent.setup();
  await user.click(await screen.findByRole("button", { name: "속성" }));
  const propertyDialog = screen.getByRole("dialog", { name: "프로젝트 속성" });
  const originalLabels = within(propertyDialog).getAllByLabelText("레이블");
  await user.clear(originalLabels[0]); await user.type(originalLabels[0], "매우 높음");
  await user.click(within(propertyDialog).getByRole("button", { name: "옵션 추가" }));
  const labels = within(propertyDialog).getAllByLabelText("레이블");
  await user.clear(labels[2]); await user.type(labels[2], "보통");
  await user.click(within(propertyDialog).getAllByRole("button", { name: "위로" })[2]);
  await user.click(within(screen.getByRole("dialog")).getAllByRole("button", { name: "닫기" })[1]);
  expect(screen.getByRole("dialog", { name: "변경사항 버리기" })).toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "계속 편집" }));
  const continued = within(screen.getByRole("dialog", { name: "프로젝트 속성" })).getAllByLabelText("레이블");
  expect(continued.map(input => (input as HTMLInputElement).value)).toEqual(["매우 높음", "보통", "낮음"]);
  await user.click(within(screen.getByRole("dialog", { name: "프로젝트 속성" })).getAllByRole("button", { name: "닫기" })[1]);
  await user.click(screen.getByRole("button", { name: "변경사항 버리기" }));
  await user.click(screen.getByRole("button", { name: "속성" }));
  const restored = within(screen.getByRole("dialog", { name: "프로젝트 속성" })).getAllByLabelText("레이블");
  expect(restored.map(input => (input as HTMLInputElement).value)).toEqual(["높음", "낮음"]);
});

test("calendar event detail and list return preserve the active workspace context", async () => {
  http();
  window.location.hash = "#/projects/p1/schedules?view=builtin-calendar&date=2090-09-10&mode=week&page=0";
  render(<App />);
  const detailLinks = await screen.findAllByRole("link", { name: /Design review/ });
  expect(detailLinks).toHaveLength(2);
  for (const link of detailLinks) expect(link.getAttribute("href")).toContain("return=view%3Dbuiltin-calendar%26date%3D2090-09-10%26mode%3Dweek%26page%3D0");
  expect(screen.getByRole("link", { name: "일정 만들기" }).getAttribute("href")).toContain("return=view%3Dbuiltin-calendar%26date%3D2090-09-10%26mode%3Dweek%26page%3D0");
  const event = detailLinks.find(link => link.classList.contains("week-event-link"))!;
  expect(event.getAttribute("href")).toContain("return=view%3Dbuiltin-calendar%26date%3D2090-09-10%26mode%3Dweek%26page%3D0");
  await userEvent.setup().click(event);
  const back = await screen.findByRole("link", { name: "일정 목록" });
  expect(back.getAttribute("href")).toContain("view=builtin-calendar&date=2090-09-10&mode=week&page=0");
});

test("closing a workspace dialog with Escape restores focus to its caller", async () => {
  http();
  window.location.hash = "#/projects/p1/schedules";
  render(<App />);
  const user = userEvent.setup();
  const trigger = await screen.findByRole("button", { name: "속성" });
  await user.click(trigger);
  expect(screen.getByRole("dialog", { name: "프로젝트 속성" })).toBeInTheDocument();
  fireEvent(screen.getByRole("dialog", { name: "프로젝트 속성" }), new Event("cancel", { cancelable: true }));
  await waitFor(() => expect(screen.queryByRole("dialog", { name: "프로젝트 속성" })).not.toBeInTheDocument());
  await waitFor(() => expect(trigger).toHaveFocus());
});

test("calendar marks a ten-page overlapping result partial even when duplicate IDs stay below the cap", async () => {
  const server = http();
  let pages = 0;
  server.on("POST", "/api/v1/projects/p1/schedule-workspace/query", body => {
    pages += 1;
    return json({ records: [{ schedule, values: {} }], total: 1001, hasMore: true, page: body.page, size: 100, groups: [], queriedAt: "2090-09-10T00:00:00Z" });
  });
  window.location.hash = "#/projects/p1/schedules?view=builtin-calendar&date=2090-09-10&mode=month&page=0";
  render(<App />);
  expect(await screen.findByRole("alert")).toHaveTextContent("추가 일정 페이지를 불러오지 못했습니다");
  expect(pages).toBe(10);
  expect(screen.getAllByRole("link", { name: /Design review/ })).toHaveLength(2);
});

test.each([
  ["MEMBER", "POST", "PERSONAL"],
  ["MANAGER", "PATCH", "SHARED"],
] as const)("%s saves a shared view with the authorized immutable scope", async (role, method, scope) => {
  const server = http();
  const shared = { id: "shared-1", name: "공유 보기", scope: "SHARED" as const, ownerId: "u1", rowVersion: 2, archived: false, config: { type: "LIST" as const, filters: [], sorts: [], groupBy: null, legendBy: null, visibleFields: ["title" as const] } };
  server.on("GET", "/api/v1/projects", () => json([{ id: "p1", groupId: "g1", name: "Planning", role }]));
  server.on("GET", "/api/v1/projects/p1/schedule-workspace", () => json({ properties: [], views: [shared], dashboardViewId: null, dashboardRowVersion: 0 }));
  const endpoint = method === "POST" ? "/api/v1/projects/p1/schedule-workspace/views" : "/api/v1/projects/p1/schedule-workspace/views/shared-1";
  server.on(method, endpoint, body => json({ ...shared, id: method === "POST" ? "personal-copy" : shared.id, name: body.name, scope: body.scope, rowVersion: 3 }));
  window.location.hash = "#/projects/p1/schedules?view=shared-1&page=0";
  render(<App />);
  const user = userEvent.setup();
  await user.click(await screen.findByRole("button", { name: "보기 설정" }));
  const dialog = screen.getByRole("dialog");
  const name = within(dialog).getByLabelText("이름");
  await user.clear(name); await user.type(name, "변경한 보기");
  await user.click(within(dialog).getByRole("button", { name: "보기 저장" }));
  await waitFor(() => expect(server.calls.some(call => call.method === method && call.url === endpoint && call.body.scope === scope)).toBe(true));
});

test("fractional number filters keep step any and submit a finite decimal", async () => {
  const server = http();
  const property = { id: "estimate", name: "예상치", type: "NUMBER", position: 0, archived: false, rowVersion: 1, options: [] };
  server.on("GET", "/api/v1/projects/p1/schedule-workspace", () => json({ properties: [property], views: [], dashboardViewId: null, dashboardRowVersion: 0 }));
  server.on("POST", "/api/v1/projects/p1/schedule-workspace/views", body => json({ id: "decimal-view", name: body.name, scope: body.scope, ownerId: "u1", rowVersion: 1, archived: false, config: body.config }));
  window.location.hash = "#/projects/p1/schedules?view=builtin-list&page=0";
  render(<App />);
  const user = userEvent.setup();
  await user.click(await screen.findByRole("button", { name: "보기 설정" }));
  const dialog = screen.getByRole("dialog");
  await user.click(within(dialog).getByRole("button", { name: "필터 추가" }));
  await user.selectOptions(within(dialog).getByLabelText("필드"), "property:estimate");
  const value = within(dialog).getByLabelText("값");
  expect(value).toHaveAttribute("step", "any");
  fireEvent.change(value, { target: { value: "1.25" } });
  await user.click(within(dialog).getByRole("button", { name: "보기 저장" }));
  await waitFor(() => expect(server.calls.some(call => call.method === "POST" && call.url.endsWith("/schedule-workspace/views") && call.body.config.filters[0].value === 1.25)).toBe(true));
});

test("list time action opens the shared time flow and preserves the workspace return context", async () => {
  const server = http();
  server.on("PATCH", "/api/v1/projects/p1/schedules/s1", body => {
    expect(body).toEqual({ title: "Design review", description: "Keep description", startsAt: "2090-09-10T02:00:00.000Z", endsAt: "2090-09-10T03:00:00.000Z", rowVersion: 3 });
    return json({ ...schedule, ...body, rowVersion: 4 });
  });
  window.location.hash = "#/projects/p1/schedules?view=builtin-list&page=0";
  render(<App />);
  const user = userEvent.setup();
  const region = await screen.findByRole("region", { name: "일정 목록" });
  expect(within(region).getByRole("link", { name: "일정 편집" })).toHaveAttribute("href", "#/projects/p1/schedules/s1/edit?return=view%3Dbuiltin-list%26page%3D0");
  await user.click(within(region).getByRole("button", { name: "시간 변경: Design review" }));
  const dialog = await screen.findByRole("dialog", { name: "시간 변경" });
  fireEvent.change(within(dialog).getByLabelText("시작"), { target: { value: "2090-09-10T11:00" } });
  fireEvent.change(within(dialog).getByLabelText("종료"), { target: { value: "2090-09-10T12:00" } });
  await user.click(within(dialog).getByRole("button", { name: "변경 저장" }));
  await waitFor(() => expect(server.calls.some(call => call.method === "PATCH" && call.url === "/api/v1/projects/p1/schedules/s1")).toBe(true));
});

test("dashboard clean refetch adopts server values while dirty conflict preserves the draft until cancel", async () => {
  const server = http();
  const property = { id: "notes", name: "메모", type: "TEXT", position: 0, archived: false, rowVersion: 1, options: [] };
  const view = { id: "shared-cards", name: "공유 카드", scope: "SHARED", ownerId: "u1", rowVersion: 1, archived: false, config: { type: "CARDS", filters: [], sorts: [], groupBy: null, legendBy: null, visibleFields: ["title", "property:notes"] } };
  let externalValue = "alpha";
  let externalVersion = 3;
  const valueWrites: any[] = [];
  server.on("GET", "/api/v1/projects/p1/schedule-workspace", () => json({ properties: [property], views: [view], dashboardViewId: view.id, dashboardRowVersion: 1 }));
  server.on("POST", "/api/v1/projects/p1/schedule-workspace/query", body => json({ records: [{ schedule: { ...schedule, rowVersion: externalVersion }, values: { notes: externalValue } }], total: 1, hasMore: false, page: body.page, size: body.size, groups: [], queriedAt: "2090-09-10T00:00:00Z" }));
  server.on("PATCH", "/api/v1/projects/p1/schedule-workspace/records/s1/values", body => { valueWrites.push(body); return valueWrites.length === 1 ? json({ code: "ROW_VERSION_CONFLICT" }, 409) : json({ schedule: { ...schedule, rowVersion: 6 }, values: body.values }); });
  window.location.hash = "#/projects/p1";
  render(<App />);
  const user = userEvent.setup();
  await user.click(await screen.findByRole("button", { name: "속성 바로 수정" }));
  const input = screen.getByLabelText("메모");
  expect(input).toHaveValue("alpha");

  externalValue = "beta"; externalVersion = 4;
  await act(async () => { vi.setSystemTime(new Date("2090-09-10T00:01:00Z")); focusManager.setFocused(false); focusManager.setFocused(true); });
  await waitFor(() => expect(input).toHaveValue("beta"));

  await user.clear(input); await user.type(input, "mine");
  externalValue = "gamma"; externalVersion = 5;
  await act(async () => { vi.setSystemTime(new Date("2090-09-10T00:02:00Z")); focusManager.setFocused(false); focusManager.setFocused(true); });
  await waitFor(() => expect(server.calls.filter(call => call.method === "POST" && call.url === "/api/v1/projects/p1/schedule-workspace/query").length).toBeGreaterThan(2));
  expect(input).toHaveValue("mine");

  await user.click(screen.getByRole("button", { name: "속성 저장" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("입력한 초안을 유지했습니다");
  expect(screen.getByRole("button", { name: "최신 값 다시 불러오기" })).toBeInTheDocument();
  expect(input).toHaveValue("mine");
  await user.click(screen.getByRole("button", { name: "취소" }));
  await user.click(screen.getByRole("button", { name: "속성 바로 수정" }));
  const latestInput = screen.getByLabelText("메모");
  expect(latestInput).toHaveValue("gamma");
  await user.clear(latestInput); await user.type(latestInput, "final");
  await user.click(screen.getByRole("button", { name: "속성 저장" }));
  await waitFor(() => expect(valueWrites).toHaveLength(2));
  expect(valueWrites[1]).toEqual({ rowVersion: 5, values: { notes: "final" } });
});
