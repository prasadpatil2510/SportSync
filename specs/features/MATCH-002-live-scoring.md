# MATCH-002 — Match setup and live scoring

Status: verified

## Intent

Allow an organiser to prepare and score a limited-overs cricket match from Android while keeping the cloud delivery ledger authoritative.

## Scope

- Match, teams, overs and playing-XI setup.
- Toss, opening batters and opening bowler.
- Runs, extras, wickets, strike rotation and over count.
- Pause/resume, bowler change, player replacement and undo.
- Innings break, chase target, completion and result.
- Recent deliveries and live batter/bowler summaries.

## Rules and invariants

- Wide and no-ball do not consume a legal delivery.
- A one-run automatic wide or no-ball penalty does not itself rotate strike.
- Odd completed running runs rotate strike; the end of every six legal balls rotates it again.
- Undo voids the latest active delivery and recalculates totals from the ledger.
- The server, not the Android screen, determines totals and results.
- A paused match rejects scoring commands.
- Stale refresh responses must not overwrite a locally acknowledged newer command.

## Failure behavior

- Reject scoring when the innings is not live.
- Reject scoring while the match is paused.
- Reject lineups containing fewer than two players per team.
- Preserve accepted deliveries when a client disconnects.

## Acceptance criteria

- [x] The organiser can select both playing XIs and toss details.
- [x] Legal balls, extras, wickets, strike and overs calculate consistently.
- [x] The latest delivery can be undone without deleting its audit record.
- [x] Two devices receive refreshed authoritative scores.
- [x] The second innings shows a target and produces a result.
- [x] Cricket-rule unit tests cover legal balls, strike rotation and results.

## Compatibility

Database migration `0003_matches_and_scoring.sql` is forward-only. Existing tournament, team and player records remain valid.
