# NMTCC Cricket Cloud

An isolated Android + Cloudflare project. It does not modify or depend on the existing scorer, auction website, OBS setup, Supabase project, Excel files, or local Node server.

This is an original NMTCC cricket platform inspired by familiar cricket-scoring workflows. It is not affiliated with, endorsed by, or a source-code copy of CricHeroes.

See [Production architecture](docs/ARCHITECTURE.md) and [Security policy](SECURITY.md) before using real player data or live tournaments.

## Components

- `worker/`: Cloudflare Worker REST API with D1 cloud database.
- `android/`: Kotlin and Jetpack Compose Android client with no Room/SQLite database.
- `.github/workflows/android-testing.yml`: on-demand testing APK build and optional Firebase App Distribution upload.

## Environments

Create separate Workers and D1 databases for `development`, `testing`, and `production`. Never bind the production Android build to a testing database.

## First deployment

1. Create a Cloudflare account.
2. Install dependencies in `worker` with `npm install`.
3. Login using `npx wrangler login`.
4. Create three D1 databases and place their IDs in the appropriate Wrangler environment configuration.
5. Apply all migrations in `worker/migrations` to each database.
6. Store an admin token using `wrangler secret put ADMIN_TOKEN` for every environment.
7. Deploy the Worker.
8. Set the deployed testing API URL in the Android testing build.

## Android testing delivery

Firebase is used only for distributing test APKs; D1 remains the application database.

1. In Firebase, register the testing Android package `club.nmtcc.cricket.test` and enable App Distribution.
2. Add tester email addresses in Firebase App Distribution.
3. Add these repository secrets:
   - `FIREBASE_ANDROID_APP_ID`: optional override for the registered Firebase App ID. The testing app currently uses `1:3540673696:android:a5090de7d45bdc40e055c5`.
   - `FIREBASE_TESTERS`: comma-separated tester email addresses.
   - `FIREBASE_SERVICE_ACCOUNT`: the complete service-account JSON used only by the build workflow.
4. Run the **Android testing build** workflow manually.
5. The workflow always retains the APK as a downloadable build artifact. When Firebase secrets are configured, it also sends the release to invited testers.

No Firebase credential, signing key, APK, or local configuration file should be committed. They are excluded by `.gitignore`.

The free Cloudflare platform has usage limits and no contractual uptime SLA. The API itself has no sleeping server.

## Implemented app workflow

- Create tournaments, teams and reusable player profiles.
- Edit teams and add or remove existing players from squads.
- Create matches with round, teams, venue and overs.
- Select the playing XI for both teams before the toss.
- Record the toss decision and opening batters/bowler.
- Score runs, wides, no-balls, byes, leg-byes and wickets in the cloud.
- Pause/resume, change bowler, undo the latest delivery and complete innings or matches.
- Display chase targets and calculate the match result.
