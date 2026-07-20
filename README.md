<div align="center">
  <h1>AB Player</h1>
  <p><strong>Offline music & video player for Android</strong></p>
  <p>
    <img src="https://img.shields.io/badge/version-2.7.1-3DDC84?style=flat-square"/>
    <img src="https://img.shields.io/badge/Android-13+-3DDC84?logo=android&logoColor=white&style=flat-square"/>
    <img src="https://img.shields.io/badge/License-MIT-yellow?style=flat-square"/>
    <img src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&style=flat-square"/>
  </p>
</div>

---

## Features

- **Music Player** — Albums, Artists, Playlists, Folders, Search
- **Video Player** — Grid/List view, Picture-in-Picture (PiP), gesture controls
- **Equalizer** — 11 presets + manual mode
- **Sleep Timer** — fall asleep to music
- **Editable Queue** — reorder, play next
- **Lyrics** — auto-fetch from lrclib.net
- **7 Themes** — System, Light, Dark, AMOLED (4 variants)
- **8 Accent Colors** — Purple, Blue, Pink, Cyan, Gold, Green, Orange, Teal
- **Dynamic Color** — matches wallpaper
- **No tracking, no ads, 100% offline**

---

## Screenshots

<div align="center">

| Home | Player | Lyrics |
|:----:|:------:|:------:|
| <img src="screenshots/1%20Home.png" width="200"/> | <img src="screenshots/2%20music%20player.png" width="200"/> | <img src="screenshots/3%20song%20lyrics.png" width="200"/> |

| Videos | Video Player | Explorer |
|:-----:|:------------:|:--------:|
| <img src="screenshots/4%20videos.png" width="200"/> | <img src="screenshots/5%20video%20player.png" width="200"/> | <img src="screenshots/6%20explorer.png" width="200"/> |

| Favorites | Settings |
|:---------:|:--------:|
| <img src="screenshots/7%20favorites.png" width="200"/> | <img src="screenshots/8%20setting.png" width="200"/> |

</div>

---

## Download

[Download latest APK](https://github.com/Sandeepbedia/AB-Player-v2/releases)

Or open **Settings > Check Updates** inside the app to download directly.

---

## Build from Source

### Prerequisites
- Android Studio (latest)
- JDK 17
- Android SDK 36

### Setup

```bash
git clone https://github.com/Sandeepbedia/AB-Player-v2.git
cd AB-Player-v2
```

### Signing (for release builds)

1. Place your keystore at `keystore/release.jks`
2. Create `keystore.properties`:
   ```properties
   storeFile=keystore/release.jks
   storePassword=your_password
   keyAlias=release
   keyPassword=your_key_password
   ```
3. Build:
   ```bash
   ./gradlew assembleRelease
   ```

### Debug build (no signing needed)

```bash
./gradlew installDebug
```

---

## Tech Stack

| Component | Library |
|-----------|---------|
| UI | Jetpack Compose + Material 3 |
| Media | Media3 / ExoPlayer |
| Database | Room + KSP |
| DI | Hilt |
| Images | Coil |
| Navigation | Navigation Compose |
| Preferences | DataStore |
| Min SDK | 26 |
| Target SDK | 36 |

---

## Permissions

| Permission | Purpose |
|------------|---------|
| `READ_MEDIA_AUDIO` | Read music files |
| `READ_MEDIA_VIDEO` | Read video files |
| `POST_NOTIFICATIONS` | Playback controls in notification |
| `FOREGROUND_SERVICE` | Background playback |
| `BLUETOOTH_CONNECT` | Bluetooth media controls |
| `MODIFY_AUDIO_SETTINGS` | Equalizer |
| `REQUEST_INSTALL_PACKAGES` | In-app APK updates |

No internet permission required for playback. Internet is used only for lyrics fetching and update checks.

---

## Author

**Sandeepbedia**

[![GitHub](https://img.shields.io/badge/GitHub-@Sandeepbedia-181717?logo=github)](https://github.com/Sandeepbedia)
[![Instagram](https://img.shields.io/badge/Instagram-@sandee_bedia_08-E4405F?logo=instagram)](https://www.instagram.com/sandee_bedia_08/)
[![Telegram](https://img.shields.io/badge/Telegram-@Infinity_384-26A5E4?logo=telegram)](https://t.me/Infinity_384)

---

## License

MIT License — see [LICENSE](LICENSE).

Copyright (c) 2026 Sandeepbedia. All rights reserved.

Redistribution with credit required.
