# Random Chat (text + video)

Omegle-style app: tap Start, get paired with a random stranger, chat by video and text, tap Next to skip.

```
randomchat/
  server/     Node.js WebSocket server: matchmaking, WebRTC signaling, text relay, reports/bans
  android/    Kotlin + Jetpack Compose app, WebRTC video (stream-webrtc-android)
  .github/    Workflow that builds the APK in the cloud
```

Video flows peer-to-peer over WebRTC. The server only pairs people and relays signaling and text.

## 1. Run the server

```bash
cd server
npm install
npm start          # listens on :8080
```

Deploy it somewhere with HTTPS/WSS (Render, Fly.io, Railway). On Render: New Web Service, root directory `server`,
build command `npm install`, start command `npm start`. Your URL becomes `wss://<your-app>.onrender.com`.

Optional TURN relay (about 15-20% of mobile networks cannot connect with STUN alone). Set these env vars on the server:
`TURN_URL` (e.g. `turn:turn.example.com:3478`), `TURN_USER`, `TURN_PASS`. The server hands them to both clients on each match.

## 2. Get the APK without installing anything (GitHub Actions)

1. Push this folder to a new GitHub repo (branch `main`).
2. Repo Settings > Secrets and variables > Actions > Variables > New variable: `SERVER_URL` = `wss://<your-app>.onrender.com`.
3. Actions tab > "Build APK" > Run workflow (it also runs on every push).
4. Open the finished run and download the `randomchat-debug-apk` artifact. Unzip it and copy `app-debug.apk` to your phone.
5. On the phone, allow "Install unknown apps" for your file manager/browser, then tap the APK.

## 3. Or build locally

Open the `android/` folder in Android Studio and press Run, or Build > Build APK(s).
Command line (needs JDK 17 + Android SDK): `cd android && gradle assembleDebug -PserverUrl=wss://your-server`.
Without `-PserverUrl` the app points at `ws://10.0.2.2:8080`, which reaches a server on your computer from the emulator.
Use two emulators/phones to test a match.

## Safety and moderation (read before publishing)

Built in: 18+ confirmation, report button, 3 reports from different users within 24h bans that IP for 24h,
chat rate limit (5 messages per 3s), 500-character message cap, max 5 connections per IP, no message logging.

Limits you should know about:
- Bans are by IP. Users behind one shared network (campus, mobile carrier NAT) can be banned together, and bans are lost on restart (in-memory).
- The 18+ checkbox is self-declared, not verification.
- There is no automatic video moderation. Apps in this category generally need much stronger moderation (video/nudity
  detection, accounts, human review) to pass Google Play review and to keep users safe. Plan for this before a public launch.
- The workflow produces a debug-signed APK, fine for sideloading and testing. For Play Store you need a release build signed with your own keystore.

## Notes

- Not compiled in the environment that generated it (no Android SDK access there). The server was tested; if the first
  Gradle build reports an error, send me the message and I'll fix it.
- Change `applicationId`/`namespace` (`com.example.randomchat`) before publishing.
