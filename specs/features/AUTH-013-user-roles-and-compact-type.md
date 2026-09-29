# AUTH-013 — User authentication, roles and compact typography

Status: implemented

## Intent

Give each SportSync user an individual authenticated account and expose only the screens and actions appropriate to their role, while making the Android interface more compact.

## Scope

- PIN-based individual accounts identified by email.
- Player, Scorer and Tournament Admin roles.
- Player self-registration, sign-in, sign-out and persistent sessions.
- Tournament Admin account management and role assignment.
- Role-aware Android navigation and scoring controls.
- A global reduction in Android font scaling without reducing touch targets.
- Existing shared organiser PIN remains available only as a testing bootstrap for the first Tournament Admin session.

## Rules and invariants

- New self-registered accounts always receive the Player role.
- Only a Tournament Admin can create users with elevated roles or change another user's role.
- Players have read-only access to tournaments, teams, fixtures and scorecards.
- Scorers may update match preparation and live scoring, but may not manage tournaments, teams, players or user roles.
- Tournament Admins retain all management and scoring permissions.
- Session tokens are stored only as hashes and expire after 30 days.
- PINs are salted and hashed; plain PINs are never persisted.
- Existing scoring audit and correction invariants remain unchanged.

## Failure behavior

- Invalid credentials return a generic authentication error.
- Missing, expired or insufficient credentials return HTTP 401 or 403 without changing data.
- A role-restricted Android action is hidden, and backend authorization remains authoritative.

## Acceptance criteria

- [x] A person can register as a Player and sign in with email and a 4–8 digit PIN.
- [x] The testing admin can sign in using the bootstrap admin identity and existing testing PIN.
- [x] A Tournament Admin can create Player or Scorer accounts and change roles.
- [x] A Player cannot reach mutation controls; a Scorer can reach scoring but not management; a Tournament Admin can reach both.
- [x] Backend scoring routes accept Scorer and Tournament Admin sessions and reject Player sessions.
- [x] Backend management routes accept only Tournament Admin sessions.
- [x] Android typography is visibly smaller while controls retain their existing physical size.
- [x] Worker tests, worker dry-run, specification checks, secret scan and staging APK build pass.

## Compatibility

Migration `0007_user_authentication.sql` is forward-only and adds account, session and authentication-audit tables. Existing cricket records and public read endpoints are unchanged. Existing `x-admin-token` support remains temporarily available for testing and automation; Android uses bearer sessions after login.

## Decisions

- 2026-09-09: Use one account with one current application role; cricket-specific team roles remain separate player attributes.
- 2026-09-09: Use email plus numeric PIN for the testing release to keep mobile onboarding simple.
