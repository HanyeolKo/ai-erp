import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, test, vi } from "vitest";
import App from "./App";
import { http } from "./test/http";

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  window.location.hash = "";
  window.history.replaceState(null, "", "/");
  sessionStorage.clear();
});

function pressEscape() {
  const event = new KeyboardEvent("keydown", { key: "Escape", bubbles: true, cancelable: true });
  document.dispatchEvent(event);
  return event;
}

test("an open notification dialog receives Escape before the underlying mobile menu", async () => {
  http();
  window.location.hash = "#/projects/p1/schedules";
  render(<App />);
  const user = userEvent.setup();
  await screen.findByRole("heading", { name: "프로젝트 일정" });
  const menu = screen.getByRole("button", { name: "메뉴" });
  const notification = screen.getByRole("button", { name: "알림" });
  await user.click(menu);
  expect(menu).toHaveAttribute("aria-expanded", "true");
  expect(screen.getByRole("complementary", { name: "프로젝트 메뉴" })).toHaveClass("is-open");
  await user.click(notification);
  const dialog = await screen.findByRole("dialog", { name: "알림" });
  const hash = window.location.hash;

  const keydown = pressEscape();
  expect(keydown.defaultPrevented).toBe(false);
  expect(menu).toHaveAttribute("aria-expanded", "true");
  expect(screen.getByRole("complementary", { name: "프로젝트 메뉴" })).toHaveClass("is-open");
  expect(screen.getByRole("dialog", { name: "알림" })).toBe(dialog);
  expect(menu).not.toHaveFocus();

  fireEvent(dialog, new Event("cancel", { cancelable: true }));
  await waitFor(() => expect(screen.queryByRole("dialog", { name: "알림" })).not.toBeInTheDocument());
  await waitFor(() => expect(notification).toHaveFocus());
  expect(menu).toHaveAttribute("aria-expanded", "true");
  expect(window.location.hash).toBe(hash);
});

test("menu-only Escape still closes the menu, prevents the default, and restores menu focus", async () => {
  http();
  window.location.hash = "#/projects/p1/schedules";
  render(<App />);
  const user = userEvent.setup();
  await screen.findByRole("heading", { name: "프로젝트 일정" });
  const menu = screen.getByRole("button", { name: "메뉴" });
  await user.click(menu);
  expect(menu).toHaveAttribute("aria-expanded", "true");

  const keydown = pressEscape();
  expect(keydown.defaultPrevented).toBe(true);
  await waitFor(() => expect(menu).toHaveAttribute("aria-expanded", "false"));
  expect(screen.getByRole("complementary", { name: "프로젝트 메뉴" })).not.toHaveClass("is-open");
  expect(menu).toHaveFocus();
});
