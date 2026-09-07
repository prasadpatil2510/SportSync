# INT-005 — Auction data bridge

Status: verified

## Goal

Reuse auction teams, registered player profiles and photos in SportSync without allowing either application to overwrite the other application's source data.

## Invariants

- Import is started manually by an authenticated SportSync organiser.
- Data movement from auction to SportSync is one-way and idempotent.
- Imported records retain stable auction identifiers and are updated rather than duplicated.
- SportSync never sends a write request to the auction application.
- The auction may read aggregated SportSync player statistics, but cannot alter scoring data through that interface.
- Mobile numbers, organiser credentials and scoring write tokens are never exposed by the statistics feed.

## Behaviour

1. From a tournament, the organiser selects **Refresh auction data**.
2. SportSync fetches the active auction state and public registered-player profiles.
3. Active auction teams are added to the tournament. Captains and sold players are attached to their teams; all registered profiles are retained in the player pool.
4. A refresh summary reports created/updated teams, players and memberships.
5. A public read-only endpoint supplies aggregate career batting and bowling statistics keyed by SportSync and auction external identifiers.

## Failure behaviour

- A failed upstream request changes no imported records and records a failed import run.
- Malformed upstream data returns a clear error.
- Repeating a successful refresh produces no duplicate teams, players or memberships.

## Acceptance criteria

- [x] Forward-only migration stores external identifiers and import history.
- [x] Authenticated refresh endpoint performs idempotent one-way upserts.
- [x] Android tournament view has an explicit refresh control and result message.
- [x] Public statistics endpoint is GET-only and excludes private fields.
- [x] Automated tests cover identifier normalization and URL resolution.

## Compatibility

Existing team and player endpoints remain unchanged. New columns are nullable, and existing records need no migration data.
