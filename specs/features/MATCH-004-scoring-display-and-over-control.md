# MATCH-004 — Broadcast-style scoring controls

Status: implemented

## Intent

Make live scoring easier to read and harder to operate incorrectly during a match.

## Acceptance criteria

- [x] The innings total and over progress are the strongest visual elements.
- [x] Current striker and non-striker are shown side by side with runs and balls.
- [x] The striker is distinctly highlighted in teal and marked with an asterisk.
- [x] The current bowler, bowling figures, and recent deliveries are grouped together.
- [x] After the sixth legal ball, scoring is locked and a different bowler must be selected.
- [x] The bowler requirement is recovered from server state after refresh or restart.
- [x] A successful mutation response produces a temporary **Synced** confirmation.
- [x] Failed updates do not show **Synced** and continue to display the error.

## Invariants

- Wides and no-balls do not complete the over.
- The bowler who completed the previous over is not offered for the next over.
- Background GET refreshes never produce a false **Synced** confirmation.
- Existing undo, wicket, pause, lineup, and innings controls remain available.

## Verification

- Compile the staging application.
- Score five legal balls and confirm no bowler prompt appears.
- Score a wide or no-ball and confirm no bowler prompt appears.
- Score the sixth legal ball and confirm scoring locks until another bowler is selected.
- Confirm a successful update shows **Synced** and a rejected update does not.
