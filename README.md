Note: this project was built in 2023, but uploaded to GitHub in 2026 for documentation

# MusicPlayer
Click below to watch a showcase video of the app:
- (I talk about specifications criterion, so feel free to skip around)
[![Watch the video](https://img.youtube.com/vi/DQ9krkrXvTw/0.jpg)](https://youtu.be/DQ9krkrXvTw)

Android music player client for a Jellyfin server.

This project is a Jetpack Compose app that:
- Authenticates against a Jellyfin server
- Syncs your music library (songs/albums/artists/playlists)
- Downloads audio files to the app’s external storage directory for playback
- Provides basic playback controls (queue, shuffle, repeat, seek/progress)

## Features

- **Login to Jellyfin** with username/password (and optional access token)
- **Library browsing**: Songs, Albums, Artists, Playlists
- **Search** songs
- **Playback** screen with progress bar + controls (play/pause, skip, shuffle, repeat)
- **Queue management**: add to queue / play next
- **Playlist actions**: view playlists and add songs to playlists (where supported by UI)

## Requirements

- **Android Studio** (or a Gradle-capable environment)
- **JDK 17**
- **Android SDK** (project uses `compileSdk 34`)
- A reachable **Jellyfin** server with a music library

Android configuration (from `app/build.gradle.kts`):
- `minSdk`: 24
- `targetSdk`: 33

## Run in Android Studio

1. Open the project folder in Android Studio.
2. Let Gradle sync.
3. Select a device/emulator.
4. Click **Run**.

## Build from the command line

```bash
./gradlew :app:assembleDebug
```

To install on a connected device:

```bash
./gradlew :app:installDebug
```

## Using the app

On the login screen you’ll enter:
- **Username** / **Password**
- **IP Address / Host** of your Jellyfin server
- **Access Token** (optional; if omitted, the app will request one via Jellyfin auth)

Notes:
- The app currently normalizes the host to `http://<host>:8096` if you don’t supply protocol/port.
- If your server runs on a different port or HTTPS, you may need to enter the full base URL accordingly.

### Emulator & localhost

If your Jellyfin server is running on your development machine:

- **Android emulator**: use `10.0.2.2` instead of `localhost`
- **Physical device over USB** (port forwarding):

```bash
adb reverse tcp:8096 tcp:8096
```

## Data & storage

- Audio is downloaded to the app’s external files directory under a `songs/` folder (e.g. `<external_files>/songs`).
- The app uses **Room** to persist song/album/artist/playlist metadata locally.

## Permissions

- `INTERNET` is required to talk to the Jellyfin server.

## Tech stack

- Kotlin + Jetpack Compose (Material / Material3)
- Navigation Compose
- Jellyfin SDK (`org.jellyfin.sdk:*`)
- Room
- DataStore (preferences)
- Networking: OkHttp + Ktor client
- Images: Coil

## Security notes (important)

This app currently:
- Enables `usesCleartextTraffic=true` in the manifest (HTTP is allowed)
- Persists login details (including password and access token) in `SharedPreferences`

If you plan to use this beyond local/dev scenarios, consider:
- Using HTTPS and disabling cleartext traffic
- Storing secrets more securely (e.g., encrypted storage)

## Project layout

- `app/src/main/java/com/example/musicplayer/` — Compose screens + navigation
- `app/src/main/java/com/example/musicplayer/Managers/` — Jellyfin sync, models, Room DAOs, download/playback helpers
- `app/src/main/res/` — resources (themes, colors, icons)

## Troubleshooting

- **Can’t connect to Jellyfin**: verify device can reach the server host/port; try entering full base URL.
- **Emulator can’t reach localhost**: use `10.0.2.2`.
- **Slow/failed downloads**: check server performance and network; large libraries may take time to sync.

## License

No license file is currently included in this repository. If you intend to share or distribute the code, add a `LICENSE` that matches your intent.
