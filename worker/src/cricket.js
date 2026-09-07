export const NON_LEGAL_EXTRAS = new Set(["WIDE", "NO_BALL"]);

export function isLegalDelivery(extraType = "NONE") {
  return !NON_LEGAL_EXTRAS.has(extraType);
}

export function strikeRunningRuns(batterRuns = 0, extraRuns = 0, extraType = "NONE") {
  if (extraType === "BYE" || extraType === "LEG_BYE") return batterRuns + extraRuns;
  if (extraType === "NO_BALL") return batterRuns + Math.max(0, extraRuns - 1);
  return batterRuns;
}

export function matchResult({ firstRuns, secondRuns, secondWickets, battingTeamName, bowlingTeamName }) {
  if (secondRuns > firstRuns) return `${battingTeamName} won by ${Math.max(0, 10 - secondWickets)} wickets`;
  if (secondRuns < firstRuns) return `${bowlingTeamName} won by ${firstRuns - secondRuns} runs`;
  return "Match tied";
}

export function fieldersRequired(dismissalType) {
  if (dismissalType === "CAUGHT") return { primary: true, assistant: false };
  if (dismissalType === "RUN_OUT") return { primary: true, assistant: true };
  return { primary: false, assistant: false };
}
