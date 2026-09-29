# LIVE-016 — Match-linked live video broadcasting

Status: implemented (physical-device/remote-relay validation pending)

## Intent

Two phones can cover a cricket match: one scores while the other broadcasts camera and microphone. The broadcast phone sees current score data without being able to edit it.

## Scope

- A scorer or tournament administrator creates a short-lived, match-specific broadcast PIN.
- The generated PIN is displayed prominently and cached locally to prefill the Broadcast screen on the same device.
- A second Android device enters the match PIN, then selects either direct YouTube RTMPS or a remotely reachable RTMPS/RTMP ingest for OBS.
- The YouTube stream key is entered manually on the broadcast device and is never sent to the SportSync API or persisted.
- Direct YouTube mode burns a compact live score strip into the outgoing video.
- OBS mode sends camera and microphone without a burned score strip. OBS uses a separate browser-source overlay URL for current score data.
- The overlay URL is keyed by an unguessable read-only token and can be revoked/rotated.
- Camera and microphone permission are requested only on the broadcaster device.
- Camera and microphone encoders are prepared before preview begins, and the score overlay is attached before publishing starts.
- Live camera startup waits for the Android preview surface callback before preparing and opening the camera.
- The Broadcast screen layers a visible scorebar over the camera preview while the encoded output receives an OpenGL-rendered Android scorebar view with the same two-line score content; a Compose-only preview overlay is not treated as proof that the scorebar is present in the RTMP output.

## Rules and invariants

- Broadcast PIN grants read-only match access, not scoring, account, tournament or stream-key access.
- PINs and overlay tokens are stored hashed in the database. Plaintext values are returned only once on creation/redemption.
- `301022` is the permanent read-only testing PIN. It resolves to the currently active broadcast grant.
- Exactly one generated PIN is active system-wide alongside the permanent testing PIN. Creating a new generated PIN immediately invalidates the previous generated PIN and its read-only tokens, including when the new PIN is for a different match.
- Generated PINs expire after four hours. Failed PIN attempts must be rate limited.
- Stream keys are not logged, persisted, included in URLs displayed to viewers, or checked into source control.
- A staging APK may receive a temporary test endpoint and stream key through build-time environment variables. The values must remain absent from committed source and non-staging variants, and testers must be warned that the APK contains the temporary credential.
- Staging provides a blank-screen test source with silent audio so ingest connectivity can be verified without camera or microphone permission.
- Stream may start only after the broadcaster explicitly presses Start; leaving the broadcast screen stops camera and network publishing.
- Production authentication and testing bypass remain separate.
- The existing auction and OBS projects are not modified.

## Failure behavior

- Expired/invalid PIN: show a clear error without disclosing match details.
- Unavailable score API: keep camera running, mark score overlay stale, and retry without fabricating scores.
- Network stream failure: stop publishing, show a reconnection action, and do not silently switch destinations.
- Missing camera/microphone permission: do not start streaming.

## Acceptance criteria

- [x] An authorized scorer can generate and revoke a match-specific PIN.
- [x] A broadcaster can redeem a valid PIN and cannot score or edit a match through that access.
- [x] Direct mode is wired to publish the standard phone camera/microphone source by RTMP/RTMPS and includes a live score strip in the encoded video. (Physical-device confirmation remains pending.)
- [ ] OBS mode publishes phone camera/microphone to a configured remote ingest; a read-only overlay URL reflects score changes.
- [x] No stream key is saved or transmitted to SportSync API.
- [x] PIN expiry, invalid PIN, revoked overlay token, and unauthorized edit requests have tests.
- [x] Permanent PIN, generated-PIN replacement, cross-match rotation, expiry, revocation, and malformed PIN cases have end-to-end API tests.
- [ ] Android staging APK compiles and has been tested with a real camera/microphone device and test ingest. (Build passed; device/ingest test pending.)
- [x] Staging can compile with build-time test credentials and start a black-video/silent-audio test source without camera or microphone permission.

## Compatibility

Add a forward-only D1 migration for broadcast grants and add read-only API routes. Android staging gains camera/microphone permissions and a streaming dependency. No existing scoring route or persisted match data changes.

## Decisions

- 2026-09-22: Use manual YouTube stream keys for testing; no YouTube account integration.
- 2026-09-22: Remote OBS mode must work across the internet, not only same Wi-Fi. It therefore requires a public ingest/relay endpoint supplied by the operator.
- 2026-09-22: Keep OBS composition on the PC; only provide a browser-source overlay from this project.
- 2026-09-29: Permit temporary build-time credentials and a blank-screen staging test mode; never commit the supplied key.
- 2026-09-29: Use RootEncoder's standard camera and microphone source for live mode; prepare encoders before starting preview or publishing.
- 2026-09-29: Follow RootEncoder's documented `SurfaceHolder.Callback` lifecycle because opening the camera before `OpenGlView` is ready produces a blank preview on some devices.
- 2026-09-29: Render the full scorebar into RootEncoder with `ViewFilterRender`; the separate Compose preview layer is local-only and cannot appear in YouTube by itself.
