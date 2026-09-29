import test from "node:test";
import assert from "node:assert/strict";
import { buildStandings, roundRobin } from "../src/scheduling.js";

test("single round robin schedules every pair exactly once with odd-team bye", () => {
  const rounds = roundRobin(["a", "b", "c", "d", "e"]);
  const pairs = rounds.flat();
  assert.equal(rounds.length, 5);
  assert.equal(pairs.length, 10);
  assert.equal(new Set(pairs.map(pair => pair.slice().sort().join("-"))).size, 10);
  assert.ok(pairs.every(([a, b]) => a !== b));
});

test("double round robin reverses every fixture", () => {
  const pairs = roundRobin(["a", "b", "c", "d"], 2).flat();
  assert.equal(pairs.length, 12);
  assert.ok(pairs.some(([a, b]) => a === "a" && b === "b"));
  assert.ok(pairs.some(([a, b]) => a === "b" && b === "a"));
});

test("standings rank by points then NRR", () => {
  const teams = [{ id: "a", name: "A" }, { id: "b", name: "B" }, { id: "c", name: "C" }];
  const match = (a, b, ar, br) => ({ team_a_id: a, team_b_id: b, innings: [
    { batting_team_id: a, runs: ar, wickets: 1, batting_players: 11, legal_balls: 120, overs_per_innings: 20 },
    { batting_team_id: b, runs: br, wickets: 2, batting_players: 11, legal_balls: 120, overs_per_innings: 20 }
  ] });
  const rows = buildStandings(teams, [match("a", "b", 160, 120), match("c", "a", 130, 100)]);
  assert.deepEqual(rows.map(row => row.teamId), ["c", "a", "b"]);
  assert.equal(rows[0].points, 2);
  assert.equal(rows[1].points, 2);
});

test("standard NRR permits legitimate high rates in short matches", () => {
  const teams = [{ id:"a",name:"A" },{ id:"b",name:"B" }];
  const rows = buildStandings(teams,[{team_a_id:"a",team_b_id:"b",innings:[
    {batting_team_id:"a",runs:66,wickets:1,batting_players:7,legal_balls:18,overs_per_innings:3},
    {batting_team_id:"b",runs:24,wickets:2,batting_players:7,legal_balls:18,overs_per_innings:3}
  ]}]);
  assert.equal(rows.find(row=>row.teamId==="a").nrr,14);
  assert.equal(rows.find(row=>row.teamId==="b").nrr,-14);
});

test("all-out innings use the full quota and zero-ball innings do not distort NRR", () => {
  const teams = [{ id:"a",name:"A" },{ id:"b",name:"B" }];
  const allOut = {team_a_id:"a",team_b_id:"b",innings:[
    {batting_team_id:"a",runs:60,wickets:6,batting_players:7,legal_balls:30,overs_per_innings:10},
    {batting_team_id:"b",runs:61,wickets:2,batting_players:7,legal_balls:48,overs_per_innings:10}
  ]};
  const rows = buildStandings(teams,[allOut]);
  assert.equal(rows.find(row=>row.teamId==="a").nrr,-1.625);
  assert.equal(rows.find(row=>row.teamId==="b").nrr,1.625);
  const invalid = buildStandings(teams,[{team_a_id:"a",team_b_id:"b",innings:[
    {batting_team_id:"a",runs:29,wickets:0,batting_players:7,legal_balls:12,overs_per_innings:10},
    {batting_team_id:"b",runs:0,wickets:0,batting_players:7,legal_balls:0,overs_per_innings:10}
  ]}]);
  assert.equal(invalid.find(row=>row.teamId==="a").nrr,0);
  assert.equal(invalid.find(row=>row.teamId==="b").nrr,0);
});
