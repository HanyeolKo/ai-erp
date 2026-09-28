import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, test, vi } from "vitest";
import App from "./App";
import { http, json, project, schedule } from "./test/http";
import { terminateSession } from "./session";

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  window.location.hash = "";
  window.history.replaceState(null, "", "/");
  sessionStorage.clear();
});

const mount = (path = "/projects/p1/schedules") => {
  window.location.hash = `#${path}`;
  render(<App />);
  return userEvent.setup();
};

const schedulePath = "/api/v1/projects/p1/schedules";
const detailPath = "/api/v1/projects/p1/schedules/s1";
const patchPath = detailPath;
const actionName = "시간 변경: Design review";

async function openDialog(user: ReturnType<typeof userEvent.setup>) {
  const actions = within(await screen.findByRole("region", { name: "일정 목록" })).getAllByRole("button", { name: actionName });
  await user.click(actions[0]);
  await screen.findByRole("heading", { name: "시간 변경" });
}

function patchCalls(server: ReturnType<typeof http>) {
  return server.calls.filter(call => call.method === "PATCH" && call.url === patchPath);
}

test("dialog submit preserves title, nullable description and rowVersion while omitting participants", async () => {
  const server = http();
  const initial = { ...schedule, description: null };
  const updated = { ...initial, startsAt: "2090-09-11T01:00:00Z", endsAt: "2090-09-11T02:00:00Z", rowVersion: 4 };
  let current = initial;
  server.on("GET", schedulePath, () => json([current]));
  server.on("PATCH", patchPath, body => {
    expect(body).toEqual({
      title: "Design review",
      description: null,
      startsAt: "2090-09-11T01:00:00.000Z",
      endsAt: "2090-09-11T02:00:00.000Z",
      rowVersion: 3,
    });
    expect("memberParticipantIds" in body).toBe(false);
    expect("externalParticipants" in body).toBe(false);
    current = updated;
    return json(updated);
  });

  const user = mount();
  await openDialog(user);
  fireEvent.change(screen.getByLabelText("시작"), { target: { value: "2090-09-11T10:00" } });
  fireEvent.change(screen.getByLabelText("종료"), { target: { value: "2090-09-11T11:00" } });
  await user.click(screen.getByRole("button", { name: "변경 저장" }));
  await screen.findByText(/Design review 일정을/);
  expect(patchCalls(server)).toHaveLength(1);
});

test("unchanged dialog values do not issue PATCH", async () => {
  const server = http();
  server.on("PATCH", patchPath, () => json(schedule));
  const user = mount();
  await openDialog(user);
  await user.click(screen.getByRole("button", { name: "변경 저장" }));
  expect(patchCalls(server)).toHaveLength(0);
});

test("a nonpositive dialog interval is visibly rejected without PATCH", async () => {
  const server = http();
  server.on("PATCH", patchPath, () => json(schedule));
  const user = mount();
  await openDialog(user);
  fireEvent.change(screen.getByLabelText("종료"), { target: { value: "2090-09-10T09:59" } });
  await user.click(screen.getByRole("button", { name: "변경 저장" }));
  const dialog = screen.getByRole("dialog", { name: "시간 변경" });
  expect(within(dialog).getByRole("alert")).toHaveTextContent("종료 시간은 시작 시간보다 뒤여야 합니다.");
  expect(patchCalls(server)).toHaveLength(0);
  expect(screen.getByRole("heading", { name: "시간 변경" })).toBeInTheDocument();
});

test("a subminute no-op does not PATCH or truncate the stored instants", async () => {
  const server = http();
  const precise = { ...schedule, startsAt: "2090-09-10T01:00:30.125Z", endsAt: "2090-09-10T02:00:45.500Z" };
  server.on("GET", schedulePath, () => json([precise]));
  server.on("PATCH", patchPath, () => json(precise));
  const user = mount();
  await openDialog(user);
  await user.click(screen.getByRole("button", { name: "변경 저장" }));
  expect(patchCalls(server)).toHaveLength(0);
});

test("editing one dialog endpoint preserves the other endpoint seconds and milliseconds", async () => {
  const server = http();
  const precise = { ...schedule, startsAt: "2090-09-10T01:00:30.125Z", endsAt: "2090-09-10T02:00:45.500Z" };
  let body: any;
  server.on("GET", schedulePath, () => json([precise]));
  server.on("PATCH", patchPath, value => { body = value; return json({ ...precise, endsAt: "2090-09-10T02:30:00.000Z", rowVersion: 4 }); });
  const user = mount();
  await openDialog(user);
  fireEvent.change(screen.getByLabelText("종료"), { target: { value: "2090-09-10T11:30" } });
  await user.click(screen.getByRole("button", { name: "변경 저장" }));
  await screen.findByText(/Design review 일정을/);
  expect(body.startsAt).toBe(precise.startsAt);
  expect(body.endsAt).toBe("2090-09-10T02:30:00.000Z");
});

test("pending time write disables every time-change action and prevents duplicate submissions", async () => {
  const server = http();
  let finish!: (response: Response) => void;
  server.on("PATCH", patchPath, () => new Promise<Response>(resolve => { finish = resolve; }));
  const user = mount();
  await openDialog(user);
  fireEvent.change(screen.getByLabelText("종료"), { target: { value: "2090-09-10T11:30" } });
  await user.click(screen.getByRole("button", { name: "변경 저장" }));
  await screen.findByText("Design review 저장 중…");
  const actions = within(screen.getByRole("region", { name: "일정 목록" })).getAllByRole("button", { name: actionName });
  expect(actions).toHaveLength(1);
  expect(actions.every(button => (button as HTMLButtonElement).disabled)).toBe(true);
  expect(patchCalls(server)).toHaveLength(1);
  finish(json({ ...schedule, endsAt: "2090-09-10T02:30:00Z", rowVersion: 4 }));
  await screen.findByText(/Design review 일정을/);
});

test.each([
  ["MANAGER", "u2", "CONFIRMED", 1],
  ["MEMBER", "u1", "CONFIRMED", 1],
  ["MEMBER", "u2", "CONFIRMED", 0],
  ["VIEWER", "u1", "CONFIRMED", 0],
  ["MANAGER", "u1", "CANCELLED", 0],
] as const)("time-change visibility follows %s/%s/%s capability", async (role, creator, status, expectedActions) => {
  const server = http();
  server.on("GET", "/api/v1/projects", () => json([{ ...project, role }]));
  server.on("GET", schedulePath, () => json([{ ...schedule, createdBy: creator, status }]));
  mount();
  await screen.findByRole("grid", { name: "월간 일정" });
  expect(within(screen.getByRole("region", { name: "일정 목록" })).queryAllByRole("button", { name: actionName })).toHaveLength(expectedActions);
});

test("409 refetches targeted detail and project role, then removes editing after access changes", async () => {
  const server = http();
  let role = "MANAGER";
  let detailReads = 0;
  const latest = { ...schedule, rowVersion: 5, startsAt: "2090-09-10T03:00:00Z", endsAt: "2090-09-10T04:00:00Z" };
  server.on("GET", "/api/v1/projects", () => json([{ ...project, role }]));
  server.on("GET", schedulePath, () => json([latest]));
  server.on("GET", detailPath, () => { detailReads += 1; return json(latest); });
  server.on("PATCH", patchPath, () => {
    role = "VIEWER";
    return json({ code: "ROW_VERSION_CONFLICT" }, 409);
  });
  const user = mount();
  await openDialog(user);
  fireEvent.change(screen.getByLabelText("종료"), { target: { value: "2090-09-10T14:00" } });
  await user.click(screen.getByRole("button", { name: "변경 저장" }));
  await screen.findByRole("alert");
  await waitFor(() => expect(detailReads).toBeGreaterThan(0));
  expect(server.calls.filter(call => call.method === "GET" && call.url.startsWith("/api/v1/projects?")).length).toBeGreaterThan(1);
  expect(screen.getByRole("alert")).toHaveTextContent("다른 사용자가 이 일정을 변경했습니다.");
  expect(screen.queryByRole("button", { name: actionName })).not.toBeInTheDocument();
  expect(patchCalls(server)).toHaveLength(1);
});

test("response-lost write uses targeted detail when the refreshed range is empty", async () => {
  const server = http();
  const updated = { ...schedule, startsAt: "2091-01-10T01:00:00Z", endsAt: "2091-01-10T02:00:00Z", rowVersion: 4 };
  let listed = [schedule];
  let authoritative = schedule;
  let detailReads = 0;
  server.on("GET", schedulePath, () => json(listed));
  server.on("GET", detailPath, () => { detailReads += 1; return json(authoritative); });
  server.on("PATCH", patchPath, () => {
    authoritative = updated;
    listed = [];
    return json({ code: "WRITE_RESULT_UNKNOWN" }, 500);
  });
  const user = mount();
  await openDialog(user);
  fireEvent.change(screen.getByLabelText("시작"), { target: { value: "2091-01-10T10:00" } });
  fireEvent.change(screen.getByLabelText("종료"), { target: { value: "2091-01-10T11:00" } });
  await user.click(screen.getByRole("button", { name: "변경 저장" }));
  await screen.findByText(/현재 저장된 시간은/);
  expect(await screen.findAllByText("조건에 맞는 일정이 없습니다.")).not.toHaveLength(0);
  expect(detailReads).toBeGreaterThan(0);
  expect(patchCalls(server)).toHaveLength(1);
});

test("failed detail recovery keeps the row locked and exposes access retry until fresh reads succeed", async () => {
  const server = http();
  let detailForbidden = true;
  server.on("GET", schedulePath, () => json([schedule]));
  server.on("GET", detailPath, () => detailForbidden ? json({ code: "DETAIL_FORBIDDEN" }, 403) : json(schedule));
  server.on("PATCH", patchPath, () => json({ code: "WRITE_RESULT_UNKNOWN" }, 500));
  const user = mount();
  await openDialog(user);
  fireEvent.change(screen.getByLabelText("종료"), { target: { value: "2090-09-10T11:30" } });
  await user.click(screen.getByRole("button", { name: "변경 저장" }));
  await screen.findByRole("alert");
  expect(screen.getByRole("alert")).toHaveTextContent("편집을 잠갔습니다");
  expect(within(screen.getByRole("region", { name: "일정 목록" })).getAllByRole("button", { name: actionName }).every(button => (button as HTMLButtonElement).disabled)).toBe(true);
  const retry = screen.getByRole("button", { name: "접근 상태 다시 확인" });
  detailForbidden = false;
  await user.click(retry);
  await waitFor(() => expect(within(screen.getByRole("region", { name: "일정 목록" })).getAllByRole("button", { name: actionName }).some(button => !(button as HTMLButtonElement).disabled)).toBe(true));
});

test("late successful response after unmount does not update the departed route", async () => {
  const server = http();
  let finish!: (response: Response) => void;
  server.on("PATCH", patchPath, () => new Promise<Response>(resolve => { finish = resolve; }));
  const user = mount();
  await openDialog(user);
  fireEvent.change(screen.getByLabelText("종료"), { target: { value: "2090-09-10T11:30" } });
  await user.click(screen.getByRole("button", { name: "변경 저장" }));
  await screen.findByText("Design review 저장 중…");
  window.location.hash = "#/";
  window.dispatchEvent(new HashChangeEvent("hashchange"));
  await screen.findByRole("heading", { name: "프로젝트 선택" });
  finish(json({ ...schedule, endsAt: "2090-09-10T02:30:00Z", rowVersion: 4 }));
  await waitFor(() => expect(screen.queryByText(/저장했습니다/)).not.toBeInTheDocument());
});

test("late p1 response does not repaint a newly mounted p2 schedule route", async () => {
  const server = http();
  const projectB = { ...project, id: "p2", name: "Reporting" };
  const scheduleB = { ...schedule, id: "s2", projectId: "p2", title: "Reporting review" };
  let finish!: (response: Response) => void;
  server.on("GET", "/api/v1/projects", () => json([project, projectB]));
  server.on("GET", schedulePath, () => json([schedule]));
  server.on("GET", "/api/v1/projects/p2/schedules", () => json([scheduleB]));
  server.on("PATCH", patchPath, () => new Promise<Response>(resolve => { finish = resolve; }));
  const user = mount();
  await openDialog(user);
  fireEvent.change(screen.getByLabelText("종료"), { target: { value: "2090-09-10T11:30" } });
  await user.click(screen.getByRole("button", { name: "변경 저장" }));
  await screen.findByText("Design review 저장 중…");
  window.location.hash = "#/projects/p2/schedules";
  window.dispatchEvent(new HashChangeEvent("hashchange"));
  await waitFor(() => expect(screen.getByRole("link", { name: "일정" })).toHaveAttribute("href", "#/projects/p2/schedules"));
  await user.click(screen.getByRole("button", { name: "목록 보기" }));
  await screen.findByText("Reporting review");
  finish(json({ ...schedule, endsAt: "2090-09-10T02:30:00.000Z", rowVersion: 4 }));
  await waitFor(() => expect(screen.getByText("Reporting review")).toBeInTheDocument());
  expect(screen.queryByText(/Design review 일정을/)).not.toBeInTheDocument();
});

test("session termination ignores a late successful response", async () => {
  const server = http();
  let finish!: (response: Response) => void;
  server.on("PATCH", patchPath, () => new Promise<Response>(resolve => { finish = resolve; }));
  const user = mount();
  await openDialog(user);
  fireEvent.change(screen.getByLabelText("종료"), { target: { value: "2090-09-10T11:30" } });
  await user.click(screen.getByRole("button", { name: "변경 저장" }));
  await screen.findByText("Design review 저장 중…");
  terminateSession();
  finish(json({ ...schedule, endsAt: "2090-09-10T02:30:00Z", rowVersion: 4 }));
  await screen.findByRole("heading", { name: "다시 로그인해 주세요" });
  expect(screen.queryByText(/저장했습니다/)).not.toBeInTheDocument();
});
