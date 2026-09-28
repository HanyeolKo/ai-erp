import { act, fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { StrictMode, useState } from "react";
import { afterEach, expect, test, vi } from "vitest";
import { Dialog } from "./ui";

afterEach(() => vi.unstubAllGlobals());

function ReopenableDialog({ conditional = false }: { conditional?: boolean }) {
  const [open, setOpen] = useState(false);
  const [mounted, setMounted] = useState(true);
  return <>
    <button type="button" onClick={() => { setMounted(true); setOpen(true); }}>열기</button>
    <button type="button" onClick={() => setMounted(false)}>제거</button>
    {(!conditional || mounted) && <Dialog open={open} title="편집" labelledBy="focus-dialog-title" onClose={() => setOpen(false)}>
      <label>이름<input /></label>
      {conditional && <button type="button" onClick={() => setMounted(false)}>dialog 제거</button>}
    </Dialog>}
  </>;
}

test("dialog opening cannot steal focus after the user moves to an input", () => {
  const frames: FrameRequestCallback[] = [];
  vi.stubGlobal("requestAnimationFrame", (callback: FrameRequestCallback) => { frames.push(callback); return frames.length; });
  render(<ReopenableDialog />);
  fireEvent.click(screen.getByRole("button", { name: "열기" }));
  const input = screen.getByLabelText("이름");
  input.focus();
  fireEvent.input(input, { target: { value: "보존할 이름" } });
  frames.forEach((callback) => callback(0));
  expect(input).toHaveFocus();
  expect(input).toHaveValue("보존할 이름");
});

test("closing and conditionally unmounting a focused dialog restores its caller", async () => {
  const user = userEvent.setup();
  render(<ReopenableDialog conditional />);
  const caller = screen.getByRole("button", { name: "열기" });
  await user.click(caller);
  expect(screen.getByRole("heading", { name: "편집" })).toHaveFocus();
  await user.click(screen.getByRole("button", { name: "닫기" }));
  expect(caller).toHaveFocus();
  await user.click(caller);
  await user.click(screen.getByRole("button", { name: "dialog 제거" }));
  expect(caller).toHaveFocus();
});

test("dialog cleanup preserves focus that already moved to another control", async () => {
  const { rerender } = render(<><button type="button">다른 control</button><Dialog open title="이전" labelledBy="old-dialog-title" onClose={() => undefined}><p>내용</p></Dialog></>);
  const other = screen.getByRole("button", { name: "다른 control" });
  other.focus();
  rerender(<><button type="button">다른 control</button></>);
  await act(async () => { await Promise.resolve(); });
  expect(other).toHaveFocus();
});

test("closing and reopening in the same turn invalidates the pending focus return", async () => {
  render(<ReopenableDialog />);
  const caller = screen.getByRole("button", { name: "열기" });
  fireEvent.click(caller);
  expect(screen.getByRole("heading", { name: "편집" })).toHaveFocus();
  act(() => {
    fireEvent.click(screen.getByRole("button", { name: "닫기" }));
    fireEvent.click(caller);
  });
  await act(async () => { await Promise.resolve(); });
  expect(screen.getByRole("heading", { name: "편집" })).toHaveFocus();
});

test("StrictMode preserves the original caller across close and reopen", async () => {
  const user = userEvent.setup();
  render(<StrictMode><ReopenableDialog /></StrictMode>);
  const caller = screen.getByRole("button", { name: "열기" });
  await user.click(caller);
  expect(screen.getByRole("heading", { name: "편집" })).toHaveFocus();
  await user.click(screen.getByRole("button", { name: "닫기" }));
  expect(caller).toHaveFocus();
  await user.click(caller);
  expect(screen.getByRole("heading", { name: "편집" })).toHaveFocus();
  await user.click(screen.getByRole("button", { name: "닫기" }));
  expect(caller).toHaveFocus();
});
