# LIVE-019 — Offline overlay preview and navigation drawer

Status: implemented

## Intent

Let a scorer inspect the complete broadcast overlay without starting a YouTube broadcast, and make all home tabs and broadcast tools reachable from a hamburger menu.

## Scope

- The home screen provides a hamburger drawer containing the same Matches, Tournaments, Teams and Stats destinations as the visible tabs.
- The drawer also links to Broadcast, Overlay Preview and role-appropriate administration actions.
- Overlay Preview uses a small local five-second looping motion scene so testing does not consume network data or require a stream key.
- The preview renders a compact, linear lower-third score strip occupying approximately 10% of the 16:9 frame height.
- The strip shows the batting score, explicitly labelled striker and non-striker, current bowler and comma-separated current-over history.
- A reserved controls area is included for future overlay controls.
- Camera access changes are excluded from this change.

## Rules and invariants

- Opening Overlay Preview must not start camera, microphone or network publishing.
- Preview data is read-only and uses existing scoring APIs and cricket calculations.
- The local motion scene contains no third-party media or branding.
- Role-based menu visibility remains enforced.

## Failure behavior

- With no matches, show a clear empty state and return action.
- If score refresh fails, keep the last successful preview and show the error.
- If a match has not started, show teams and a waiting-for-play message.

## Acceptance criteria

- [x] Drawer navigation changes the same home destination selected by each corresponding tab.
- [x] Overlay Preview opens from the drawer without camera or broadcast permission.
- [x] The preview has a looping local motion background and a linear lower-third occupying approximately 10% of frame height.
- [x] The overlay shows team score, striker with a bat marker, non-striker, current bowler and comma-separated chronological current-over balls.
- [x] A visible controls area is present without claiming unfinished controls work.

## Compatibility

Android-only UI change. No API, database, permission or persisted schema change.

## Decisions

- 2026-09-29: Use an original code-rendered five-second motion scene instead of bundled video or YouTube, avoiding APK growth, copyright concerns and preview data usage.
- 2026-09-29: Defer camera-access repair; overlay preview is intentionally independent from the broadcast encoder.
- 2026-09-29: Follow common cricket scorebar conventions: compact lower-third, team score and overs first, partnership in the middle, bowler figures and current-over context to the right.
