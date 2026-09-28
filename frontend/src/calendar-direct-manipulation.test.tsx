import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, test } from "vitest";
import App from "./App";
import { http, json, schedule } from "./test/http";
import { applyMonthChange, applyWeekMove, editBody, isValidChange, layoutWeekDay, snapQuarterMinute } from "./screens/calendar-direct-manipulation";
import { utcToLocalDateTime } from "./time";

afterEach(() => { window.location.hash = ""; sessionStorage.clear(); });

test("calendar schedule rows expose a time-change action", async () => {
  http();
  window.location.hash = "#/projects/p1/schedules";
  render(<App />);
  expect(await screen.findAllByRole("button", { name: "시간 변경: Design review" })).toHaveLength(1);
});

test("time-change dialog sends a time-only PATCH", async () => {
  const server = http();
  server.on("PATCH", "/api/v1/projects/p1/schedules/s1", body => {
    expect(body).toEqual({ title: "Design review", description: "Keep description", startsAt: "2090-09-10T02:00:00.000Z", endsAt: "2090-09-10T03:00:00.000Z", rowVersion: 3 });
    return new Response(JSON.stringify({ ...schedule, startsAt: body.startsAt, endsAt: body.endsAt }), { status: 200, headers: { "content-type": "application/json" } });
  });
  window.location.hash = "#/projects/p1/schedules";
  render(<App />);
  const action = (await screen.findAllByRole("button", { name: "시간 변경: Design review" }))[0];
  const user = userEvent.setup(); await user.click(action);
  expect(await screen.findByRole("dialog", { name: "시간 변경" })).toBeInTheDocument();
  fireEvent.change(screen.getByLabelText("시작"), { target: { value: "2090-09-10T11:00" } });
  fireEvent.change(screen.getByLabelText("종료"), { target: { value: "2090-09-10T12:00" } });
  await user.click(screen.getByRole("button", { name: "변경 저장" }));
  await waitFor(() => expect(server.calls.filter(call => call.method === "PATCH")).toHaveLength(1));
  expect(await screen.findByText(/저장했습니다/)).toBeInTheDocument();
});

test("an unchanged dialog submit releases the interaction lock", async () => {
  http();
  window.location.hash = "#/projects/p1/schedules";
  render(<App />);
  const user = userEvent.setup();
  const action = (await screen.findAllByRole("button", { name: "시간 변경: Design review" }))[0];
  await user.click(action);
  await user.click(screen.getByRole("button", { name: "변경 저장" }));
  await user.click(action);
  expect(screen.getByRole("dialog", { name: "시간 변경" })).toBeInTheDocument();
});

test("calendar arithmetic preserves local subminutes and omits participants", () => {
  const source = { ...schedule, startsAt: "2090-09-10T01:00:30.125Z", endsAt: "2090-09-10T03:00:45.500Z" };
  const moved = applyMonthChange(source, "2090-09-10", "2090-09-11", "move", "Asia/Seoul");
  expect(utcToLocalDateTime(moved.startsAt, "Asia/Seoul")).toBe("2090-09-11T10:00");
  expect(moved.startsAt.endsWith("30.125Z")).toBe(true);
  expect(moved.endsAt.endsWith("45.500Z")).toBe(true);
  expect(editBody(source, moved)).toEqual({ title: source.title, description: source.description, startsAt: moved.startsAt, endsAt: moved.endsAt, rowVersion: source.rowVersion });
  expect("memberParticipantIds" in editBody(source, moved)).toBe(false);
});

test("quarter snap uses a forward midpoint and rejects equal endpoints", () => {
  expect([snapQuarterMinute(0), snapQuarterMinute(7.49), snapQuarterMinute(7.5), snapQuarterMinute(1439), snapQuarterMinute(1440), snapQuarterMinute(-1)]).toEqual([0, 0, 15, 1440, 1440, 0]);
  expect(isValidChange({ startsAt: "2090-01-01T00:00:00.000Z", endsAt: "2090-01-01T00:00:00.000Z" })).toBe(false);
});

test("week movement preserves grab offset and lane order is independent of input order", () => {
  const source = { ...schedule, startsAt: "2090-09-10T01:00:00Z", endsAt: "2090-09-10T03:00:00Z" };
  const moved = applyWeekMove(source, { day: "2090-09-10", minute: 630 }, { day: "2090-09-10", minute: 705 }, "Asia/Seoul");
  expect(utcToLocalDateTime(moved.startsAt, "Asia/Seoul")).toBe("2090-09-10T11:15");
  const a = { ...source, id: "a", title: "A", startsAt: "2090-09-10T01:00:00Z", endsAt: "2090-09-10T02:00:00Z" };
  const b = { ...source, id: "b", title: "B", startsAt: "2090-09-10T01:30:00Z", endsAt: "2090-09-10T03:00:00Z" };
  const c = { ...source, id: "c", title: "C", startsAt: "2090-09-10T02:00:00Z", endsAt: "2090-09-10T02:30:00Z" };
  const first = layoutWeekDay([a, b, c], "2090-09-10", "Asia/Seoul").occurrences.map(item => [item.schedule.id, item.lane]);
  const second = layoutWeekDay([c, a, b], "2090-09-10", "Asia/Seoul").occurrences.map(item => [item.schedule.id, item.lane]);
  expect(second.sort()).toEqual(first.sort());
  expect(first.find(item => item[0] === "a")?.[1]).toBe(first.find(item => item[0] === "c")?.[1]);
});

test("month pointer move crosses the six-pixel threshold and writes one time-only PATCH", async () => {
  const server = http();
  server.on("PATCH", "/api/v1/projects/p1/schedules/s1", body => json({ ...schedule, startsAt: body.startsAt, endsAt: body.endsAt }));
  window.location.hash = "#/projects/p1/schedules";
  render(<App />);
  const event = (await screen.findAllByRole("link", { name: /Design review/ })).find(link => link.className.includes("month-event"))!;
  const wrapper = event.parentElement!;
  const target = screen.getByRole("gridcell", { name: "2090-09-11" });
  const previousElementFromPoint = (document as Document & { elementFromPoint?: unknown }).elementFromPoint;
  Object.defineProperty(document, "elementFromPoint", { configurable: true, value: () => target });
  fireEvent.pointerDown(wrapper, { pointerId: 1, pointerType: "mouse", button: 0, clientX: 10, clientY: 10 });
  fireEvent.pointerMove(wrapper, { pointerId: 1, pointerType: "mouse", clientX: 17, clientY: 10 });
  fireEvent.pointerUp(wrapper, { pointerId: 1, pointerType: "mouse", clientX: 17, clientY: 10 });
  Object.defineProperty(document, "elementFromPoint", { configurable: true, value: previousElementFromPoint });
  await waitFor(() => expect(server.calls.filter(call => call.method === "PATCH")).toHaveLength(1));
  expect(server.calls.find(call => call.method === "PATCH")?.body).toMatchObject({ title: "Design review", description: "Keep description", rowVersion: 3 });
});
