# Pro Video Downloader

**Pro Video Saver & 4K Player** — an Android app for downloading, organizing,
and playing online videos, with a built-in transcript studio and a
PIN-protected private vault.

- Package: `com.pro.videodownloader.player`
- Language: Kotlin
- Minimum SDK: 24 · Target / Compile SDK: 34
- Android Gradle Plugin: 8.5.2 · Kotlin: 2.0.20 · Gradle: 8.7
- Java: 17

## Features

### Built-in custom media player with gesture controls
- Hardware-accelerated video playback with a tap-to-toggle overlay
  (play/pause, ±10s seek, playback speed, picture-in-picture, fullscreen).
- Swipe gestures on the video: left side adjusts screen brightness,
  right side adjusts media volume, with an on-screen feedback indicator.
- Double-tap left/right to seek backward/forward by 10 seconds.
- Optional background audio playback and an auto-hiding control overlay.

### Script Studio with timestamped transcripts
- A dedicated Script Studio tab that turns a video link into a full,
  word-by-word transcript.
- Transcripts are timestamped, line by line, and can be viewed with
  timestamps toggled on or off.
- One-click copy of the complete script, and save as a `.txt` file.
- A second sub-tab shows the video description and creator notes.
- A clipboard radar on the Home tab detects a copied video link and
  offers to download it or open it directly in Script Studio.

### Saved media library
- A Saved tab listing all downloaded videos and audio files, newest first.
- Pause, resume, and cancel downloads, with live progress, speed,
  and resumable downloads.
- Extract an MP3 audio track from a saved video, share media,
  move files to the vault, or play them in the built-in player.

### PIN-protected private storage vault
- A Private Vault tab protected by a 4-digit PIN (created on first use).
- Move any downloaded file into the vault to hide it from the
  Saved library and the player list.
- Unlock the vault with the PIN, play vault files, or restore them
  back to the Saved library.

### Built-in browser with stream detection
- An in-app browser with ad blocking, desktop/mobile user-agent switching,
  and automatic detection of the video currently playing in the viewport.
- Paste a link, search, or open a supported platform from the
  Home dashboard to download or extract a video.

## Project structure

```
ProVideoDownloader/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── java/com/pro/videodownloader/player/
│       │   ├── MainActivity.kt      # App UI, browser, player, vault, studio
│       │   ├── P5UiHelper.kt
│       │   └── P6UiHelper.kt
│       ├── res/                     # Drawables, colors, strings, themes
│       └── AndroidManifest.xml
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew / gradlew.bat
└── gradle/wrapper/
```

## Build instructions

Requirements: JDK 17 and the Android SDK (API level 34, build-tools 34.0.0).

```bash
./gradlew assembleDebug
```

The debug APK is produced at:

```
app/build/outputs/apk/debug/app-debug.apk
```

To install it on a connected device or emulator:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Notes

- Downloaded files are stored in the app's private downloads folder
  (`ProVideoSaver`); exported copies can also be written to the
  public Downloads folder on Android 10+.
- The vault PIN is stored locally on the device in the app's
  private preferences.
- This repository contains source code only. Build outputs
  (`build/`, `.gradle/`, `.apk` files) are excluded via `.gitignore`.
