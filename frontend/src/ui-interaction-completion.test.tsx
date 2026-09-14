import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, test, vi } from "vitest";
import App from "./App";
import { http, json, schedule } from "./test/http";

afterEach(() => {
  vi.useRealTimers();
  window.location.hash = "";
  sessionStorage.clear();
});

const mount = (path: string) => {
  window.location.hash = `#${path}`;
  render(<App />);
  return userEvent.setup();
};

test("notification popup keeps schedule context, closes, and restores trigger focus", async () => {
  http();
  const user = mount("/projects/p1/schedules");
  await screen.findByRole("heading", { name: "프로젝트 일정" });
  const trigger = screen.getByRole("button", { name: "알림" });
  await user.click(trigger);
  await screen.findByRole("dialog", { name: "알림" });
  expect(window.location.hash).toBe("#/projects/p1/schedules");
  expect(await screen.findByText("일정 확정")).toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "닫기" }));
  await waitFor(() => expect(screen.queryByRole("dialog", { name: "알림" })).not.toBeInTheDocument());
  expect(trigger).toHaveFocus();
  expect(screen.getByRole("heading", { name: "프로젝트 일정" })).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "알림" })).toHaveAttribute("aria-expanded", "false");
});

test("notification popup related link closes and follows the validated hash", async () => {
  http();
  const user = mount("/projects/p1");
  await screen.findByRole("heading", { name: "프로젝트 개요" });
  await user.click(screen.getByRole("button", { name: "알림" }));
  await screen.findByRole("dialog", { name: "알림" });
  await user.click(await screen.findByRole("link", { name: "관련 일정 보기" }));
  expect(window.location.hash).toBe("#/projects/p1/schedules/s1");
  await screen.findByRole("heading", { name: "Design review" });
  expect(screen.queryByRole("dialog", { name: "알림" })).not.toBeInTheDocument();
});

test("date jump validates in place, cancels without moving, and accepts a valid civil date", async () => {
  http();
  const user = mount("/projects/p1/schedules");
  await screen.findByRole("heading", { name: "프로젝트 일정" });
  const period = screen.getByRole("button", { name: "날짜로 이동" });
  await user.click(period);
  const dialog = await screen.findByRole("dialog", { name: "날짜로 이동" });
  const input = screen.getByLabelText("기준 날짜");
  fireEvent.change(input, { target: { value: "" } });
  await user.click(screen.getByRole("button", { name: "이동" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("YYYY-MM-DD");
  expect(dialog).toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "취소" }));
  await waitFor(() => expect(screen.queryByRole("dialog", { name: "날짜로 이동" })).not.toBeInTheDocument());
  expect(period).toHaveTextContent("2090년 9월");

  await user.click(period);
  fireEvent.change(screen.getByLabelText("기준 날짜"), { target: { value: "2090-10-02" } });
  await user.click(screen.getByRole("button", { name: "이동" }));
  await waitFor(() => expect(period).toHaveTextContent("2090년 10월"));
  expect(period).toHaveFocus();
});

test("date jump rejects an impossible civil date without changing the anchor", async () => {
  http();
  const user = mount("/projects/p1/schedules");
  await screen.findByRole("heading", { name: "프로젝트 일정" });
  const period = screen.getByRole("button", { name: "날짜로 이동" });
  await user.click(period);
  fireEvent.change(screen.getByLabelText("기준 날짜"), { target: { value: "2090-02-31" } });
  await user.click(screen.getByRole("button", { name: "이동" }));
  expect(await screen.findByRole("alert")).toBeInTheDocument();
  expect(screen.getByRole("dialog", { name: "날짜로 이동" })).toBeInTheDocument();
  fireEvent(screen.getByRole("dialog", { name: "날짜로 이동" }), new Event("cancel", { cancelable: true }));
  await waitFor(() => expect(screen.queryByRole("dialog", { name: "날짜로 이동" })).not.toBeInTheDocument());
  expect(period).toHaveTextContent("2090년 9월");
});

test("direct notifications route retains full page content", async () => {
  const server = http();
  server.on("GET", "/api/v1/notifications", () => json([]));
  mount("/notifications");
  expect(await screen.findByRole("heading", { name: "알림" })).toBeInTheDocument();
  expect(screen.queryByRole("dialog", { name: "알림" })).not.toBeInTheDocument();
  expect(await screen.findByText("새 알림이 없습니다")).toBeInTheDocument();
});

test("Today uses the active display zone and resets the result page without clearing filters", async () => {
  const server = http();
  server.on("GET", "/api/v1/projects/p1/schedules", (_, url) =>
    json(Array.from({ length: 20 }, (_, index) => ({
      ...schedule,
      id: `s${url.searchParams.get("page")}-${index}`,
      startsAt: "2080-01-01T00:00:00Z",
      endsAt: "2080-01-01T01:00:00Z",
    }))),
  );
  const user = mount("/projects/p1/schedules");
  await screen.findByRole("heading", { name: "프로젝트 일정" });
  await user.click(await screen.findByRole("button", { name: "다음 일정 페이지" }));
  await waitFor(() => expect(screen.getByText("현재 2페이지")).toBeInTheDocument());
  await user.click(screen.getByText("상세 필터"));
  const search = screen.getByLabelText("검색");
  const zone = screen.getByLabelText("표시 시간대");
  fireEvent.change(search, { target: { value: "Design" } });
  fireEvent.change(zone, { target: { value: "America/New_York" } });
  await waitFor(() => expect(screen.getByText("현재 1페이지")).toBeInTheDocument());
  expect(search).toHaveValue("Design");
  expect(zone).toHaveValue("America/New_York");
  await user.click(screen.getByRole("button", { name: "다음 일정 페이지" }));
  await waitFor(() => expect(screen.getByText("현재 2페이지")).toBeInTheDocument());
  fireEvent.click(screen.getByRole("button", { name: "오늘" }));
  expect(screen.getByText("현재 1페이지")).toBeInTheDocument();
  expect(search).toHaveValue("Design");
  expect(zone).toHaveValue("America/New_York");
  fireEvent.click(screen.getByRole("button", { name: "날짜로 이동" }));
  expect(screen.getByLabelText("기준 날짜")).toHaveValue("2090-09-09");
  fireEvent.click(screen.getByRole("button", { name: "취소" }));
  expect(server.calls.some((call) => call.url.includes("page=0"))).toBe(true);
});

test("mobile menu Escape does not consume the notification dialog Escape", async () => {
  http();
  const user = mount("/projects/p1");
  await screen.findByRole("heading", { name: "프로젝트 개요" });
  await user.click(screen.getByRole("button", { name: "메뉴" }));
  await user.click(screen.getByRole("button", { name: "알림" }));
  await screen.findByRole("dialog", { name: "알림" });
  await user.keyboard("{Escape}");
  await waitFor(() => expect(screen.queryByRole("dialog", { name: "알림" })).not.toBeInTheDocument());
});
