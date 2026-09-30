import { render, screen, waitFor } from "@testing-library/react";
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
