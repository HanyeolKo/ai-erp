import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, test } from "vitest";
import App from "./App";
import { http, json, schedule } from "./test/http";

afterEach(() => {
  window.location.hash = "";
  window.history.replaceState(null, "", "/");
  sessionStorage.clear();
});

const schedulesPath = "/api/v1/projects/p1/schedules";
const editPath = "/api/v1/projects/p1/schedules/s1";

function mount() {
  window.location.hash = "#/projects/p1/schedules";
  render(<App />);
}

async function monthEvent(title = "Design review") {
  const links = await screen.findAllByRole("link", { name: new RegExp(title) });
  return links.find(link => link.classList.contains("month-event"))!;
}

function setHitTarget(target: Element | null) {
  const documentWithHitTest = document as Document & { elementFromPoint?: unknown };
  const previous = documentWithHitTest.elementFromPoint;
  Object.defineProperty(document, "elementFromPoint", { configurable: true, value: () => target });
  return () => Object.defineProperty(document, "elementFromPoint", { configurable: true, value: previous });
}

function patchCalls(server: ReturnType<typeof http>) {
  return server.calls.filter(call => call.method === "PATCH" && call.url === editPath);
}

test("a pointer move of six pixels or less remains a link click and sends no PATCH", async () => {
  const server = http();
  mount();
  const anchor = await monthEvent();
  const wrapper = anchor.parentElement!;
  fireEvent.pointerDown(wrapper, { pointerId: 1, pointerType: "mouse", button: 0, clientX: 10, clientY: 10 });
  fireEvent.pointerMove(wrapper, { pointerId: 1, pointerType: "mouse", clientX: 16, clientY: 10 });
  fireEvent.pointerUp(wrapper, { pointerId: 1, pointerType: "mouse", clientX: 16, clientY: 10 });
  await userEvent.setup().click(anchor);
  await waitFor(() => expect(window.location.hash).toBe("#/projects/p1/schedules/s1"));
  expect(patchCalls(server)).toHaveLength(0);
});

test("a pointer drop outside the rendered date cells is cancelled without PATCH or navigation", async () => {
  const server = http();
  mount();
  const anchor = await monthEvent();
  const wrapper = anchor.parentElement!;
  const header = screen.getByRole("columnheader", { name: "월" });
  const restoreHitTest = setHitTarget(header);
  try {
    fireEvent.pointerDown(wrapper, { pointerId: 2, pointerType: "mouse", button: 0, clientX: 10, clientY: 10 });
    fireEvent.pointerMove(wrapper, { pointerId: 2, pointerType: "mouse", clientX: 30, clientY: 10 });
    fireEvent.pointerUp(wrapper, { pointerId: 2, pointerType: "mouse", clientX: 30, clientY: 10 });
  } finally {
    restoreHitTest();
  }
  expect(window.location.hash).toBe("#/projects/p1/schedules");
  expect(patchCalls(server)).toHaveLength(0);
});

test("Escape cancels a moved anchor preview without PATCH", async () => {
  const server = http();
  mount();
  const anchor = await monthEvent();
  const wrapper = anchor.parentElement!;
  const target = screen.getByRole("gridcell", { name: "2090-09-11" });
  const restoreHitTest = setHitTarget(target);
  try {
    fireEvent.pointerDown(wrapper, { pointerId: 3, pointerType: "mouse", button: 0, clientX: 10, clientY: 10 });
    fireEvent.pointerMove(wrapper, { pointerId: 3, pointerType: "mouse", clientX: 30, clientY: 10 });
    fireEvent.keyDown(window, { key: "Escape" });
    fireEvent.pointerUp(wrapper, { pointerId: 3, pointerType: "mouse", clientX: 30, clientY: 10 });
  } finally {
    restoreHitTest();
  }
  expect(window.location.hash).toBe("#/projects/p1/schedules");
  expect(patchCalls(server)).toHaveLength(0);
});

test("pointercancel on the actual resize handle cancels without PATCH", async () => {
  const server = http();
  mount();
  await monthEvent();
  const handle = document.querySelector<HTMLElement>(".calendar-resize-handle--end")!;
  const wrapper = handle.parentElement!;
  const target = screen.getByRole("gridcell", { name: "2090-09-11" });
  const restoreHitTest = setHitTarget(target);
  try {
    fireEvent.pointerDown(handle, { pointerId: 4, pointerType: "mouse", button: 0, clientX: 10, clientY: 10 });
    fireEvent.pointerMove(wrapper, { pointerId: 4, pointerType: "mouse", clientX: 30, clientY: 10 });
    fireEvent.pointerCancel(wrapper, { pointerId: 4, pointerType: "mouse", clientX: 30, clientY: 10 });
  } finally {
    restoreHitTest();
  }
  expect(patchCalls(server)).toHaveLength(0);
});

test("touch pointer movement does not start a drag write and the event anchor stays non-draggable", async () => {
  const server = http();
  mount();
  const anchor = await monthEvent();
  const wrapper = anchor.parentElement!;
  const target = screen.getByRole("gridcell", { name: "2090-09-11" });
  const restoreHitTest = setHitTarget(target);
  try {
    fireEvent.pointerDown(wrapper, { pointerId: 5, pointerType: "touch", button: 0, clientX: 10, clientY: 10 });
    fireEvent.pointerMove(wrapper, { pointerId: 5, pointerType: "touch", clientX: 40, clientY: 10 });
    fireEvent.pointerUp(wrapper, { pointerId: 5, pointerType: "touch", clientX: 40, clientY: 10 });
  } finally {
    restoreHitTest();
  }
  expect(anchor).toHaveAttribute("draggable", "false");
  expect(window.location.hash).toBe("#/projects/p1/schedules");
  expect(patchCalls(server)).toHaveLength(0);
});

test("one active gesture blocks a second event until the first gesture commits one PATCH", async () => {
  const server = http();
  const second = { ...schedule, id: "s2", title: "Another review" };
  server.on("GET", schedulesPath, () => json([schedule, second]));
  server.on("PATCH", editPath, body => json({ ...schedule, startsAt: body.startsAt, endsAt: body.endsAt }));
  server.on("PATCH", "/api/v1/projects/p1/schedules/s2", body => json({ ...second, startsAt: body.startsAt, endsAt: body.endsAt }));
  mount();
  const firstAnchor = await monthEvent();
  const secondAnchor = await monthEvent("Another review");
  const firstWrapper = firstAnchor.parentElement!;
  const secondWrapper = secondAnchor.parentElement!;
  const target = screen.getByRole("gridcell", { name: "2090-09-11" });
  const restoreHitTest = setHitTarget(target);
  try {
    fireEvent.pointerDown(firstWrapper, { pointerId: 6, pointerType: "mouse", button: 0, clientX: 10, clientY: 10 });
    fireEvent.pointerDown(secondWrapper, { pointerId: 7, pointerType: "mouse", button: 0, clientX: 10, clientY: 10 });
    fireEvent.pointerMove(secondWrapper, { pointerId: 7, pointerType: "mouse", clientX: 40, clientY: 10 });
    fireEvent.pointerUp(secondWrapper, { pointerId: 7, pointerType: "mouse", clientX: 40, clientY: 10 });
    fireEvent.pointerMove(firstWrapper, { pointerId: 6, pointerType: "mouse", clientX: 40, clientY: 10 });
    fireEvent.pointerUp(firstWrapper, { pointerId: 6, pointerType: "mouse", clientX: 40, clientY: 10 });
  } finally {
    restoreHitTest();
  }
  const allSchedulePatchCalls = () => server.calls.filter(call => call.method === "PATCH" && call.url.startsWith("/api/v1/projects/p1/schedules/"));
  await waitFor(() => expect(allSchedulePatchCalls()).toHaveLength(1));
  expect(allSchedulePatchCalls()[0].url).toBe(editPath);
  expect(allSchedulePatchCalls()[0].body).toMatchObject({ title: "Design review", rowVersion: 3 });
});
