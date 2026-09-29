import { hashValue, randomHex, tokenHash } from "./auth.js";

const jsonHeaders = {
  "content-type": "application/json; charset=utf-8",
  "cache-control": "no-store",
  "access-control-allow-origin": "*",
  "access-control-allow-headers": "content-type, authorization, x-admin-token",
  "access-control-allow-methods": "GET, POST, DELETE, OPTIONS"
};
const reply = (value, status = 200) => new Response(JSON.stringify(value), { status, headers: jsonHeaders });
const fail = (message, status = 400) => reply({ error: message }, status);
const EXPIRY_MS = 4 * 60 * 60 * 1000;
const IP_WINDOW_MS = 15 * 60 * 1000;
const MAX_FAILED_ATTEMPTS = 5;
export const PERMANENT_TEST_BROADCAST_PIN = "301022";

export function validBroadcastPin(value) {
  return /^\d{6}$/.test(String(value ?? ""));
}

export function newBroadcastPin() {
  const bytes = new Uint32Array(1);
  let pin;
  do {
    crypto.getRandomValues(bytes);
    pin = String(bytes[0] % 1_000_000).padStart(6, "0");
  } while (pin === PERMANENT_TEST_BROADCAST_PIN);
  return pin;
}

export function publicBroadcastSnapshot(match) {
  const innings = match.innings?.at(-1) ?? null;
  return {
    matchId: match.id,
    status: match.status,
    teamA: match.team_a_name,
    teamB: match.team_b_name,
    teamALogo: match.team_a_logo_url ?? "",
    teamBLogo: match.team_b_logo_url ?? "",
    battingTeam: match.batting_team_name,
    bowlingTeam: match.bowling_team_name,
    battingTeamLogo: match.batting_team_logo_url ?? "",
    bowlingTeamLogo: match.bowling_team_logo_url ?? "",
    inningsNumber: match.current_innings,
    runs: innings?.runs ?? 0,
    wickets: innings?.wickets ?? 0,
    legalBalls: innings?.legal_balls ?? 0,
    oversLimit: match.overs_per_innings,
    striker: innings?.striker_name ?? "",
    nonStriker: innings?.non_striker_name ?? "",
    bowler: innings?.bowler_name ?? "",
    result: match.result_text ?? "",
    updatedAt: innings?.updated_at ?? match.updated_at
  };
}

export async function createBroadcastGrant(env, matchId, actorId) {
  const match = await env.DB.prepare("SELECT id FROM matches WHERE id=?").bind(matchId).first();
  if (!match) return fail("Match not found", 404);
  const pin = newBroadcastPin();
  const salt = randomHex(16);
  const phoneToken = randomHex(32);
  const overlayToken = randomHex(32);
  const expiresAt = new Date(Date.now() + EXPIRY_MS).toISOString();
  const replaceGrant = env.DB.prepare(`INSERT INTO match_broadcast_grants
    (match_id,pin_salt,pin_hash,phone_token_hash,overlay_token_hash,expires_at,created_by)
    VALUES(?,?,?,?,?,?,?)
    ON CONFLICT(match_id) DO UPDATE SET pin_salt=excluded.pin_salt,pin_hash=excluded.pin_hash,
    phone_token_hash=excluded.phone_token_hash,overlay_token_hash=excluded.overlay_token_hash,
    expires_at=excluded.expires_at,revoked_at=NULL,created_by=excluded.created_by,created_at=CURRENT_TIMESTAMP`)
    .bind(matchId, salt, await hashValue(pin, salt), await tokenHash(phoneToken), await tokenHash(overlayToken), expiresAt, actorId);
  // There is one generated PIN system-wide. Creating another grant invalidates the
  // previous generated PIN and its read-only tokens, even when it belonged to a
  // different match. The permanent testing PIN resolves to this same active grant.
  await env.DB.batch([
    env.DB.prepare("UPDATE match_broadcast_grants SET revoked_at=CURRENT_TIMESTAMP WHERE revoked_at IS NULL"),
    replaceGrant
  ]);
  // Only the PIN is shown to the scorer. Read-only tokens are revealed to the broadcaster after redemption.
  return reply({ matchId, pin, expiresAt }, 201);
}

export async function revokeBroadcastGrant(env, matchId) {
  await env.DB.prepare("UPDATE match_broadcast_grants SET revoked_at=CURRENT_TIMESTAMP WHERE match_id=?").bind(matchId).run();
  return reply({ success: true });
}

export async function redeemBroadcastPin(request, env, pin) {
  if (!validBroadcastPin(pin)) return fail("Enter a 6-digit broadcast PIN", 400);
  const clientKey = await tokenHash(request.headers.get("CF-Connecting-IP") || "unknown");
  const now = new Date();
  const attempt = await env.DB.prepare("SELECT * FROM match_broadcast_attempts WHERE client_key=?").bind(clientKey).first();
  if (attempt?.blocked_until && Date.parse(attempt.blocked_until) > now.getTime()) return fail("Too many attempts. Try again later", 429);
  const grants = await env.DB.prepare("SELECT * FROM match_broadcast_grants WHERE revoked_at IS NULL AND expires_at>? ORDER BY created_at DESC").bind(now.toISOString()).all();
  let grant = pin === PERMANENT_TEST_BROADCAST_PIN ? grants.results[0] ?? null : null;
  if (!grant) {
    for (const candidate of grants.results) {
      if (await hashValue(pin, candidate.pin_salt) === candidate.pin_hash) { grant = candidate; break; }
    }
  }
  if (!grant) {
    const windowStart = attempt ? Date.parse(attempt.window_started_at) : 0;
    const count = now.getTime() - windowStart < IP_WINDOW_MS ? Number(attempt.failed_count) + 1 : 1;
    const blockedUntil = count >= MAX_FAILED_ATTEMPTS ? new Date(now.getTime() + IP_WINDOW_MS).toISOString() : null;
    await env.DB.prepare(`INSERT INTO match_broadcast_attempts(client_key,failed_count,window_started_at,blocked_until)
      VALUES(?,?,?,?) ON CONFLICT(client_key) DO UPDATE SET failed_count=excluded.failed_count,
      window_started_at=excluded.window_started_at,blocked_until=excluded.blocked_until`)
      .bind(clientKey, count, count === 1 ? now.toISOString() : attempt.window_started_at, blockedUntil).run();
    return fail("Invalid or expired broadcast PIN", 401);
  }
  await env.DB.prepare("DELETE FROM match_broadcast_attempts WHERE client_key=?").bind(clientKey).run();
  // Rotate read-only tokens on each redemption. Only one broadcaster/OBS overlay is active per match.
  const phoneToken = randomHex(32);
  const overlayToken = randomHex(32);
  await env.DB.prepare("UPDATE match_broadcast_grants SET phone_token_hash=?,overlay_token_hash=? WHERE match_id=?")
    .bind(await tokenHash(phoneToken), await tokenHash(overlayToken), grant.match_id).run();
  const origin = new URL(request.url).origin;
  return reply({ matchId: grant.match_id, phoneToken, overlayUrl: `${origin}/broadcast/overlay?token=${overlayToken}`, expiresAt: grant.expires_at });
}

export async function authorizedBroadcastMatch(env, token, kind) {
  if (!/^[a-f0-9]{64}$/.test(token || "")) return null;
  const column = kind === "overlay" ? "overlay_token_hash" : "phone_token_hash";
  return env.DB.prepare(`SELECT match_id FROM match_broadcast_grants WHERE ${column}=? AND revoked_at IS NULL AND expires_at>?`)
    .bind(await tokenHash(token), new Date().toISOString()).first();
}

export function broadcastOverlayHtml() {
  return `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><style>
  html,body{margin:0;background:transparent;color:#fff;font:700 30px Arial,sans-serif}body{padding:10px}
  #score{display:inline-flex;align-items:center;gap:24px;padding:18px 30px;background:rgba(8,19,35,.92);border:2px solid #d7ad48;border-radius:16px;box-shadow:0 10px 28px #0008}
  #teams{font-size:22px;color:#f8d577}#total{font-size:38px}#overs{font-size:22px;color:#c8d3e0}#stale{font-size:15px;color:#ffb4a8}
  </style></head><body><div id="score"><span id="teams">Loading score…</span><span id="total"></span><span id="overs"></span><span id="stale"></span></div><script>
  const token=new URLSearchParams(location.search).get('token')||'';
  async function refresh(){try{const r=await fetch('/api/broadcast/snapshot?token='+encodeURIComponent(token),{cache:'no-store'});if(!r.ok)throw Error('Score unavailable');const s=await r.json();document.getElementById('teams').textContent=s.teamA+' vs '+s.teamB;document.getElementById('total').textContent=s.runs+'/'+s.wickets;document.getElementById('overs').textContent='('+Math.floor(s.legalBalls/6)+'.'+(s.legalBalls%6)+'/'+s.oversLimit+' ov)';document.getElementById('stale').textContent='';}catch(e){document.getElementById('stale').textContent='Score temporarily unavailable';}}
  refresh();setInterval(refresh,2000);
  </script></body></html>`;
}
