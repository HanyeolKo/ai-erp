import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, test, vi } from "vitest";
import App from "./App";
import { http, json, project } from "./test/http";

afterEach(() => { vi.unstubAllGlobals(); window.history.replaceState(null, "", "/"); sessionStorage.clear(); });
const mount = () => { window.location.hash = "#/"; render(<App/>); return userEvent.setup(); };
const allowed = { id: "g1", name: "제품팀", canCreate: true, reason: null };

test("group administrator with no project can create and enter the new project", async () => {
  const server = http(); let created = false;
  server.on("GET", "/api/v1/projects", () => json(created ? [project] : []));
  server.on("GET", "/api/v1/projects/creation-options", () => json([allowed]));
  server.on("POST", "/api/v1/projects", body => { expect(body).toEqual({ groupId: "g1", name: "새 프로젝트" }); created = true; return json(project); });
  const user = mount(); await user.type(await screen.findByLabelText("프로젝트 이름"), "  새 프로젝트  ");
  await user.click(screen.getByRole("button", { name: "프로젝트 만들기" }));
  await screen.findByRole("heading", { name: "Planning 대시보드" });
  expect(server.calls.filter(c => c.method === "POST" && c.url === "/api/v1/projects")).toHaveLength(1);
});

test.each([
  { reason: "GROUP_ROLE_REQUIRED", text: "그룹 소유자 또는 관리자만 프로젝트를 만들 수 있습니다." },
  { reason: "GROUP_ROLE_NOT_CONFIGURED", text: "그룹 역할이 아직 설정되지 않아 생성 권한을 확인할 수 없습니다." },
])("creation denial $reason has explanation and a selectable administrator request", async ({ reason, text }) => {
  const server = http(); server.on("GET", "/api/v1/projects", () => json([]));
  server.on("GET", "/api/v1/projects/creation-options", () => json([{ ...allowed, canCreate: false, reason }]));
  const user = mount(); await screen.findByText(text);
  expect(screen.queryByRole("button", { name: "프로젝트 만들기" })).not.toBeInTheDocument();
  expect((screen.getByLabelText("관리자 요청문") as HTMLTextAreaElement).value).toContain("제품팀");
  await user.click(screen.getByRole("button", { name: "요청문 복사" }));
  expect(await screen.findByRole("status")).toHaveTextContent("복사했습니다");
});

test("no group offers initial setup request and invitation entry without inventing creation permission", async () => {
  const server = http(); server.on("GET", "/api/v1/projects", () => json([]));
  mount(); await screen.findByText("참여 중인 그룹이 없습니다. 관리자에게 그룹 설정 또는 초대를 요청하세요.");
  expect(screen.queryByRole("button", { name: "프로젝트 만들기" })).not.toBeInTheDocument();
  expect(screen.getByLabelText("초대 링크")).toBeEnabled();
  expect(screen.getByLabelText("관리자 요청문")).toBeInTheDocument();
});

test("creation capability error is independent from invitation entry and can retry", async () => {
  const server = http(); server.on("GET", "/api/v1/projects/creation-options", () => json({ code: "FAILED" }, 500));
  const user = mount(); await screen.findByText("생성 권한을 확인하지 못했습니다.");
  expect(screen.queryByText("참여 중인 그룹이 없습니다. 관리자에게 그룹 설정 또는 초대를 요청하세요.")).not.toBeInTheDocument();
  expect(screen.getByLabelText("초대 링크")).toBeEnabled();
  await user.type(screen.getByLabelText("초대 링크"), "#/invitations/t1");
  await user.click(screen.getByRole("button", { name: "초대 열기" }));
  expect(await screen.findByRole("heading", { name: "프로젝트 초대" }));
  await user.click(screen.getAllByRole("link", { name: "프로젝트 선택" })[1]);
  server.on("GET", "/api/v1/projects/creation-options", () => json([allowed]));
  await screen.findByText("생성 권한을 확인하지 못했습니다.");
  await user.click(screen.getByRole("button", { name: "생성 권한 다시 확인" }));
  expect(await screen.findByLabelText("프로젝트 이름")).toBeEnabled();
});

test("project creation validates name, prevents duplicate submit, and preserves server validation feedback", async () => {
  const server = http(); server.on("GET", "/api/v1/projects/creation-options", () => json([allowed]));
  let finish!: (response: Response) => void;
  server.on("POST", "/api/v1/projects", () => new Promise(resolve => { finish = resolve; }));
  const user = mount(); await user.click(await screen.findByRole("button", { name: "프로젝트 만들기" }));
  expect(screen.getByLabelText("프로젝트 이름")).toHaveAttribute("aria-invalid", "true");
  await user.type(screen.getByLabelText("프로젝트 이름"), "새 프로젝트");
  await user.click(screen.getByRole("button", { name: "프로젝트 만들기" }));
  await waitFor(() => expect(finish).toBeTypeOf("function"));
  expect(screen.getByRole("button", { name: "프로젝트 만들기" })).toBeDisabled();
  finish(json({ code: "VALIDATION_FAILED", fieldErrors: [{ field: "name", message: "프로젝트 이름을 확인하세요." }] }, 400));
  await screen.findByText("입력한 내용을 확인해 주세요.");
  expect(screen.getByLabelText("프로젝트 이름")).toHaveValue("새 프로젝트");
  expect(screen.getByLabelText("프로젝트 이름")).toHaveAttribute("aria-invalid", "true");
});

test("lost creation permission blocks resubmission and refreshes capabilities", async () => {
  const server = http(); let denied = false;
  server.on("GET", "/api/v1/projects/creation-options", () => json([{ ...allowed, canCreate: !denied, reason: denied ? "GROUP_ROLE_REQUIRED" : null }]));
  server.on("POST", "/api/v1/projects", () => { denied = true; return json({ code: "FORBIDDEN" }, 403); });
  const user = mount(); await user.type(await screen.findByLabelText("프로젝트 이름"), "보존할 이름");
  await user.click(screen.getByRole("button", { name: "프로젝트 만들기" }));
  await screen.findByText("이 작업을 수행할 접근 권한이 없습니다. 프로젝트 선택 또는 접근 상태 다시 확인을 이용하세요.");
  expect(screen.getByRole("button", { name: "프로젝트 만들기" })).toBeDisabled();
  await user.click(screen.getByRole("button", { name: "생성 권한 다시 확인" }));
  await screen.findByText("그룹 소유자 또는 관리자만 프로젝트를 만들 수 있습니다.");
  expect(screen.queryByRole("button", { name: "프로젝트 만들기" })).not.toBeInTheDocument();
});

test("unknown creation result does not automatically retry and points to the project list", async () => {
  const server = http(); server.on("GET", "/api/v1/projects/creation-options", () => json([allowed]));
  server.on("POST", "/api/v1/projects", () => json({ code: "SERVER_ERROR" }, 500));
  const user = mount(); await user.type(await screen.findByLabelText("프로젝트 이름"), "중복 주의");
  await user.click(screen.getByRole("button", { name: "프로젝트 만들기" }));
  await screen.findByRole("button", { name: "프로젝트 목록 새로고침" });
  expect(server.calls.filter(c => c.method === "POST")).toHaveLength(1);
  expect(screen.getByRole("button", { name: "프로젝트 목록 새로고침" })).toBeEnabled();
});

test("creation-options pagination reaches an eligible group after a full denied page", async () => {
  const server = http(); let created = false;
  const denied: { id: string; name: string; canCreate: boolean; reason: "GROUP_ROLE_REQUIRED" | "GROUP_ROLE_NOT_CONFIGURED" | null }[] = Array.from({ length: 100 }, (_, i) => ({ id: `g-${i}`, name: `팀 ${i}`, canCreate: false, reason: "GROUP_ROLE_REQUIRED" }));
  server.on("GET", "/api/v1/projects/creation-options", (_body, url) => {
    const page = Number(url.searchParams.get("page") ?? "0");
    if (page === 0) return json(denied);
    if (page === 1) return json([{ ...allowed, id: "g-100" }]);
    return json([]);
  });
  server.on("GET", "/api/v1/projects", () => json(created ? [project] : []));
  server.on("POST", "/api/v1/projects", body => { expect(body).toEqual({ groupId: "g-100", name: "다음 팀 프로젝트" }); created = true; return json(project); });
  const user = mount(); await user.click(await screen.findByRole("button", { name: "다음 그룹" }));
  await user.type(await screen.findByLabelText("프로젝트 이름"), "다음 팀 프로젝트");
  await user.click(screen.getByRole("button", { name: "프로젝트 만들기" }));
  expect(await screen.findByRole("heading", { name: "Planning 대시보드" })).toBeInTheDocument();
});

test("empty later creation page keeps a page-local empty message and not a global no-group message", async () => {
  const server = http(); const denied: { id: string; name: string; canCreate: boolean; reason: "GROUP_ROLE_REQUIRED" | "GROUP_ROLE_NOT_CONFIGURED" | null }[] = Array.from({ length: 100 }, (_, i) => ({ id: `g-${i}`, name: `팀 ${i}`, canCreate: false, reason: "GROUP_ROLE_REQUIRED" }));
  server.on("GET", "/api/v1/projects/creation-options", (_body, url) => {
    const page = Number(url.searchParams.get("page") ?? "0");
    if (page === 0) return json(denied);
    return json([]);
  });
  server.on("GET", "/api/v1/projects", () => json([]));
  const user = mount(); await user.click(await screen.findByRole("button", { name: "다음 그룹" }));
  await screen.findByText("이 페이지에 그룹이 없습니다. 이전 그룹 페이지를 확인하세요.");
  expect(screen.queryByText("참여 중인 그룹이 없습니다. 관리자에게 그룹 설정 또는 초대를 요청하세요.")).not.toBeInTheDocument();
});

test("changing selected group switches create target while preserving form input", async () => {
  const server = http(); let created = false;
  server.on("GET", "/api/v1/projects/creation-options", () => json([{ ...allowed, id: "g-owner", name: "소유자 그룹" }, { ...allowed, id: "g-member", name: "멤버 그룹", canCreate: false, reason: "GROUP_ROLE_REQUIRED" }]));
  server.on("GET", "/api/v1/projects", () => json(created ? [project] : []));
  server.on("POST", "/api/v1/projects", body => { expect(body).toEqual({ groupId: "g-owner", name: "전환한 이름" }); created = true; return json(project); });
  const user = mount(); const group = await screen.findByLabelText("그룹");
  await user.type(await screen.findByLabelText("프로젝트 이름"), "전환한 이름");
  await user.selectOptions(group, "g-member");
  expect(screen.queryByRole("button", { name: "프로젝트 만들기" })).not.toBeInTheDocument();
  await user.selectOptions(group, "g-owner");
  expect(screen.getByRole("button", { name: "프로젝트 만들기" })).toBeEnabled();
  expect(screen.getByLabelText("프로젝트 이름")).toHaveValue("전환한 이름");
  await user.click(screen.getByRole("button", { name: "프로젝트 만들기" }));
  expect(await screen.findByRole("heading", { name: "Planning 대시보드" })).toBeInTheDocument();
});

test("clipboard failure keeps administrator request text selectable and explains manual copy", async () => {
  const server = http(); server.on("GET", "/api/v1/projects", () => json([])); server.on("GET", "/api/v1/projects/creation-options", () => json([{ ...allowed, canCreate: false, reason: "GROUP_ROLE_REQUIRED" }]));
  const copy = vi.spyOn(navigator.clipboard, "writeText").mockRejectedValue(new Error("nope"));
  const user = mount(); const request = await screen.findByLabelText("관리자 요청문");
  expect((request as HTMLTextAreaElement).value).toContain("제품팀");
  await user.click(screen.getByRole("button", { name: "요청문 복사" }));
  expect(await screen.findByRole("status")).toHaveTextContent("자동 복사를 사용할 수 없습니다");
  copy.mockRestore();
});
