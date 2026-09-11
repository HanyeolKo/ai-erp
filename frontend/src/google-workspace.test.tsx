import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, test, vi } from "vitest";
import { focusManager } from "@tanstack/react-query";
import App from "./App";
import { http, json } from "./test/http";

afterEach(() => {
  vi.unstubAllGlobals();
  window.location.hash = "";
  sessionStorage.clear();
  focusManager.setFocused(undefined);
});

const mount = (path: string) => {
  window.location.hash = `#${path}`;
  render(<App />);
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
