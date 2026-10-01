import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, test } from "vitest";
import App from "./App";
import { http, json } from "./test/http";

afterEach(() => {
  window.location.hash = "";
  sessionStorage.clear();
});

test("notification popup keeps schedule context and restores trigger focus", async () => {
  http();
  window.location.hash = "#/projects/p1/schedules";
  render(<App />);
  const user = userEvent.setup();
  await screen.findByRole("heading", { name: "프로젝트 일정" });
  const trigger = screen.getByRole("button", { name: "알림" });
  await user.click(trigger);
  await screen.findByRole("dialog", { name: "알림" });
  expect(window.location.hash).toBe("#/projects/p1/schedules");
  expect(await screen.findByText("일정 확정")).toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "닫기" }));
  await waitFor(() => expect(screen.queryByRole("dialog", { name: "알림" })).not.toBeInTheDocument());
  expect(trigger).toHaveFocus();
});

test("notification popup follows validated schedule links and direct route retains full content", async () => {
  const server = http();
  server.on("GET", "/api/v1/notifications", () => json([]));
  window.location.hash = "#/projects/p1";
  render(<App />);
  const user = userEvent.setup();
  await screen.findByRole("heading", { name: "프로젝트 개요" });
  await user.click(screen.getByRole("button", { name: "알림" }));
  await screen.findByRole("dialog", { name: "알림" });
  await user.click(screen.getByRole("link", { name: "모든 알림 보기" }));
  await screen.findByRole("heading", { name: "알림" });
  expect(screen.queryByRole("dialog", { name: "알림" })).not.toBeInTheDocument();
  expect(await screen.findByText("새 알림이 없습니다")).toBeInTheDocument();
});

test("full notification content distinguishes captured history, current state, deleted, legacy, restricted, and invalid rows", async () => {
  const server = http();
  server.on("GET", "/api/v1/notifications", () => json([
    { id: "rich", type: "SCHEDULE_CANCELLED", link: "/projects/p1/schedules/s1", readAt: null, createdAt: "2026-09-09T00:00:00Z", content: {
      provenance: "SNAPSHOT", projectName: "Planning", scheduleTitle: "Review", actorDisplayName: "Morgan", occurredAt: "2026-09-09T00:00:00Z",
      scheduleStatus: "CONFIRMED", businessRevision: 4, summary: "Schedule cancelled",
      changedFields: [{ field: "status", label: "Status", before: "CONFIRMED", after: "CANCELLED" }],
      currentProjectName: "Planning", currentScheduleTitle: "Review", currentScheduleStatus: "CANCELLED", resourceAvailable: true,
    } },
    { id: "legacy", type: "INVITATION_CREATED", link: "/projects/p1", readAt: null, createdAt: "2026-09-08T00:00:00Z", content: {
      provenance: "LEGACY", projectName: null, scheduleTitle: null, actorDisplayName: null, occurredAt: "not-a-date", scheduleStatus: null,
      businessRevision: null, summary: "Notification details unavailable", changedFields: [], currentProjectName: "Planning",
      currentScheduleTitle: null, currentScheduleStatus: null, resourceAvailable: true,
    } },
    { id: "deleted", type: "SCHEDULE_CONFIRMED", link: null, readAt: "2026-09-07T00:00:00Z", createdAt: "2026-09-07T00:00:00Z", content: {
      provenance: "SNAPSHOT", projectName: "Planning", scheduleTitle: "Removed review", actorDisplayName: "Morgan", occurredAt: "2026-09-06T00:00:00Z",
      scheduleStatus: "CONFIRMED", businessRevision: 3, summary: "Schedule confirmed", changedFields: [], currentProjectName: "Planning",
      currentScheduleTitle: null, currentScheduleStatus: null, resourceAvailable: false,
    } },
    { id: "restricted", type: "RESTRICTED_ITEM", link: null, readAt: null, createdAt: "2026-09-05T00:00:00Z", content: {
      provenance: "RESTRICTED", projectName: null, scheduleTitle: null, actorDisplayName: null, occurredAt: "2026-09-05T00:00:00Z",
      scheduleStatus: null, businessRevision: null, summary: "This item is unavailable", changedFields: [], currentProjectName: null,
      currentScheduleTitle: null, currentScheduleStatus: null, resourceAvailable: false,
    } },
    { id: "unavailable", type: null, link: null, readAt: null, createdAt: "bad-time", content: {
      provenance: "UNAVAILABLE", projectName: null, scheduleTitle: null, actorDisplayName: null, occurredAt: "bad-time",
      scheduleStatus: null, businessRevision: null, summary: "Notification details unavailable", changedFields: [], currentProjectName: null,
      currentScheduleTitle: null, currentScheduleStatus: null, resourceAvailable: false,
    } },
  ]));
  window.location.hash = "#/notifications";
  render(<App />);
  await screen.findByRole("heading", { name: "알림" });
  const richTitle = await screen.findByText("Planning → Review · 일정 취소");
  const richRow = richTitle.closest("li");
  expect(richRow).not.toBeNull();
  const rich = within(richRow as HTMLElement);
  expect(rich.getByText(/Morgan ·/)).toBeInTheDocument();
  expect(rich.getByRole("time").getAttribute("dateTime")).toBe("2026-09-09T00:00:00.000Z");
  expect(rich.getByRole("listitem")).toHaveTextContent("상태: 확정 → 취소");
  expect(rich.getByText("이벤트 당시 상태: 확정")).toBeInTheDocument();
  expect(rich.getByText("현재 상태: 취소")).toBeInTheDocument();
  expect(screen.getByText("현재 프로젝트: Planning")).toBeInTheDocument();
  expect(screen.getByText("관련 일정을 현재 사용할 수 없습니다.")).toBeInTheDocument();
  expect(screen.getByText("제한된 알림")).toBeInTheDocument();
  expect(screen.getAllByText("알림 상세 정보를 사용할 수 없습니다.")).toHaveLength(2);
  expect(screen.getAllByText("시간 정보 없음").length).toBeGreaterThan(0);
  const restrictedRow = screen.getByText("제한된 알림").closest("li");
  expect(restrictedRow).not.toBeNull();
  expect(within(restrictedRow as HTMLElement).queryByText(/Morgan/)).not.toBeInTheDocument();
  expect(server.calls.some(call => call.method === "POST" && call.url.includes("/read"))).toBe(false);
});

test("compact rich rows keep read and navigation as separate actions", async () => {
  const server = http();
  server.on("GET", "/api/v1/notifications", () => json([{ id: "rich", type: "SCHEDULE_CONFIRMED", link: "/projects/p1/schedules/s1", readAt: null, createdAt: "2090-09-09T00:00:00Z", content: {
    provenance: "SNAPSHOT", projectName: "Planning", scheduleTitle: "Review", actorDisplayName: "Morgan", occurredAt: "2090-09-09T00:00:00Z",
    scheduleStatus: "CONFIRMED", businessRevision: 4, summary: "Schedule confirmed", changedFields: [], currentProjectName: "Planning",
    currentScheduleTitle: "Review", currentScheduleStatus: "CONFIRMED", resourceAvailable: true,
  } }]));
  server.on("POST", "/api/v1/notifications/rich/read", () => json({ id: "rich", type: "SCHEDULE_CONFIRMED", link: "/projects/p1/schedules/s1", readAt: "2090-09-10T00:00:00Z", createdAt: "2090-09-09T00:00:00Z", content: { provenance: "SNAPSHOT" } }));
  window.location.hash = "#/projects/p1";
  render(<App />);
  const user = userEvent.setup();
  await screen.findByRole("heading", { name: "프로젝트 개요" });
  await user.click(screen.getByRole("button", { name: "알림" }));
  await screen.findByRole("dialog", { name: "알림" });
  expect(await screen.findByText("Planning → Review · 일정 확정")).toBeInTheDocument();
  const link = screen.getByRole("link", { name: "관련 일정 보기" });
  expect(link).toBeInTheDocument();
  expect(server.calls.some(call => call.method === "POST" && call.url.includes("/read"))).toBe(false);
  await user.click(link);
  await waitFor(() => expect(window.location.hash).toContain("/projects/p1/schedules/s1"));
  expect(server.calls.some(call => call.method === "POST" && call.url.includes("/read"))).toBe(false);
  expect(server.calls.some(call => call.url.includes("acknowledge"))).toBe(false);
  await user.click(screen.getByRole("button", { name: "알림" }));
  await screen.findByRole("dialog", { name: "알림" });
  await user.click(screen.getByRole("button", { name: "읽음 처리" }));
  await waitFor(() => expect(server.calls.some(call => call.method === "POST" && call.url === "/api/v1/notifications/rich/read")).toBe(true));
  expect(server.calls.some(call => call.url.includes("acknowledge"))).toBe(false);
});
