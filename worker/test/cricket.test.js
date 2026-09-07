import test from "node:test";
import assert from "node:assert/strict";
import { isLegalDelivery, matchResult, strikeRunningRuns } from "../src/cricket.js";

test("wide and no-ball do not consume a legal delivery", () => {
  assert.equal(isLegalDelivery("WIDE"), false);
  assert.equal(isLegalDelivery("NO_BALL"), false);
  assert.equal(isLegalDelivery("BYE"), true);
  assert.equal(isLegalDelivery("NONE"), true);
});

test("a single automatic wide does not rotate strike", () => {
  assert.equal(strikeRunningRuns(0, 1, "WIDE"), 0);
});

test("byes and leg-byes use completed running runs", () => {
  assert.equal(strikeRunningRuns(0, 1, "BYE"), 1);
  assert.equal(strikeRunningRuns(0, 2, "LEG_BYE"), 2);
});

test("no-ball excludes its automatic penalty when rotating strike", () => {
  assert.equal(strikeRunningRuns(0, 1, "NO_BALL"), 0);
  assert.equal(strikeRunningRuns(1, 1, "NO_BALL"), 1);
  assert.equal(strikeRunningRuns(0, 2, "NO_BALL"), 1);
});

test("result covers chase, defence and tie", () => {
  assert.equal(matchResult({ firstRuns: 50, secondRuns: 51, secondWickets: 4, battingTeamName: "Blue", bowlingTeamName: "Red" }), "Blue won by 6 wickets");
  assert.equal(matchResult({ firstRuns: 50, secondRuns: 47, secondWickets: 9, battingTeamName: "Blue", bowlingTeamName: "Red" }), "Red won by 3 runs");
  assert.equal(matchResult({ firstRuns: 50, secondRuns: 50, secondWickets: 8, battingTeamName: "Blue", bowlingTeamName: "Red" }), "Match tied");
});
