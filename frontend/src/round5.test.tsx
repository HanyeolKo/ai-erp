import { render, screen, fireEvent, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, test, vi } from "vitest";
import App from "./App";
import { http, json, schedule } from "./test/http";
afterEach(() => { vi.unstubAllGlobals(); window.location.hash = ""; sessionStorage.clear(); });
const mount = (path: string) => { window.location.hash = `#${path}`; render(<App />); return userEvent.setup(); };
const setDate = (label: string, value: string) => fireEvent.change(screen.getByLabelText(label), { target: { value } });

test("P03 dates request another period with inclusive local dates converted to exact UTC bounds", async () => {
  const server = http();
  server.on("GET", "/api/v1/projects/p1/schedules", (_, url) => json(url.searchParams.get("from") === "2091-01-14T15:00:00.000Z" && url.searchParams.get("to") === "2091-01-20T15:00:00.000Z" ? [{ ...schedule, id: "s2", title: "January planning", startsAt: "2091-01-15T01:00:00Z", endsAt: "2091-01-15T02:00:00Z" }] : []));
  mount("/projects/p1/schedules");
  await screen.findAllByText("조건에 맞는 일정이 없습니다.", { selector: "p.schedule-empty" });
  setDate("날짜 이후", "2091-01-15"); setDate("날짜 이전", "2091-01-20");
  expect(await screen.findByRole("link", { name: /January planning/ })).toBeInTheDocument();
  expect(server.calls.some(c => c.url === "/api/v1/projects/p1/schedules?page=0&limit=20&from=2091-01-14T15%3A00%3A00.000Z&to=2091-01-20T15%3A00%3A00.000Z")).toBe(true);
  expect(server.calls.some(c => c.url.includes("/calendar"))).toBe(false);
});
test("P03 incomplete and reversed date filters show field validation without issuing a schedule request", async () => {
  const server = http(); mount("/projects/p1/schedules"); await screen.findByRole("grid", { name: "월간 일정" });
  const requests = () => server.calls.filter(c => c.url.includes("/schedules?")).length;
  const count = requests(); setDate("날짜 이후", "2091-01-20");
  expect(await screen.findByRole("alert")).toHaveTextContent("시작일과 종료일을 모두 입력하세요.");
  expect(screen.queryByText("조건에 맞는 일정이 없습니다.")).not.toBeInTheDocument();
  expect(requests()).toBe(count);
  setDate("날짜 이전", "2091-01-15");
  expect(await screen.findByRole("alert")).toHaveTextContent("종료일은 시작일보다 빠를 수 없습니다.");
  expect(requests()).toBe(count);
  expect(screen.queryByRole("link", { name: /Design review/ })).not.toBeInTheDocument();
});
test("P03 changing date range resets pagination and restores view range when cleared", async () => {
  const server = http();
  server.on("GET", "/api/v1/projects/p1/schedules", (_, url) => json(url.searchParams.get("page") === "0" && !url.searchParams.get("from")?.startsWith("2091") ? Array.from({ length: 20 }, (_, i) => ({ ...schedule, id: "s" + i })) : []));
  const user = mount("/projects/p1/schedules"); await screen.findByRole("grid", { name: "월간 일정" }); expect(server.calls.some(c => c.url.includes("/calendar"))).toBe(false);
  await user.click(screen.getByRole("button", { name: "다음 일정 페이지" })); await screen.findAllByText("조건에 맞는 일정이 없습니다.", { selector: "p.schedule-empty" });
  setDate("날짜 이후", "2091-01-15"); setDate("날짜 이전", "2091-01-20");
  await waitFor(() => expect(server.calls.some(c => c.url.includes("page=0&limit=20&from=2091-01-14"))).toBe(true));
  setDate("날짜 이후", ""); setDate("날짜 이전", "");
  expect(await within(screen.getByRole("region", { name: "일정 목록" })).findAllByRole("link", { name: /Design review/ })).toHaveLength(20);
  expect(screen.getByRole("button", { name: "이전 일정 페이지" })).toBeDisabled();
});
test("P03 date bounds use the selected timezone including daylight-saving offset changes", async () => {
  const server = http(); mount("/projects/p1/schedules"); await screen.findByLabelText("날짜 이후");
  setDate("표시 시간대", "America/New_York"); setDate("날짜 이후", "2026-03-08"); setDate("날짜 이전", "2026-03-08");
  await waitFor(() => expect(server.calls.some(c => c.url.includes("from=2026-03-08T05%3A00%3A00.000Z&to=2026-03-09T04%3A00%3A00.000Z"))).toBe(true));
});
test("P03 renders creator independently and mine removes another creator's row", async () => {
  const server = http(); server.on("GET", "/api/v1/projects/p1/schedules", () => json([{ ...schedule, createdBy: "u2" }]));
  const user = mount("/projects/p1/schedules"); const links = await screen.findAllByRole("link", { name: /Design review/ });
  const row = within(links[links.length - 1].closest("li")!);
  expect(row.getByText("확정")).toBeInTheDocument(); expect(row.getByText("확인 대기")).toBeInTheDocument();
  await user.click(screen.getByLabelText("내가 만든 일정")); expect(screen.queryByRole("link", { name: /Design review/ })).not.toBeInTheDocument();
  await user.click(screen.getByLabelText("내가 만든 일정")); expect(screen.getAllByRole("link", { name: /Design review/ }).length).toBeGreaterThan(0);
});
test.each(["/projects/p1", "#/projects/p1"])("P06 project invitation link %s navigates to the project dashboard", async link => {
  const server = http(); server.on("GET", "/api/v1/notifications", () => json([{ id: "n1", type: "INVITATION_ACCEPTED", link, readAt: "2090-09-10T00:00:00Z", createdAt: "2090-09-10T00:00:00Z" }]));
  const user = mount("/notifications"); const notification = await screen.findByRole("link", { name: "관련 프로젝트 보기" }); expect(notification).toHaveAttribute("href", "#/projects/p1");
  await user.click(notification); expect(await screen.findByRole("heading", { name: "프로젝트 개요" })).toBeInTheDocument();
});
test.each(["https://evil.test/projects/p1", "//evil.test/projects/p1", "javascript:alert(1)", "/projects/../calendar", "/projects/%2e%2e", "/projects/p1?redirect=evil", "/projects/p1#evil", "##/projects/p1", "/projects/p1/schedules/s1?x=1"])("P06 rejects unsafe API notification link %s", async link => {
  const server = http(); server.on("GET", "/api/v1/notifications", () => json([{ id: "n1", type: "INVITATION_ACCEPTED", link, readAt: "2090-09-10T00:00:00Z", createdAt: "2090-09-10T00:00:00Z" }]));
  mount("/notifications"); await screen.findByText("초대 수락"); expect(screen.queryByRole("link", { name: "관련 프로젝트 보기" })).not.toBeInTheDocument();
});
test.each([
  ["시간대", "America/", "2090-09-10T10:00", "2090-09-10T11:00", "지원하지 않는 IANA 시간대입니다."],
  ["시작", "America/New_York", "2026-03-08T02:30", "2026-03-08T04:00", "존재하지 않는 현지 시간입니다."],
  ["종료", "Asia/Seoul", "2090-09-10T11:00", "2090-09-10T10:00", "종료 시간은 시작 시간보다 뒤여야 합니다."]
])("P04 %s validation is programmatically associated with its input", async (label, zone, start, end, message) => {
  http(); const user = mount("/projects/p1/schedules/new"); await screen.findByLabelText("제목"); setDate("제목", "Planning"); setDate("시간대", zone); setDate("시작", start); setDate("종료", end);
  await user.click(screen.getByRole("button", { name: "일정 저장" })); const field = screen.getByLabelText(label); const error = await screen.findByText(message);
  expect(field).toHaveAttribute("aria-invalid", "true"); expect(error.id).not.toBe(""); expect(field).toHaveAttribute("aria-describedby", error.id); expect(field).toHaveAccessibleDescription(message);
});
