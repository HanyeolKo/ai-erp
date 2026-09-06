import { expect, test } from "vitest";
import { localDateTimeToUtc, utcToLocalDateTime } from "./time";

test("converts an IANA local time across the DST spring boundary", () => {
  expect(localDateTimeToUtc("2026-03-08T03:30", "America/New_York")).toBe("2026-03-08T07:30:00.000Z");
  expect(() => localDateTimeToUtc("2026-03-08T02:30", "America/New_York")).toThrow(/존재하지 않는/);
});

test("round-trips an instant to editable IANA local fields", () => {
  expect(utcToLocalDateTime("2026-09-10T01:00:00Z", "Asia/Seoul")).toBe("2026-09-10T10:00");
});
