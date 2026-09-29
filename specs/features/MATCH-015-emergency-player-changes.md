# MATCH-015 — Emergency player changes during scoring

Status: verified

## Intent

Support informal tournament emergencies by allowing an authorised scorer to add or replace a match player at any time, including borrowing an existing player from another tournament team or creating a new temporary player.

## Scope

- A Change Players control available throughout live scoring and at the innings break.
- Selection of either match team, an optional outgoing player and an incoming existing player.
- Existing-player choices include active players from the shared tournament database and show their usual team where available.
- A new player name can be entered when no existing profile is suitable.
- Changes update the active match lineup immediately and are retained in a substitution audit ledger.

## Rules and invariants

- Only a Scorer or Tournament Admin may change match players.
- The target team must be one of the two teams in the match.
- An incoming player cannot simultaneously play for both teams in the same match.
- Replacing a current striker, non-striker or bowler transfers that live position to the incoming player without rewriting earlier deliveries.
- Previous deliveries, dismissals and statistics always remain attributed to the player who originally participated.
- A newly created emergency player is a reusable player profile but is not silently added to a permanent team roster.
- Removing an outgoing player marks the match appearance inactive rather than deleting its history.

## Failure behavior

- Reject missing teams, invalid players, teams outside the match and cross-team duplicate appearances.
- A failed change leaves the lineup and innings participants unchanged.
- The Android screen reports the error and keeps scoring state refreshable.

## Acceptance criteria

- [x] During scoring, the scorer can open Change Players without pausing or ending the match.
- [x] The scorer can add an existing player whose usual team is not playing the match.
- [x] The scorer can create and add a new emergency player by name.
- [x] The scorer may add a player or select an outgoing player for a one-for-one replacement.
- [x] Replacing an active batter or bowler updates the current innings participant immediately.
- [x] Historical deliveries and scorecard attribution are preserved.
- [x] Every successful change is recorded in the substitution ledger.
- [x] Worker tests, dry-run validation, specification checks, secret scan and staging APK build pass.

## Compatibility

Migration `0008_match_player_substitutions.sql` adds an append-only substitution ledger. Existing matches and lineups remain compatible.

## Decisions

- 2026-09-14: Treat emergency participants as match-specific loans; do not change their permanent team membership.
