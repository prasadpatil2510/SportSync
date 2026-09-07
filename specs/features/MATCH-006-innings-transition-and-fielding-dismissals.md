# MATCH-006 — Innings transition and fielding dismissals

Status: verified

## Intent

Make the innings break reliable and capture the players involved in caught and run-out wickets.

## Acceptance criteria

- [x] Ending the first innings shows the innings-break setup screen immediately.
- [x] Starting the chase creates innings two and changes the match back to LIVE.
- [x] The second innings accepts scoring deliveries.
- [x] A caught dismissal requires selection of the catcher.
- [x] A run-out dismissal requires a primary fielder and permits an assisting fielder.
- [x] Fielding involvement is stored on the delivery ledger and returned by the delivery API.

## Invariants

- The first innings is completed before innings two is created.
- Innings two cannot start without two opening batters and an opening bowler.
- Selected fielders must belong to the bowling playing XI.
- Existing deliveries remain compatible; new fielder columns are nullable.

## Verification

- Worker rule tests and dry-run validation pass.
- Staging Android build compiles.
- Test a manual first-innings end, select chase participants, then score a ball.
- Record caught and run-out dismissals and verify involved players are retained after refresh.
