# MATCH-007 — Scoring console and live scorecard

Status: verified

## Intent

Separate fast score entry from score viewing and make extras and dismissals complete and consistent.

## Acceptance criteria

- [x] The scoring console shows only deliveries from the current over and clears that strip after six legal balls.
- [x] The large live score and detailed scorecard are removed from the scoring console.
- [x] A separate live-score screen shows every innings, batter dismissal detail and complete bowler figures.
- [x] Bowling figures include overs, runs, wickets, maidens and economy.
- [x] Dismissal type and all involved-player inputs use uniform dropdown controls.
- [x] Wide and no-ball entry accepts one or more extra runs.

## Invariants

- Wide and no-ball remain illegal deliveries regardless of extra-run count.
- Current-over display is derived from the authoritative delivery ledger.
- The scorecard remains read-only.
- Score mutations retain the existing synced acknowledgement and stale-response protection.

## Verification

- Cricket-rule tests, Worker validation and Android staging compilation pass.
- Firebase staging release is distributed after the testing API is deployed.
