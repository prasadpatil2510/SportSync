export function substitutionRequestError(match, input = {}) {
  const teamId = String(input.teamId || "").trim();
  if (!match || ![match.team_a_id, match.team_b_id].includes(teamId)) return "Select one of the teams in this match";
  if (!String(input.playerId || "").trim() && !String(input.newPlayerName || "").trim()) return "Select an existing player or enter a new player name";
  if (String(input.playerId || "").trim() && String(input.playerId || "").trim() === String(input.outgoingPlayerId || "").trim()) return "Select a different incoming player";
  return null;
}
