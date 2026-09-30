import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, test, vi } from "vitest";
import App from "./App";
import { http, json, schedule } from "./test/http";

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  window.location.hash = "";
  window.history.replaceState(null, "", "/");
  sessionStorage.clear();
});

const response = (body: any, title: string, total: number) => json({
  records: [{ schedule: { ...schedule, title }, values: {} }],
  total,
  hasMore: false,
  page: body.page,
  size: body.size,
  groups: [],
  queriedAt: "2090-09-10T00:00:00Z",
});

test("fresh legacy list initializes its canonical URL, query, rows, and footer at page zero", async () => {
  const server = http();
  const bodies: any[] = [];
  server.on("POST", "/api/v1/projects/p1/schedule-workspace/query", body => {
    bodies.push(body);
    return response(body, body.page === 0 ? "Page zero schedule" : "Wrong page schedule", 2);
  });
  window.location.hash = "#/projects/p1/schedules?view=list&date=2090-09-10&page=2&text=Page";
  render(<App />);

  expect(await screen.findByText("Page zero schedule")).toBeInTheDocument();
  expect(screen.getByText("2건 · 1페이지")).toBeInTheDocument();
  await waitFor(() => {
    const params = new URLSearchParams(window.location.hash.split("?")[1]);
    expect(params.get("view")).toBe("builtin-list");
    expect(params.get("page")).toBe("0");
    expect(params.get("date")).toBe("2090-09-10");
    expect(params.get("text")).toBe("Page");
  });
  expect(bodies.map(body => body.page)).toEqual([0]);
});

test.each(["month", "week"] as const)("fresh legacy %s initializes canonical Calendar context and only queries page zero", async mode => {
  const server = http();
  const bodies: any[] = [];
  server.on("POST", "/api/v1/projects/p1/schedule-workspace/query", body => { bodies.push(body); return response(body, "Design review", 1); });
  window.location.hash = `#/projects/p1/schedules?view=${mode}&date=2090-09-10&page=7&text=Design&zone=UTC`;
  render(<App />);

  expect(await screen.findByRole("grid", { name: mode === "week" ? "주간 일정" : "월간 일정" })).toBeInTheDocument();
  expect(screen.getByRole("button", { name: mode === "week" ? "주간 보기" : "월간 보기" })).toHaveAttribute("aria-pressed", "true");
  expect(screen.getByLabelText("기준 날짜")).toHaveValue("2090-09-10");
  expect(await screen.findAllByText(/Design review/)).not.toHaveLength(0);
  await waitFor(() => {
    const params = new URLSearchParams(window.location.hash.split("?")[1]);
    expect(params.get("view")).toBe("builtin-calendar");
    expect(params.get("mode")).toBe(mode);
    expect(params.get("page")).toBe("0");
    expect(params.get("date")).toBe("2090-09-10");
    expect(params.get("text")).toBe("Design");
    expect(params.get("zone")).toBe("UTC");
  });
  expect(bodies).toHaveLength(1);
  expect(bodies[0]).toMatchObject({ page: 0, size: 100 });
});

test("fresh canonical list retains page two in its URL, query, rows, and footer", async () => {
  const server = http();
  const bodies: any[] = [];
  server.on("POST", "/api/v1/projects/p1/schedule-workspace/query", body => { bodies.push(body); return response(body, `Canonical page ${body.page}`, 41); });
  window.location.hash = "#/projects/p1/schedules?view=builtin-list&date=2090-09-10&page=2";
  render(<App />);

  expect(await screen.findByText("Canonical page 2")).toBeInTheDocument();
  expect(screen.getByText("41건 · 3페이지")).toBeInTheDocument();
  const params = new URLSearchParams(window.location.hash.split("?")[1]);
  expect(params.get("view")).toBe("builtin-list");
  expect(params.get("page")).toBe("2");
  expect(bodies.map(body => body.page)).toEqual([2]);
});
