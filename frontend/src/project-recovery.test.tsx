import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, test, vi } from "vitest";
import { focusManager } from "@tanstack/react-query";
import App from "./App";
import { http, json, project, schedule } from "./test/http";

afterEach(() => { vi.unstubAllGlobals(); window.location.hash = ""; sessionStorage.clear(); focusManager.setFocused(undefined); });
const mount = (path: string) => { window.location.hash = `#${path}`; render(<App />); return userEvent.setup(); };

test.each(["/projects/missing", "/projects/missing/schedules", "/projects/missing/schedules/new", "/projects/missing/schedules/s1", "/projects/missing/schedules/s1/edit"])("missing project exposes a real membership retry without child requests: %s", async path => {
  const server = http(); server.on("GET", "/api/v1/projects", () => json([]));
  const user = mount(path);
  expect(await screen.findByText(/선택한 프로젝트가 삭제되었거나/)).toBeInTheDocument();
  expect(server.calls.some(c => c.url.startsWith("/api/v1/projects/missing/"))).toBe(false);
  server.on("GET", "/api/v1/projects", () => json([{ ...project, id: "missing" }]));
  if (path === "/projects/missing") server.on("GET", "/api/v1/projects/missing/dashboard", () => json({ projectId: "missing", memberCount: 0, scheduleCount: 0, pendingAcknowledgementCount: 0, calendarRiskCount: 0, upcomingSchedules: [], actionQueue: [] }));
  // Other destinations keep their retry actionable without making unrelated fixture requests.
  if (path !== "/projects/missing") return;
  await user.click(screen.getByRole("button", { name: "접근 상태 다시 확인" }));
  expect(await screen.findByRole("heading", { name: "Planning 대시보드" })).toBeInTheDocument();
});

test("project query retry stays disabled while one retry is in flight", async () => {
  const server = http(); server.on("GET", "/api/v1/projects", () => json({ code: "UPSTREAM" }, 500));
  const user = mount("/projects/p1");
  expect(await screen.findByRole("alert")).toHaveTextContent(/불러오지 못|처리하지 못/);
  let complete!: (value: Response) => void;
  server.on("GET", "/api/v1/projects", () => new Promise(resolve => { complete = resolve; }));
  await user.click(screen.getByRole("button", { name: "다시 시도" }));
  const retry = screen.getByRole("button", { name: "다시 시도" });
  expect(retry).toBeDisabled(); await user.click(retry);
  expect(server.calls.filter(c => c.url.startsWith("/api/v1/projects?"))).toHaveLength(2);
  await act(async () => complete(json([])));
  expect(await screen.findByText(/선택한 프로젝트가 삭제되었거나/)).toBeInTheDocument();
});

test("dashboard 403 overrides cached Manager capability and offers project selection", async () => {
  const server = http(); server.on("GET", "/api/v1/projects/p1/dashboard", () => json({ code: "FORBIDDEN" }, 403));
  mount("/projects/p1");
  expect(await screen.findByRole("alert")).toHaveTextContent(/접근 권한/);
  expect(screen.queryByRole("link", { name: "일정 만들기" })).not.toBeInTheDocument();
  expect(screen.queryByRole("link", { name: "구성원 초대" })).not.toBeInTheDocument();
  expect(within(screen.getByRole("navigation")).getByRole("link", { name: "프로젝트 선택" })).toHaveAttribute("href", "#/");
});

test("schedule 404 provides schedule-list recovery without claiming project deletion", async () => {
  const server = http(); server.on("GET", "/api/v1/projects/p1/schedules/s1", () => json({ code: "NOT_FOUND" }, 404));
  const user = mount("/projects/p1/schedules/s1");
  expect(await screen.findByRole("alert")).toHaveTextContent(/찾을 수 없/);
  expect(screen.queryByText(/선택한 프로젝트가 삭제되었거나/)).not.toBeInTheDocument();
  await user.click(screen.getByRole("link", { name: "일정 목록으로" }));
  expect(await screen.findByRole("heading", { name: "프로젝트 일정" })).toBeInTheDocument();
});

test("background list 403 removes previously displayed schedule and create action", async () => {
  const server = http(); mount("/projects/p1/schedules");
  await screen.findByRole("link", { name: "Design review" });
  server.on("GET", "/api/v1/projects/p1/schedules", () => json({ code: "FORBIDDEN" }, 403));
  await act(async () => { vi.setSystemTime(new Date("2090-09-10T00:01:00Z")); focusManager.setFocused(false); focusManager.setFocused(true); });
  expect(await screen.findByRole("alert")).toHaveTextContent(/접근 권한/);
  expect(screen.queryByRole("link", { name: "Design review" })).not.toBeInTheDocument();
  expect(screen.queryByRole("link", { name: "일정 만들기" })).not.toBeInTheDocument();
});

test("a failed retry cannot restore capabilities after a list access denial", async () => {
  const server = http(); const user = mount("/projects/p1/schedules");
  await screen.findByRole("link", { name: "Design review" });
  server.on("GET", "/api/v1/projects/p1/schedules", () => json({ code: "FORBIDDEN" }, 403));
  await act(async () => { vi.setSystemTime(new Date("2090-09-10T00:01:00Z")); focusManager.setFocused(false); focusManager.setFocused(true); });
  await screen.findByText("FORBIDDEN");
  server.on("GET", "/api/v1/projects/p1/schedules", () => json({ code: "UPSTREAM_RETRY_FAILED" }, 500));
  await user.click(screen.getByRole("button", { name: "다시 시도" }));
  await screen.findByText("UPSTREAM_RETRY_FAILED");
  expect(screen.queryByRole("link", { name: "Design review" })).not.toBeInTheDocument();
  expect(screen.queryByRole("link", { name: "일정 만들기" })).not.toBeInTheDocument();
  server.on("GET", "/api/v1/projects/p1/schedules", () => json([schedule]));
  await user.click(screen.getByRole("button", { name: "다시 시도" }));
  expect(await screen.findByRole("link", { name: "Design review" })).toBeInTheDocument();
  expect(screen.getByRole("link", { name: "일정 만들기" })).toBeInTheDocument();
});

test("save 403 preserves typed draft and blocks resubmission until access is checked", async () => {
  const server = http(); server.on("POST", "/api/v1/projects/p1/schedules", () => json({ code: "FORBIDDEN" }, 403));
  const user = mount("/projects/p1/schedules/new");
  fireEvent.change(await screen.findByLabelText("제목"), { target: { value: "Unsaved plan" } });
  fireEvent.change(screen.getByLabelText("시작"), { target: { value: "2090-09-10T10:00" } });
  fireEvent.change(screen.getByLabelText("종료"), { target: { value: "2090-09-10T11:00" } });
  await user.click(screen.getByRole("button", { name: "일정 저장" }));
  expect(await screen.findByRole("alert")).toHaveTextContent(/접근 권한/);
  expect(screen.getByLabelText("제목")).toHaveValue("Unsaved plan");
  expect(screen.getByRole("button", { name: "일정 저장" })).toBeDisabled();
  await user.click(screen.getByRole("button", { name: "접근 상태 다시 확인" }));
  await waitFor(() => expect(screen.getByRole("button", { name: "일정 저장" })).toBeEnabled());
  expect(screen.getByLabelText("제목")).toHaveValue("Unsaved plan");
  expect(server.calls.filter(c => c.method === "POST")).toHaveLength(1);
});

test("detail command 403 blocks cached write controls until explicit access recovery", async () => {
  const server = http(); server.on("POST", "/api/v1/projects/p1/schedules/s1/cancel", () => json({ code: "FORBIDDEN" }, 403));
  const user = mount("/projects/p1/schedules/s1");
  await user.click(await screen.findByRole("button", { name: "일정 취소" }));
  expect(await screen.findByRole("alert")).toHaveTextContent(/접근 권한/);
  expect(screen.queryByRole("link", { name: "일정 수정" })).not.toBeInTheDocument();
  expect(screen.getByRole("button", { name: "일정 취소" })).toBeDisabled();
  expect(screen.getByRole("button", { name: "접근 상태 다시 확인" })).toBeEnabled();
});

test("protected query 401 removes the old workspace and stays signed out on route changes", async () => {
  const server = http(); const user = mount("/projects/p1/schedules/s1");
  await screen.findByRole("heading", { name: schedule.title });
  server.on("GET", "/api/v1/notifications", () => json({ code: "UNAUTHENTICATED" }, 401));
  await user.click(screen.getByRole("link", { name: "알림" }));
  expect(await screen.findByRole("link", { name: "Google로 로그인" })).toBeInTheDocument();
  await act(async () => { window.location.hash = "#/projects/p1/schedules/s1"; window.dispatchEvent(new HashChangeEvent("hashchange")); });
  expect(screen.queryByRole("heading", { name: schedule.title })).not.toBeInTheDocument();
  expect(screen.queryByRole("navigation")).not.toBeInTheDocument();
  expect(server.calls.filter(c => c.url === "/api/v1/me")).toHaveLength(1);
});

test("protected mutation 401 enters the login flow without replaying the command", async () => {
  const server = http(); server.on("POST", "/api/v1/projects/p1/schedules/s1/cancel", () => json({ code: "UNAUTHENTICATED" }, 401));
  const user = mount("/projects/p1/schedules/s1");
  await user.click(await screen.findByRole("button", { name: "일정 취소" }));
  expect(await screen.findByRole("link", { name: "Google로 로그인" })).toBeInTheDocument();
  expect(screen.queryByRole("heading", { name: schedule.title })).not.toBeInTheDocument();
  expect(server.calls.filter(c => c.method === "POST")).toHaveLength(1);
});

test("deliberate route change focuses the new heading after its async load", async () => {
  http(); const user = mount("/projects/p1"); await screen.findByRole("heading", { name: "Planning 대시보드" });
  await user.click(screen.getByRole("link", { name: "일정" }));
  const heading = await screen.findByRole("heading", { name: "프로젝트 일정" });
  await waitFor(() => expect(heading).toHaveFocus());
});
