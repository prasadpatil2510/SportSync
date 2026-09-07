# SYS-001 — Cloud-first tournament foundation

Status: verified

## Intent

Provide an Android tournament-management foundation that is continuously available without depending on a local database or the auction website.

## Scope

- Organiser testing access.
- Tournament, team and reusable player management.
- Separate development, testing and production cloud environments.
- Firebase App Distribution for Android testing only.

## Invariants

- The cloud API is authoritative.
- Testing data never shares a database with production data.
- Credentials and personal tester configuration are not committed.

## Acceptance criteria

- [x] An organiser can create and list tournaments.
- [x] An organiser can create and edit teams.
- [x] An organiser can create, add and remove squad players.
- [x] The staging APK targets only the testing API.
- [x] Generated builds and credentials are ignored by Git.

## Evidence

- Android staging build succeeds.
- Worker dry-run succeeds.
- Testing Worker health reports `UP` and `CONNECTED`.
