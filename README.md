# PlayOnDlna

📦 **Version:** 1 (1.0)
⚙️ **Build-Tool:** Gradle 8.14.3

## 🤖 Android Configuration

- **Application ID:** io.github.scovillo.playondlna  
- **Compile SDK:** android-35  
- **Min SDK:** 26  
- **Target SDK:** 35

## 📱 Description

Play Youtube videos on DLNA players (e.g. <a href="https://kodi.tv/">Kodi</a>) in your LAN!
Browse youtube in your favorite client and share the link to the PlayOnDlna app to play the video on a dlna player found in your LAN.
If the app serves you well, consider a donation to support my efforts.

The app is built entirely on free software libraries.
All dependencies are compatible with the GNU GPLv3 license.
The app itself is licensed under the GNU GPLv3. See the 
[THIRD_PARTY_LICENSES.md](https://github.com/scovillo/playondlna/blob/main/THIRD_PARTY_LICENSES.md) 
file in the sourcerepository for full license information.

❤️ Happy streaming! ❤️

## 🎁 Donation

[![GitHub Sponsors](https://img.shields.io/badge/GitHub%20Sponsors-❤️-pink?logo=github&style=flat-square)](https://github.com/sponsors/scovillo)

[![PayPal](https://www.paypalobjects.com/webstatic/icon/pp50.png)](https://paypal.me/muemmelmaus)

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
  - androidx.appcompat:appcompat:1.7.1
  - androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2
  - androidx.compose.ui:ui:1.8.3
  - androidx.compose.material3:material3:1.3.2
  - androidx.compose.ui:ui-tooling-preview:1.8.3
  - androidx.activity:activity-compose:1.10.1
  - androidx.lifecycle:lifecycle-runtime-ktx:2.9.2
  - com.arthenica:smart-exception-java:0.2.1
  - org.nanohttpd:nanohttpd:2.3.1
  - com.github.teamnewpipe:NewPipeExtractor:0.24.6
  - com.squareup.okhttp3:okhttp:4.12.0
  - com.arthenica:ffmpeg-kit-custom:main

## 📄 License

PlayOnDlna - An Android application to play media on dlna devices
Copyright (C) 2025 Lukas Scheerer

Licensed under the GNU General Public License v3.0

You should have received a copy of the GNU GPL v3 in the [LICENSE](https://github.com/scovillo/playondlna/blob/main/LICENSE)
file along with this program. If not, see <https://www.gnu.org/licenses/>