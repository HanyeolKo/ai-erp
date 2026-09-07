import { act, cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, test, vi } from "vitest";
import App from "./App";
import { http, json, project } from "./test/http";
import { beginSessionBoundary } from "./session";
import { resetAccessState } from "./state";

afterEach(() => { cleanup(); vi.unstubAllGlobals(); window.history.replaceState(null, "", "/"); sessionStorage.clear(); });
const mount = (path = "/") => { window.location.hash = `#${path}`; render(<App />); return userEvent.setup(); };

test("direct project creation sends only the trimmed name and a stable requestId", async () => {
  const server = http(); let created = false; let firstBody: any;
  server.on("GET", "/api/v1/projects", () => json(created ? [project] : []));
  server.on("POST", "/api/v1/projects", body => { firstBody = body; created = true; return json(project); });
  const user = mount(); await user.click(await screen.findByRole("button", { name: "새 프로젝트" }));
  await user.type(screen.getByLabelText("프로젝트 이름"), "  새 프로젝트  "); await user.click(screen.getByRole("button", { name: "프로젝트 만들기" }));
  await waitFor(() => expect(firstBody).toBeTruthy()); expect(firstBody.name).toBe("새 프로젝트"); expect(firstBody.groupId).toBeUndefined(); expect(firstBody.requestId).toMatch(/^[0-9a-f-]{36}$/i);
});

test("lobby accepts a Crockford code but rejects unsafe external URLs", async () => {
  const server = http(); server.on("GET", "/api/v1/project-invitations/7K3M9F2D6R8TWX4C", () => json({ state: "ACTIVE", projectId: "p1", projectName: "Planning", inviterName: "김관리자", role: "MEMBER", expiresAt: "2199-10-01T00:00:00Z", alreadyMember: false }));
  const user = mount(); await user.click(await screen.findByRole("button", { name: "초대로 참여" })); const input = screen.getByLabelText("초대 링크 또는 코드");
  await user.type(input, "7K3M-9F2D-6R8T-WX4C"); await user.click(screen.getByRole("button", { name: "초대 확인" })); await screen.findByRole("heading", { name: "프로젝트 참여" }); expect(window.location.hash).toBe("#/join/7K3M9F2D6R8TWX4C");
  expect(server.calls.some(c => c.url.includes("project-invitations"))).toBe(true);
  window.location.hash = "#/";
  await screen.findByRole("heading", { name: "프로젝트 선택" });
  await user.click(screen.getByRole("button", { name: "초대로 참여" }));
  const unsafe = screen.getByLabelText("초대 링크 또는 코드");
  await user.type(unsafe, "https://evil.example/#/join/7K3M9F2D6R8TWX4C");
  await user.click(screen.getByRole("button", { name: "초대 확인" }));
  expect(unsafe).toHaveAttribute("aria-invalid", "true");
  expect(window.location.hash).toBe("#/");
  expect(server.calls.filter(c => c.url.includes("project-invitations")).length).toBe(1);
});

test("share invitation is read before explicit generation and can be copied", async () => {
  const server = http(); server.on("GET", "/api/v1/projects/p1/members", () => json([{ userId: "u1", displayName: "김관리자", email: "manager@example.test", role: "MANAGER" }])); server.on("GET", "/api/v1/projects/p1/share-invitation", () => json({ state: "NOT_CREATED", code: null, expiresAt: null })); server.on("POST", "/api/v1/projects/p1/share-invitation", () => json({ state: "ACTIVE", code: "7K3M9F2D6R8TWX4C", expiresAt: "2199-10-01T00:00:00Z" }));
  const user = mount("/projects/p1/members"); await user.click(await screen.findByRole("button", { name: "구성원 초대" })); await screen.findByRole("heading", { name: "Planning에 구성원 초대" }); await screen.findByText("초대 만들기"); expect(server.calls.some(c => c.method === "GET" && c.url.endsWith("share-invitation"))).toBe(true); expect(server.calls.some(c => c.method === "POST" && c.url.endsWith("share-invitation"))).toBe(false); await user.click(screen.getByRole("button", { name: "초대 만들기" })); await screen.findByDisplayValue("7K3M-9F2D-6R8T-WX4C"); await user.click(screen.getByRole("button", { name: "코드 복사" })); await screen.findByText(/복사했습니다|자동 복사를 사용할 수 없습니다/); expect(screen.getByDisplayValue("7K3M-9F2D-6R8T-WX4C")).toBeInTheDocument();
});

test("expired share invitations do not expose a code or copy actions", async () => {
  const server = http();
  server.on(
    "GET",
    "/api/v1/projects/p1/members",
    () =>
      json([
        {
          userId: "u1",
          displayName: "김관리자",
          email: "manager@example.test",
          role: "MANAGER",
        },
      ]),
  );
  server.on(
    "GET",
    "/api/v1/projects/p1/share-invitation",
    () =>
      json({
        state: "ACTIVE",
        code: "7K3M9F2D6R8TWX4C",
        expiresAt: "2090-09-09T00:00:00Z",
      }),
  );
  const user = mount("/projects/p1/members");
  await user.click(await screen.findByRole("button", { name: "구성원 초대" }));
  expect(await screen.findByText(/초대는 만료되었습니다/)).toBeInTheDocument();
  expect(screen.queryByDisplayValue(/7K3M/)).not.toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "코드 복사" })).not.toBeInTheDocument();
});

test("join conflict refreshes the preview before deciding whether joining remains available", async () => {
  const server = http();
  let reads = 0;
  server.on("GET", "/api/v1/project-invitations/7K3M9F2D6R8TWX4C", () => {
    reads += 1;
    return reads === 1
      ? json({ state: "ACTIVE", projectId: "p1", projectName: "Planning", inviterName: "김관리자", role: "MEMBER", expiresAt: "2199-10-01T00:00:00Z", alreadyMember: false })
      : reads === 2
        ? json({ code: "TEMPORARY_FAILURE" }, 500)
        : json({ state: "ACTIVE", projectId: "p1", projectName: "Planning", inviterName: "김관리자", role: "MANAGER", expiresAt: "2199-10-01T00:00:00Z", alreadyMember: true });
  });
  server.on("POST", "/api/v1/project-invitations/7K3M9F2D6R8TWX4C/join", () => json({ code: "ALREADY_RESOLVED" }, 409));
  const user = mount("/join/7K3M9F2D6R8TWX4C");
  await user.click(await screen.findByRole("button", { name: "프로젝트 참여" }));
  await waitFor(() => expect(reads).toBe(2));
  expect(screen.queryByRole("button", { name: "프로젝트 참여" })).not.toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "다시 시도" }));
  await screen.findByRole("link", { name: "프로젝트 열기" });
  expect(reads).toBe(3);
  expect(screen.queryByText("받게 될 역할")).not.toBeInTheDocument();
});

test("share denial hides a cached code through failed recovery until a fresh allowed GET", async () => {
  const server = http();
  server.on(
    "GET",
    "/api/v1/projects/p1/members",
    () =>
      json([
        {
          userId: "u1",
          displayName: "김관리자",
          email: "manager@example.test",
          role: "MANAGER",
        },
      ]),
  );
  let shareReads = 0;
  server.on("GET", "/api/v1/projects/p1/share-invitation", () => {
    shareReads += 1;
    return shareReads === 1
      ? json({
          state: "ACTIVE",
          code: "7K3M9F2D6R8TWX4C",
          expiresAt: "2199-10-01T00:00:00Z",
        })
      : json({ code: "UPSTREAM" }, 500);
  });
  server.on(
    "POST",
    "/api/v1/projects/p1/share-invitation",
    () => json({ code: "FORBIDDEN" }, 403),
  );
  vi.stubGlobal("confirm", () => true);
  const user = mount("/projects/p1/members");
  await user.click(await screen.findByRole("button", { name: "구성원 초대" }));
  await screen.findByDisplayValue("7K3M-9F2D-6R8T-WX4C");
  await user.click(screen.getByRole("button", { name: "새 초대로 바꾸기" }));
  await screen.findByRole("button", { name: "초대 상태 다시 확인" });
  await waitFor(() => expect(shareReads).toBe(2));
  expect(screen.queryByDisplayValue(/7K3M/)).not.toBeInTheDocument();

  server.on(
    "GET",
    "/api/v1/projects/p1/share-invitation",
    () =>
      json({
        state: "ACTIVE",
        code: "7K3M9F2D6R8TWX4C",
        expiresAt: "2199-10-01T00:00:00Z",
      }),
  );
  await user.click(screen.getByRole("button", { name: "초대 상태 다시 확인" }));
  await screen.findByDisplayValue("7K3M-9F2D-6R8T-WX4C");
  expect(
    server.calls.filter((call) => call.url.endsWith("share-invitation")).length,
  ).toBe(4);
});

test("share POST result survives route leave and return without a second POST", async () => {
  const server = http();
  let created = false;
  let posts = 0;
  let finish!: (response: Response) => void;
  server.on("GET", "/api/v1/projects/p1/share-invitation", () =>
    created
      ? json({ state: "ACTIVE", code: "7K3M9F2D6R8TWX4C", expiresAt: "2199-10-01T00:00:00Z" })
      : json({ state: "NOT_CREATED", code: null, expiresAt: null }),
  );
  server.on("POST", "/api/v1/projects/p1/share-invitation", () => {
    posts += 1;
    return new Promise<Response>((resolve) => {
      finish = (response) => {
        created = true;
        resolve(response);
      };
    });
  });
  const user = mount("/projects/p1/members");
  await user.click(await screen.findByRole("button", { name: "구성원 초대" }));
  await user.click(await screen.findByRole("button", { name: "초대 만들기" }));
  await waitFor(() => expect(posts).toBe(1));

  window.location.hash = "#/notifications";
  await screen.findByRole("heading", { name: "알림" });
  window.location.hash = "#/projects/p1/members";
  await screen.findByRole("heading", { name: "구성원" });
  await user.click(screen.getByRole("button", { name: "구성원 초대" }));
  expect(await screen.findByRole("button", { name: "만드는 중…" })).toBeDisabled();
  expect(posts).toBe(1);

  await act(async () => finish(json({ state: "ACTIVE", code: "7K3M9F2D6R8TWX4C", expiresAt: "2199-10-01T00:00:00Z" })));
  await screen.findByDisplayValue("7K3M-9F2D-6R8T-WX4C");
  expect(posts).toBe(1);
});

test("a share read started during a pending POST cannot replace the mutation result", async () => {
  const server = http();
  let reads = 0;
  let posts = 0;
  let finishPost!: (response: Response) => void;
  let finishRead!: (response: Response) => void;
  server.on("GET", "/api/v1/projects/p1/share-invitation", () => {
    reads += 1;
    if (reads === 1) return json({ state: "NOT_CREATED", code: null, expiresAt: null });
    if (reads === 2)
      return new Promise<Response>((resolve) => {
        finishRead = resolve;
      });
    return json({ state: "ACTIVE", code: "7K3M9F2D6R8TWX4C", expiresAt: "2199-10-01T00:00:00Z" });
  });
  server.on("POST", "/api/v1/projects/p1/share-invitation", () => {
    posts += 1;
    return new Promise<Response>((resolve) => {
      finishPost = resolve;
    });
  });
  const user = mount("/projects/p1/members");
  await user.click(await screen.findByRole("button", { name: "구성원 초대" }));
  await user.click(await screen.findByRole("button", { name: "초대 만들기" }));
  await waitFor(() => expect(posts).toBe(1));

  window.location.hash = "#/notifications";
  await screen.findByRole("heading", { name: "알림" });
  window.location.hash = "#/projects/p1/members";
  await screen.findByRole("heading", { name: "구성원" });
  await user.click(screen.getByRole("button", { name: "구성원 초대" }));
  await waitFor(() => expect(finishRead).toBeTypeOf("function"));

  await act(async () => finishPost(json({ state: "ACTIVE", code: "0A1B2C3D4E5F6G7H", expiresAt: "2199-10-01T00:00:00Z" })));
  await act(async () => finishRead(json({ state: "ACTIVE", code: "1A1B2C3D4E5F6G7H", expiresAt: "2199-10-01T00:00:00Z" })));
  expect(screen.queryByDisplayValue("1A1B-2C3D-4E5F-6G7H")).not.toBeInTheDocument();
  expect(screen.getByDisplayValue("0A1B-2C3D-4E5F-6G7H")).toBeInTheDocument();
  expect(posts).toBe(1);

  window.location.hash = "#/notifications";
  await screen.findByRole("heading", { name: "알림" });
  window.location.hash = "#/projects/p1/members";
  await screen.findByRole("heading", { name: "구성원" });
  await user.click(screen.getByRole("button", { name: "구성원 초대" }));
  await screen.findByDisplayValue("7K3M-9F2D-6R8T-WX4C");
  expect(posts).toBe(1);
});

test("an old session recovery cannot clear a new session recovery with the same revision", async () => {
  const server = http();
  let reads = 0;
  let finishOld!: (response: Response) => void;
  let finishNew!: (response: Response) => void;
  server.on("GET", "/api/v1/projects/p1/share-invitation", () => {
    reads += 1;
    if (reads === 1 || reads === 3) return json({ code: "FORBIDDEN" }, 403);
    if (reads === 2)
      return new Promise<Response>((resolve) => {
        finishOld = resolve;
      });
    return new Promise<Response>((resolve) => {
      finishNew = resolve;
    });
  });
  const first = mount("/projects/p1/members");
  await first.click(await screen.findByRole("button", { name: "구성원 초대" }));
  await screen.findByRole("button", { name: "초대 상태 다시 확인" });
  await first.click(screen.getByRole("button", { name: "초대 상태 다시 확인" }));
  await waitFor(() => expect(finishOld).toBeTypeOf("function"));

  cleanup();
  beginSessionBoundary();
  resetAccessState();
  const second = mount("/projects/p1/members");
  await second.click(await screen.findByRole("button", { name: "구성원 초대" }));
  await screen.findByRole("button", { name: "초대 상태 다시 확인" });
  await second.click(screen.getByRole("button", { name: "초대 상태 다시 확인" }));
  await waitFor(() => expect(finishNew).toBeTypeOf("function"));

  await act(async () => finishOld(json({ state: "ACTIVE", code: "OLDOLDOLDOLDOLD01", expiresAt: "2199-10-01T00:00:00Z" })));
  await act(async () => finishNew(json({ state: "ACTIVE", code: "7K3M9F2D6R8TWX4C", expiresAt: "2199-10-01T00:00:00Z" })));
  await screen.findByDisplayValue("7K3M-9F2D-6R8T-WX4C");
  expect(screen.queryByDisplayValue(/OLD/)).not.toBeInTheDocument();
});
