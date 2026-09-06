import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, expect, test, vi } from "vitest";
import App from "./App";
import { http, json } from "./test/http";

afterEach(() => { vi.unstubAllGlobals(); window.history.replaceState(null, "", "/"); sessionStorage.clear(); });

const mount = (path = "/") => { window.location.hash = `#${path}`; render(<App />); };
const expectNoBusinessNavigation = () => {
  expect(screen.queryByRole("navigation", { name: "주 메뉴" })).not.toBeInTheDocument();
  expect(screen.queryByRole("link", { name: "알림" })).not.toBeInTheDocument();
  expect(screen.queryByRole("link", { name: "Calendar" })).not.toBeInTheDocument();
};

test("signed-out entry shows Google login and preserves invitation continuation without business navigation", async () => {
  const server = http();
  server.on("GET", "/api/v1/me", () => json({ code: "UNAUTHENTICATED" }, 401));
  mount("/invitations/t1");
  const login = await screen.findByRole("link", { name: "Google로 로그인" });
  expect(login).toHaveAttribute("href", "/oauth2/authorization/google");
  expectNoBusinessNavigation();
  fireEvent.click(login);
  expect(sessionStorage.getItem("ai-erp.invitation")).toBe("#/invitations/t1");
  expect(server.calls.some(call => call.url === "/api/v1/invitations/t1")).toBe(false);
});

test("OAuth callback failure returns to Google login without business navigation", async () => {
  const server = http();
  server.on("GET", "/api/v1/me", () => json({ code: "UNAUTHENTICATED" }, 401));
  window.history.replaceState(null, "", "/?login=failed");
  sessionStorage.setItem("ai-erp.invitation", "#/invitations/t1");
  mount();
  expect(await screen.findByRole("link", { name: "Google로 로그인" })).toHaveAttribute("href", "/oauth2/authorization/google");
  expectNoBusinessNavigation();
  expect(sessionStorage.getItem("ai-erp.invitation")).toBe("#/invitations/t1");
});

test.each([
  { login: "CONFIGURATION_REQUIRED", loginUrl: null },
  { login: "READY", loginUrl: null },
])("login configuration $login with no URL hides business navigation", async configuration => {
  const server = http();
  server.on("GET", "/api/v1/system/configuration", () => json({ ...configuration, calendar: "READY" }));
  mount();
  await screen.findByText("Google 로그인이 구성되지 않았습니다.");
  expectNoBusinessNavigation();
  expect(screen.queryByRole("link", { name: "Google로 로그인" })).not.toBeInTheDocument();
});

test.each(["/api/v1/system/configuration", "/api/v1/me"])("pending %s hides business navigation", async endpoint => {
  const server = http();
  let finish!: (response: Response) => void;
  server.on("GET", endpoint, () => new Promise(resolve => { finish = resolve; }));
  mount();
  await waitFor(() => expect(finish).toBeTypeOf("function"));
  expect(screen.getByRole("status")).toHaveTextContent("불러오는 중입니다.");
  expectNoBusinessNavigation();
  finish(json({ code: "AUTH_FAILED" }, 500));
  await screen.findByRole("alert");
});

test.each(["/api/v1/system/configuration", "/api/v1/me"])("failed %s hides business navigation and retains retry", async endpoint => {
  const server = http();
  server.on("GET", endpoint, () => json({ code: "AUTH_FAILED" }, 500));
  mount();
  expect(await screen.findByRole("alert")).toHaveTextContent("AUTH_FAILED");
  expectNoBusinessNavigation();
  expect(screen.getByRole("button", { name: "다시 시도" })).toBeEnabled();
});

test("signed-in entry retains business navigation", async () => {
  http();
  mount();
  await screen.findByRole("heading", { name: "프로젝트" });
  const navigation = within(screen.getByRole("navigation", { name: "주 메뉴" }));
  expect(navigation.getByRole("link", { name: "알림" })).toHaveAttribute("href", "#/notifications");
  expect(navigation.getByRole("link", { name: "Calendar" })).toHaveAttribute("href", "#/calendar");
  expect(screen.queryByRole("link", { name: "Google로 로그인" })).not.toBeInTheDocument();
});
