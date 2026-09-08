# TOUR-012 — Auction-linked scheduling and standings

Status: implemented

## Goal

Let an organiser import the exact auction into a tournament, create manual or standard automatic fixtures, and see a trustworthy league points table.

## Behaviour

- An empty tournament offers **Start a match** and **Schedule matches**.
- Manual match setup selects both teams from compact dropdowns that include team logos.
- Auction import presents all available auctions newest-first. Selecting one imports that auction's teams and players without requiring it to be the currently active auction.
- Automatic scheduling supports single round robin, double round robin, and a knockout opening round. Round-robin fixtures use the circle method and support an odd number of teams with a bye.
- League standings award 2 points for a win and 1 point for a tie/no-result, then rank by points, net run rate, wins, and team name.
- Net run rate uses aggregate innings runs and legal balls; an all-out innings uses the scheduled full quota when calculating its rate.
- Knockout matches do not contribute to the league points table.

## Failure behaviour

- Scheduling requires at least two selected tournament teams, a valid date/time, and no existing tournament matches.
- An unknown or inactive auction reference imports nothing and returns a clear message.
- Repeated auction imports remain idempotent.

## Acceptance criteria

- [x] Empty tournament presents both match actions.
- [x] Manual match team selection is a logo dropdown.
- [x] Exact auction reference is resolved and persisted.
- [x] Single/double league and knockout-opening fixtures can be generated.
- [x] Odd-team scheduling never creates a team-vs-itself fixture.
- [x] Points table is calculated from completed league matches.
- [x] Worker tests cover fixture counts, pair uniqueness, and standings order.

## Compatibility

New database columns are nullable/defaulted. Existing tournaments and matches continue to work and manual matches remain league matches.
