import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, test, vi } from "vitest";
import App from "./App";
import { http, json, project } from "./test/http";

afterEach(() => { cleanup(); vi.unstubAllGlobals(); window.history.replaceState(null, "", "/"); sessionStorage.clear(); });
const mount = () => { window.location.hash = "#/"; render(<App />); return userEvent.setup(); };

test("a verified account creates a project without loading groups", async () => {
  const server = http(); let created = false;
  server.on("GET", "/api/v1/projects", () => json(created ? [project] : []));
  server.on("POST", "/api/v1/projects", body => { expect(body.groupId).toBeUndefined(); expect(body.name).toBe("새 프로젝트"); expect(body.requestId).toMatch(/^[0-9a-f-]{36}$/i); created = true; return json(project); });
  const user = mount(); await user.click(await screen.findByRole("button", { name: "새 프로젝트" }));
  await user.type(screen.getByLabelText("프로젝트 이름"), "  새 프로젝트  "); await user.click(screen.getByRole("button", { name: "프로젝트 만들기" }));
  await waitFor(() => expect(server.calls.some(call => call.method === "POST")).toBe(true));
  expect(window.location.hash).toBe("#/projects/p1");
  expect(server.calls.some(call => call.url.includes("creation-options"))).toBe(false);
});

test("invalid project names stay in the dialog and focus the field", async () => {
  http();
  const user = mount(); await user.click(await screen.findByRole("button", { name: "새 프로젝트" }));
  const input = screen.getByLabelText("프로젝트 이름"); await user.click(screen.getByRole("button", { name: "프로젝트 만들기" }));
  expect(input).toHaveAttribute("aria-invalid", "true"); expect(input).toHaveFocus();
});

test("uncertain create result keeps the same request until explicitly resolved", async () => {
  const server = http(); let calls = 0; const bodies: any[] = [];
  server.on("POST", "/api/v1/projects", body => { bodies.push(body); calls += 1; return calls === 1 ? json({ code: "RESULT_UNKNOWN" }, 500) : json(project); });
  const user = mount(); await user.click(await screen.findByRole("button", { name: "새 프로젝트" })); await user.type(screen.getByLabelText("프로젝트 이름"), "보존할 이름"); await user.click(screen.getByRole("button", { name: "프로젝트 만들기" }));
  await screen.findByText(/결과를 확인하지 못했습니다/); expect(bodies).toHaveLength(1); const requestId = bodies[0].requestId;
  await user.click(screen.getByRole("button", { name: "취소" })); await user.click(screen.getByRole("button", { name: "새 프로젝트" }));
  expect(screen.getByLabelText("프로젝트 이름")).toHaveValue("보존할 이름"); expect(screen.getByRole("button", { name: "결과 확인 필요" })).toBeDisabled();
  await user.click(screen.getByRole("button", { name: "같은 요청 결과 확인" })); await waitFor(() => expect(bodies).toHaveLength(2)); expect(bodies[1].requestId).toBe(requestId); expect(bodies[1].name).toBe("보존할 이름");
});

test("lobby actions stay available while project listing fails", async () => {
  const server = http(); server.on("GET", "/api/v1/projects", () => json({ code: "LIST_FAILED" }, 500)); const user = mount();
  await screen.findByText("프로젝트를 불러오지 못했습니다."); expect(screen.getByRole("button", { name: "새 프로젝트" })).toBeEnabled(); await user.click(screen.getByRole("button", { name: "초대로 참여" })); expect(screen.getByLabelText("초대 링크 또는 코드")).toBeEnabled();
});

test("successful create keeps its pending request while project refresh is waiting", async () => {
  const server = http();
  let listCalls = 0;
  let finishRefresh!: (response: Response) => void;
  server.on("GET", "/api/v1/projects", () => {
    listCalls += 1;
    return listCalls === 1
      ? json([])
      : new Promise<Response>((resolve) => {
          finishRefresh = resolve;
        });
  });
  let createCalls = 0;
  server.on("POST", "/api/v1/projects", () => {
    createCalls += 1;
    return json(project);
  });

  const user = mount();
  await user.click(await screen.findByRole("button", { name: "새 프로젝트" }));
  await user.type(screen.getByLabelText("프로젝트 이름"), "느린 목록 갱신");
  await user.click(screen.getByRole("button", { name: "프로젝트 만들기" }));
  await waitFor(() => expect(createCalls).toBe(1));
  window.location.hash = "#/notifications";
  await screen.findByRole("heading", { name: "알림" });
  window.location.hash = "#/";
  await screen.findByRole("heading", { name: "프로젝트 선택" });
  await user.click(screen.getByRole("button", { name: "새 프로젝트" }));
  expect(screen.getByRole("button", { name: "프로젝트 만들기" })).toBeDisabled();
  expect(screen.getByLabelText("프로젝트 이름")).toHaveValue("느린 목록 갱신");
  expect(createCalls).toBe(1);

  finishRefresh(json([project]));
  await waitFor(() => expect(window.location.hash).toBe("#/projects/p1"));
});
