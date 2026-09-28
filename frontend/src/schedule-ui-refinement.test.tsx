import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { afterEach, expect, test, vi } from "vitest";
import App from "./App";
import { http, json, project, schedule } from "./test/http";
import { useUnsavedChanges } from "./unsaved-changes";

afterEach(() => { vi.restoreAllMocks(); window.location.hash = ""; sessionStorage.clear(); });
const mount = (path: string) => { window.location.hash = `#${path}`; render(<App />); return userEvent.setup(); };

test("schedule list switches to one primary representation with direct full edit and time change", async () => {
  http();
  const user = mount("/projects/p1/schedules");
  await screen.findByRole("grid", { name: "월간 일정" });
  await user.click(screen.getByRole("button", { name: "목록 보기" }));
  expect(screen.queryByRole("grid", { name: "월간 일정" })).not.toBeInTheDocument();
  const list = await screen.findByRole("region", { name: "일정 목록" });
  expect(within(list).getByRole("link", { name: "일정 편집" })).toHaveAttribute("href", "#/projects/p1/schedules/s1/edit");
  expect(within(list).getByRole("button", { name: "시간 변경: Design review" })).toBeEnabled();
});

test("calendar date creation transfers the selected date and one hour duration", async () => {
  http();
  const user = mount("/projects/p1/schedules");
  const grid = await screen.findByRole("grid", { name: "월간 일정" });
  fireEvent.change(screen.getByLabelText("기준 날짜"), { target: { value: "2090-09-10" } });
  const day = await within(grid).findByRole("gridcell", { name: "2090-09-10" });
  await user.click(within(day).getByRole("button", { name: "이 날짜에 일정 추가" }));
  expect(await screen.findByRole("heading", { name: "일정 만들기" })).toBeInTheDocument();
  expect(screen.getByLabelText("시작")).toHaveValue("2090-09-10T09:00");
  expect(screen.getByLabelText("종료")).toHaveValue("2090-09-10T10:00");
});

function GuardProbe() {
  const [value, setValue] = useState("");
  const guard = useUnsavedChanges(Boolean(value));
  return <><input aria-label="초안" value={value} onChange={event => setValue(event.target.value)} /><button type="button" onClick={() => { if (guard.confirmDiscard()) setValue(""); }}>닫기</button><output>{value || "pristine"}</output></>;
}

test("dirty editor close prompts once and preserves draft when declined", async () => {
  const user = userEvent.setup();
  const confirm = vi.spyOn(window, "confirm").mockReturnValue(false);
  render(<GuardProbe />);
  await user.type(screen.getByLabelText("초안"), "draft");
  await user.click(screen.getByRole("button", { name: "닫기" }));
  expect(confirm).toHaveBeenCalledTimes(1);
  expect(screen.getByDisplayValue("draft")).toBeInTheDocument();
  confirm.mockReturnValue(true);
  await user.click(screen.getByRole("button", { name: "닫기" }));
  expect(screen.getByText("pristine")).toBeInTheDocument();
});

test("dirty hook blocks unload while modified and modified new-tab intent does not discard the current draft", async () => {
  http();
  const user = mount("/projects/p1/schedules/new");
  await user.type(await screen.findByLabelText("제목"), "Draft");
  const unload = new Event("beforeunload", { cancelable: true });
  window.dispatchEvent(unload);
  expect(unload.defaultPrevented).toBe(true);
  const confirm = vi.spyOn(window, "confirm");
  fireEvent.click(screen.getByRole("link", { name: "취소" }), { ctrlKey: true });
  expect(confirm).not.toHaveBeenCalled();
  expect(screen.getByLabelText("제목")).toHaveValue("Draft");
});

test("dirty time dialog treats native cancel as guarded Escape and preserves the edit when declined", async () => {
  http();
  const user = mount("/projects/p1/schedules");
  await user.click((await screen.findAllByRole("button", { name: "시간 변경: Design review" }))[0]);
  fireEvent.change(screen.getByLabelText("시작"), { target: { value: "2090-09-10T10:30" } });
  const confirm = vi.spyOn(window, "confirm").mockReturnValue(false);
  const dialog = screen.getByRole("dialog");
  fireEvent(dialog, new Event("cancel", { cancelable: true }));
  expect(confirm).toHaveBeenCalledTimes(1);
  expect(dialog).toHaveAttribute("open");
  confirm.mockReturnValue(true);
  fireEvent(dialog, new Event("cancel", { cancelable: true }));
  await waitFor(() => expect(dialog).not.toHaveAttribute("open"));
});

test("real schedule editor rejects route departure without losing draft or focus", async () => {
  http();
  const user = mount("/projects/p1/schedules/new");
  const title = await screen.findByLabelText("제목");
  await user.type(title, "Draft");
  const confirm = vi.spyOn(window, "confirm").mockReturnValue(false);

  window.location.hash = "#/projects/p1/schedules";
  await waitFor(() => expect(confirm).toHaveBeenCalledTimes(1));
  await waitFor(() => expect(window.location.hash).toBe("#/projects/p1/schedules/new"));
  expect(screen.getByLabelText("제목")).toHaveValue("Draft");
  expect(screen.getByLabelText("제목")).toHaveFocus();

  confirm.mockReturnValue(true);
  await user.click(screen.getByRole("link", { name: "취소" }));
  await waitFor(() => expect(window.location.hash).toBe("#/projects/p1/schedules"));
  expect(confirm).toHaveBeenCalledTimes(2);
});

test("dirty new schedule guards context query changes and accepted replacement applies validated context", async () => {
  http();
  const user = mount("/projects/p1/schedules/new?date=2090-09-10&start=2090-09-10T09:00&zone=Asia%2FSeoul");
  const title = await screen.findByLabelText("제목");
  await user.type(title, "Draft");
  const confirm = vi.spyOn(window, "confirm").mockReturnValue(false);

  window.location.hash = "#/projects/p1/schedules/new?date=2090-09-11&start=2090-09-11T13:00&zone=Asia%2FSeoul";
  await waitFor(() => expect(confirm).toHaveBeenCalledTimes(1));
  await waitFor(() => expect(window.location.hash).toContain("2090-09-10"));
  expect(title).toHaveValue("Draft");
  expect(screen.getByLabelText("시작")).toHaveValue("2090-09-10T09:00");

  confirm.mockReturnValue(true);
  window.location.hash = "#/projects/p1/schedules/new?date=2090-09-11&start=2090-09-11T13:00&zone=Asia%2FSeoul";
  await waitFor(() => expect(screen.getByLabelText("제목")).toHaveValue(""));
  expect(screen.getByLabelText("시작")).toHaveValue("2090-09-11T13:00");
  expect(screen.getByLabelText("종료")).toHaveValue("2090-09-11T14:00");
});

test("automatic end preserves DST instants and stops tracking after manual edit", async () => {
  const server = http();
  let saved: any;
  server.on("POST", "/api/v1/projects/p1/schedules", body => {
    saved = body;
    return json({ ...schedule, ...body });
  });
  const user = mount("/projects/p1/schedules/new?start=2026-11-01T00:30&zone=America%2FNew_York");
  await screen.findByLabelText("제목");
  expect(screen.getByLabelText("종료")).toHaveValue("2026-11-01T01:30");
  await user.type(screen.getByLabelText("제목"), "Overlap");
  await user.click(screen.getByRole("button", { name: "일정 저장" }));
  await waitFor(() => expect(saved).toBeTruthy());
  expect(saved.startsAt).toBe("2026-11-01T04:30:00.000Z");
  expect(saved.endsAt).toBe("2026-11-01T05:30:00.000Z");

  window.location.hash = "#/projects/p1/schedules/new?start=2026-03-08T01:30&zone=America%2FNew_York";
  await waitFor(() => expect(screen.getByLabelText("종료")).toHaveValue("2026-03-08T03:30"));
  fireEvent.change(screen.getByLabelText("종료"), { target: { value: "2026-03-08T04:00" } });
  fireEvent.change(screen.getByLabelText("시작"), { target: { value: "2026-03-08T03:30" } });
  expect(screen.getByLabelText("종료")).toHaveValue("2026-03-08T04:00");
});

test("populated week day offers keyboard time selection without changing the 1152px canvas contract", async () => {
  http();
  const user = mount("/projects/p1/schedules");
  fireEvent.change(await screen.findByLabelText("기준 날짜"), { target: { value: "2090-09-10" } });
  await user.click(screen.getByRole("button", { name: "주간 보기" }));
  const picker = await screen.findByRole("combobox", { name: "2090-09-10 일정 시작 시간" });
  expect(within(picker).queryByRole("option", { name: "10:00" })).not.toBeInTheDocument();
  await user.selectOptions(picker, "570");
  expect(await screen.findByLabelText("시작")).toHaveValue("2090-09-10T09:30");
});

test.each([
  ["VIEWER", false],
  ["MEMBER", true],
] as const)("week creation control follows %s create capability", async (role, expected) => {
  const server = http();
  server.on("GET", "/api/v1/projects", () => json([{ ...project, role }]));
  const user = mount("/projects/p1/schedules");
  fireEvent.change(await screen.findByLabelText("기준 날짜"), { target: { value: "2090-09-10" } });
  await user.click(screen.getByRole("button", { name: "주간 보기" }));
  const picker = await screen.findByRole("grid", { name: "주간 일정" });
  expect(within(picker).queryByRole("combobox", { name: "2090-09-10 일정 시작 시간" }) !== null).toBe(expected);
});

test("409 shows both versions, retries immediately, and latest apply preserves fresh subminutes", async () => {
  const server = http();
  let patchCount = 0;
  let detailCount = 0;
  const initial = { ...schedule, startsAt: "2090-09-10T01:00:15.010Z", endsAt: "2090-09-10T02:00:15.020Z" };
  const latest = { ...schedule, title: "Server title", rowVersion: 8, startsAt: "2090-09-10T01:00:45.123Z", endsAt: "2090-09-10T02:00:45.456Z" };
  server.on("GET", "/api/v1/projects/p1/schedules/s1", () => json(detailCount++ === 0 ? initial : latest));
  server.on("PATCH", "/api/v1/projects/p1/schedules/s1", body => {
    patchCount += 1;
    if (patchCount <= 2) return json({ code: "ROW_VERSION_CONFLICT" }, 409);
    expect(body.rowVersion).toBe(8);
    expect(body.startsAt).toBe(latest.startsAt);
    expect(body.endsAt).toBe(latest.endsAt);
    return json({ ...latest, ...body, rowVersion: 9 });
  });
  const user = mount("/projects/p1/schedules/s1/edit");
  await screen.findByDisplayValue("Design review");
  await user.clear(screen.getByLabelText("제목"));
  await user.type(screen.getByLabelText("제목"), "My title");
  await user.click(screen.getByRole("button", { name: "일정 저장" }));
  expect(await screen.findByText(/서버 최신값/)).toBeInTheDocument();
  expect(screen.getByText("내 입력", { selector: "dt" })).toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "내 입력으로 즉시 재시도" }));
  await waitFor(() => expect(patchCount).toBe(2));
  await screen.findByRole("button", { name: "최신값 적용" });
  await user.click(screen.getByRole("button", { name: "최신값 적용" }));
  await user.clear(screen.getByLabelText("제목"));
  await user.type(screen.getByLabelText("제목"), "Unrelated title edit");
  await user.click(screen.getByRole("button", { name: "일정 저장" }));
  await waitFor(() => expect(patchCount).toBe(3));
});

test("409 latest-read failure keeps the draft and exposes a working recovery action", async () => {
  const server = http();
  let reads = 0;
  const latest = { ...schedule, title: "Recovered latest", rowVersion: 12 };
  server.on("GET", "/api/v1/projects/p1/schedules/s1", () => {
    reads += 1;
    return reads === 2 ? json({ code: "READ_FAILED" }, 500) : json(reads > 2 ? latest : schedule);
  });
  server.on("PATCH", "/api/v1/projects/p1/schedules/s1", () => json({ code: "ROW_VERSION_CONFLICT" }, 409));
  const user = mount("/projects/p1/schedules/s1/edit");
  await screen.findByDisplayValue("Design review");
  await user.type(screen.getByLabelText("제목"), " retained");
  await user.click(screen.getByRole("button", { name: "일정 저장" }));
  const retry = await screen.findByRole("button", { name: "최신값 다시 불러오기" });
  expect(screen.getByLabelText("제목")).toHaveValue("Design review retained");
  await user.click(retry);
  expect(await screen.findByText("Recovered latest", { exact: false })).toBeInTheDocument();
});

test("secondary server validation opens the disclosure and points to the hidden field", async () => {
  const server = http();
  server.on("POST", "/api/v1/projects/p1/schedules", () => json({
    code: "VALIDATION_FAILED",
    fieldErrors: [{ field: "externalAttendeeEmails[0]", message: "외부 참석자 오류" }],
  }, 400));
  const user = mount("/projects/p1/schedules/new?start=2090-09-10T09:00&zone=Asia%2FSeoul");
  await user.type(await screen.findByLabelText("제목"), "Validation");
  await user.click(screen.getByRole("button", { name: "일정 저장" }));
  const external = await screen.findByLabelText("외부 참석자 이메일");
  expect(document.getElementById("server-error-external")).toHaveTextContent("외부 참석자 오류");
  expect(external.closest("details")).toHaveAttribute("open");
  expect(external).toHaveAttribute("aria-invalid", "true");
});
