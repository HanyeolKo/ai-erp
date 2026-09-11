import { act, cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, test, vi } from "vitest";
import { focusManager } from "@tanstack/react-query";
import { StrictMode } from "react";
import App from "./App";
import { http, json } from "./test/http";

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  window.location.hash = "";
  sessionStorage.clear();
  focusManager.setFocused(undefined);
});

const mount = (path: string, strict = false) => {
  window.location.hash = `#${path}`;
  render(strict ? <StrictMode><App /></StrictMode> : <App />);
  return userEvent.setup();
};

test("Google service connection requests only the selected feature and exposes the returned local authorization link", async () => {
  const server = http();
  server.on("GET", "/api/v1/google/connection", () => json({ configurationRequired: false, accountEmail: "manager@example.test", drive: { status: "NOT_CONNECTED" }, gmail: { status: "PERMISSION_REQUIRED" }, calendar: { status: "CONNECTED" } }));
  server.on("POST", "/api/v1/google/connect", (body) => { expect(body).toEqual({ feature: "DRIVE" }); return json({ authorizationUrl: "/oauth2/authorization/google" }); });
  const user = mount("/account/google");
  await screen.findByRole("heading", { name: "Google 서비스" });
  await user.click((await screen.findAllByRole("button", { name: "Google 권한 연결" }))[0]);
  expect(await screen.findByRole("link", { name: "Google 동의 화면 열기" })).toHaveAttribute("href", "/oauth2/authorization/google");
  expect(server.calls.filter((call) => call.method === "POST")).toHaveLength(1);
});

test("Google callback query renders the services screen and keeps connected feedback untrusted", async () => {
  const server = http();
  server.on("GET", "/api/v1/google/connection", () => json({ configurationRequired: false, accountEmail: "manager@example.test", drive: { status: "NOT_CONNECTED" }, gmail: { status: "NOT_CONNECTED" }, calendar: { status: "NOT_CONNECTED" } }));
  mount("/account/google?outcome=connected");
  expect(await screen.findByRole("heading", { name: "Google 서비스" })).toBeInTheDocument();
  expect(await screen.findByText("인증 화면에서 돌아왔습니다. 아래 연결 상태를 확인하세요.")).toBeInTheDocument();
  expect(screen.queryByRole("heading", { name: "화면을 찾을 수 없습니다." })).not.toBeInTheDocument();
  expect(screen.queryByText("Google 서비스 연결이 완료되었습니다.")).not.toBeInTheDocument();
  expect(screen.getAllByText("연결되지 않음")).toHaveLength(3);
});

test.each([
  ["cancelled", "Google 권한 연결을 취소했습니다. 아래 연결 상태를 확인하세요."],
  ["denied", "Google 권한이 승인되지 않았습니다. 아래 연결 상태를 확인하세요."],
  ["failed", "Google 서비스 연결을 완료하지 못했습니다. 아래 연결 상태를 확인하세요."],
])("Google callback outcome %s keeps an exit link and avoids the not-found screen", async (outcome, message) => {
  const server = http();
  server.on("GET", "/api/v1/google/connection", () => json({ configurationRequired: false, accountEmail: "manager@example.test", drive: { status: "NOT_CONNECTED" }, gmail: { status: "NOT_CONNECTED" }, calendar: { status: "NOT_CONNECTED" } }));
  mount(`/account/google?outcome=${outcome}`);
  expect(await screen.findByText(message)).toBeInTheDocument();
  expect(screen.getByRole("link", { name: "내 계정으로 돌아가기" })).toHaveAttribute("href", "#/account");
  expect(screen.queryByRole("heading", { name: "화면을 찾을 수 없습니다." })).not.toBeInTheDocument();
});

test("direct Google entry clears an invalid external return target", async () => {
  const server = http();
  server.on("GET", "/api/v1/google/connection", () => json({ configurationRequired: false, accountEmail: "manager@example.test", drive: { status: "NOT_CONNECTED" }, gmail: { status: "NOT_CONNECTED" }, calendar: { status: "NOT_CONNECTED" } }));
  sessionStorage.setItem("ai-erp.google-return", JSON.stringify({ userId: "u1", returnTo: "https://evil.example.test", createdAt: Date.now() }));
  mount("/account/google");
  expect(await screen.findByRole("link", { name: "내 계정으로 돌아가기" })).toHaveAttribute("href", "#/account");
  expect(sessionStorage.getItem("ai-erp.google-return")).toBeNull();
});

test("configuration-required Google settings can refetch into the personal permission flow", async () => {
  const server = http();
  let reads = 0;
  server.on("GET", "/api/v1/google/connection", () => {
    reads += 1;
    return reads === 1
      ? json({ configurationRequired: true, accountEmail: null, drive: { status: "NOT_CONNECTED" }, gmail: { status: "NOT_CONNECTED" }, calendar: { status: "NOT_CONNECTED" } })
      : json({ configurationRequired: false, accountEmail: "manager@example.test", drive: { status: "NOT_CONNECTED" }, gmail: { status: "NOT_CONNECTED" }, calendar: { status: "NOT_CONNECTED" } });
  });
  const user = mount("/account/google");
  await screen.findByText("현재 Google 연결을 시작할 수 없습니다.");
  await user.click(screen.getByRole("button", { name: "연결 상태 다시 확인" }));
  expect(await screen.findAllByRole("button", { name: "Google 권한 연결" })).toHaveLength(3);
  expect(screen.queryByText("서비스 설정이 완료된 뒤에 개인 Google 권한 연결을 시작할 수 있습니다.")).not.toBeInTheDocument();
});

test("project Google entry survives the callback route and consumes its return context once", async () => {
  const server = http();
  server.on("GET", "/api/v1/google/connection", () => json({ configurationRequired: false, accountEmail: "manager@example.test", drive: { status: "NOT_CONNECTED" }, gmail: { status: "NOT_CONNECTED" }, calendar: { status: "NOT_CONNECTED" } }));
  server.on("GET", "/api/v1/projects/p1/files", () => json({ files: [], hasNext: false }));
  const user = mount("/projects/p1/files", true);
  await user.click(await screen.findByRole("link", { name: "Google 연결 설정" }));
  expect(window.location.hash).toBe("#/account/google?returnTo=%2Fprojects%2Fp1%2Ffiles");
  await screen.findByRole("heading", { name: "Google 서비스" });
  window.location.hash = "#/account/google?outcome=connected";
  const returnLink = await screen.findByRole("link", { name: "프로젝트 파일로 돌아가기" });
  expect(returnLink).toHaveAttribute("href", "#/projects/p1/files");
  expect(sessionStorage.getItem("ai-erp.google-return")).toBeNull();
});

test.each(["denied", "cancelled"])("%s callback can retry consent while preserving the original return timestamp", async (outcome) => {
  const server = http();
  const originalCreatedAt = Date.now() - 30_000;
  server.on("GET", "/api/v1/google/connection", () => json({ configurationRequired: false, accountEmail: "manager@example.test", drive: { status: "NOT_CONNECTED" }, gmail: { status: "NOT_CONNECTED" }, calendar: { status: "NOT_CONNECTED" } }));
  server.on("POST", "/api/v1/google/connect", (body) => { expect(body).toEqual({ feature: "DRIVE" }); return json({ authorizationUrl: "/oauth2/authorization/google" }); });
  sessionStorage.setItem("ai-erp.google-return", JSON.stringify({ userId: "u1", returnTo: "/projects/p1/files", createdAt: originalCreatedAt }));
  const user = mount(`/account/google?outcome=${outcome}`);
  await screen.findByRole("link", { name: "프로젝트 파일로 돌아가기" });
  expect(sessionStorage.getItem("ai-erp.google-return")).toBeNull();
  await user.click((await screen.findAllByRole("button", { name: "Google 권한 연결" }))[0]);
  const consent = await screen.findByRole("link", { name: "Google 동의 화면 열기" });
  consent.addEventListener("click", (event) => event.preventDefault(), { once: true });
  await user.click(consent);
  expect(JSON.parse(sessionStorage.getItem("ai-erp.google-return") || "null")).toEqual({ userId: "u1", returnTo: "/projects/p1/files", createdAt: originalCreatedAt });
  cleanup();
  window.location.hash = "#/account/google?outcome=connected";
  render(<StrictMode><App /></StrictMode>);
  expect(await screen.findByRole("link", { name: "프로젝트 파일로 돌아가기" })).toBeInTheDocument();
});

test.each(["expired", "different-user"])("%s callback context cannot be restored during a consent retry", async (mode) => {
  const server = http();
  const originalCreatedAt = mode === "expired" ? Date.now() - 10 * 60 * 1000 - 1 : Date.now();
  server.on("GET", "/api/v1/google/connection", () => json({ configurationRequired: false, accountEmail: "manager@example.test", drive: { status: "NOT_CONNECTED" }, gmail: { status: "NOT_CONNECTED" }, calendar: { status: "NOT_CONNECTED" } }));
  server.on("POST", "/api/v1/google/connect", () => json({ authorizationUrl: "/oauth2/authorization/google" }));
  if (mode === "different-user") server.on("GET", "/api/v1/me", () => json({ id: "u2", displayName: "다른 사용자", email: "u2@example.test", authorities: ["ROLE_USER"] }));
  sessionStorage.setItem("ai-erp.google-return", JSON.stringify({ userId: "u1", returnTo: "/projects/p1/files", createdAt: originalCreatedAt }));
  const user = mount("/account/google?outcome=denied");
  await screen.findByRole("link", { name: "내 계정으로 돌아가기" });
  expect(sessionStorage.getItem("ai-erp.google-return")).toBeNull();
  await user.click((await screen.findAllByRole("button", { name: "Google 권한 연결" }))[0]);
  const consent = await screen.findByRole("link", { name: "Google 동의 화면 열기" });
  consent.addEventListener("click", (event) => event.preventDefault(), { once: true });
  await user.click(consent);
  expect(sessionStorage.getItem("ai-erp.google-return")).toBeNull();
});

test("callback return context survives an App remount under StrictMode", async () => {
  const server = http();
  server.on("GET", "/api/v1/google/connection", () => json({ configurationRequired: false, accountEmail: "manager@example.test", drive: { status: "NOT_CONNECTED" }, gmail: { status: "NOT_CONNECTED" }, calendar: { status: "NOT_CONNECTED" } }));
  sessionStorage.setItem("ai-erp.google-return", JSON.stringify({ userId: "u1", returnTo: "/projects/p1/files", createdAt: Date.now() }));
  window.location.hash = "#/account/google?outcome=connected";
  render(<StrictMode><App /></StrictMode>);
  expect(await screen.findByRole("link", { name: "프로젝트 파일로 돌아가기" })).toHaveAttribute("href", "#/projects/p1/files");
  expect(sessionStorage.getItem("ai-erp.google-return")).toBeNull();
});

test("unknown callback outcome is ignored without falling into the not-found route", async () => {
  const server = http();
  server.on("GET", "/api/v1/google/connection", () => json({ configurationRequired: false, accountEmail: "manager@example.test", drive: { status: "NOT_CONNECTED" }, gmail: { status: "NOT_CONNECTED" }, calendar: { status: "NOT_CONNECTED" } }));
  mount("/account/google?outcome=unexpected");
  expect(await screen.findByRole("heading", { name: "Google 서비스" })).toBeInTheDocument();
  expect(screen.queryByText("unexpected")).not.toBeInTheDocument();
  expect(screen.queryByRole("heading", { name: "화면을 찾을 수 없습니다." })).not.toBeInTheDocument();
});

test.each(["configuration", "error"])("connected callback cannot fabricate success when the fresh connection response is %s", async (label) => {
  const server = http();
  server.on("GET", "/api/v1/google/connection", () => label === "configuration"
    ? json({ configurationRequired: true, accountEmail: null, drive: { status: "NOT_CONNECTED" }, gmail: { status: "NOT_CONNECTED" }, calendar: { status: "NOT_CONNECTED" } })
    : json({ code: "GOOGLE_UNAVAILABLE" }, 500));
  mount("/account/google?outcome=connected");
  expect(await screen.findByText("인증 화면에서 돌아왔습니다. 아래 연결 상태를 확인하세요.")).toBeInTheDocument();
  expect(screen.queryByText("Google 서비스 연결이 완료되었습니다.")).not.toBeInTheDocument();
  expect(screen.queryByRole("link", { name: "Google 동의 화면 열기" })).not.toBeInTheDocument();
});

test("mounted identity change clears project context without rebinding it", async () => {
  const server = http();
  let userId = "u1";
  server.on("GET", "/api/v1/google/connection", () => json({ configurationRequired: false, accountEmail: "manager@example.test", drive: { status: "NOT_CONNECTED" }, gmail: { status: "NOT_CONNECTED" }, calendar: { status: "NOT_CONNECTED" } }));
  server.on("GET", "/api/v1/me", () => json({ id: userId, displayName: "사용자", email: `${userId}@example.test`, authorities: ["ROLE_USER"] }));
  window.location.hash = "#/account/google?returnTo=%2Fprojects%2Fp1%2Ffiles";
  sessionStorage.setItem("ai-erp.google-return", JSON.stringify({ userId: "u1", returnTo: "/projects/p1/files", createdAt: Date.now() }));
  render(<App />);
  await screen.findByRole("link", { name: "프로젝트 파일로 돌아가기" });
  userId = "u2";
  await act(async () => {
    vi.setSystemTime(new Date("2090-09-10T00:00:16Z"));
    focusManager.setFocused(false);
    focusManager.setFocused(true);
  });
  await waitFor(() => expect(server.calls.filter((call) => call.method === "GET" && call.url === "/api/v1/me").length).toBeGreaterThan(1));
  await waitFor(() => expect(screen.getByRole("link", { name: "내 계정으로 돌아가기" })).toBeInTheDocument());
  expect(sessionStorage.getItem("ai-erp.google-return")).toBeNull();
});

test("expired callback return context clears persistent storage and memory link", async () => {
  const server = http();
  server.on("GET", "/api/v1/google/connection", () => json({ configurationRequired: false, accountEmail: "manager@example.test", drive: { status: "NOT_CONNECTED" }, gmail: { status: "NOT_CONNECTED" }, calendar: { status: "NOT_CONNECTED" } }));
  const baselineNow = Date.now();
  vi.setSystemTime(new Date(baselineNow));
  sessionStorage.setItem("ai-erp.google-return", JSON.stringify({ userId: "u1", returnTo: "/projects/p1/files", createdAt: baselineNow - (10 * 60 * 1000 - 500) }));
  mount("/account/google?outcome=connected");
  await screen.findByRole("link", { name: "프로젝트 파일로 돌아가기" });
  vi.setSystemTime(new Date(baselineNow + 501));
  expect(await screen.findByRole("link", { name: "내 계정으로 돌아가기" })).toBeInTheDocument();
  expect(sessionStorage.getItem("ai-erp.google-return")).toBeNull();
});

test("project return waits for a fresh connection response before exposing attachment", async () => {
  const server = http();
  let reads = 0;
  let finish!: (response: Response) => void;
  server.on("GET", "/api/v1/google/connection", () => {
    reads += 1;
    return reads === 1
      ? json({ configurationRequired: false, accountEmail: "manager@example.test", drive: { status: "CONNECTED" }, gmail: { status: "NOT_CONNECTED" }, calendar: { status: "NOT_CONNECTED" } })
      : new Promise<Response>((resolve) => { finish = resolve; });
  });
  server.on("GET", "/api/v1/projects/p1/files", () => json({ files: [], hasNext: false }));
  mount("/account/google");
  await screen.findByRole("heading", { name: "Google 서비스" });
  window.location.hash = "#/projects/p1/files";
  await screen.findByRole("heading", { name: "프로젝트 파일" });
  await waitFor(() => expect(reads).toBe(2));
  expect(finish).toBeDefined();
  expect(screen.queryByRole("button", { name: "Drive 파일 첨부" })).not.toBeInTheDocument();
  await act(async () => { finish(json({ configurationRequired: false, accountEmail: "manager@example.test", drive: { status: "CONNECTED" }, gmail: { status: "NOT_CONNECTED" }, calendar: { status: "NOT_CONNECTED" } })); });
  expect(await screen.findByRole("button", { name: "Drive 파일 첨부" })).toBeInTheDocument();
});

test("storage write failure keeps the actual settings navigation usable", async () => {
  const server = http();
  server.on("GET", "/api/v1/google/connection", () => json({ configurationRequired: false, accountEmail: "manager@example.test", drive: { status: "NOT_CONNECTED" }, gmail: { status: "NOT_CONNECTED" }, calendar: { status: "NOT_CONNECTED" } }));
  server.on("GET", "/api/v1/projects/p1/files", () => json({ files: [], hasNext: false }));
  const setItem = vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => { throw new Error("storage unavailable"); });
  const user = mount("/projects/p1/files");
  await user.click(await screen.findByRole("link", { name: "Google 연결 설정" }));
  expect(await screen.findByRole("link", { name: "내 계정으로 돌아가기" })).toBeInTheDocument();
  setItem.mockRestore();
});

test("configuration-required Google state blocks consent and project attachment even with contradictory Drive status", async () => {
  const server = http();
  server.on("GET", "/api/v1/google/connection", () => json({ configurationRequired: true, accountEmail: null, drive: { status: "CONNECTED" }, gmail: { status: "CONNECTED" }, calendar: { status: "CONNECTED" } }));
  server.on("GET", "/api/v1/projects/p1/files", () => json({ files: [], hasNext: false }));
  const user = mount("/projects/p1/files");
  await screen.findByText("현재 Google 연결을 시작할 수 없습니다.");
  expect(screen.queryByRole("button", { name: "Drive 파일 첨부" })).not.toBeInTheDocument();
  await user.click(await screen.findByRole("link", { name: "Google 연결 설정" }));
  expect(await screen.findByRole("button", { name: "연결 상태 다시 확인" })).toBeInTheDocument();
  expect(screen.queryByRole("link", { name: "Google 동의 화면 열기" })).not.toBeInTheDocument();
  expect(screen.queryByRole("button", { name: /Google 권한 연결/ })).not.toBeInTheDocument();
});

test("Google connection refresh hides the previous private account through denial and failure until a fresh success", async () => {
  const server = http();
  let reads = 0;
  server.on("GET", "/api/v1/google/connection", () => {
    reads += 1;
    if (reads === 2) return json({ code: "GOOGLE_FORBIDDEN" }, 403);
    if (reads === 3) return json({ code: "GOOGLE_UNAVAILABLE" }, 500);
    return json({ configurationRequired: false, accountEmail: "fresh@example.test", drive: { status: "CONNECTED" }, gmail: { status: "CONNECTED" }, calendar: { status: "CONNECTED" } });
  });
  const user = mount("/account/google");
  await screen.findByText("fresh@example.test");
  await user.click(screen.getByRole("button", { name: "연결 상태 새로고침" }));
  await waitFor(() => expect(screen.queryByText("fresh@example.test")).not.toBeInTheDocument());
  await user.click(await screen.findByRole("button", { name: "다시 시도" }));
  await waitFor(() => expect(screen.queryByText("fresh@example.test")).not.toBeInTheDocument());
  await user.click(await screen.findByRole("button", { name: "다시 시도" }));
  expect(await screen.findByText("fresh@example.test")).toBeInTheDocument();
});

test("Gmail closes a private detail dialog when a later connection check is denied", async () => {
  const server = http();
  let denied = false;
  server.on("GET", "/api/v1/google/connection", () => denied
    ? json({ code: "GOOGLE_FORBIDDEN" }, 403)
    : json({ configurationRequired: false, accountEmail: "manager@example.test", drive: { status: "CONNECTED" }, gmail: { status: "CONNECTED" }, calendar: { status: "NOT_CONNECTED" } }));
  server.on("GET", "/api/v1/google/mail/messages", () => json({ messages: [{ id: "message-1", subject: "Private subject", from: "sender@example.test", to: ["manager@example.test"], snippet: "Private preview", internalDate: "2090-09-10T00:00:00Z", unread: true }], nextPageToken: null }));
  server.on("GET", "/api/v1/google/mail/messages/message-1", () => json({ id: "message-1", subject: "Private subject", from: "sender@example.test", to: ["manager@example.test"], cc: [], date: "2090-09-10T00:00:00Z", bodyText: "Private body", truncated: false }));
  const user = mount("/account/mail");
  await user.click(await screen.findByRole("button", { name: /Private subject/ }));
  await screen.findByText("Private body");
  denied = true;
  await act(async () => { vi.setSystemTime(new Date("2090-09-10T00:01:00Z")); focusManager.setFocused(false); focusManager.setFocused(true); });
  await waitFor(() => expect(server.calls.filter((call) => call.method === "GET" && call.url === "/api/v1/google/connection").length).toBeGreaterThan(1));
  await waitFor(() => expect(screen.queryByText("Private body")).not.toBeInTheDocument());
  expect(screen.queryByRole("dialog", { name: "메일 상세" })).not.toBeInTheDocument();
});

test("Gmail closes a private compose dialog when a later connection check is denied", async () => {
  const server = http();
  let denied = false;
  server.on("GET", "/api/v1/google/connection", () => denied
    ? json({ code: "GOOGLE_FORBIDDEN" }, 403)
    : json({ configurationRequired: false, accountEmail: "manager@example.test", drive: { status: "CONNECTED" }, gmail: { status: "CONNECTED" }, calendar: { status: "NOT_CONNECTED" } }));
  server.on("GET", "/api/v1/google/mail/messages", () => json({ messages: [], nextPageToken: null }));
  const user = mount("/account/mail");
  await user.click(await screen.findByRole("button", { name: "메일 작성" }));
  await user.type(screen.getByLabelText("제목"), "Private draft");
  denied = true;
  await act(async () => { vi.setSystemTime(new Date("2090-09-10T00:01:00Z")); focusManager.setFocused(false); focusManager.setFocused(true); });
  await waitFor(() => expect(server.calls.filter((call) => call.method === "GET" && call.url === "/api/v1/google/connection").length).toBeGreaterThan(1));
  await waitFor(() => expect(screen.queryByDisplayValue("Private draft")).not.toBeInTheDocument());
  expect(screen.queryByRole("dialog", { name: "메일 작성" })).not.toBeInTheDocument();
});

test("Google connection refuses an external authorization URL", async () => {
  const server = http();
  server.on("GET", "/api/v1/google/connection", () => json({ configurationRequired: false, accountEmail: "manager@example.test", drive: { status: "NOT_CONNECTED" }, gmail: { status: "NOT_CONNECTED" }, calendar: { status: "NOT_CONNECTED" } }));
  server.on("POST", "/api/v1/google/connect", () => json({ authorizationUrl: "https://evil.example.test/oauth" }));
  const user = mount("/account/google");
  await user.click((await screen.findAllByRole("button", { name: "Google 권한 연결" }))[0]);
  expect(await screen.findByRole("alert")).toHaveTextContent("연결 주소를 확인하지 못했습니다");
  expect(screen.queryByRole("link", { name: "Google 동의 화면 열기" })).not.toBeInTheDocument();
});

test("Drive project reference requires an explicit selection and attach action", async () => {
  const server = http();
  server.on("GET", "/api/v1/google/connection", () => json({ configurationRequired: false, accountEmail: "manager@example.test", drive: { status: "CONNECTED" }, gmail: { status: "CONNECTED" }, calendar: { status: "NOT_CONNECTED" } }));
  server.on("GET", "/api/v1/projects/p1/files", () => json({ files: [], hasNext: false }));
  server.on("GET", "/api/v1/google/drive/files", () => json({ files: [{ id: "file-1", name: "Roadmap.pdf", mimeType: "application/pdf", url: "https://drive.google.com/open?id=file-1", modifiedTime: "2090-09-09T00:00:00Z" }], nextPageToken: null }));
  server.on("POST", "/api/v1/projects/p1/files", (body) => { expect(body).toEqual({ fileId: "file-1" }); return json({ id: "ref-1", fileId: "file-1", name: "Roadmap.pdf", mimeType: "application/pdf", url: "https://drive.google.com/open?id=file-1", attachedBy: "김관리자", attachedAt: "2090-09-10T00:00:00Z", canRemove: true }); });
  const user = mount("/projects/p1/files");
  await user.click(await screen.findByRole("button", { name: "Drive 파일 첨부" }));
  expect(server.calls.filter((call) => call.method === "POST")).toHaveLength(0);
  await user.click(await screen.findByRole("button", { name: /Roadmap\.pdf/ }));
  await user.click(screen.getByRole("button", { name: "첨부" }));
  await waitFor(() => expect(server.calls.filter((call) => call.method === "POST")).toHaveLength(1));
});

test("Gmail requires review before one send and retains the request id", async () => {
  const server = http();
  server.on("GET", "/api/v1/google/connection", () => json({ configurationRequired: false, accountEmail: "manager@example.test", drive: { status: "CONNECTED" }, gmail: { status: "CONNECTED" }, calendar: { status: "NOT_CONNECTED" } }));
  server.on("GET", "/api/v1/google/mail/messages", () => json({ messages: [], nextPageToken: null }));
  server.on("GET", "/api/v1/google/mail/sends/00000000-0000-4000-8000-000000000001", () => json({ requestId: "00000000-0000-4000-8000-000000000001", status: "SENT", messageId: "m-1" }));
  server.on("POST", "/api/v1/google/mail/send", (body) => { expect(body).toMatchObject({ requestId: "00000000-0000-4000-8000-000000000001", to: ["person@example.test"], cc: [], bcc: [], subject: "안내", body: "내용" }); return json({ requestId: body.requestId, status: "SENT", messageId: "m-1" }); });
  vi.spyOn(crypto, "randomUUID").mockReturnValue("00000000-0000-4000-8000-000000000001");
  const user = mount("/account/mail");
  await user.click(await screen.findByRole("button", { name: "메일 작성" }));
  await user.type(screen.getByRole("textbox", { name: "To" }), "person@example.test");
  await user.type(screen.getByLabelText("제목"), "안내");
  await user.type(screen.getByLabelText("본문"), "내용");
  await user.click(screen.getByRole("button", { name: "보내기 확인" }));
  expect(server.calls.filter((call) => call.method === "POST" && call.url === "/api/v1/google/mail/send")).toHaveLength(0);
  await user.click(screen.getByRole("button", { name: "메일 보내기" }));
  await waitFor(() => expect(server.calls.filter((call) => call.method === "POST" && call.url === "/api/v1/google/mail/send")).toHaveLength(1));
  expect(sessionStorage.getItem("ai-erp.google-mail-send:u1")).toBe("00000000-0000-4000-8000-000000000001");
});

test("UNKNOWN Gmail send checks its receipt without automatically sending again", async () => {
  const server = http();
  server.on("GET", "/api/v1/google/connection", () => json({ configurationRequired: false, accountEmail: "manager@example.test", drive: { status: "CONNECTED" }, gmail: { status: "CONNECTED" }, calendar: { status: "NOT_CONNECTED" } }));
  server.on("GET", "/api/v1/google/mail/messages", () => json({ messages: [], nextPageToken: null }));
  server.on("POST", "/api/v1/google/mail/send", () => json({ requestId: "00000000-0000-4000-8000-000000000002", status: "UNKNOWN" }));
  server.on("GET", "/api/v1/google/mail/sends/00000000-0000-4000-8000-000000000002", () => json({ requestId: "00000000-0000-4000-8000-000000000002", status: "UNKNOWN" }));
  vi.spyOn(crypto, "randomUUID").mockReturnValue("00000000-0000-4000-8000-000000000002");
  const user = mount("/account/mail");
  await user.click(await screen.findByRole("button", { name: "메일 작성" }));
  await user.type(screen.getByRole("textbox", { name: "To" }), "person@example.test");
  await user.type(screen.getByLabelText("제목"), "상태 확인");
  await user.click(screen.getByRole("button", { name: "보내기 확인" }));
  await user.click(screen.getByRole("button", { name: "메일 보내기" }));
  await screen.findByText(/전송 결과를 확인하지 못했습니다/);
  const sendsBefore = server.calls.filter((call) => call.method === "POST" && call.url === "/api/v1/google/mail/send").length;
  await user.click(screen.getByRole("button", { name: "전송 결과 확인" }));
  await waitFor(() => expect(server.calls.filter((call) => call.method === "GET" && call.url.includes("/api/v1/google/mail/sends/")).length).toBeGreaterThan(0));
  expect(server.calls.filter((call) => call.method === "POST" && call.url === "/api/v1/google/mail/send")).toHaveLength(sendsBefore);
});

test("Gmail send retains one request across a route leave while the POST is pending", async () => {
  const server = http();
  let finish!: (response: Response) => void;
  let sent = false;
  server.on("GET", "/api/v1/google/connection", () => json({ configurationRequired: false, accountEmail: "manager@example.test", drive: { status: "CONNECTED" }, gmail: { status: "CONNECTED" }, calendar: { status: "NOT_CONNECTED" } }));
  server.on("GET", "/api/v1/google/mail/messages", () => json({ messages: [], nextPageToken: null }));
  server.on("GET", "/api/v1/google/mail/sends/00000000-0000-4000-8000-000000000003", () => json({ requestId: "00000000-0000-4000-8000-000000000003", status: sent ? "SENT" : "SENDING" }));
  server.on("POST", "/api/v1/google/mail/send", () => new Promise<Response>((resolve) => { finish = resolve; }));
  vi.spyOn(crypto, "randomUUID").mockReturnValue("00000000-0000-4000-8000-000000000003");
  const user = mount("/account/mail");
  await user.click(await screen.findByRole("button", { name: "메일 작성" }));
  await user.type(screen.getByRole("textbox", { name: "To" }), "person@example.test");
  await user.type(screen.getByRole("textbox", { name: "제목" }), "지연 확인");
  await user.click(screen.getByRole("button", { name: "보내기 확인" }));
  await user.click(screen.getByRole("button", { name: "메일 보내기" }));
  expect(sessionStorage.getItem("ai-erp.google-mail-send:u1")).toBe("00000000-0000-4000-8000-000000000003");
  window.location.hash = "#/account";
  await waitFor(() => expect(screen.getByRole("heading", { name: "내 계정" })).toBeInTheDocument());
  window.location.hash = "#/account/mail";
  await screen.findByRole("button", { name: "메일 작성" });
  expect(sessionStorage.getItem("ai-erp.google-mail-send:u1")).toBe("00000000-0000-4000-8000-000000000003");
  await waitFor(() => { expect(server.calls.some((call) => call.method === "GET" && call.url.includes("/api/v1/google/mail/sends/"))).toBe(true); });
  expect(server.calls.filter((call) => call.method === "POST" && call.url === "/api/v1/google/mail/send")).toHaveLength(1);
  sent = true;
  await act(async () => { finish(json({ requestId: "00000000-0000-4000-8000-000000000003", status: "SENT" })); });
  await user.click(await screen.findByRole("button", { name: "전송 결과 확인" }));
  expect(await screen.findByText("메일을 보냈습니다.")).toBeInTheDocument();
});

test("project Calendar settings binds only after selecting a writable calendar", async () => {
  const server = http();
  server.on("GET", "/api/v1/google/connection", () => json({ configurationRequired: false, accountEmail: "manager@example.test", drive: { status: "CONNECTED" }, gmail: { status: "CONNECTED" }, calendar: { status: "CONNECTED" } }));
  server.on("GET", "/api/v1/projects/p1/calendar", () => json({ status: "NOT_BOUND", isOwner: true, canManage: true, backfillPending: false }));
  server.on("GET", "/api/v1/google/calendars", () => json({ calendars: [{ id: "cal-1", name: "업무 일정" }], nextPageToken: null }));
  server.on("POST", "/api/v1/projects/p1/calendar", (body) => { expect(body).toEqual({ calendarId: "cal-1" }); return json({ status: "BOUND", calendarName: "업무 일정", ownerName: "김관리자", isOwner: true, canManage: true, backfillPending: true }); });
  const user = mount("/projects/p1/calendar");
  await screen.findByRole("heading", { name: "프로젝트 Calendar 설정" });
  expect(await screen.findByRole("button", { name: "이 Calendar 연결" })).toBeDisabled();
  await user.selectOptions(screen.getByLabelText("Calendar"), "cal-1");
  await user.click(screen.getByRole("button", { name: "이 Calendar 연결" }));
  await waitFor(() => expect(server.calls.filter((call) => call.method === "POST")).toHaveLength(1));
});
