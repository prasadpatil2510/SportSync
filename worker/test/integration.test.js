import test from "node:test";
import assert from "node:assert/strict";
import { absoluteAssetUrl, normalizedName, shortName } from "../src/integration.js";

test("names normalize consistently for safe profile matching", () => {
  assert.equal(normalizedName("  Aditya   Priyam "), "aditya priyam");
});

test("auction asset paths become durable absolute URLs", () => {
  assert.equal(absoluteAssetUrl("https://auction.example", "/uploads/a.webp"), "https://auction.example/uploads/a.webp");
  assert.equal(absoluteAssetUrl("https://auction.example", "https://cdn.example/a.png"), "https://cdn.example/a.png");
});

test("team short names are deterministic", () => {
  assert.equal(shortName("Wicket Whiskers"), "WW");
  assert.equal(shortName("Bloodhawks"), "BLOODHAWKS");
});
