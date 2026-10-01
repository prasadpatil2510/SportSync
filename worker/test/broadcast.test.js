import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { DatabaseSync } from "node:sqlite";
import { authorizedBroadcastMatch, broadcastOverlayHtml, createBroadcastGrant, newBroadcastPin, PERMANENT_TEST_BROADCAST_PIN, publicBroadcastSnapshot, redeemBroadcastPin, revokeBroadcastGrant, validBroadcastPin } from "../src/broadcast.js";
import worker from "../src/index.js";

function testEnvironment() {
  const sqlite = new DatabaseSync(":memory:");
  sqlite.exec("CREATE TABLE matches(id TEXT PRIMARY KEY,tournament_id TEXT,status TEXT DEFAULT 'SCHEDULED',updated_at TEXT DEFAULT CURRENT_TIMESTAMP)");
  sqlite.exec(readFileSync(new URL("../migrations/0007_user_authentication.sql", import.meta.url), "utf8"));
  sqlite.exec(readFileSync(new URL("../migrations/0009_match_broadcast.sql", import.meta.url), "utf8"));
  sqlite.prepare("INSERT INTO matches(id,tournament_id) VALUES(?,?)").run("match_1", "tournament_1");
  sqlite.prepare("INSERT INTO matches(id,tournament_id) VALUES(?,?)").run("match_2", "tournament_2");
  const prepare = sql => {
    const stmt = sqlite.prepare(sql);
    const bound = values => ({ first: async () => stmt.get(...values), all: async () => ({ results: stmt.all(...values) }), run: async () => stmt.run(...values) });
    return { bind: (...values) => bound(values), first: async () => stmt.get(), all: async () => ({ results: stmt.all() }), run: async () => stmt.run() };
  };
  return { sqlite, env: { ADMIN_TOKEN: "test-admin", DB: { prepare, async batch(statements) { sqlite.exec("BEGIN"); try { const results = []; for (const statement of statements) results.push(await statement.run()); sqlite.exec("COMMIT"); return results; } catch (error) { sqlite.exec("ROLLBACK"); throw error; } } } } };
}

test("broadcast PINs are six digits and validation rejects other inputs", () => {
  for (let i = 0; i < 100; i++) {
    const pin = newBroadcastPin();
    assert.match(pin, /^\d{6}$/);
    assert.notEqual(pin, PERMANENT_TEST_BROADCAST_PIN);
  }
  assert.equal(validBroadcastPin("123456"), true);
  assert.equal(validBroadcastPin("12345"), false);
  assert.equal(validBroadcastPin("abcdef"), false);
});

test("permanent testing PIN and latest generated PIN work together end-to-end", async () => {
  const { sqlite, env } = testEnvironment();
  try {
    const create = await worker.fetch(new Request("https://example.test/api/matches/match_1/broadcast/grant", {
      method: "POST", headers: { "x-admin-token": "test-admin" }
    }), env);
    assert.equal(create.status, 201);
    const { pin: generatedPin } = await create.json();
    assert.notEqual(generatedPin, PERMANENT_TEST_BROADCAST_PIN);

    for (const [pin, ip] of [[generatedPin, "203.0.113.10"], [PERMANENT_TEST_BROADCAST_PIN, "203.0.113.11"]]) {
      const redeemed = await worker.fetch(new Request("https://example.test/api/broadcast/redeem", {
        method: "POST", headers: { "content-type": "application/json", "CF-Connecting-IP": ip }, body: JSON.stringify({ pin })
      }), env);
      assert.equal(redeemed.status, 200);
      assert.equal((await redeemed.json()).matchId, "match_1");
    }
  } finally { sqlite.close(); }
});

test("a new generated PIN invalidates the previous generated PIN while permanent PIN follows the new match", async () => {
  const { sqlite, env } = testEnvironment();
  try {
    const firstPin = (await (await createBroadcastGrant(env, "match_1", "scorer_1")).json()).pin;
    const secondPin = (await (await createBroadcastGrant(env, "match_2", "scorer_1")).json()).pin;
    assert.notEqual(firstPin, secondPin);
    assert.equal(sqlite.prepare("SELECT COUNT(*) active FROM match_broadcast_grants WHERE revoked_at IS NULL").get().active, 1);

    const redeem = (pin, ip) => redeemBroadcastPin(new Request("https://example.test/api/broadcast/redeem", { headers: { "CF-Connecting-IP": ip } }), env, pin);
    assert.equal((await redeem(firstPin, "203.0.113.20")).status, 401);
    const current = await (await redeem(secondPin, "203.0.113.21")).json();
    assert.equal(current.matchId, "match_2");
    const permanent = await (await redeem(PERMANENT_TEST_BROADCAST_PIN, "203.0.113.22")).json();
    assert.equal(permanent.matchId, "match_2");
  } finally { sqlite.close(); }
});

test("revoked or expired active grant rejects both generated and permanent PINs", async () => {
  const { sqlite, env } = testEnvironment();
  try {
    const request = pin => redeemBroadcastPin(new Request("https://example.test/api/broadcast/redeem", { headers: { "CF-Connecting-IP": `198.51.100.${pin === PERMANENT_TEST_BROADCAST_PIN ? 1 : 2}` } }), env, pin);
    const generated = (await (await createBroadcastGrant(env, "match_1", "scorer_1")).json()).pin;
    await revokeBroadcastGrant(env, "match_1");
    assert.equal((await request(generated)).status, 401);
    assert.equal((await request(PERMANENT_TEST_BROADCAST_PIN)).status, 401);

    const refreshed = (await (await createBroadcastGrant(env, "match_1", "scorer_1")).json()).pin;
    sqlite.prepare("UPDATE match_broadcast_grants SET expires_at=? WHERE match_id=?").run("2000-01-01T00:00:00.000Z", "match_1");
    assert.equal((await request(refreshed)).status, 401);
    assert.equal((await request(PERMANENT_TEST_BROADCAST_PIN)).status, 401);
  } finally { sqlite.close(); }
});

test("broadcast snapshot contains score but no private match or account fields", () => {
  const result = publicBroadcastSnapshot({
    id: "match_1", status: "LIVE", team_a_name: "A", team_a_logo_url: "https://assets.test/a.png", team_b_name: "B", team_b_logo_url: "https://assets.test/b.png",
    batting_team_name: "A", batting_team_logo_url: "https://assets.test/a.png", bowling_team_name: "B", bowling_team_logo_url: "https://assets.test/b.png", current_innings: 1,
    overs_per_innings: 20, updated_at: "2026-09-22", private_note: "secret",
    innings: [{ runs: 42, wickets: 2, legal_balls: 35, striker_name: "P1", striker_runs: 24, striker_balls: 16, non_striker_name: "P2", non_striker_runs: 10, non_striker_balls: 8, bowler_name: "P3", bowler_legal_balls: 11, bowler_runs: 18, bowler_wickets: 1, current_over: [{ batter_runs: 1, extra_runs: 0, extra_type: "NONE", is_wicket: 0 }], batting_card: [{ name: "P1", runs: 24, balls: 16, dismissal: "NOT OUT" }] }]
  });
  assert.deepEqual([result.runs, result.wickets, result.legalBalls], [42, 2, 35]);
  assert.deepEqual([result.battingTeamLogo, result.bowlingTeamLogo], ["https://assets.test/a.png", "https://assets.test/b.png"]);
  assert.deepEqual([result.strikerRuns, result.strikerBalls, result.nonStrikerRuns, result.nonStrikerBalls], [24, 16, 10, 8]);
  assert.deepEqual([result.bowlerLegalBalls, result.bowlerRuns, result.bowlerWickets, result.currentOver.join(",")], [11, 18, 1, "1"]);
  assert.deepEqual(result.battingCard, [{ name: "P1", runs: 24, balls: 16, dismissal: "NOT OUT" }]);
  assert.equal("private_note" in result, false);
});

test("OBS overlay is transparent and polls read-only score data", () => {
  const html = broadcastOverlayHtml();
  assert.match(html, /background:transparent/);
  assert.match(html, /api\/broadcast\/snapshot/);
  assert.doesNotMatch(html, /stream.key|youtube\.com|POST/i);
});

test("PIN redemption issues read-only tokens; rotation and revocation invalidate old tokens", async () => {
  const { sqlite, env } = testEnvironment();
  try {
    const created = await createBroadcastGrant(env, "match_1", "scorer_1");
    assert.equal(created.status, 201);
    const { pin } = await created.json();
    assert.match(pin, /^\d{6}$/);
    const stored = sqlite.prepare("SELECT * FROM match_broadcast_grants WHERE match_id=?").get("match_1");
    assert.notEqual(stored.pin_hash, pin);
    assert.equal(stored.pin_hash.length, 64);
    const request = new Request("https://example.test/api/broadcast/redeem", { headers: { "CF-Connecting-IP": "203.0.113.1" } });
    const first = await (await redeemBroadcastPin(request, env, pin)).json();
    assert.equal(first.matchId, "match_1");
    assert.equal((await authorizedBroadcastMatch(env, first.phoneToken, "phone"))?.match_id, "match_1");
    const overlayToken = new URL(first.overlayUrl).searchParams.get("token");
    assert.equal((await authorizedBroadcastMatch(env, overlayToken, "overlay"))?.match_id, "match_1");
    assert.equal(await authorizedBroadcastMatch(env, first.phoneToken, "overlay"), undefined);
    const second = await (await redeemBroadcastPin(request, env, pin)).json();
    assert.equal(await authorizedBroadcastMatch(env, first.phoneToken, "phone"), undefined);
    assert.equal((await authorizedBroadcastMatch(env, second.phoneToken, "phone"))?.match_id, "match_1");
    await revokeBroadcastGrant(env, "match_1");
    assert.equal(await authorizedBroadcastMatch(env, second.phoneToken, "phone"), undefined);
  } finally { sqlite.close(); }
});

test("a tournament broadcast token follows the latest live match", async () => {
  const { sqlite, env } = testEnvironment();
  try {
    sqlite.prepare("INSERT INTO matches(id,tournament_id,status,updated_at) VALUES(?,?,?,?)").run("match_live", "tournament_1", "LIVE", "2026-10-01T10:00:00Z");
    const { pin } = await (await createBroadcastGrant(env, "match_1", "scorer_1")).json();
    const session = await (await redeemBroadcastPin(new Request("https://example.test/api/broadcast/redeem", { headers: { "CF-Connecting-IP": "203.0.113.33" } }), env, pin)).json();
    assert.equal((await authorizedBroadcastMatch(env, session.phoneToken, "phone"))?.match_id, "match_live");
    sqlite.prepare("UPDATE matches SET status='COMPLETE' WHERE id='match_live'").run();
    assert.equal((await authorizedBroadcastMatch(env, session.phoneToken, "phone"))?.match_id, "match_live");
    sqlite.prepare("INSERT INTO matches(id,tournament_id,status,updated_at) VALUES(?,?,?,?)").run("match_live_2", "tournament_1", "LIVE", "2026-10-01T12:00:00Z");
    assert.equal((await authorizedBroadcastMatch(env, session.phoneToken, "phone"))?.match_id, "match_live_2");
  } finally { sqlite.close(); }
});

test("invalid, expired, and repeatedly guessed PINs are rejected", async () => {
  const { sqlite, env } = testEnvironment();
  try {
    const request = new Request("https://example.test/api/broadcast/redeem", { headers: { "CF-Connecting-IP": "203.0.113.2" } });
    assert.equal((await redeemBroadcastPin(request, env, "invalid")).status, 400);
    const grant = await createBroadcastGrant(env, "match_1", "scorer_1");
    const { pin } = await grant.json();
    const wrong = pin === "000000" ? "999999" : "000000";
    for (let i = 0; i < 5; i++) assert.equal((await redeemBroadcastPin(request, env, wrong)).status, 401);
    assert.equal((await redeemBroadcastPin(request, env, pin)).status, 429);
    sqlite.prepare("DELETE FROM match_broadcast_attempts").run();
    sqlite.prepare("UPDATE match_broadcast_grants SET expires_at=? WHERE match_id=?").run("2000-01-01T00:00:00.000Z", "match_1");
    assert.equal((await redeemBroadcastPin(request, env, pin)).status, 401);
  } finally { sqlite.close(); }
});

test("a PIN-derived phone token cannot post a scoring delivery", async () => {
  const { sqlite, env } = testEnvironment();
  try {
    const { pin } = await (await createBroadcastGrant(env, "match_1", "scorer_1")).json();
    const { phoneToken } = await (await redeemBroadcastPin(new Request("https://example.test/api/broadcast/redeem", { headers: { "CF-Connecting-IP": "203.0.113.3" } }), env, pin)).json();
    const response = await worker.fetch(new Request("https://example.test/api/innings/inn_1/deliveries", { method: "POST", headers: { authorization: `Bearer ${phoneToken}`, "content-type": "application/json" }, body: "{}" }), env);
    assert.equal(response.status, 403);
  } finally { sqlite.close(); }
});
