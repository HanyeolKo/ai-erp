import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, test, vi } from "vitest";
import App from "./App";
import { http, invitation, json, project } from "./test/http";

afterEach(() => { cleanup(); vi.unstubAllGlobals(); window.history.replaceState(null, "", "/"); sessionStorage.clear(); });
const mount = (path = "/") => { window.location.hash = `#${path}`; render(<App />); return userEvent.setup(); };

test("empty account can open an invitation, accept it, and enter the project", async () => {
  const server = http();
  let accepted = false;
  server.on("GET", "/api/v1/projects", () => json(accepted ? [project] : []));
  server.on("GET", "/api/v1/invitations/t1", () => json({ ...invitation, status: accepted ? "ACCEPTED" : "PENDING" }));
  server.on("POST", "/api/v1/invitations/t1/accept", () => { accepted = true; return json({ ...invitation, status: "ACCEPTED" }); });
  const user = mount();
  await screen.findByText("아직 프로젝트가 없습니다");
  expect(screen.queryByRole("button", { name: "이전 프로젝트" })).not.toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "다음 프로젝트" })).not.toBeInTheDocument();
  expect(screen.getByText(/새 프로젝트를 만들거나 초대 코드/)).toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "초대로 참여" }));
  await user.type(screen.getByLabelText("초대 링크 또는 코드"), "#/invitations/t1");
  await user.click(screen.getByRole("button", { name: "초대 확인" }));
  await user.click(await screen.findByRole("button", { name: "초대 수락" }));
  const open = await screen.findByRole("link", { name: "프로젝트 열기" });
  expect(open).toHaveAttribute("href", "#/projects/p1");
  await user.click(open);
  await screen.findByRole("heading", { name: "프로젝트 개요" });
  expect(server.calls.filter(c => c.method === "POST" && c.url.endsWith("/accept"))).toHaveLength(1);
});

test.each(["https://evil.example/#/invitations/t1", "javascript:alert(1)", "#/projects/p1", "#/invitations/t1?next=bad"])("invitation input rejects unsafe or invalid route %s", async input => {
  const server = http(); server.on("GET", "/api/v1/projects", () => json([]));
  const user = mount();
  await user.click(await screen.findByRole("button", { name: "초대로 참여" }));
  const field = await screen.findByLabelText("초대 링크 또는 코드");
  await user.type(field, input); await user.click(screen.getByRole("button", { name: "초대 확인" }));
  expect(field).toHaveAttribute("aria-invalid", "true");
  expect(await screen.findByRole("alert")).toHaveTextContent("현재 서비스에서 발행한 초대 링크 또는 코드를 입력하세요.");
  expect(window.location.hash).toBe("#/");
  expect(server.calls.some(c => c.url.startsWith("/api/v1/invitations/"))).toBe(false);
});

test.each(["/invitations/t1", `${window.location.origin}/#/invitations/t1`])("invitation input accepts application URL %s", async input => {
  const server = http(); server.on("GET", "/api/v1/projects", () => json([]));
  const user = mount(); await user.click(await screen.findByRole("button", { name: "초대로 참여" }));
  await user.type(await screen.findByLabelText("초대 링크 또는 코드"), input);
  await user.click(screen.getByRole("button", { name: "초대 확인" }));
  await screen.findByRole("heading", { name: "프로젝트 초대" });
});

test("project loading and retry remain distinct from successful empty membership", async () => {
  const server = http(); let finish!: (response: Response) => void;
  server.on("GET", "/api/v1/projects", () => new Promise(resolve => { finish = resolve; }));
  const user = mount(); await waitFor(() => expect(finish).toBeTypeOf("function"));
  expect(screen.getByText("프로젝트를 불러오는 중…")).toBeInTheDocument();
  expect(screen.queryByText("아직 프로젝트가 없습니다")).not.toBeInTheDocument();
  finish(json({ code: "FAILED" }, 500));
  expect(await screen.findByRole("alert")).toHaveTextContent("프로젝트를 불러오지 못했습니다.");
  expect(screen.getByLabelText("초대 링크 또는 코드")).toBeEnabled();
  server.on("GET", "/api/v1/projects", () => json([]));
  await user.click(screen.getByRole("button", { name: "다시 시도" }));
  await screen.findByText("아직 프로젝트가 없습니다");
});

test("refresh disables duplicate requests and exposes newly available membership", async () => {
  const server = http(); server.on("GET", "/api/v1/projects", () => json([]));
  const user = mount(); await screen.findByText("아직 프로젝트가 없습니다");
  let finish!: (response: Response) => void;
  server.on("GET", "/api/v1/projects", () => new Promise(resolve => { finish = resolve; }));
  const refresh = screen.getByRole("button", { name: "목록 새로고침" });
  await user.click(refresh); await waitFor(() => expect(finish).toBeTypeOf("function"));
  expect(refresh).toBeDisabled(); fireEvent.click(refresh);
  finish(json([project])); await screen.findByRole("link", { name: /Planning/ });
  expect(server.calls.filter(c => c.url.startsWith("/api/v1/projects?"))).toHaveLength(2);
});

test("empty later project page does not imply no memberships and allows returning", async () => {
  const server = http(); server.on("GET", "/api/v1/projects", (_body, url) => json(url.searchParams.get("page") === "1" ? [] : Array.from({ length: 100 }, (_, i) => ({ ...project, id: `p${i}`, name: `Project ${i}` }))));
  const user = mount(); await user.click(await screen.findByRole("button", { name: "다음 프로젝트" }));
  await screen.findByText("이 페이지에 프로젝트가 없습니다");
  expect(screen.queryByText("아직 프로젝트가 없습니다")).not.toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "이전 프로젝트" }));
  await screen.findByRole("link", { name: /Project 0/ });
});

test("wrong invitation account can log out with CSRF and preserve safe continuation", async () => {
  const server = http(); let signedOut = false;
  server.on("GET", "/api/v1/invitations/t1", () => json({ code: "FORBIDDEN" }, 403));
  server.on("GET", "/api/v1/me", () => signedOut ? json({ code: "UNAUTHENTICATED" }, 401) : json({ id: "u1", displayName: "김관리자", email: "manager@example.test", authorities: ["ROLE_USER"] }));
  server.on("POST", "/api/v1/logout", () => { signedOut = true; return new Response(null, { status: 204 }); });
  const user = mount("/invitations/t1"); await screen.findByRole("alert");
  await user.click(screen.getByText("김관리자"));
  await user.click(screen.getByRole("button", { name: "다른 계정으로 로그인" }));
  await screen.findByRole("link", { name: "Google로 로그인" });
  expect(sessionStorage.getItem("ai-erp.invitation")).toBe("#/invitations/t1");
  expect(screen.queryByRole("navigation", { name: "주 메뉴" })).not.toBeInTheDocument();
  expect(server.calls.filter(c => c.method === "POST" && c.url === "/api/v1/logout")).toHaveLength(1);
});

test("failed logout retains authenticated screen and can retry", async () => {
  const server = http(); server.on("GET", "/api/v1/projects", () => json([]));
  server.on("POST", "/api/v1/logout", () => json({ code: "LOGOUT_FAILED" }, 500));
  const user = mount(); await screen.findByText("아직 프로젝트가 없습니다");
  await user.click(screen.getByText("김관리자"));
  await user.click(screen.getByRole("button", { name: "다른 계정으로 로그인" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("LOGOUT_FAILED");
  expect(screen.getByText("아직 프로젝트가 없습니다")).toBeInTheDocument();
});

test("navigation explicitly offers project selection outside a project", async () => {
  http(); mount("/notifications"); await screen.findByRole("heading", { name: "알림" });
  expect(screen.getByRole("link", { name: "AI ERP" })).toHaveAttribute("href", "#/" );
});

test("lost invite permission disables resubmission until access is refreshed", async () => {
  const server = http();
  vi.stubGlobal("confirm", () => true);
  let shareReads = 0;
  server.on("GET", "/api/v1/projects/p1/share-invitation", () => {
    shareReads += 1;
    return shareReads === 1
      ? json({ state: "ACTIVE", code: "7K3M9F2D6R8TWX4C", expiresAt: "2199-10-01T00:00:00Z" })
      : shareReads === 2
        ? json({ code: "READ_FAILED" }, 500)
        : json({ state: "ACTIVE", code: "7K3M9F2D6R8TWX4C", expiresAt: "2199-10-01T00:00:00Z" });
  });
  server.on("POST", "/api/v1/projects/p1/share-invitation", () => json({ code: "FORBIDDEN" }, 403));
  const user = mount("/projects/p1/invitations/new");
  await user.click(await screen.findByRole("button", { name: "구성원 초대" }));
  await screen.findByDisplayValue("7K3M-9F2D-6R8T-WX4C");
  await user.click(screen.getByRole("button", { name: "새 초대로 바꾸기" }));
  await screen.findAllByRole("alert");
  expect(screen.queryAllByDisplayValue(/7K3M/)).toHaveLength(0);
  await user.click(screen.getByRole("button", { name: "초대 상태 다시 확인" }));
  await waitFor(() => expect(screen.getByRole("button", { name: "새 초대로 바꾸기" })).toBeEnabled());
});

test("accepted invitation retains project entry if the following read fails", async () => {
  const server = http(); let accepted = false;
  server.on("GET", "/api/v1/invitations/t1", () => accepted ? json({ code: "READ_FAILED" }, 500) : json(invitation));
  server.on("POST", "/api/v1/invitations/t1/accept", () => { accepted = true; return json({ ...invitation, status: "ACCEPTED" }); });
  const user = mount("/invitations/t1"); await user.click(await screen.findByRole("button", { name: "초대 수락" }));
  await screen.findByRole("alert");
  expect(screen.getByRole("link", { name: "프로젝트 열기" })).toHaveAttribute("href", "#/projects/p1");
  expect(screen.queryByRole("button", { name: "초대 수락" })).not.toBeInTheDocument();
});

test.each([403, 404])("invitation mutation %s blocks another response until refreshed", async status => {
  const server = http(); server.on("POST", "/api/v1/invitations/t1/accept", () => json({ code: status === 403 ? "FORBIDDEN" : "NOT_FOUND" }, status));
  const user = mount("/invitations/t1"); await user.click(await screen.findByRole("button", { name: "초대 수락" }));
  await screen.findByRole("alert");
  expect(screen.queryByRole("button", { name: "초대 수락" })).not.toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "초대 거절" })).not.toBeInTheDocument();
  expect(screen.queryByText("서버에서 현재 Google 계정의 인증된 이메일과 초대 이메일이 일치함을 확인했습니다.")).not.toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "최신 초대 불러오기" }));
  await waitFor(() => expect(screen.getByRole("button", { name: "초대 수락" })).toBeEnabled());
});
