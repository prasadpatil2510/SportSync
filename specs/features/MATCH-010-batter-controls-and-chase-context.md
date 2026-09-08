# MATCH-010 — Batter controls and chase context

Status: verified

## Goal

Keep the compact scoring screen readable and allow a safe striker replacement only before the first delivery of an over.

## Acceptance criteria

- [x] Long batter names use a smaller font and fall back to the first name when needed.
- [x] Pause and general player-replacement controls are removed from the scoring console.
- [x] The current striker can be replaced only before the first delivery of an over; the non-striker cannot be replaced directly.
- [x] Used and dismissed batters cannot be selected as replacements.
- [x] Wide and no-ball dialogs include zero additional runs and preserve their automatic free run.
- [x] The no-ball free run does not influence strike rotation.
- [x] The second innings shows runs required, balls remaining and wickets in hand.

## Verification

- Build the staging Android app.
- Run worker cricket-rule tests.
- Verify the specification registry.
