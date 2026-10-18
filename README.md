# Random Chat (text only)

Anonymous random text chat: tap Start, get paired with a stranger, chat, tap Next to skip.

```
randomchat/
  server/     Node.js WebSocket server: matchmaking, text relay, typing indicator, reports/bans
  android/    Kotlin + Jetpack Compose app
  .github/    Workflow that builds the APK in the cloud
```

## 1. Run the server

```bash
cd server
npm install
npm start          # listens on :8080
```

Deploy it somewhere with HTTPS/WSS (Render, Fly.io, Railway). On Render: New Web Service, root directory `server`,
build command `npm install`, start command `npm start`. Your URL becomes `wss://<your-app>.onrender.com`.

## 2. Get the APK without installing anything (GitHub Actions)

1. Push this folder to a GitHub repo (branch `main`).
2. Repo Settings > Secrets and variables > Actions > Variables > New variable: `SERVER_URL` = `wss://<your-app>.onrender.com`.
3. Actions tab > "Build APK" > Run workflow (it also runs on every push).
4. Download the `randomchat-debug-apk` artifact, unzip it, and copy `app-debug.apk` to your phone.
5. Allow "Install unknown apps" for your file manager/browser, then tap the APK.

## 3. Or build locally

Open `android/` in Android Studio and press Run, or from the command line (JDK 17 + Android SDK):
`cd android && gradle assembleDebug -PserverUrl=wss://your-server`.
Without `-PserverUrl` the app points at `ws://10.0.2.2:8080`, which reaches a server on your computer from the emulator.
Use two emulators/phones to test a match.

## Safety and moderation

Built in: 18+ confirmation, report button, 3 reports from different users within 24h bans that IP for 24h,
chat rate limit (5 messages per 3s), 500-character message cap, max 5 connections per IP, no message logging.

Limits:
- Bans are by IP. Users on a shared network can be banned together, and bans reset when the server restarts.
- The 18+ checkbox is self-declared, not verification.
- There is no automatic content filtering. Public anonymous chat attracts abuse; add filtering and human review
  before a public launch or Play Store submission.
- The workflow builds a debug-signed APK for sideloading. Play Store needs a release build signed with your own keystore.

## Notes

- The Android app was not compiled in the environment that generated it. The server is tested.
- Change `applicationId`/`namespace` (`com.example.randomchat`) before publishing.
