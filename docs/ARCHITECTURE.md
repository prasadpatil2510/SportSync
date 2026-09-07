# Production architecture

## Repository layout

This repository is a monorepo so an API contract, database migration and Android release can be reviewed together.

```text
android/                 Jetpack Compose Android client
worker/                  Cloudflare Worker API and D1 migrations
.github/workflows/       Build, test and deployment automation
docs/                    Architecture and operational guidance
```

The auction and OBS applications are intentionally outside this repository.

## Recommended production topology

```text
Android app
   │ HTTPS + authenticated user token
   ▼
Cloudflare Worker API
   ├── D1: tournaments, teams, players, matches and delivery ledger
   ├── Durable Object per live match: serializes scoring commands
   ├── R2: team logos, player photos and tournament banners
   ├── Queue: scorecard/statistics recalculation and notifications
   └── Turnstile + rate limiting: public registration endpoints
```

Firebase App Distribution remains a testing-delivery service only. It is not the application database.

## Android layers

As the application grows, split the current prototype into these modules:

- `app`: navigation, dependency wiring and build flavors.
- `feature-*`: tournament, team, match setup, scorer and scorecard screens.
- `domain`: use cases and cricket rules without Android or networking dependencies.
- `data`: API client, DTO mapping, repositories and an encrypted offline command queue.
- `design-system`: reusable NMTCC components, typography, icons and colors.

Use unidirectional state flow with one ViewModel per screen. Persist unsent scoring commands locally with unique command IDs so a temporary network loss can be retried without duplicating a delivery.

## Backend layers

Move the Worker from a single route file toward:

- `routes`: HTTP validation and response mapping.
- `services`: tournament, roster and scoring use cases.
- `domain`: cricket rules and invariants.
- `repositories`: D1 queries and transactions.
- `durable-objects`: one coordinator for each live match.

Every scoring mutation should include a unique command ID and expected match revision. The backend must reject duplicate commands and serialize accepted commands. Clients should treat the server revision as authoritative.

## Environments

Keep physically separate D1 databases, R2 buckets, secrets and Worker names for:

- `development`: local engineering and disposable records.
- `testing`: Firebase-distributed beta builds.
- `production`: real tournaments only.

Production deployments should require GitHub Environment approval. Never point a debug or staging APK at production.

## Production gates

Before public launch:

1. Replace the shared organiser PIN with authenticated accounts and roles.
2. Add Durable Object serialization and idempotency keys for scoring commands.
3. Add automated cricket-rule tests and API integration tests.
4. Add encrypted offline scoring and conflict recovery in Android.
5. Configure R2 uploads with size/type validation and signed URLs.
6. Add database backups, restore drills, audit logs and monitoring alerts.
7. Publish privacy policy, data-retention rules and account deletion flow.
8. Use a signed Android App Bundle and Play Console internal testing before production.
