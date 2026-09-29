# SportSync product backlog

Last reviewed: 2026-09-23

This is the product-level view. Detailed behavior and verification live in `specs/registry.json` and the linked specifications.

## Existing

- Tournament, team and player management
- Auction-linked team/player import
- Manual and scheduled fixtures with points table and standard NRR
- Complete ball-by-ball scoring, innings transition, scorecard and fielding dismissals
- Emergency player substitutions during a live match
- Player, scorer and tournament-admin roles with a staging full-access mode
- Classic red and dark-gold themes with SportSync branding
- Match-PIN camera broadcasting: direct YouTube testing and remote OBS overlay beta
- Firebase App Distribution testing channel

## Upcoming

- Bulk-add saved players to a team — TEAM-017 (in progress)
- Mobile-first player registration with photo and cricket role — AUTH-018 (design pending)
- Secure super-user governance and audit trail — part of AUTH-018
- SMS-assisted proof flow for sideloaded testing, subject to an approved destination number and Android distribution decision — AUTH-018
- Physical-device validation for direct YouTube and remote OBS broadcasting — LIVE-016
- Idempotency keys and match revisions for every scoring command before production
- Production API/domain, database, authentication recovery and operational monitoring

## Better to have

- Push notifications for fixtures, toss, innings break and results
- Offline scoring queue with conflict-safe synchronization
- Player availability and RSVP
- Tournament seeding, venue/time-slot constraints and rain rescheduling
- Rich player career statistics, awards and comparisons
- Shareable match cards, score graphics and highlights
- Team chat/announcements with moderation
- Import/export and scheduled backups
- Accessibility audit, localization and tablet layouts
- Coach/analyst dashboards and video-event bookmarks
- Automated end-to-end device testing and performance monitoring

## Deferred or constrained

- Broad SMS inbox access in the production Play Store app: restricted by Google Play policy; use a compliant verification provider or explicit user-mediated alternative.
- Silent SMS sending: not planned. The player must knowingly send through the phone's SMS app.
- Production launch: deferred until authentication, scoring concurrency, recovery, privacy and store-policy gates are complete.
