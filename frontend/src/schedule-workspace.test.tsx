import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, test, vi } from "vitest";
import App from "./App";
import { http, json, schedule } from "./test/http";

afterEach(() => {
  vi.unstubAllGlobals();
  window.location.hash = "";
  sessionStorage.clear();
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
  await user.click(screen.getByRole("button", { name: "카드" }));
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
  expect(await screen.findByRole("button", { name: "카드" })).toHaveAttribute("aria-pressed", "true");
  window.location.hash = "#/projects/p1/schedules?view=builtin-list&page=0";
  window.dispatchEvent(new HashChangeEvent("hashchange"));
  await waitFor(() => expect(screen.getByRole("button", { name: "목록" })).toHaveAttribute("aria-pressed", "true"));
  window.location.hash = "#/projects/p1/schedules?view=removed-view&page=-8";
  window.dispatchEvent(new HashChangeEvent("hashchange"));
  expect(await screen.findByText("선택한 보기를 사용할 수 없어 기본 캘린더로 이동했습니다.")).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "캘린더" })).toHaveAttribute("aria-pressed", "true");
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

test("property dirty close uses an accessible discard dialog and preserves the draft while editing continues", async () => {
  const server = http();
  server.on("GET", "/api/v1/projects/p1/schedule-workspace", () => json({ properties: [{ id: "notes", name: "메모", type: "TEXT", position: 0, archived: false, rowVersion: 1, options: [] }], views: [], dashboardViewId: null, dashboardRowVersion: 0 }));
  window.location.hash = "#/projects/p1/schedules";
  render(<App />);
  const user = userEvent.setup();
  await user.click(await screen.findByRole("button", { name: "속성" }));
  const name = within(screen.getByRole("dialog")).getByLabelText("이름");
  await user.clear(name); await user.type(name, "회의 메모");
  await user.click(within(screen.getByRole("dialog")).getAllByRole("button", { name: "닫기" })[1]);
  expect(screen.getByRole("dialog", { name: "변경사항 버리기" })).toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "계속 편집" }));
  expect(within(screen.getByRole("dialog", { name: "프로젝트 속성" })).getByLabelText("이름")).toHaveValue("회의 메모");
});

test("calendar event detail and list return preserve the active workspace context", async () => {
  http();
  window.location.hash = "#/projects/p1/schedules?view=builtin-calendar&date=2090-09-10&mode=week&page=0";
  render(<App />);
  const detailLinks = await screen.findAllByRole("link", { name: /Design review/ });
  expect(detailLinks).toHaveLength(3);
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
