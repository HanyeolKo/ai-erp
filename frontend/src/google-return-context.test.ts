import { afterEach, expect, test, vi } from "vitest";
import {
  clearGoogleReturnContext,
  consumeGoogleReturnContext,
  getGoogleReturnContext,
  saveGoogleReturnContext,
} from "./google-return-context";
import { beginSessionBoundary, terminateSession } from "./session";

afterEach(() => {
  vi.useRealTimers();
  vi.restoreAllMocks();
  sessionStorage.clear();
});

test("return context is user-bound, expires after ten minutes, and is consumed once", () => {
  vi.useFakeTimers();
  vi.setSystemTime(new Date("2090-09-10T00:00:00Z"));
  saveGoogleReturnContext("u1", "/projects/p1/files");
  expect(getGoogleReturnContext("u2")).toBeUndefined();
  expect(getGoogleReturnContext("u1")?.returnTo).toBe("/projects/p1/files");
  vi.advanceTimersByTime(10 * 60 * 1000 + 1);
  expect(consumeGoogleReturnContext("u1")).toBeUndefined();
  saveGoogleReturnContext("u1", "/projects/p1/files");
  expect(consumeGoogleReturnContext("u1")?.returnTo).toBe("/projects/p1/files");
  expect(consumeGoogleReturnContext("u1")).toBeUndefined();
  const originalCreatedAt = Date.now() - 30_000;
  expect(saveGoogleReturnContext("u1", "/projects/p1/files", originalCreatedAt)).toBe(true);
  expect(JSON.parse(sessionStorage.getItem("ai-erp.google-return") || "null").createdAt).toBe(originalCreatedAt);
  expect(saveGoogleReturnContext("u1", "/projects/p1/files", Date.now() - (10 * 60 * 1000 + 1))).toBe(false);
  expect(sessionStorage.getItem("ai-erp.google-return")).toBeNull();
});

test("unavailable storage does not prevent clear or navigation helpers", () => {
  const getItem = vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => { throw new Error("storage unavailable"); });
  const setItem = vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => { throw new Error("storage unavailable"); });
  const removeItem = vi.spyOn(Storage.prototype, "removeItem").mockImplementation(() => { throw new Error("storage unavailable"); });
  expect(() => saveGoogleReturnContext("u1", "/projects/p1/files")).not.toThrow();
  expect(() => getGoogleReturnContext("u1")).not.toThrow();
  expect(() => consumeGoogleReturnContext("u1")).not.toThrow();
  expect(() => clearGoogleReturnContext()).not.toThrow();
  expect(getItem).toHaveBeenCalled();
  expect(setItem).toHaveBeenCalled();
  expect(removeItem).toHaveBeenCalled();
});

test("session termination clears pending Google return context", () => {
  beginSessionBoundary();
  saveGoogleReturnContext("u1", "/projects/p1/files");
  terminateSession();
  expect(getGoogleReturnContext("u1")).toBeUndefined();
});
