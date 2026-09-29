import test from "node:test";
import assert from "node:assert/strict";
import { canAccess, normalizeRole, PBKDF2_ITERATIONS, ROLES, testingAccessAllowed, validEmail, validPin } from "../src/auth.js";

test("self-registration inputs accept valid email and numeric PIN only", () => {
  assert.equal(validEmail("player@example.com"), true);
  assert.equal(validEmail("not-an-email"), false);
  assert.equal(validPin("1234"), true);
  assert.equal(validPin("123456789"), false);
  assert.equal(validPin("12ab"), false);
});

test("unknown roles normalize to player and elevated access is explicit", () => {
  assert.equal(normalizeRole("unknown"), ROLES.PLAYER);
  assert.equal(canAccess(ROLES.PLAYER, [ROLES.SCORER, ROLES.TOURNAMENT_ADMIN]), false);
  assert.equal(canAccess(ROLES.SCORER, [ROLES.SCORER, ROLES.TOURNAMENT_ADMIN]), true);
  assert.equal(canAccess(ROLES.SCORER, [ROLES.TOURNAMENT_ADMIN]), false);
});

test("registration-free access is confined to enabled testing environments", () => {
  assert.equal(testingAccessAllowed("testing", "true"), true);
  assert.equal(testingAccessAllowed("testing", "false"), false);
  assert.equal(testingAccessAllowed("production", "true"), false);
  assert.equal(testingAccessAllowed("development", "true"), false);
});

test("password hashing stays within the Cloudflare Workers PBKDF2 limit", () => {
  assert.equal(PBKDF2_ITERATIONS, 100_000);
});
