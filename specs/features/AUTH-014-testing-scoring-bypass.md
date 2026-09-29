# AUTH-014 — Registration-free scoring test mode

Status: verified

## Intent

Allow invited testers to open the staging Android application and test cricket scoring without registering or signing in, while leaving production authentication unchanged.

## Scope

- A testing-only backend endpoint that issues a temporary Scorer session.
- Automatic session creation when the staging Android app starts.
- Removal of registration and sign-in screens from the staging workflow.
- Production and development builds continue to require normal authentication.

## Rules and invariants

- Bypass access is available only when `APP_ENV` is `testing` and the database `testing_enabled` setting is `true`.
- The automatic account receives Tournament Admin access so invited testers can exercise the complete workflow.
- The session uses the existing hashed-token storage and expiry behavior.
- The automatic testing identity has no usable PIN and cannot sign in through the normal login endpoint.
- Existing role checks, score audit behavior and correction invariants remain authoritative.
- Production must never expose the testing-session endpoint.

## Failure behavior

- A non-testing environment returns HTTP 404 without creating a user or session.
- A testing environment with testing disabled returns HTTP 403.
- If automatic access cannot be established, the app shows a retryable error and does not expose non-functional scoring controls.

## Acceptance criteria

- [x] A staging tester can open the app without entering registration or login details.
- [x] The staging app automatically receives Tournament Admin permissions and can use the complete setup and scoring workflow.
- [x] The automatic tester can manage tournaments, teams, players, matches and user roles in the isolated testing environment.
- [x] Development and production builds retain the authentication screen.
- [x] Worker tests, worker dry-run, specification checks, secret scan and staging APK build pass.

## Compatibility

No persisted cricket schema or public production behavior changes. The testing endpoint is environment-gated and reuses the existing account and session tables.

## Decisions

- 2026-09-13: Use an environment-gated session instead of embedding a shared PIN or secret in the APK.
- 2026-09-14: Elevate the isolated testing identity to Tournament Admin so the full workflow can be tested without registration.
