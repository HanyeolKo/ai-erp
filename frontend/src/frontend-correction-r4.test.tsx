import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { focusManager } from "@tanstack/react-query";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, test, vi } from "vitest";
import App from "./App";
import { http, json, schedule } from "./test/http";

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  window.location.hash = "";
  sessionStorage.clear();
  focusManager.setFocused(undefined);
});

const queryResult = (body: any, nextSchedule = schedule) => json({ records: [{ schedule: nextSchedule, values: {} }], total: 1, hasMore: false, page: body.page, size: body.size, groups: [], queriedAt: "2090-09-10T00:00:00Z" });

function mountCalendar() {
  window.location.hash = "#/projects/p1/schedules?view=builtin-calendar&page=0&date=2090-09-10&mode=month";
  render(<App />);
  return userEvent.setup();
}

async function openTimeDialog(user: ReturnType<typeof userEvent.setup>) {
  const actions = within(await screen.findByRole("region", { name: "일정 목록" })).getAllByRole("button", { name: "시간 변경: Design review" });
  await user.click(actions[0]);
  return screen.findByRole("dialog", { name: "시간 변경" });
}

test("Calendar retires a recovered override when the active workspace target reaches a newer version", async () => {
  const server = http();
  const recovered = { ...schedule, rowVersion: 4, startsAt: "2090-09-10T02:00:00Z", endsAt: "2090-09-10T03:00:00Z" };
  const external = { ...schedule, rowVersion: 5, startsAt: "2090-09-10T03:00:12.125Z", endsAt: "2090-09-10T04:00:37.250Z" };
  let target = schedule;
  const writes: any[] = [];
  server.on("POST", "/api/v1/projects/p1/schedule-workspace/query", body => queryResult(body, target));
  server.on("GET", "/api/v1/projects/p1/schedules/s1", () => json(recovered));
  server.on("PATCH", "/api/v1/projects/p1/schedules/s1", body => {
    writes.push(body);
    if (writes.length === 1) return json({ code: "ROW_VERSION_CONFLICT" }, 409);
    return json({ ...external, ...body, rowVersion: 6 });
  });
  const user = mountCalendar();
  let dialog = await openTimeDialog(user);
  fireEvent.change(within(dialog).getByLabelText("종료"), { target: { value: "2090-09-10T12:30" } });
  await user.click(within(dialog).getByRole("button", { name: "변경 저장" }));
  await screen.findByText(/다른 사용자가 이 일정을 변경했습니다/);
  expect(writes[0].rowVersion).toBe(3);

  target = external;
  fireEvent.change(screen.getByLabelText("표시 시간대"), { target: { value: "UTC" } });
  expect(await screen.findAllByText(/03:00–04:00/)).not.toHaveLength(0);
  dialog = await openTimeDialog(user);
  fireEvent.change(within(dialog).getByLabelText("종료"), { target: { value: "2090-09-10T05:00" } });
  await user.click(within(dialog).getByRole("button", { name: "변경 저장" }));
  await waitFor(() => expect(writes).toHaveLength(2));
  expect(writes[1]).toMatchObject({ rowVersion: 5, startsAt: external.startsAt, endsAt: "2090-09-10T05:00:00.000Z" });
});

test("an exact-target denial during Calendar mutation recovery locks the whole workspace", async () => {
  const server = http();
  let targetDenied = false;
  let writes = 0;
  server.on("POST", "/api/v1/projects/p1/schedule-workspace/query", body => targetDenied ? json({ code: "FORBIDDEN" }, 403) : queryResult(body));
  server.on("PATCH", "/api/v1/projects/p1/schedules/s1", () => { writes += 1; targetDenied = true; return json({ code: "TEMPORARY" }, 500); });
  const user = mountCalendar();
  const dialog = await openTimeDialog(user);
  fireEvent.change(within(dialog).getByLabelText("종료"), { target: { value: "2090-09-10T12:30" } });
  await user.click(within(dialog).getByRole("button", { name: "변경 저장" }));
  await screen.findByText(/일정 접근 권한을 다시 확인해야 합니다/);
  expect(screen.queryByRole("region", { name: "일정 목록" })).not.toBeInTheDocument();
  expect(screen.queryByRole("link", { name: "일정 만들기" })).not.toBeInTheDocument();
  expect(screen.getByRole("button", { name: "보기 설정" })).toBeDisabled();
  expect(screen.getByRole("button", { name: "속성" })).toBeDisabled();
  expect(writes).toBe(1);
});

test("retry target denial stays globally locked through failed recovery and unlocks only after all fresh reads succeed", async () => {
  const server = http();
  let targetState: "ok" | "temporary" | "denied" = "ok";
  let writes = 0;
  server.on("POST", "/api/v1/projects/p1/schedule-workspace/query", body => targetState === "denied" ? json({ code: "FORBIDDEN" }, 403) : targetState === "temporary" ? json({ code: "TEMPORARY" }, 500) : queryResult(body));
  server.on("PATCH", "/api/v1/projects/p1/schedules/s1", () => { writes += 1; targetState = "temporary"; return json({ code: "TEMPORARY" }, 500); });
  const user = mountCalendar();
  const dialog = await openTimeDialog(user);
  fireEvent.change(within(dialog).getByLabelText("종료"), { target: { value: "2090-09-10T12:30" } });
  await user.click(within(dialog).getByRole("button", { name: "변경 저장" }));
  const retry = await screen.findByRole("button", { name: "접근 상태 다시 확인" });
  targetState = "denied";
  await user.click(retry);
  await screen.findByText(/일정 접근 권한을 다시 확인해야 합니다/);
  expect(screen.getByRole("button", { name: "보기 설정" })).toBeDisabled();
  expect(writes).toBe(1);

  await user.click(screen.getByRole("button", { name: "접근 상태 다시 확인" }));
  await screen.findByText("접근 상태를 확인하지 못해 보호된 작업을 잠갔습니다.");
  expect(screen.getByRole("button", { name: "보기 설정" })).toBeDisabled();
  expect(writes).toBe(1);

  targetState = "ok";
  await user.click(screen.getByRole("button", { name: "접근 상태 다시 확인" }));
  await waitFor(() => expect(screen.getByRole("button", { name: "보기 설정" })).toBeEnabled());
  expect(await screen.findByRole("region", { name: "일정 목록" })).toBeInTheDocument();
  expect(screen.getByRole("link", { name: "일정 만들기" })).toBeInTheDocument();
  expect(writes).toBe(1);
});
