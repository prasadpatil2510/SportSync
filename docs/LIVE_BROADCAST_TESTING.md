# Live broadcast testing

This is a staging-only workflow. Never put a YouTube or relay stream key in source code, a screenshot, a ticket, or the SportSync API. The broadcaster enters it on the camera phone for each session; leaving that screen clears it.

## Phone A: scoring

Open a live match as a scorer or tournament admin. Tap **BROADCAST PIN**, then **NEW PIN**. Tell the camera operator the six-digit PIN privately. It expires after four hours. **REVOKE** invalidates the PIN and both score-feed URLs immediately; generating a replacement also invalidates the previous one.

## Phone B: direct YouTube

Open **BROADCAST** from the Android home screen, redeem the PIN, and choose **Direct YouTube**. In YouTube Live Control Room, create/select a test stream and copy its RTMPS server URL and stream key into the two fields. Grant camera and microphone access, preview, then tap **START**. The app draws the current score onto the outgoing video. Stop the stream before leaving the screen. The score phone continues scoring independently.

Use a private/unlisted test event first. A successful encoder connection does not necessarily mean YouTube is publicly live; YouTube may require **Go Live** in Live Control Room when auto-start is off.

For the staging connectivity check, select **Blank-screen test** and press **START BLANK TEST**. It sends black video with silent audio and does not request camera or microphone permission. Temporary endpoint credentials may be injected into a staging APK at build time through `SPORTSYNC_TEST_BROADCAST_SERVER` and `SPORTSYNC_TEST_BROADCAST_KEY`; they must never be committed, logged, or used for production.

## Phone B → internet relay → OBS PC

The phone and PC do **not** need to share Wi-Fi. They do need a publicly reachable ingest/relay server. A private LAN address such as `192.168.x.x` will not work across networks. Use an RTMPS endpoint where possible. An operator can run MediaMTX or a comparable relay on a public host; configure publishing authentication, HTTPS/TLS where supported, firewall ingress, and a stable public hostname. This repo deliberately does not provision a relay or hard-code its credentials.

On Phone B choose **Remote OBS**, enter the public relay RTMP/RTMPS server and publish key, preview and start. On the OBS PC, add the relay's playback URL as a Media Source/VLC Source (the exact URL depends on relay configuration). Add the app's **OBS Browser Source URL** as a separate Browser Source, set its dimensions to e.g. 1100×100, position it above video, and publish the composed OBS scene to YouTube with the key configured in OBS. Keep the browser-source URL private because it contains a read-only score token.

The relay must support the chosen ingest and playback protocols and enough upload/download bandwidth. This project cannot verify the full remote path until a public relay endpoint and a real phone/OBS pair are available.

## Security and failure behavior

- The match PIN grants only a read-only score session. It cannot write deliveries or change a match.
- The stream key stays on Phone B in memory for that screen only; the SportSync API never receives it.
- If score polling fails, the last score stays visible while a refresh error is shown. Do not treat an old score as confirmed live.
- If the stream disconnects, the operator must explicitly restart it. The app never silently switches destinations.
- Close the broadcast screen to stop the camera, microphone, and network stream. Revoke the PIN after the event.

## Release gate

Before distributing a testing APK, apply `worker/migrations/0009_match_broadcast.sql` to the testing D1 database and deploy the matching Worker version. Verify PIN redemption and score updates, then test each video mode on a physical Android device. This is **not** a production rollout.
