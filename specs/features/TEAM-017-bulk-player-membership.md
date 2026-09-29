# TEAM-017 — Bulk player membership

Status: verified

## Intent

An administrator can add several saved players to a team in one operation instead of reopening the player picker for every player.

## Scope

- Multi-select cards in the existing-player picker.
- A selected-player counter, select-all/clear controls, and one confirmation action.
- Existing team members remain excluded.

## Rules and invariants

- Duplicate team membership remains impossible through the existing database key.
- The action is available only to existing team-management access.
- A failed request is reported and does not falsely mark the whole selection complete.

## Failure behavior

- An empty selection cannot be submitted.
- Network or authorization failures keep the picker open and display an error.

## Acceptance criteria

- [x] Two or more available players can be selected and added without reopening the screen.
- [x] Select all and clear selection work only on currently available players.
- [x] Existing members are not offered and duplicate rows are not created.
- [x] Android staging compiles.

## Compatibility

Uses the existing single-membership API repeatedly; no database or public API changes.

## Decisions

- 2026-09-23: Keep the existing idempotent membership endpoint and coordinate the batch from Android to minimize backend change.
