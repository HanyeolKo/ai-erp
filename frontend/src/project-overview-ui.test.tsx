import { render, screen } from "@testing-library/react";
import { afterEach, expect, test, vi } from "vitest";
import App from "./App";
import { http, json } from "./test/http";

afterEach(() => { vi.unstubAllGlobals(); window.location.hash = ""; });
test("period controls expose pressed state and preserve calendar context", async () => {
  const server = http();
  server.on("GET", "/api/v1/projects/p1/plan", () => json({ projectId: "p1", rowVersion: 1, targetStart: null, targetEnd: null, asOfDate: "2090-09-10", items: [], matchedIds: [], summary: { taskCount: 0, doneCount: 0, blockedCount: 0, overdueCount: 0, unplannedCount: 0, unassignedCount: 0, progressPercent: null, forecastStart: null, forecastEnd: null, forecastState: "EMPTY", outsideTarget: false }, complete: true, totalCount: 0 }));
  server.on("GET", "/api/v1/projects/p1/dashboard", () => json({ projectId: "p1", memberCount: 1, scheduleCount: 0, pendingAcknowledgementCount: 0, calendarRiskCount: 0, upcomingSchedules: [], actionQueue: [] }));
  server.on("GET", "/api/v1/projects/p1/schedules", () => json([]));
  server.on("GET", "/api/v1/projects/p1/schedule-workspace", () => json({ properties: [], views: [], dashboardViewId: null, dashboardRowVersion: 0 }));
  window.location.hash = "#/projects/p1?period=today&zone=Asia%2FSeoul";
  render(<App />);
  const today = await screen.findByRole("button", { name: "오늘" });
  expect(today).toHaveAttribute("aria-pressed", "true");
  expect(screen.getByRole("button", { name: "이번 주" })).toHaveAttribute("aria-pressed", "false");
  expect(screen.getByRole("link", { name: "캘린더 열기" })).toHaveAttribute("href", expect.stringContaining("zone=Asia%2FSeoul"));
});
