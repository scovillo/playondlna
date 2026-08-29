# PlayOnDlna

[🌐 Official website](https://scovillo.github.io/playondlna/) · [📥 Download on F-Droid](https://f-droid.org/packages/io.github.scovillo.playondlna/)

📦 **Version:** 15 (1.14)
⚙️ **Build-Tool:** Gradle 8.14.3

## ✨ Highlights

**Your media. Your player. Your network.**

- 🌍 **More than YouTube** — Share links from YouTube, YMusic, PeerTube, SoundCloud, Bandcamp, and media.ccc.de
- 🎧 **Video and audio** — PlayOnDlna prepares compatible media, cover artwork, and subtitles for your DLNA player
- 🎶 **Playlists that just work** — Enjoy continuous playback even when your player has no native playlist support
- 📚 **Your personal media library** — Keep prepared videos and audio ready to play again whenever you like
- 📡 **Effortless discovery and control** — Find compatible players automatically and control playback from the app
- 🔒 **Local by design** — Media is served directly from your Android device over your local network

No account, no ads, and no complicated setup—just share a link, choose a player, and enjoy.

## 📱 Description

Play your media ad-free on DLNA players such as Kodi and Smart TVs in your local network!
Simply share a supported media link with PlayOnDlna. The app prepares compatible video or audio and streams it directly to your selected player.

Supported sources include YouTube, YMusic, PeerTube, SoundCloud, Bandcamp, and media.ccc.de.

## 🤖 Android Configuration

- **Application ID:** io.github.scovillo.playondlna
- **Compile SDK:** android-36
- **Min SDK:** 26
- **Target SDK:** 36

PlayOnDlna runs on Android 8.0 and newer.

The global configuration `android:usesCleartextTraffic="true"` is needed to communicate with DLNA devices in the local network and is never used in the public internet context.

The app is built entirely on free software libraries.
All dependencies are compatible with the GNU GPLv3 license.
The app itself is licensed under the GNU GPLv3.
See the [THIRD_PARTY_LICENSES.md](https://github.com/scovillo/playondlna/blob/main/THIRD_PARTY_LICENSES.md) for full license information.

## 🎁 Support PlayOnDlna

PlayOnDlna is free, open source, and developed independently.

If the app is useful to you, you can support its continued development with a voluntary donation.

Donations help with:

- Maintenance, bug fixes, and new features
- Testing on different devices and DLNA players
- Continued development of this free and open-source project

PlayOnDlna will remain fully usable without any donation.

[![Donate using Liberapay](https://liberapay.com/assets/widgets/donate.svg)](https://liberapay.com/scovillo/donate)

[![Donate using GitHub Sponsors](https://img.shields.io/badge/GitHub%20Sponsors-❤️-pink?logo=github&style=flat-square)](https://github.com/sponsors/scovillo)

[![Donate using PayPal](https://www.paypalobjects.com/webstatic/icon/pp50.png)](https://paypal.me/muemmelmaus)

You can also support the project without donating:

- ⭐ Star the project on [GitHub](https://github.com/scovillo/playondlna)
- Share PlayOnDlna with others
- Send feedback or bug reports
- Contribute code or translations

Thank you for every kind of support! ❤️

## 🛠️ Build Instructions

```bash
./gradlew build
```

## 📚 Dependencies

  - androidx.core:core-ktx:1.10.1
  - androidx.core:core-splashscreen:1.0.1
  - androidx.lifecycle:lifecycle-runtime-ktx:2.6.1
  - androidx.activity:activity-compose:1.8.0
  - androidx.compose:compose-bom:2024.09.00
  - androidx.compose.ui:ui:unspecified
  - androidx.compose.ui:ui-graphics:unspecified
  - androidx.compose.ui:ui-tooling-preview:unspecified
  - androidx.compose.material3:material3:unspecified
  - androidx.recyclerview:recyclerview:1.4.0
  - androidx.navigation:navigation-compose:2.9.3
  - androidx.datastore:datastore-preferences:1.1.7
  - androidx.appcompat:appcompat:1.7.1
  - androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4
  - androidx.compose.material:material-icons-extended-android:1.7.8
  - io.github.scovillo:ffmpeg-kit:1.15
  - com.arthenica:smart-exception-java:0.2.1
  - org.nanohttpd:nanohttpd:2.3.1
  - com.github.teamnewpipe:NewPipeExtractor:v0.26.3
  - com.squareup.okhttp3:okhttp:4.12.0
  - com.arthenica:ffmpeg-kit-custom:main

## 📄 License

PlayOnDlna - An Android application to play media on dlna devices
Copyright (C) 2025 Lukas Scheerer

Licensed under the GNU General Public License v3.0

You should have received a copy of the GNU GPL v3 in the [LICENSE](https://github.com/scovillo/playondlna/blob/main/LICENSE)
file along with this program. If not, see <https://www.gnu.org/licenses/>