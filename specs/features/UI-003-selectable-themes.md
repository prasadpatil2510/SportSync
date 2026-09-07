# UI-003 — Selectable SportSync application themes

Status: implemented

## Intent

Give testers a persistent choice between the existing red visual identity and a premium dark-gold SportSync identity without changing any cricket workflow or cloud data.

## Acceptance criteria

- [x] The existing red theme remains available as **Classic Red**.
- [x] A **Dark Gold** theme uses charcoal surfaces, readable light text, and gold actions.
- [x] Every application header exposes a compact theme selector.
- [x] The selected theme survives application restarts on the same device.
- [x] The login screen displays the matching SportSync logo.
- [x] Theme changes do not recreate, delete, or alter tournament, team, player, match, or scoring data.

## Invariants

- The organiser authentication and API endpoints remain unchanged.
- Both themes meet readable foreground/background contrast in primary screens.
- Brand assets are bundled locally; selecting a theme does not require network access.

## Verification

- Build the staging APK.
- Launch with Classic Red, switch to Dark Gold, restart, and confirm Dark Gold is restored.
- Visit login, tournament list, team, match setup, and scoring screens in both themes.
