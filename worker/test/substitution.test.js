import test from "node:test";
import assert from "node:assert/strict";
import { substitutionRequestError } from "../src/substitution.js";

const match = { team_a_id: "team-a", team_b_id: "team-b" };

test("emergency changes target a match team and require an incoming player", () => {
  assert.equal(substitutionRequestError(match, { teamId:"team-a", playerId:"player-2", outgoingPlayerId:"player-1" }), null);
  assert.equal(substitutionRequestError(match, { teamId:"team-b", newPlayerName:"Late Player" }), null);
  assert.match(substitutionRequestError(match, { teamId:"team-c", playerId:"player-2" }), /teams in this match/);
  assert.match(substitutionRequestError(match, { teamId:"team-a" }), /existing player/);
  assert.match(substitutionRequestError(match, { teamId:"team-a", playerId:"player-1", outgoingPlayerId:"player-1" }), /different incoming/);
});
