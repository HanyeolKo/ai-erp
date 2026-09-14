import { act, fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, test } from "vitest";
import App from "./App";
import { http, json } from "./test/http";

afterEach(() => {
  window.location.hash = "";
  sessionStorage.clear();
});

const mount = () => {
  window.location.hash = "#/projects/p1/schedules/s1";
  render(<App />);
  return userEvent.setup();
};

test("Calendar status is a compact accessible disclosure with keyboard escape recovery", async () => {
  http();
  const user = mount();
  const trigger = await screen.findByRole("button", { name: "Calendar: 동기화 완료" });

  expect(trigger).toHaveAttribute("aria-expanded", "false");
  expect(screen.queryByRole("heading", { name: "Calendar 동기화" })).not.toBeInTheDocument();
  expect(screen.queryByText("동기화 상태")).not.toBeInTheDocument();

  trigger.focus();
  await user.keyboard("{Enter}");
  expect(trigger).toHaveAttribute("aria-expanded", "true");
  expect(screen.getByText("동기화 완료")).toBeInTheDocument();
  expect(screen.getByText("최근 변경 버전 · 4")).toBeInTheDocument();

  const summary = screen.getByText("진단 정보");
  summary.focus();
  fireEvent.keyDown(summary, { key: "Escape" });
  expect(trigger).toHaveAttribute("aria-expanded", "false");
  expect(trigger).toHaveFocus();

  await user.keyboard(" ");
  expect(trigger).toHaveAttribute("aria-expanded", "true");
  await user.keyboard(" ");
  expect(trigger).toHaveAttribute("aria-expanded", "false");
});

test("Calendar unknown response has a distinct compact state and disclosure text", async () => {
  const server = http();
  server.on("GET", "/api/v1/projects/p1/schedules/s1/calendar", () => json({ scheduleId: "s1", status: "UNRECOGNIZED", businessRevision: 4 }));
  server.on("GET", "/api/v1/calendar/connection", () => json({ status: "UNRECOGNIZED", configurationRequired: false }));
  const user = mount();
  const trigger = await screen.findByRole("button", { name: "Calendar: 상태 확인 필요" });

  expect(screen.queryByText("연결 확인 필요")).not.toBeInTheDocument();
  await user.click(trigger);
  expect(screen.getByText("상태 확인 필요")).toBeInTheDocument();
  expect(server.calls.filter(call => call.url.includes("/calendar")).length).toBe(2);
});

test("Calendar configuration and reconnect actions remain outside the disclosure", async () => {
  const server = http();
  server.on("GET", "/api/v1/projects/p1/schedules/s1/calendar", () => json({ scheduleId: "s1", status: "REAUTH_REQUIRED", businessRevision: 4 }));
  server.on("GET", "/api/v1/calendar/connection", () => json({ status: "NOT_CONNECTED", configurationRequired: true }));
  const user = mount();
  const trigger = await screen.findByRole("button", { name: "Calendar: 구성 필요" });

  expect(screen.getByText("Calendar 연동이 구성되지 않았습니다.")).toBeInTheDocument();
  expect(screen.queryByRole("link", { name: "Calendar 다시 연결" })).not.toBeInTheDocument();
  await user.click(trigger);
  expect(screen.getByText("구성 필요")).toBeInTheDocument();
});

test("Calendar projection query errors keep a direct retry action and error cue", async () => {
  const server = http();
  server.on("GET", "/api/v1/projects/p1/schedules/s1/calendar", () => json({ code: "PROJECTION_FAILED" }, 500));
  const user = mount();
  const trigger = await screen.findByRole("button", { name: "Calendar: 오류 확인 필요" });

  expect(screen.getByText("PROJECTION_FAILED")).toBeInTheDocument();
  const retry = screen.getByRole("button", { name: "투영 다시 시도" });
  expect(retry).toBeEnabled();
  await user.click(trigger);
  expect(screen.getByText("오류 확인 필요")).toBeInTheDocument();
  await user.click(retry);
  expect(server.calls.filter(call => call.url.endsWith("/calendar")).length).toBeGreaterThan(1);
});

test("Calendar keeps reconnect available when the other query fails", async () => {
  const server = http();
  server.on("GET", "/api/v1/projects/p1/schedules/s1/calendar", () => json({ scheduleId: "s1", status: "REAUTH_REQUIRED", businessRevision: 4 }));
  server.on("GET", "/api/v1/calendar/connection", () => json({ code: "CONNECTION_FAILED" }, 500));
  const user = mount();
  await screen.findByRole("button", { name: "Calendar: 오류 확인 필요" });
  expect(screen.getByRole("link", { name: "Calendar 다시 연결" })).toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "Calendar: 오류 확인 필요" }));
  expect(screen.getByText("오류 확인 필요")).toBeInTheDocument();
});

test("Calendar shows a loading cue while either required query is still pending", async () => {
  const server = http();
  let finishProjection!: (response: Response) => void;
  server.on("GET", "/api/v1/projects/p1/schedules/s1/calendar", () => new Promise(resolve => { finishProjection = resolve; }));
  mount();
  const trigger = await screen.findByRole("button", { name: "Calendar: 상태 확인 중" });
  expect(screen.queryAllByText("불러오는 중입니다.")).toHaveLength(0);
  await act(async () => finishProjection(json({ scheduleId: "s1", status: "SYNCED", businessRevision: 4 })));
  expect(await screen.findByRole("button", { name: "Calendar: 동기화 완료" })).toBeInTheDocument();
  expect(trigger).toHaveAttribute("aria-label", "Calendar: 동기화 완료");
});
