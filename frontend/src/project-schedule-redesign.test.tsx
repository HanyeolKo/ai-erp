import { fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, test, vi } from "vitest";
import App from "./App";
import { http, json, schedule } from "./test/http";

afterEach(() => {
  vi.unstubAllGlobals();
  window.location.hash = "";
  sessionStorage.clear();
});

const mount = (path: string) => {
  window.location.hash = `#${path}`;
  render(<App />);
  return userEvent.setup();
};

test("schedule listing exposes a readable date-grouped agenda from the filtered page", async () => {
  http();
  const user = mount("/projects/p1/schedules");
  expect(await screen.findByRole("heading", { name: "프로젝트 일정" })).toBeInTheDocument();
  const agenda = await screen.findByLabelText("날짜별 일정");
  expect(within(agenda).getByRole("link", { name: /10:00–11:00.*Design review.*확정/ })).toBeInTheDocument();
  expect(within(agenda).getByText(/10:00–11:00/)).toBeInTheDocument();
  expect(screen.getByText("검색·상태·확인·날짜 범위")).toBeInTheDocument();
  expect(screen.getByRole("link", { name: "일정 만들기" })).toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "주간 보기" }));
  expect(screen.getByRole("grid", { name: "주간 일정" })).toBeInTheDocument();
});

test("month events expose local day labels, stable ordering, and lifecycle status without Calendar fan-out", async () => {
  const server = http();
  const make = (id: string, title: string, startsAt: string, endsAt: string, status = "CONFIRMED") => ({ ...schedule, id, title, startsAt, endsAt, status });
  server.on("GET", "/api/v1/projects/p1/schedules", () => json([
    make("continuing", "Long workshop", "2090-09-09T10:00:00Z", "2090-09-12T15:00:00Z"),
    make("early", "Early", "2090-09-10T00:00:00Z", "2090-09-10T01:00:00Z"),
    make("same-b", "Beta", "2090-09-10T01:00:00Z", "2090-09-10T02:00:00Z"),
    make("same-a", "Alpha", "2090-09-10T01:00:00Z", "2090-09-10T02:00:00Z"),
    make("midnight", "Midnight", "2090-09-10T03:00:00Z", "2090-09-10T15:00:00Z", "CANCELLED"),
    make("midnight-seconds", "Midnight plus seconds", "2090-09-11T01:00:00Z", "2090-09-11T15:00:30Z"),
  ]));
  mount("/projects/p1/schedules");
  const grid = await screen.findByRole("grid", { name: "월간 일정" });
  const day10 = within(grid).getByRole("gridcell", { name: "2090-09-10" });
  const monthLinks = within(day10).getAllByRole("link");
  expect(monthLinks.map(link => link.textContent)).toEqual([
    "계속 · Long workshop · 확정",
    "09:00–10:00 · Early · 확정",
    "10:00–11:00 · Alpha · 확정",
    "10:00–11:00 · Beta · 확정",
    "12:00–24:00 · Midnight · 취소",
  ]);
  expect(within(grid).getByRole("gridcell", { name: "2090-09-11" })).toHaveTextContent("계속 · Long workshop · 확정");
  expect(within(grid).getByRole("gridcell", { name: "2090-09-12" })).toHaveTextContent("00:00 종료 · Midnight plus seconds · 확정");
  expect(within(grid).getByRole("gridcell", { name: "2090-09-12" })).toHaveTextContent("24:00 종료 · Long workshop · 확정");
  expect(screen.queryByLabelText("Calendar 상태")).not.toBeInTheDocument();
  expect(screen.queryByText("Calendar 연동이 구성되지 않았습니다.")).not.toBeInTheDocument();
  expect(server.calls.some(call => call.url.includes("/calendar"))).toBe(false);
});

test("weekly calendar preserves local clock placement and translated status", async () => {
  http();
  const user = mount("/projects/p1/schedules");
  await screen.findByRole("heading", { name: "프로젝트 일정" });
  fireEvent.change(screen.getByLabelText("기준 날짜"), { target: { value: "2090-09-10" } });
  await user.click(screen.getByRole("button", { name: "주간 보기" }));
  const grid = await screen.findByRole("grid", { name: "주간 일정" });
  const event = within(grid).getByRole("link", { name: /Design review/ }).closest("[data-start-minute]");
  expect(event).toHaveAttribute("data-start-minute", "600");
  expect(event).toHaveAttribute("data-duration-minute", "60");
  expect(within(grid).getByText("확정")).toBeInTheDocument();
});

test("detail translates lifecycle and Calendar states while keeping participant actions available", async () => {
  const server = http();
  server.on("GET", "/api/v1/projects/p1/schedules/s1", () => json({ ...schedule, status: "CONFIRMED", changes: [{ ...schedule.changes[0], type: "SCHEDULE_CONFIRMED" }] }));
  server.on("GET", "/api/v1/projects/p1/schedules/s1/calendar", () => json({ scheduleId: "s1", status: "UNRECOGNIZED", businessRevision: 4 }));
  server.on("GET", "/api/v1/calendar/connection", () => json({ status: "UNRECOGNIZED", configurationRequired: false }));
  mount("/projects/p1/schedules/s1");
  expect(await screen.findByRole("heading", { name: "Design review" })).toBeInTheDocument();
  expect(screen.getByRole("heading", { name: "참여자 확인" })).toBeInTheDocument();
  expect(screen.getByText("나")).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "일정 취소" })).toBeEnabled();
  expect(screen.getByText(/일정 확정/)).toBeInTheDocument();
  expect(screen.getByRole("heading", { name: "Calendar 동기화" })).toBeInTheDocument();
  expect(server.calls.some(call => call.url.includes("/calendar"))).toBe(true);
});

test("detail renders bounded participant identity from the authorized detail response", async () => {
  const server = http();
  server.on("GET", "/api/v1/projects/p1/schedules/s1", () => json({
    ...schedule,
    participants: [{ memberUserId: "u200", externalEmail: null, acknowledged: false, displayName: "Later member", email: "later@example.test" }],
  }));
  mount("/projects/p1/schedules/s1");
  expect(await screen.findByText("Later member · later@example.test")).toBeInTheDocument();
  expect(server.calls.some(call => call.url === "/api/v1/projects/p1/members")).toBe(false);
});

test("detail hides cached participant identity through read 403 and 500 until a fresh 200", async () => {
  const server = http();
  let reads = 0;
  server.on("GET", "/api/v1/projects/p1/schedules/s1", () => {
    reads += 1;
    if (reads === 2) return json({ code: "DETAIL_FORBIDDEN" }, 403);
    if (reads === 3) return json({ code: "DETAIL_UNAVAILABLE" }, 500);
    return json({ ...schedule, participants: [{ memberUserId: "u2", externalEmail: null, acknowledged: false, displayName: "Private member", email: "private@example.test" }] });
  });
  server.on("POST", "/api/v1/projects/p1/schedules/s1/cancel", () => json(schedule));
  const user = mount("/projects/p1/schedules/s1");
  expect(await screen.findByText("Private member · private@example.test")).toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "일정 취소" }));
  await screen.findByText("DETAIL_FORBIDDEN");
  expect(screen.queryByText("Private member · private@example.test")).not.toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "일정 다시 확인" }));
  await screen.findByText("DETAIL_UNAVAILABLE");
  expect(screen.queryByText("Private member · private@example.test")).not.toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "일정 다시 확인" }));
  expect(await screen.findByText("Private member · private@example.test")).toBeInTheDocument();
});

test("editor keeps basic, date/time, and participant sections with field focused on invalid submit", async () => {
  http();
  const user = mount("/projects/p1/schedules/new");
  expect(await screen.findByRole("heading", { name: "일정 만들기" })).toBeInTheDocument();
  expect(screen.getByRole("group", { name: "기본 정보" })).toBeInTheDocument();
  expect(screen.getByRole("group", { name: "날짜와 시간" })).toBeInTheDocument();
  expect(screen.getByRole("group", { name: "참석자" })).toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "일정 저장" }));
  expect((await screen.findAllByRole("alert")).some(alert => alert.textContent?.includes("제목을 입력하세요."))).toBe(true);
  const title = screen.getByLabelText("제목");
  expect(title).toHaveAttribute("aria-invalid", "true");
  expect(title).toHaveFocus();
});
