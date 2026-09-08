# MATCH-011 — Home data and date pickers

Status: verified

## Goal

Make the primary home tabs functional and prevent invalid tournament and match scheduling values.

## Acceptance criteria

- [x] The home Matches tab lists matches from every tournament.
- [x] Live matches appear first, followed by the newest scheduled and completed matches.
- [x] A home match card opens its existing setup or scoring flow.
- [x] The home Teams tab lists every active saved team and opens its squad.
- [x] Tournament dates use calendar selectors.
- [x] Match scheduling uses date and time selectors.
- [x] The API rejects malformed dates, impossible dates and malformed match times.

## Verification

- Run worker tests and the Worker dry-run compilation.
- Build the staging Android application.
- Verify the specification registry.
