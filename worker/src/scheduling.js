export function roundRobin(teamIds, legs = 1) {
  const original = [...new Set(teamIds.filter(Boolean))];
  if (original.length < 2) throw new Error("Select at least two teams");
  const teams = original.length % 2 ? [...original, null] : original;
  const rounds = [];
  let rotation = [...teams];
  for (let round = 0; round < teams.length - 1; round++) {
    const pairs = [];
    for (let i = 0; i < teams.length / 2; i++) {
      const a = rotation[i];
      const b = rotation[teams.length - 1 - i];
      if (a && b) pairs.push(round % 2 ? [b, a] : [a, b]);
    }
    rounds.push(pairs);
    rotation = [rotation[0], rotation[rotation.length - 1], ...rotation.slice(1, -1)];
  }
  if (legs === 2) return [...rounds, ...rounds.map(pairs => pairs.map(([a, b]) => [b, a]))];
  return rounds;
}

export function knockoutOpening(teamIds) {
  const teams = [...new Set(teamIds.filter(Boolean))];
  if (teams.length < 2) throw new Error("Select at least two teams");
  const pairs = [];
  for (let i = 0; i < Math.floor(teams.length / 2); i++) pairs.push([teams[i], teams[teams.length - 1 - i]]);
  return [pairs];
}

export function buildStandings(teams, completedMatches) {
  const rows = new Map(teams.map(team => [team.id, { teamId: team.id, teamName: team.name, logoUrl: team.logo_url || null, played: 0, won: 0, lost: 0, tied: 0, noResult: 0, points: 0, runsFor: 0, ballsFor: 0, runsAgainst: 0, ballsAgainst: 0, nrr: 0 }]));
  const inningsBalls = innings => Number(innings.wickets) >= Number(innings.batting_players) - 1 ? Math.max(Number(innings.legal_balls), Number(innings.overs_per_innings) * 6) : Number(innings.legal_balls);
  for (const match of completedMatches) {
    const a = rows.get(match.team_a_id), b = rows.get(match.team_b_id);
    if (!a || !b) continue;
    a.played++; b.played++;
    const ia = match.innings.find(i => i.batting_team_id === a.teamId);
    const ib = match.innings.find(i => i.batting_team_id === b.teamId);
    if (ia && ib) {
      const aBalls = inningsBalls(ia), bBalls = inningsBalls(ib);
      a.runsFor += Number(ia.runs); a.ballsFor += aBalls; a.runsAgainst += Number(ib.runs); a.ballsAgainst += bBalls;
      b.runsFor += Number(ib.runs); b.ballsFor += bBalls; b.runsAgainst += Number(ia.runs); b.ballsAgainst += aBalls;
      if (Number(ia.runs) > Number(ib.runs)) { a.won++; a.points += 2; b.lost++; }
      else if (Number(ib.runs) > Number(ia.runs)) { b.won++; b.points += 2; a.lost++; }
      else { a.tied++; b.tied++; a.points++; b.points++; }
    } else { a.noResult++; b.noResult++; a.points++; b.points++; }
  }
  for (const row of rows.values()) row.nrr = Number(((row.ballsFor ? row.runsFor * 6 / row.ballsFor : 0) - (row.ballsAgainst ? row.runsAgainst * 6 / row.ballsAgainst : 0)).toFixed(3));
  return [...rows.values()].sort((a, b) => b.points - a.points || b.nrr - a.nrr || b.won - a.won || a.teamName.localeCompare(b.teamName));
}
