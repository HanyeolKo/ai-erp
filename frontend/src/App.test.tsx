import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, test, vi } from "vitest";
import App from "./App";

afterEach(() => {
  vi.unstubAllGlobals();
});

test("shows a loading status before the foundation API responds", () => {
  vi.stubGlobal("fetch", vi.fn(() => new Promise(() => {})));

  render(<App />);

  expect(screen.getByText("AI ERP 연결을 확인하고 있습니다.")).toBeInTheDocument();
});

test("shows the connected foundation phase from the real API boundary", async () => {
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ name: "AI ERP", phase: "foundation" }))));

  render(<App />);

  expect(await screen.findByText("AI ERP 기반 환경에 연결되었습니다.")).toBeInTheDocument();
});

test("retries the connection after a failed API request", async () => {
  const fetch = vi.fn()
    .mockRejectedValueOnce(new Error("network"))
    .mockResolvedValueOnce(new Response(JSON.stringify({ name: "AI ERP", phase: "foundation" })));
  vi.stubGlobal("fetch", fetch);
  const user = userEvent.setup();

  render(<App />);
  await screen.findByText("연결을 확인하지 못했습니다.");
  await user.click(screen.getByRole("button", { name: "다시 시도" }));

  expect(await screen.findByText("AI ERP 기반 환경에 연결되었습니다.")).toBeInTheDocument();
  expect(fetch).toHaveBeenCalledTimes(2);
});
