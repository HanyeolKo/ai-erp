import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, test, vi } from "vitest";
import App from "./App";
import { http, json } from "./test/http";
afterEach(() => { vi.unstubAllGlobals(); window.location.hash = ""; sessionStorage.clear(); });
const mount = (path: string) => { window.location.hash = `#${path}`; render(<App />); return userEvent.setup(); };
const change = (label: string, value: string) => fireEvent.change(screen.getByLabelText(label), { target: { value } });
test.each([
  ["America/Santiago", "2026-09-06", "2026-09-06T04:00:00.000Z", "2026-09-07T03:00:00.000Z"],
  ["America/Havana", "2026-11-01", "2026-11-01T04:00:00.000Z", "2026-11-02T05:00:00.000Z"],
  ["America/New_York", "2026-03-08", "2026-03-08T05:00:00.000Z", "2026-03-09T04:00:00.000Z"]
])("P03 civil-date request includes the entire day across skipped/repeated midnight: %s", async (zone, day, from, to) => {
  const server = http(); const user = mount("/projects/p1/schedules"); await screen.findByLabelText("날짜 이후"); await user.click(screen.getByText("상세 필터"));
  change("표시 시간대", zone); change("날짜 이후", day); change("날짜 이전", day);
  const expected = "/api/v1/projects/p1/schedules?page=0&limit=20&from=" + encodeURIComponent(from) + "&to=" + encodeURIComponent(to);
  await waitFor(() => expect(server.calls.some(c => c.url === expected)).toBe(true));
  expect(screen.queryByRole("alert")).not.toBeInTheDocument();
});
test("P03 schedule browsing omits Calendar diagnostics and per-schedule projection requests", async () => {
  const server = http();
  mount("/projects/p1/schedules");
  expect(await screen.findByRole("heading", { name: "프로젝트 일정" })).toBeInTheDocument();
  expect(screen.queryByLabelText("Calendar 상태")).not.toBeInTheDocument();
  expect(screen.queryByText("동기화 완료")).not.toBeInTheDocument();
  expect(screen.queryByText("Calendar 연동이 구성되지 않았습니다.")).not.toBeInTheDocument();
  expect(server.calls.some(call => call.url.includes("/calendar"))).toBe(false);
});
test("P05 projection error does not suppress known reauthorization status or reconnect navigation", async () => {
  const server = http(); server.on("GET", "/api/v1/calendar/connection", () => json({ status: "REAUTH_REQUIRED", configurationRequired: false }));
  server.on("GET", "/api/v1/projects/p1/schedules/s1/calendar", () => json({ code: "PROJECTION_UNAVAILABLE" }, 500));
  const user = mount("/projects/p1/schedules/s1"); await screen.findByText("PROJECTION_UNAVAILABLE");
  expect(screen.getByText("연결 확인 필요", { selector: "strong" })).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "투영 다시 시도" })).toBeEnabled();
  await user.click(screen.getByRole("link", { name: "Calendar 다시 연결" }));
  expect(await screen.findByRole("heading", { name: "Calendar 연결" })).toBeInTheDocument();
  expect(screen.getByText("다시 인증 필요")).toBeInTheDocument();
});
test("P04 backend title and startsAt errors associate with controls while preserving summary", async () => {
  const server = http(); server.on("POST", "/api/v1/projects/p1/schedules", () => json({ code: "VALIDATION_FAILED", traceId: "trace-field-errors", fieldErrors: [{ field: "title", message: "Title is too long" }, { field: "startsAt", message: "Start violates project policy" }] }, 400));
  const user = mount("/projects/p1/schedules/new"); await screen.findByLabelText("제목");
  change("제목", "Planning"); change("시작", "2090-09-10T10:00"); change("종료", "2090-09-10T11:00");
  await user.click(screen.getByRole("button", { name: "일정 저장" }));
  expect(await screen.findByText("VALIDATION_FAILED")).toBeInTheDocument();
  for (const [label, message] of [["제목", "Title is too long"], ["시작", "Start violates project policy"]]) {
    const field = screen.getByLabelText(label);
    expect(field).toHaveAttribute("aria-invalid", "true");
    expect(field).toHaveAccessibleDescription(message);
    const ids = field.getAttribute("aria-describedby")!.split(" ");
    expect(ids.every(id => document.getElementById(id))).toBe(true);
  }
});
