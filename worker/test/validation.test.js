import test from "node:test";
import assert from "node:assert/strict";
import { validDate, validDateTime } from "../src/index.js";

test("calendar dates reject free text and impossible dates", () => {
  assert.equal(validDate("2026-09-08"), true);
  assert.equal(validDate("2026-02-29"), false);
  assert.equal(validDate("tomorrow"), false);
});

test("match schedule requires a valid date and time", () => {
  assert.equal(validDateTime("2026-09-08 18:30"), true);
  assert.equal(validDateTime("2026-09-08 25:00"), false);
  assert.equal(validDateTime("junk"), false);
});
