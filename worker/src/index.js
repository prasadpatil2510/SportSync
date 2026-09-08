import { fieldersRequired, isLegalDelivery, matchResult, strikeRunningRuns } from "./cricket.js";
import { absoluteAssetUrl, normalizedName, shortName } from "./integration.js";
import { buildStandings, knockoutOpening, roundRobin } from "./scheduling.js";

const jsonHeaders = {
  "content-type": "application/json; charset=utf-8",
  "access-control-allow-origin": "*",
  "access-control-allow-headers": "content-type, authorization, x-admin-token",
  "access-control-allow-methods": "GET, POST, PUT, DELETE, OPTIONS"
};

const reply = (data, status = 200) => new Response(JSON.stringify(data), { status, headers: jsonHeaders });
const fail = (message, status = 400) => reply({ error: message }, status);
const makeId = prefix => `${prefix}_${crypto.randomUUID()}`;

async function body(request) {
  try { return await request.json(); }
  catch { throw new Error("Request body must be valid JSON"); }
}

function requireAdmin(request, env) {
  const supplied = request.headers.get("x-admin-token") || "";
  return Boolean(env.ADMIN_TOKEN) && supplied === env.ADMIN_TOKEN;
}

async function settings(env) {
  const result = await env.DB.prepare("SELECT key, value, updated_at FROM system_settings").all();
  return Object.fromEntries(result.results.map(row => [row.key, row.value]));
}

async function list(env, table) {
  const result = await env.DB.prepare(`SELECT * FROM ${table} ORDER BY created_at DESC`).all();
  return result.results;
}

async function matchView(env, matchId) {
  const match = await env.DB.prepare(`SELECT m.*,a.name team_a_name,b.name team_b_name,
    tw.name toss_winner_name,bt.name batting_team_name,bw.name bowling_team_name
    FROM matches m JOIN teams a ON a.id=m.team_a_id JOIN teams b ON b.id=m.team_b_id
    LEFT JOIN teams tw ON tw.id=m.toss_winner_id LEFT JOIN teams bt ON bt.id=m.batting_team_id
    LEFT JOIN teams bw ON bw.id=m.bowling_team_id WHERE m.id=?`).bind(matchId).first();
  if (!match) return null;
  const innings = await env.DB.prepare(`SELECT i.*,s.name striker_name,n.name non_striker_name,b.name bowler_name
    FROM innings i LEFT JOIN players s ON s.id=i.striker_id LEFT JOIN players n ON n.id=i.non_striker_id
    LEFT JOIN players b ON b.id=i.bowler_id WHERE i.match_id=? ORDER BY i.innings_number`).bind(matchId).all();
  return { ...match, innings: innings.results };
}

async function recalculateInnings(env, inningsId) {
  const totals = await env.DB.prepare(`SELECT COALESCE(SUM(batter_runs+extra_runs),0) runs,
    COALESCE(SUM(is_wicket),0) wickets, COALESCE(SUM(is_legal),0) legal_balls
    FROM deliveries WHERE innings_id=? AND is_void=0`).bind(inningsId).first();
  await env.DB.prepare("UPDATE innings SET runs=?,wickets=?,legal_balls=?,updated_at=CURRENT_TIMESTAMP WHERE id=?")
    .bind(totals.runs, totals.wickets, totals.legal_balls, inningsId).run();
  return totals;
}

async function finishMatch(env, match, current) {
  const first = await env.DB.prepare("SELECT * FROM innings WHERE match_id=? AND innings_number=1").bind(match.id).first();
  const battingName = await env.DB.prepare("SELECT name FROM teams WHERE id=?").bind(current.batting_team_id).first();
  const bowlingName = await env.DB.prepare("SELECT name FROM teams WHERE id=?").bind(current.bowling_team_id).first();
  const result = matchResult({ firstRuns: Number(first.runs), secondRuns: Number(current.runs), secondWickets: Number(current.wickets), battingTeamName: battingName.name, bowlingTeamName: bowlingName.name });
  await env.DB.batch([
    env.DB.prepare("UPDATE innings SET status='COMPLETE',updated_at=CURRENT_TIMESTAMP WHERE id=?").bind(current.id),
    env.DB.prepare("UPDATE matches SET status='COMPLETE',result_text=?,updated_at=CURRENT_TIMESTAMP WHERE id=?").bind(result,match.id)
  ]);
  return result;
}

const clean = value => String(value || "").trim();

export function validDate(value) {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(clean(value));
  if (!match) return false;
  const [, year, month, day] = match.map(Number);
  const parsed = new Date(Date.UTC(year, month - 1, day));
  return parsed.getUTCFullYear() === year && parsed.getUTCMonth() === month - 1 && parsed.getUTCDate() === day;
}

export function validDateTime(value) {
  const match = /^(\d{4}-\d{2}-\d{2}) (\d{2}):(\d{2})$/.exec(clean(value));
  return Boolean(match && validDate(match[1]) && Number(match[2]) < 24 && Number(match[3]) < 60);
}

async function fetchAuctionJson(env, path) {
  const base = clean(env.AUCTION_API_BASE_URL).replace(/\/+$/, "");
  if (!base) throw new Error("Auction integration is not configured");
  const response = await fetch(`${base}/api/${path}`, { headers: { accept: "application/json" } });
  if (!response.ok) throw new Error(`Auction ${path} request failed (${response.status})`);
  return { base, value: await response.json() };
}

async function resolveAuction(env, reference) {
  const requested = clean(reference);
  if (!requested) throw new Error("Enter an auction ID, exact auction name, or auction link");
  const catalog = (await fetchAuctionJson(env, "auctions")).value;
  const auctions = Array.isArray(catalog) ? catalog : catalog.auctions;
  if (!Array.isArray(auctions)) throw new Error("Auction catalogue is unavailable");
  const urlPart = requested.split(/[/?#]/).filter(Boolean).at(-1);
  const found = auctions.find(item => clean(item.id) === requested || clean(item.id) === urlPart || normalizedName(item.name) === normalizedName(requested));
  if (!found) throw new Error("Auction not found. Check the exact ID, name, or link");
  const activeId = clean(catalog.activeId || catalog.activeAuctionId);
  if (activeId && clean(found.id) !== activeId) throw new Error(`Make “${clean(found.name)}” active in the auctioneer website, then import again`);
  return found;
}

async function refreshAuctionData(env, tournamentId, auctionReference) {
  const tournament = await env.DB.prepare("SELECT id FROM tournaments WHERE id=?").bind(tournamentId).first();
  if (!tournament) throw new Error("Tournament not found");
  const runId = makeId("import");
  await env.DB.prepare("INSERT INTO import_runs(id,source,tournament_id,status) VALUES(?,'AUCTION',?,'RUNNING')").bind(runId,tournamentId).run();
  try {
    const auction = await resolveAuction(env, auctionReference);
    const [stateResult, registrationResult] = await Promise.all([
      fetchAuctionJson(env, "state"), fetchAuctionJson(env, "player-registrations")
    ]);
    const state = stateResult.value;
    const registrations = registrationResult.value;
    if (!state || !Array.isArray(state.teams) || !Array.isArray(state.players) || !Array.isArray(registrations)) throw new Error("Auction returned malformed data");
    const base = stateResult.base;
    const registrationByName = new Map(registrations.map(item => [normalizedName(item.name), item]));
    const counts = { teamsCreated: 0, teamsUpdated: 0, playersCreated: 0, playersUpdated: 0, membershipsAdded: 0 };
    const statements = [];
    const teamIds = new Map();
    const playerIds = new Map();

    for (const team of state.teams.filter(item => item && item.active !== false)) {
      const externalId = clean(team.id);
      if (!externalId || !clean(team.name)) continue;
      const id = `auction_team_${externalId.replace(/[^a-zA-Z0-9_-]/g, "_")}`;
      teamIds.set(externalId, id);
      const exists = await env.DB.prepare("SELECT id FROM teams WHERE external_source='AUCTION' AND external_id=?").bind(externalId).first();
      exists ? counts.teamsUpdated++ : counts.teamsCreated++;
      statements.push(env.DB.prepare(`INSERT INTO teams(id,name,short_name,logo_url,captain_name,external_source,external_id,updated_at)
        VALUES(?,?,?,?,?,'AUCTION',?,CURRENT_TIMESTAMP)
        ON CONFLICT(id) DO UPDATE SET name=excluded.name,short_name=excluded.short_name,logo_url=excluded.logo_url,captain_name=excluded.captain_name,external_source=excluded.external_source,external_id=excluded.external_id,is_active=1,updated_at=CURRENT_TIMESTAMP`)
        .bind(id,clean(team.name),shortName(team.name),absoluteAssetUrl(base,team.logoUrl),clean(team.captain),externalId));
      statements.push(env.DB.prepare("INSERT OR IGNORE INTO tournament_teams(tournament_id,team_id) VALUES(?,?)").bind(tournamentId,id));
    }

    const profiles = [...registrations];
    for (const team of state.teams.filter(item => item && item.active !== false && clean(item.captain))) {
      if (!registrationByName.has(normalizedName(team.captain))) profiles.push({ id: `captain_${team.id}`, name: team.captain, role: "Player", photo: null });
    }
    for (const item of state.players) {
      if (!registrationByName.has(normalizedName(item.name))) profiles.push({ id: `player_${item.id}`, name: item.name, role: item.role, photo: item.photo || item.photoUrl || null });
    }

    for (const profile of profiles) {
      const externalId = clean(profile.id);
      if (!externalId || !clean(profile.name) || playerIds.has(normalizedName(profile.name))) continue;
      const id = `auction_player_${externalId.replace(/[^a-zA-Z0-9_-]/g, "_")}`;
      playerIds.set(normalizedName(profile.name), id);
      const exists = await env.DB.prepare("SELECT id FROM players WHERE external_source='AUCTION' AND external_id=?").bind(externalId).first();
      exists ? counts.playersUpdated++ : counts.playersCreated++;
      statements.push(env.DB.prepare(`INSERT INTO players(id,name,role,photo_url,external_source,external_id,updated_at)
        VALUES(?,?,?,?, 'AUCTION',?,CURRENT_TIMESTAMP)
        ON CONFLICT(id) DO UPDATE SET name=excluded.name,role=excluded.role,photo_url=COALESCE(excluded.photo_url,players.photo_url),external_source=excluded.external_source,external_id=excluded.external_id,is_active=1,updated_at=CURRENT_TIMESTAMP`)
        .bind(id,clean(profile.name),clean(profile.role)||"PLAYER",absoluteAssetUrl(base,profile.photo || profile.photoUrl),externalId));
    }

    for (const team of state.teams.filter(item => item && item.active !== false)) {
      const teamId = teamIds.get(clean(team.id));
      const memberNames = [clean(team.captain), ...state.players.filter(player => player && player.status === "SOLD" && player.soldTo === team.id).map(player => clean(player.name))].filter(Boolean);
      for (const name of new Set(memberNames)) {
        const playerId = playerIds.get(normalizedName(name));
        if (!teamId || !playerId) continue;
        const exists = await env.DB.prepare("SELECT 1 found FROM team_players WHERE team_id=? AND player_id=?").bind(teamId,playerId).first();
        if (!exists) counts.membershipsAdded++;
        const captain = normalizedName(name) === normalizedName(team.captain);
        statements.push(env.DB.prepare(`INSERT INTO team_players(team_id,player_id,squad_status,member_role,is_captain)
          VALUES(?,?,'ACTIVE',?,?) ON CONFLICT(team_id,player_id) DO UPDATE SET squad_status='ACTIVE',member_role=excluded.member_role,is_captain=excluded.is_captain`)
          .bind(teamId,playerId,captain?"CAPTAIN":"PLAYER",captain?1:0));
      }
    }
    statements.push(env.DB.prepare(`UPDATE import_runs SET status='SUCCESS',teams_created=?,teams_updated=?,players_created=?,players_updated=?,memberships_added=?,completed_at=CURRENT_TIMESTAMP WHERE id=?`)
      .bind(counts.teamsCreated,counts.teamsUpdated,counts.playersCreated,counts.playersUpdated,counts.membershipsAdded,runId));
    statements.push(env.DB.prepare("UPDATE tournaments SET auction_reference=?,updated_at=CURRENT_TIMESTAMP WHERE id=?").bind(clean(auction.id), tournamentId));
    if (statements.length) await env.DB.batch(statements);
    return { success: true, importId: runId, auctionId: clean(auction.id), auctionName: clean(auction.name), ...counts };
  } catch (error) {
    await env.DB.prepare("UPDATE import_runs SET status='FAILED',error_text=?,completed_at=CURRENT_TIMESTAMP WHERE id=?").bind(String(error.message || error).slice(0,500),runId).run();
    throw error;
  }
}

async function route(request, env) {
  const url = new URL(request.url);
  const path = url.pathname.replace(/\/+$/, "") || "/";

  if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: jsonHeaders });

  if (path === "/api/health" && request.method === "GET") {
    const currentSettings = await settings(env);
    return reply({
      status: "UP",
      environment: env.APP_ENV,
      database: "CONNECTED",
      maintenanceMode: currentSettings.maintenance_mode === "true",
      testingEnabled: currentSettings.testing_enabled === "true",
      checkedAt: new Date().toISOString()
    });
  }

  if (path === "/api/admin/status" && request.method === "GET") {
    if (!requireAdmin(request, env)) return fail("Admin access required", 401);
    const [currentSettings, counts] = await Promise.all([
      settings(env),
      env.DB.prepare(`SELECT
        (SELECT COUNT(*) FROM tournaments) tournament_count,
        (SELECT COUNT(*) FROM teams) team_count,
        (SELECT COUNT(*) FROM players) player_count`).first()
    ]);
    return reply({ environment: env.APP_ENV, settings: currentSettings, counts });
  }

  if (path === "/api/admin/testing" && request.method === "PUT") {
    if (!requireAdmin(request, env)) return fail("Admin access required", 401);
    const input = await body(request);
    const value = input.enabled === true ? "true" : "false";
    await env.DB.prepare("INSERT INTO system_settings(key,value,updated_at) VALUES('testing_enabled',?,CURRENT_TIMESTAMP) ON CONFLICT(key) DO UPDATE SET value=excluded.value, updated_at=CURRENT_TIMESTAMP").bind(value).run();
    return reply({ testingEnabled: value === "true" });
  }

  if (path === "/api/admin/maintenance" && request.method === "PUT") {
    if (!requireAdmin(request, env)) return fail("Admin access required", 401);
    const input = await body(request);
    const value = input.enabled === true ? "true" : "false";
    await env.DB.prepare("INSERT INTO system_settings(key,value,updated_at) VALUES('maintenance_mode',?,CURRENT_TIMESTAMP) ON CONFLICT(key) DO UPDATE SET value=excluded.value, updated_at=CURRENT_TIMESTAMP").bind(value).run();
    return reply({ maintenanceMode: value === "true" });
  }

  if (path === "/api/tournaments" && request.method === "GET") return reply(await list(env, "tournaments"));
  if (path === "/api/teams" && request.method === "GET") {
    const result = await env.DB.prepare("SELECT * FROM teams WHERE is_active=1 ORDER BY name").all();
    return reply(result.results);
  }
  if (path === "/api/players" && request.method === "GET") return reply(await list(env, "players"));

  if (path === "/api/integrations/auction/status" && request.method === "GET") {
    if (!requireAdmin(request, env)) return fail("Admin access required", 401);
    const latest = await env.DB.prepare("SELECT * FROM import_runs WHERE source='AUCTION' ORDER BY started_at DESC LIMIT 1").first();
    return reply({ configured: Boolean(clean(env.AUCTION_API_BASE_URL)), latest: latest || null });
  }

  if (path === "/api/integrations/auction/refresh" && request.method === "POST") {
    if (!requireAdmin(request, env)) return fail("Admin access required", 401);
    const input = await body(request);
    if (!clean(input.tournamentId)) return fail("Tournament is required");
    return reply(await refreshAuctionData(env, clean(input.tournamentId), clean(input.auctionReference)));
  }

  if (path === "/api/public/auction/player-stats" && request.method === "GET") {
    const requested = clean(url.searchParams.get("externalId"));
    const where = requested ? "WHERE p.external_source='AUCTION' AND p.external_id=?" : "WHERE p.external_source='AUCTION'";
    const query = env.DB.prepare(`SELECT p.id,p.external_id externalId,p.name,p.role,p.photo_url photoUrl,
      (SELECT COUNT(DISTINCT mp.match_id) FROM match_players mp JOIN matches m ON m.id=mp.match_id WHERE mp.player_id=p.id AND m.status='COMPLETE') matches,
      (SELECT COALESCE(SUM(d.batter_runs),0) FROM deliveries d WHERE d.striker_id=p.id AND d.is_void=0) runs,
      (SELECT COALESCE(SUM(CASE WHEN d.is_legal=1 THEN 1 ELSE 0 END),0) FROM deliveries d WHERE d.striker_id=p.id AND d.is_void=0) balls,
      (SELECT COALESCE(SUM(CASE WHEN d.batter_runs=4 THEN 1 ELSE 0 END),0) FROM deliveries d WHERE d.striker_id=p.id AND d.is_void=0) fours,
      (SELECT COALESCE(SUM(CASE WHEN d.batter_runs=6 THEN 1 ELSE 0 END),0) FROM deliveries d WHERE d.striker_id=p.id AND d.is_void=0) sixes,
      (SELECT COALESCE(SUM(CASE WHEN d.is_wicket=1 AND COALESCE(d.dismissal_type,'')<>'RUN_OUT' THEN 1 ELSE 0 END),0) FROM deliveries d WHERE d.bowler_id=p.id AND d.is_void=0) wickets
      FROM players p ${where} ORDER BY p.name`);
    const result = requested ? await query.bind(requested).all() : await query.all();
    if (requested && result.results.length === 0) return fail("Player statistics not found",404);
    return reply({ generatedAt:new Date().toISOString(),players:result.results });
  }

  const tournamentMatchesMatch = path.match(/^\/api\/tournaments\/([^/]+)\/matches$/);
  if (tournamentMatchesMatch && request.method === "GET") {
    const result = await env.DB.prepare(`SELECT m.*,a.name team_a_name,b.name team_b_name
      FROM matches m JOIN teams a ON a.id=m.team_a_id JOIN teams b ON b.id=m.team_b_id
      WHERE m.tournament_id=? ORDER BY m.created_at DESC`).bind(tournamentMatchesMatch[1]).all();
    return reply(result.results);
  }

  const tournamentPointsMatch = path.match(/^\/api\/tournaments\/([^/]+)\/points-table$/);
  if (tournamentPointsMatch && request.method === "GET") {
    const tournamentId = tournamentPointsMatch[1];
    const [teamsResult, matchesResult] = await Promise.all([
      env.DB.prepare(`SELECT t.id,t.name,t.logo_url FROM teams t JOIN tournament_teams tt ON tt.team_id=t.id WHERE tt.tournament_id=? AND t.is_active=1`).bind(tournamentId).all(),
      env.DB.prepare(`SELECT * FROM matches WHERE tournament_id=? AND status='COMPLETE' AND stage_type='LEAGUE'`).bind(tournamentId).all()
    ]);
    const completed = [];
    for (const match of matchesResult.results) {
      const innings = await env.DB.prepare(`SELECT i.*,
        (SELECT COUNT(*) FROM match_players mp WHERE mp.match_id=i.match_id AND mp.team_id=i.batting_team_id AND mp.is_playing=1) batting_players,
        ? overs_per_innings FROM innings i WHERE i.match_id=? ORDER BY i.innings_number`).bind(match.overs_per_innings, match.id).all();
      completed.push({ ...match, innings: innings.results });
    }
    return reply(buildStandings(teamsResult.results, completed));
  }

  const tournamentScheduleMatch = path.match(/^\/api\/tournaments\/([^/]+)\/schedule$/);
  if (tournamentScheduleMatch && request.method === "POST") {
    if (!requireAdmin(request, env)) return fail("Admin access required", 401);
    const tournamentId = tournamentScheduleMatch[1];
    const input = await body(request);
    const teamIds = Array.isArray(input.teamIds) ? [...new Set(input.teamIds.map(clean).filter(Boolean))] : [];
    if (teamIds.length < 2) return fail("Select at least two teams");
    if (!validDateTime(input.startDateTime)) return fail("Select a valid first match date and time");
    const existing = await env.DB.prepare("SELECT COUNT(*) count FROM matches WHERE tournament_id=?").bind(tournamentId).first();
    if (Number(existing.count)) return fail("This tournament already has matches. Add further matches manually");
    const allowed = await env.DB.prepare(`SELECT team_id FROM tournament_teams WHERE tournament_id=?`).bind(tournamentId).all();
    const allowedIds = new Set(allowed.results.map(row => row.team_id));
    if (teamIds.some(id => !allowedIds.has(id))) return fail("One or more selected teams are not in this tournament");
    const format = clean(input.format).toUpperCase();
    let rounds, stage = "LEAGUE";
    if (format === "ROUND_ROBIN") rounds = roundRobin(teamIds, 1);
    else if (format === "DOUBLE_ROUND_ROBIN") rounds = roundRobin(teamIds, 2);
    else if (format === "KNOCKOUT") { rounds = knockoutOpening(teamIds); stage = "KNOCKOUT"; }
    else return fail("Unsupported schedule format");
    const start = new Date(`${input.startDateTime.replace(" ", "T")}:00Z`);
    const interval = Math.min(10080, Math.max(15, Number(input.intervalMinutes) || 120));
    const overs = Math.max(1, Number(input.oversPerInnings) || 20);
    const statements = [];
    let fixtureIndex = 0;
    rounds.forEach((pairs, roundIndex) => pairs.forEach(([teamA, teamB]) => {
      const scheduled = new Date(start.getTime() + fixtureIndex * interval * 60000).toISOString().slice(0, 16).replace("T", " ");
      const roundsPerLeg = format === "DOUBLE_ROUND_ROBIN" ? rounds.length / 2 : rounds.length;
      const leg = format === "DOUBLE_ROUND_ROBIN" && roundIndex >= roundsPerLeg ? "Leg 2 " : "";
      const roundName = stage === "KNOCKOUT" ? "Knockout Opening Round" : `League ${leg}Round ${(roundIndex % roundsPerLeg) + 1}`;
      statements.push(env.DB.prepare(`INSERT INTO matches(id,tournament_id,round_name,team_a_id,team_b_id,scheduled_at,ground,overs_per_innings,stage_type) VALUES(?,?,?,?,?,?,?,?,?)`)
        .bind(makeId("match"), tournamentId, roundName, teamA, teamB, scheduled, clean(input.ground), overs, stage));
      fixtureIndex++;
    }));
    if (statements.length) await env.DB.batch(statements);
    return reply({ success: true, format, rounds: rounds.length, matchesCreated: fixtureIndex }, 201);
  }

  if (path === "/api/matches" && request.method === "GET") {
    const result = await env.DB.prepare(`SELECT m.*,a.name team_a_name,b.name team_b_name
      FROM matches m JOIN teams a ON a.id=m.team_a_id JOIN teams b ON b.id=m.team_b_id
      ORDER BY CASE m.status WHEN 'LIVE' THEN 0 WHEN 'PAUSED' THEN 1 WHEN 'SCHEDULED' THEN 2 ELSE 3 END,
      COALESCE(NULLIF(m.scheduled_at,''),m.created_at) DESC,m.created_at DESC`).all();
    return reply(result.results);
  }

  if (path === "/api/matches" && request.method === "POST") {
    if (!requireAdmin(request, env)) return fail("Admin access required", 401);
    const input = await body(request);
    if (!input.tournamentId || !input.teamAId || !input.teamBId) return fail("Tournament and two teams are required");
    if (input.teamAId === input.teamBId) return fail("Select two different teams");
    if (!validDateTime(input.scheduledAt)) return fail("Select a valid match date and time");
    const id = makeId("match");
    await env.DB.prepare(`INSERT INTO matches(id,tournament_id,round_name,team_a_id,team_b_id,scheduled_at,ground,overs_per_innings)
      VALUES(?,?,?,?,?,?,?,?)`).bind(id,input.tournamentId,clean(input.roundName)||"League Match",input.teamAId,input.teamBId,
      clean(input.scheduledAt),clean(input.ground),Math.max(1,Number(input.oversPerInnings)||20)).run();
    return reply(await matchView(env, id), 201);
  }

  const matchMatch = path.match(/^\/api\/matches\/([^/]+)$/);
  if (matchMatch && request.method === "GET") {
    const value = await matchView(env, matchMatch[1]);
    return value ? reply(value) : fail("Match not found", 404);
  }

  const matchStatusMatch = path.match(/^\/api\/matches\/([^/]+)\/status$/);
  if (matchStatusMatch && request.method === "PUT") {
    if (!requireAdmin(request, env)) return fail("Admin access required", 401);
    const input = await body(request);
    if (!["LIVE","PAUSED"].includes(input.status)) return fail("Status must be LIVE or PAUSED");
    await env.DB.prepare("UPDATE matches SET status=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND status IN ('LIVE','PAUSED')").bind(input.status,matchStatusMatch[1]).run();
    const value = await matchView(env, matchStatusMatch[1]);
    return value ? reply(value) : fail("Match not found", 404);
  }

  const lineupMatch = path.match(/^\/api\/matches\/([^/]+)\/lineup\/([^/]+)$/);
  if (lineupMatch && request.method === "PUT") {
    if (!requireAdmin(request, env)) return fail("Admin access required", 401);
    const input = await body(request);
    const playerIds = Array.isArray(input.playerIds) ? [...new Set(input.playerIds)] : [];
    if (playerIds.length < 2) return fail("Select at least two players");
    await env.DB.prepare("DELETE FROM match_players WHERE match_id=? AND team_id=?").bind(lineupMatch[1],lineupMatch[2]).run();
    for (const playerId of playerIds) await env.DB.prepare("INSERT INTO match_players(match_id,team_id,player_id,is_playing) VALUES(?,?,?,1)").bind(lineupMatch[1],lineupMatch[2],playerId).run();
    return reply({ success: true, count: playerIds.length });
  }

  const lineupListMatch = path.match(/^\/api\/matches\/([^/]+)\/lineup$/);
  if (lineupListMatch && request.method === "GET") {
    const result = await env.DB.prepare(`SELECT mp.team_id,p.*,mp.is_playing,mp.is_substitute FROM match_players mp
      JOIN players p ON p.id=mp.player_id WHERE mp.match_id=? ORDER BY mp.team_id,p.name`).bind(lineupListMatch[1]).all();
    return reply(result.results);
  }

  const tossMatch = path.match(/^\/api\/matches\/([^/]+)\/toss$/);
  if (tossMatch && request.method === "PUT") {
    if (!requireAdmin(request, env)) return fail("Admin access required", 401);
    const input = await body(request);
    const match = await env.DB.prepare("SELECT * FROM matches WHERE id=?").bind(tossMatch[1]).first();
    if (!match) return fail("Match not found", 404);
    if (![match.team_a_id, match.team_b_id].includes(input.tossWinnerId)) return fail("Invalid toss winner");
    const decision = input.decision === "BOWL" ? "BOWL" : "BAT";
    const other = input.tossWinnerId === match.team_a_id ? match.team_b_id : match.team_a_id;
    const batting = decision === "BAT" ? input.tossWinnerId : other;
    const bowling = batting === match.team_a_id ? match.team_b_id : match.team_a_id;
    const inningsId = makeId("innings");
    await env.DB.batch([
      env.DB.prepare(`UPDATE matches SET toss_winner_id=?,toss_decision=?,batting_team_id=?,bowling_team_id=?,current_innings=1,status='LIVE',updated_at=CURRENT_TIMESTAMP WHERE id=?`).bind(input.tossWinnerId,decision,batting,bowling,match.id),
      env.DB.prepare(`INSERT INTO innings(id,match_id,innings_number,batting_team_id,bowling_team_id,striker_id,non_striker_id,bowler_id)
        VALUES(?,?,1,?,?,?,?,?) ON CONFLICT(match_id,innings_number) DO UPDATE SET striker_id=excluded.striker_id,non_striker_id=excluded.non_striker_id,bowler_id=excluded.bowler_id`).bind(inningsId,match.id,batting,bowling,input.strikerId,input.nonStrikerId,input.bowlerId)
    ]);
    return reply(await matchView(env, match.id));
  }

  const participantsMatch = path.match(/^\/api\/innings\/([^/]+)\/participants$/);
  if (participantsMatch && request.method === "PUT") {
    if (!requireAdmin(request, env)) return fail("Admin access required", 401);
    const input = await body(request);
    await env.DB.prepare(`UPDATE innings SET striker_id=COALESCE(?,striker_id),non_striker_id=COALESCE(?,non_striker_id),bowler_id=COALESCE(?,bowler_id),updated_at=CURRENT_TIMESTAMP WHERE id=?`)
      .bind(input.strikerId||null,input.nonStrikerId||null,input.bowlerId||null,participantsMatch[1]).run();
    return reply({ success: true });
  }

  const deliveryMatch = path.match(/^\/api\/innings\/([^/]+)\/deliveries$/);
  if (deliveryMatch && request.method === "GET") {
    const result = await env.DB.prepare(`SELECT d.*,s.name striker_name,b.name bowler_name,x.name dismissed_player_name,
      f.name fielder_name,af.name assistant_fielder_name
      FROM deliveries d LEFT JOIN players s ON s.id=d.striker_id LEFT JOIN players b ON b.id=d.bowler_id
      LEFT JOIN players x ON x.id=d.dismissed_player_id LEFT JOIN players f ON f.id=d.fielder_id
      LEFT JOIN players af ON af.id=d.assistant_fielder_id WHERE d.innings_id=? AND d.is_void=0 ORDER BY d.sequence_number DESC LIMIT 30`).bind(deliveryMatch[1]).all();
    return reply(result.results);
  }
  if (deliveryMatch && request.method === "POST") {
    if (!requireAdmin(request, env)) return fail("Admin access required", 401);
    const input = await body(request);
    const innings = await env.DB.prepare("SELECT i.*,m.status match_status FROM innings i JOIN matches m ON m.id=i.match_id WHERE i.id=? AND i.status='LIVE'").bind(deliveryMatch[1]).first();
    if (!innings) return fail("Live innings not found", 404);
    if (innings.match_status !== "LIVE") return fail("Resume the match before scoring", 409);
    const batterRuns = Math.max(0, Number(input.batterRuns)||0);
    const extraRuns = Math.max(0, Number(input.extraRuns)||0);
    const extraType = input.extraType || "NONE";
    const legal = isLegalDelivery(extraType);
    const fielding = fieldersRequired(input.dismissalType);
    if (input.isWicket && fielding.primary && !clean(input.fielderId)) return fail(`${input.dismissalType === "CAUGHT" ? "Catcher" : "Primary fielder"} is required`);
    if (clean(input.fielderId) || clean(input.assistantFielderId)) {
      const validFielders = await env.DB.prepare("SELECT player_id FROM match_players WHERE match_id=? AND team_id=? AND is_playing=1").bind(innings.match_id,innings.bowling_team_id).all();
      const ids = new Set(validFielders.results.map(row => row.player_id));
      if (clean(input.fielderId) && !ids.has(input.fielderId)) return fail("Selected fielder is not in the bowling playing XI");
      if (clean(input.assistantFielderId) && !ids.has(input.assistantFielderId)) return fail("Selected assisting fielder is not in the bowling playing XI");
      if (input.fielderId === input.assistantFielderId) return fail("Select different primary and assisting fielders");
    }
    const seq = await env.DB.prepare("SELECT COALESCE(MAX(sequence_number),0)+1 next FROM deliveries WHERE innings_id=?").bind(innings.id).first();
    const id = makeId("ball");
    await env.DB.prepare(`INSERT INTO deliveries(id,innings_id,sequence_number,striker_id,non_striker_id,bowler_id,batter_runs,extra_runs,extra_type,is_wicket,dismissal_type,dismissed_player_id,is_legal,note,fielder_id,assistant_fielder_id)
      VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)`).bind(id,innings.id,seq.next,innings.striker_id,innings.non_striker_id,innings.bowler_id,batterRuns,extraRuns,extraType,input.isWicket?1:0,input.dismissalType||null,input.dismissedPlayerId||null,legal?1:0,clean(input.note),clean(input.fielderId)||null,clean(input.assistantFielderId)||null).run();
    const totals = await recalculateInnings(env, innings.id);
    let striker = innings.striker_id, nonStriker = innings.non_striker_id;
    const runningRuns = strikeRunningRuns(batterRuns, extraRuns, extraType);
    if (runningRuns % 2 === 1) [striker, nonStriker] = [nonStriker, striker];
    if (input.isWicket && input.nextBatterId) {
      if (input.dismissedPlayerId === innings.non_striker_id) nonStriker = input.nextBatterId;
      else striker = input.nextBatterId;
    }
    if (legal && Number(totals.legal_balls) % 6 === 0) [striker, nonStriker] = [nonStriker, striker];
    await env.DB.prepare("UPDATE innings SET striker_id=?,non_striker_id=? WHERE id=?").bind(striker,nonStriker,innings.id).run();
    const match = await env.DB.prepare("SELECT * FROM matches WHERE id=?").bind(innings.match_id).first();
    const lineupCount = await env.DB.prepare("SELECT COUNT(*) count FROM match_players WHERE match_id=? AND team_id=? AND is_playing=1").bind(match.id,innings.batting_team_id).first();
    const allOut = Number(totals.wickets) >= Math.max(1,Number(lineupCount.count)-1);
    const oversDone = Number(totals.legal_balls) >= Number(match.overs_per_innings)*6;
    if (Number(match.current_innings) === 2) {
      const first = await env.DB.prepare("SELECT runs FROM innings WHERE match_id=? AND innings_number=1").bind(match.id).first();
      if (Number(totals.runs) > Number(first.runs) || allOut || oversDone) {
        const updatedCurrent = { ...innings, ...totals };
        await finishMatch(env, match, updatedCurrent);
      }
    } else if (allOut || oversDone) {
      await env.DB.batch([
        env.DB.prepare("UPDATE innings SET status='COMPLETE',updated_at=CURRENT_TIMESTAMP WHERE id=?").bind(innings.id),
        env.DB.prepare("UPDATE matches SET status='INNINGS_BREAK',updated_at=CURRENT_TIMESTAMP WHERE id=?").bind(match.id)
      ]);
    }
    return reply({ success: true, deliveryId: id, totals, strikerId: striker, nonStrikerId: nonStriker }, 201);
  }

  const scorecardMatch = path.match(/^\/api\/innings\/([^/]+)\/scorecard$/);
  if (scorecardMatch && request.method === "GET") {
    const batters = await env.DB.prepare(`SELECT p.id,p.name,COALESCE(SUM(d.batter_runs),0) runs,
      COALESCE(SUM(CASE WHEN d.is_legal=1 THEN 1 ELSE 0 END),0) balls,
      COALESCE(SUM(CASE WHEN d.batter_runs=4 THEN 1 ELSE 0 END),0) fours,
      COALESCE(SUM(CASE WHEN d.batter_runs=6 THEN 1 ELSE 0 END),0) sixes,
      MAX(CASE WHEN d.is_wicket=1 AND d.dismissed_player_id=p.id THEN d.dismissal_type END) dismissal,
      MAX(CASE WHEN d.is_wicket=1 AND d.dismissed_player_id=p.id THEN f.name END) fielder_name,
      MAX(CASE WHEN d.is_wicket=1 AND d.dismissed_player_id=p.id THEN af.name END) assistant_fielder_name,
      MAX(CASE WHEN d.is_wicket=1 AND d.dismissed_player_id=p.id THEN bw.name END) dismissal_bowler_name
      FROM deliveries d JOIN players p ON p.id=d.striker_id LEFT JOIN players f ON f.id=d.fielder_id
      LEFT JOIN players af ON af.id=d.assistant_fielder_id LEFT JOIN players bw ON bw.id=d.bowler_id
      WHERE d.innings_id=? AND d.is_void=0 GROUP BY p.id,p.name ORDER BY MIN(d.sequence_number)`).bind(scorecardMatch[1]).all();
    const bowlers = await env.DB.prepare(`WITH ordered AS (
        SELECT d.*,CAST((SUM(d.is_legal) OVER (ORDER BY d.sequence_number ROWS UNBOUNDED PRECEDING)-d.is_legal)/6 AS INTEGER) over_index,
          d.batter_runs+CASE WHEN d.extra_type IN ('WIDE','NO_BALL') THEN d.extra_runs ELSE 0 END conceded
        FROM deliveries d WHERE d.innings_id=? AND d.is_void=0
      ), figures AS (
        SELECT bowler_id,COALESCE(SUM(is_legal),0) legal_balls,COALESCE(SUM(conceded),0) runs,
          COALESCE(SUM(CASE WHEN is_wicket=1 AND COALESCE(dismissal_type,'')<>'RUN_OUT' THEN 1 ELSE 0 END),0) wickets
        FROM ordered GROUP BY bowler_id
      ), overs AS (
        SELECT bowler_id,over_index,SUM(is_legal) legal_balls,SUM(conceded) runs FROM ordered GROUP BY bowler_id,over_index
      ), maidens AS (
        SELECT bowler_id,SUM(CASE WHEN legal_balls=6 AND runs=0 THEN 1 ELSE 0 END) maidens FROM overs GROUP BY bowler_id
      )
      SELECT p.id,p.name,f.legal_balls,f.runs,f.wickets,COALESCE(m.maidens,0) maidens
      FROM figures f JOIN players p ON p.id=f.bowler_id LEFT JOIN maidens m ON m.bowler_id=f.bowler_id ORDER BY p.name`).bind(scorecardMatch[1]).all();
    return reply({ batters: batters.results, bowlers: bowlers.results });
  }

  const undoMatch = path.match(/^\/api\/innings\/([^/]+)\/undo$/);
  if (undoMatch && request.method === "POST") {
    if (!requireAdmin(request, env)) return fail("Admin access required", 401);
    const latest = await env.DB.prepare("SELECT * FROM deliveries WHERE innings_id=? AND is_void=0 ORDER BY sequence_number DESC LIMIT 1").bind(undoMatch[1]).first();
    if (!latest) return fail("No delivery to undo");
    await env.DB.batch([
      env.DB.prepare("UPDATE deliveries SET is_void=1 WHERE id=?").bind(latest.id),
      env.DB.prepare("UPDATE innings SET striker_id=?,non_striker_id=?,bowler_id=?,updated_at=CURRENT_TIMESTAMP WHERE id=?").bind(latest.striker_id,latest.non_striker_id,latest.bowler_id,undoMatch[1])
    ]);
    return reply({ success: true, totals: await recalculateInnings(env, undoMatch[1]) });
  }

  const endInningsMatch = path.match(/^\/api\/matches\/([^/]+)\/end-innings$/);
  if (endInningsMatch && request.method === "POST") {
    if (!requireAdmin(request, env)) return fail("Admin access required", 401);
    const input = await body(request);
    const match = await env.DB.prepare("SELECT * FROM matches WHERE id=?").bind(endInningsMatch[1]).first();
    if (!match || !["LIVE","INNINGS_BREAK"].includes(match.status)) return fail("Live match not found", 404);
    const current = await env.DB.prepare("SELECT * FROM innings WHERE match_id=? AND innings_number=?").bind(match.id,match.current_innings).first();
    if (match.current_innings === 1) {
      const hasOpeners = clean(input.strikerId) && clean(input.nonStrikerId) && clean(input.bowlerId);
      if (match.status !== "INNINGS_BREAK" && !hasOpeners) {
        await env.DB.batch([
          env.DB.prepare("UPDATE innings SET status='COMPLETE',updated_at=CURRENT_TIMESTAMP WHERE id=?").bind(current.id),
          env.DB.prepare("UPDATE matches SET status='INNINGS_BREAK',updated_at=CURRENT_TIMESTAMP WHERE id=?").bind(match.id)
        ]);
        return reply(await matchView(env,match.id));
      }
      if (!hasOpeners) return fail("Select two opening batters and an opening bowler");
      const id = makeId("innings");
      await env.DB.batch([
        env.DB.prepare("UPDATE innings SET status='COMPLETE',updated_at=CURRENT_TIMESTAMP WHERE id=?").bind(current.id),
        env.DB.prepare("UPDATE matches SET current_innings=2,batting_team_id=?,bowling_team_id=?,status='LIVE',updated_at=CURRENT_TIMESTAMP WHERE id=?").bind(current.bowling_team_id,current.batting_team_id,match.id),
        env.DB.prepare(`INSERT INTO innings(id,match_id,innings_number,batting_team_id,bowling_team_id,striker_id,non_striker_id,bowler_id) VALUES(?,?,2,?,?,?,?,?)`).bind(id,match.id,current.bowling_team_id,current.batting_team_id,input.strikerId,input.nonStrikerId,input.bowlerId)
      ]);
    } else {
      await finishMatch(env, match, current);
    }
    return reply(await matchView(env, match.id));
  }

  if (path === "/api/tournaments" && request.method === "POST") {
    if (!requireAdmin(request, env)) return fail("Admin access required", 401);
    const input = await body(request);
    if (!String(input.name || "").trim()) return fail("Tournament name is required");
    if (!validDate(input.startDate) || !validDate(input.endDate)) return fail("Select valid tournament dates");
    if (clean(input.endDate) < clean(input.startDate)) return fail("End date cannot be before start date");
    const item = {
      id: makeId("tournament"), name: clean(input.name), format: input.format || "ROUND_ROBIN",
      overs: Math.max(1, Number(input.oversPerInnings) || 20), city: clean(input.city), ground: clean(input.ground),
      organiserName: clean(input.organiserName), organiserPhone: clean(input.organiserPhone), organiserEmail: clean(input.organiserEmail),
      startDate: clean(input.startDate), endDate: clean(input.endDate), category: input.category || "OPEN",
      ballType: input.ballType || "TENNIS", pitchType: input.pitchType || null, matchType: input.matchType || "LIMITED_OVERS"
    };
    await env.DB.prepare(`INSERT INTO tournaments(
      id,name,format,overs_per_innings,status,city,ground,organiser_name,organiser_phone,organiser_email,
      start_date,end_date,category,ball_type,pitch_type,match_type
    ) VALUES(?,?,?,?,'ONGOING',?,?,?,?,?,?,?,?,?,?,?)`).bind(
      item.id,item.name,item.format,item.overs,item.city,item.ground,item.organiserName,item.organiserPhone,item.organiserEmail,
      item.startDate,item.endDate,item.category,item.ballType,item.pitchType,item.matchType
    ).run();
    return reply(item, 201);
  }

  if (path === "/api/teams" && request.method === "POST") {
    if (!requireAdmin(request, env)) return fail("Admin access required", 401);
    const input = await body(request);
    if (!String(input.name || "").trim()) return fail("Team name is required");
    const item = { id: makeId("team"), name: clean(input.name), shortName: clean(input.shortName || input.name).slice(0, 12), logoUrl: input.logoUrl || null, city: clean(input.city), captainName: clean(input.captainName), captainPhone: clean(input.captainPhone) };
    await env.DB.prepare("INSERT INTO teams(id,name,short_name,logo_url,city,captain_name,captain_phone) VALUES(?,?,?,?,?,?,?)").bind(item.id, item.name, item.shortName, item.logoUrl, item.city, item.captainName, item.captainPhone).run();
    if (input.tournamentId) await env.DB.prepare("INSERT OR IGNORE INTO tournament_teams(tournament_id,team_id) VALUES(?,?)").bind(input.tournamentId, item.id).run();
    return reply(item, 201);
  }

  const teamMatch = path.match(/^\/api\/teams\/([^/]+)$/);
  if (teamMatch && request.method === "PUT") {
    if (!requireAdmin(request, env)) return fail("Admin access required", 401);
    const input = await body(request);
    if (!String(input.name || "").trim()) return fail("Team name is required");
    await env.DB.prepare("UPDATE teams SET name=?,short_name=?,city=?,captain_name=?,captain_phone=?,updated_at=CURRENT_TIMESTAMP WHERE id=?")
      .bind(clean(input.name), clean(input.shortName || input.name).slice(0, 12), clean(input.city), clean(input.captainName), clean(input.captainPhone), teamMatch[1]).run();
    const updated = await env.DB.prepare("SELECT * FROM teams WHERE id=?").bind(teamMatch[1]).first();
    return updated ? reply(updated) : fail("Team not found", 404);
  }

  if (path === "/api/players" && request.method === "POST") {
    if (!requireAdmin(request, env)) return fail("Admin access required", 401);
    const input = await body(request);
    if (!String(input.name || "").trim()) return fail("Player name is required");
    const item = { id: makeId("player"), name: String(input.name).trim(), role: input.role || "PLAYER" };
    await env.DB.prepare("INSERT INTO players(id,name,role,batting_style,bowling_style,photo_url) VALUES(?,?,?,?,?,?)").bind(item.id, item.name, item.role, input.battingStyle || null, input.bowlingStyle || null, input.photoUrl || null).run();
    if (input.teamId) await env.DB.prepare("INSERT INTO team_players(team_id,player_id,member_role,is_admin,is_captain,is_wicket_keeper) VALUES(?,?,?,?,?,?)").bind(input.teamId,item.id,item.role,input.isAdmin ? 1 : 0,input.isCaptain ? 1 : 0,input.isWicketKeeper ? 1 : 0).run();
    return reply(item, 201);
  }

  const tournamentTeamsMatch = path.match(/^\/api\/tournaments\/([^/]+)\/teams$/);
  if (tournamentTeamsMatch && request.method === "GET") {
    const result = await env.DB.prepare(`SELECT t.* FROM teams t JOIN tournament_teams tt ON tt.team_id=t.id WHERE tt.tournament_id=? AND t.is_active=1 ORDER BY t.name`).bind(tournamentTeamsMatch[1]).all();
    return reply(result.results);
  }

  const teamPlayersMatch = path.match(/^\/api\/teams\/([^/]+)\/players$/);
  if (teamPlayersMatch && request.method === "GET") {
    const result = await env.DB.prepare(`SELECT p.*,tp.member_role,tp.is_admin,tp.is_captain,tp.is_wicket_keeper FROM players p JOIN team_players tp ON tp.player_id=p.id WHERE tp.team_id=? AND tp.squad_status='ACTIVE' ORDER BY p.name`).bind(teamPlayersMatch[1]).all();
    return reply(result.results);
  }

  const membershipMatch = path.match(/^\/api\/teams\/([^/]+)\/players\/([^/]+)\/role$/);
  if (membershipMatch && request.method === "PUT") {
    if (!requireAdmin(request, env)) return fail("Admin access required", 401);
    const input = await body(request);
    await env.DB.prepare("UPDATE team_players SET member_role=?,is_admin=?,is_captain=?,is_wicket_keeper=? WHERE team_id=? AND player_id=?").bind(input.role || "PLAYER",input.isAdmin ? 1 : 0,input.isCaptain ? 1 : 0,input.isWicketKeeper ? 1 : 0,membershipMatch[1],membershipMatch[2]).run();
    return reply({ success: true });
  }

  const playerMatch = path.match(/^\/api\/players\/([^/]+)$/);
  if (playerMatch && request.method === "PUT") {
    if (!requireAdmin(request, env)) return fail("Admin access required", 401);
    const input = await body(request);
    await env.DB.prepare("UPDATE players SET name=COALESCE(?,name), role=COALESCE(?,role), batting_style=COALESCE(?,batting_style), bowling_style=COALESCE(?,bowling_style), photo_url=COALESCE(?,photo_url), updated_at=CURRENT_TIMESTAMP WHERE id=?")
      .bind(input.name || null, input.role || null, input.battingStyle || null, input.bowlingStyle || null, input.photoUrl || null, playerMatch[1]).run();
    return reply({ success: true });
  }

  const teamPlayerMatch = path.match(/^\/api\/teams\/([^/]+)\/players\/([^/]+)$/);
  if (teamPlayerMatch && request.method === "PUT") {
    if (!requireAdmin(request, env)) return fail("Admin access required", 401);
    await env.DB.prepare("INSERT INTO team_players(team_id,player_id,squad_status) VALUES(?,?,'ACTIVE') ON CONFLICT(team_id,player_id) DO UPDATE SET squad_status='ACTIVE'").bind(teamPlayerMatch[1], teamPlayerMatch[2]).run();
    return reply({ success: true });
  }
  if (teamPlayerMatch && request.method === "DELETE") {
    if (!requireAdmin(request, env)) return fail("Admin access required", 401);
    await env.DB.prepare("UPDATE team_players SET squad_status='REMOVED' WHERE team_id=? AND player_id=?").bind(teamPlayerMatch[1], teamPlayerMatch[2]).run();
    return reply({ success: true });
  }

  return fail("Route not found", 404);
}

export default {
  async fetch(request, env) {
    try { return await route(request, env); }
    catch (error) { console.error(error); return fail(error.message || "Internal server error", 500); }
  }
};
