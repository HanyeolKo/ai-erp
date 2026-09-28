import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, test, vi } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import App from "./App";
import { http, json } from "./test/http";
import { ItemBadge, ProjectTargetEditor } from "./screens/ProjectPlan";

const summary = { taskCount: 1, doneCount: 0, blockedCount: 0, overdueCount: 0, unplannedCount: 0, unassignedCount: 0, progressPercent: 0, forecastStart: "2090-09-10", forecastEnd: "2090-09-10", forecastState: "COMPLETE" as const, outsideTarget: false };
const snapshot = { projectId: "p1", rowVersion: 1, targetStart: "2090-09-01", targetEnd: "2090-09-30", asOfDate: "2090-09-10", complete: true, totalCount: 1, matchedIds: ["task-1"], summary, items: [{ id: "task-1", projectId: "p1", parentId: null, kind: "TASK" as const, title: "검토 작업", description: null, assigneeId: "u1", state: "READY" as const, targetStart: "2090-09-10", targetEnd: "2090-09-10", deadline: null, sortOrder: 0, labels: [], rowVersion: 1, predecessorIds: [], successorIds: [], blockerIds: [], summary }] };

afterEach(() => { vi.restoreAllMocks(); window.location.hash = ""; sessionStorage.clear(); });

function mount(path: string, configure?: (server: ReturnType<typeof http>) => void) {
  const api = http();
  api.on("GET", "/api/v1/projects/p1/plan", () => json(snapshot));
  configure?.(api);
  window.location.hash = `#${path}`;
  render(<App />);
  return { user: userEvent.setup(), api };
}

test("legacy Plan kind is visible and removable through the common kind control", async () => {
  const { user } = mount("/projects/p1/plan?view=roadmap&kind=TOPIC");
  const kind = await screen.findByRole("combobox", { name: "종류" });
  expect(kind).toHaveValue("TOPIC");
  await user.click(screen.getByRole("button", { name: /종류: TOPIC/ }));
  expect(window.location.hash).not.toContain("kind=");
});

test("mobile Plan view control exposes all six native view choices", async () => {
  mount("/projects/p1/plan?view=tasks");
  const view = await screen.findByRole("combobox", { name: "계획 보기" });
  expect(view).toHaveValue("tasks");
  expect(view.querySelectorAll("option")).toHaveLength(6);
});

test("dirty Plan editor keeps its draft when explicit close is declined", async () => {
  const { user } = mount("/projects/p1/plan?view=tasks");
  const confirm = vi.spyOn(window, "confirm").mockReturnValue(false);
  await user.click(await screen.findByRole("button", { name: "항목 추가" }));
  await user.type(screen.getByRole("textbox", { name: "제목" }), "보존할 초안");
  await user.click(screen.getByRole("button", { name: "취소" }));
  expect(confirm).toHaveBeenCalledOnce();
  expect(screen.getByDisplayValue("보존할 초안")).toBeInTheDocument();
});

test("search debounce merges the current URL instead of restoring stale filters", async () => {
  const { user } = mount("/projects/p1/plan?view=roadmap");
  const search = await screen.findByRole("textbox", { name: "검색" });
  expect(search).toHaveAccessibleName("검색");
  await user.type(search, "검토");
  await user.selectOptions(screen.getByRole("combobox", { name: "상태" }), "READY");
  await waitFor(() => expect(window.location.hash).toContain("q=%EA%B2%80%ED%86%A0"), { timeout: 800 });
  expect(window.location.hash).toContain("state=READY");
});

test("MILESTONE can keep an EPIC or TOPIC parent", async () => {
  const epic = { ...snapshot.items[0], id: "epic-1", kind: "EPIC" as const, title: "상위 EPIC", assigneeId: null };
  const topic = { ...snapshot.items[0], id: "topic-1", parentId: "epic-1", kind: "TOPIC" as const, title: "상위 Topic", assigneeId: null };
  const nested = { ...snapshot, totalCount: 3, matchedIds: ["epic-1", "topic-1", "task-1"], items: [epic, topic, snapshot.items[0]] };
  const { user } = mount("/projects/p1/plan?view=milestones", api => api.on("GET", "/api/v1/projects/p1/plan", () => json(nested)));
  await user.click(await screen.findByRole("button", { name: "항목 추가" }));
  const dialog = screen.getByRole("dialog");
  await user.selectOptions(within(dialog).getByRole("combobox", { name: "종류" }), "MILESTONE");
  const parent = within(dialog).getByRole("combobox", { name: "상위 항목" });
  expect(parent).toBeEnabled();
  expect(Array.from(parent.querySelectorAll("option"), option => option.value)).toEqual(["", "epic-1", "topic-1"]);
  await user.selectOptions(parent, "topic-1");
  expect(parent).toHaveValue("topic-1");
});

test("target editor uses a captured baseline and accepted discard restores it", async () => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  const current = { ...snapshot, rowVersion: 2, targetStart: "2090-10-01", targetEnd: "2090-10-31" };
  const view = render(<QueryClientProvider client={client}><ProjectTargetEditor projectId="p1" snapshot={snapshot} canEdit onForbidden={vi.fn()} /></QueryClientProvider>);
  const user = userEvent.setup();
  await user.click(screen.getByRole("button", { name: "목표일 편집" }));
  view.rerender(<QueryClientProvider client={client}><ProjectTargetEditor projectId="p1" snapshot={current} canEdit onForbidden={vi.fn()} /></QueryClientProvider>);
  expect(screen.getByLabelText("시작일")).toHaveValue("2090-09-01");
  expect(screen.getByLabelText("종료일")).toHaveValue("2090-09-30");
  const confirm = vi.spyOn(window, "confirm").mockReturnValue(true);
  await user.click(screen.getByRole("button", { name: "닫기" }));
  expect(confirm).not.toHaveBeenCalled();
  await user.click(screen.getByRole("button", { name: "목표일 편집" }));
  expect(screen.getByLabelText("시작일")).toHaveValue("2090-10-01");
  await user.clear(screen.getByLabelText("시작일"));
  await user.type(screen.getByLabelText("시작일"), "2090-11-01");
  await user.click(screen.getByRole("button", { name: "취소" }));
  expect(confirm).toHaveBeenCalledOnce();
  await user.click(screen.getByRole("button", { name: "목표일 편집" }));
  expect(screen.getByLabelText("시작일")).toHaveValue("2090-10-01");
});

test("server validation for a secondary field reveals its controls and error", async () => {
  const { user } = mount("/projects/p1/plan?view=tasks", api => api.on("POST", "/api/v1/projects/p1/plan/items", () => json({ code: "VALIDATION_FAILED", fieldErrors: [{ field: "labels[0]", message: "레이블이 너무 깁니다." }] }, 400)));
  await user.click(await screen.findByRole("button", { name: "항목 추가" }));
  await user.type(screen.getByRole("textbox", { name: "제목" }), "검증할 항목");
  await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "저장" }));
  expect(await screen.findByText(/레이블이 너무 깁니다/)).toBeInTheDocument();
  expect(screen.getByRole("dialog").querySelector(".plan-secondary-fields")).toHaveAttribute("open");
  expect(screen.getByRole("textbox", { name: "레이블" })).toBeVisible();
});

test("legacy kind participates in full filter reset", async () => {
  const { user } = mount("/projects/p1/plan?view=roadmap&kind=TOPIC");
  await screen.findByRole("combobox", { name: "종류" });
  await user.click(screen.getByRole("button", { name: "전체 초기화" }));
  expect(window.location.hash).not.toContain("kind=");
});

test("assigned item with an unresolved member name is distinct from an unassigned item", () => {
  const assigned = { ...snapshot.items[0], assigneeName: null };
  const unassigned = { ...snapshot.items[0], id: "task-2", assigneeId: null, assigneeName: null };
  const view = render(<><ItemBadge item={assigned} /><ItemBadge item={unassigned} /></>);
  expect(view.getByText("이름 확인 불가 (u1)")).toBeInTheDocument();
  expect(view.getByText("미배정")).toBeInTheDocument();
});

test("calendar distinguishes a target-range date from a different deadline", async () => {
  const datedItem = { ...snapshot.items[0], targetStart: "2090-09-10", targetEnd: "2090-09-10", deadline: "2090-09-11" };
  const datedSnapshot = { ...snapshot, items: [datedItem] };
  mount("/projects/p1/plan?view=calendar&month=2090-09", api => api.on("GET", "/api/v1/projects/p1/plan", () => json(datedSnapshot)));
  await screen.findAllByRole("button", { name: /검토 작업/ });
  const targetDay = document.querySelector('time[datetime="2090-09-10"]')?.parentElement;
  const deadlineDay = document.querySelector('time[datetime="2090-09-11"]')?.parentElement;
  expect(targetDay).not.toBeNull();
  expect(deadlineDay).not.toBeNull();
  expect(within(targetDay!).getByText("목표 기간")).toBeVisible();
  expect(within(deadlineDay!).getByText("마감")).toBeVisible();
});
