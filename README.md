# PlayOnDlna

📦 **Version:** 6 (1.5)
⚙️ **Build-Tool:** Gradle 8.14.3

## 🤖 Android Configuration

- **Application ID:** io.github.scovillo.playondlna
- **Compile SDK:** android-35
- **Min SDK:** 26
- **Target SDK:** 35

The global configuration `android:usesCleartextTraffic="true"` is needed to communicate with DLNA devices in the local network and is never used in the public internet context.

## 📱 Description

Play YouTube videos ad-free on DLNA players (e.g. Kodi, SmartTV) in your local network!
Browse YouTube in your favorite client and share the link to the PlayOnDlna app
to play the video ad-free on a DLNA player in your local network.

🎨 <b>Sleek Design</b><br>
A graphical interface reduced to the essentials in a dark design that's easy on the eyes even at night.<br>

💝 <b>Ad-Free</b><br>
Enjoy videos ad-free on your local network.<br>

🤩 <b>Simple</b><br>
Ad-free playback at the touch of a button without complex setup!
An automated search finds compatible players on your local network.<br>

👐 <b>Transparency</b><br>
The publicly accessible source code allows anyone to personally verify the quality, privacy, and security.<br>

If the app serves you well, consider a 🎁 donation to support my efforts.<br>

<b>Kodi Setup</b><br>
To stream videos to Kodi, you have to ensure that Kodi is found as dlna player in your local network.
To achieve this, follow the instructions below.<br>
1. Go to <b>⚙ Settings > Services > UPnP DLNA</b><br>
2. Enable <b>Enable UPnP support</b><br>
3. Enable <b>Allow remote control via UPnP</b><br>

❤️ Happy streaming! ❤️

The app is built entirely on free software libraries.
All dependencies are compatible with the GNU GPLv3 license.
The app itself is licensed under the GNU GPLv3. See the 
[THIRD_PARTY_LICENSES.md](https://github.com/scovillo/playondlna/blob/main/THIRD_PARTY_LICENSES.md) 
file in the sourcerepository for full license information.

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
  - androidx.navigation:navigation-compose:2.9.3
  - androidx.datastore:datastore-preferences:1.1.7
  - androidx.appcompat:appcompat:1.7.1
  - androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4
  - androidx.compose.material:material-icons-extended-android:1.7.8
  - io.github.scovillo:ffmpeg-kit:1.15
  - com.arthenica:smart-exception-java:0.2.1
  - org.nanohttpd:nanohttpd:2.3.1
  - com.github.teamnewpipe:NewPipeExtractor:0.24.8
  - com.squareup.okhttp3:okhttp:4.12.0
  - com.arthenica:ffmpeg-kit-custom:main

## 📄 License

PlayOnDlna - An Android application to play media on dlna devices
Copyright (C) 2025 Lukas Scheerer

Licensed under the GNU General Public License v3.0

You should have received a copy of the GNU GPL v3 in the [LICENSE](https://github.com/scovillo/playondlna/blob/main/LICENSE)
file along with this program. If not, see <https://www.gnu.org/licenses/>