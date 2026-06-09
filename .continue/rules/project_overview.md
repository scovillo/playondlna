# Project Summary

PlayOnDlna is an Android application that allows users to play YouTube videos on DLNA-compatible players (like Smart TVs or Kodi) within a local network without ads.

- **Primary Technologies:** Kotlin, Jetpack Compose, Coroutines, NewPipeExtractor (YouTube parsing), NanoHTTPD (Local Web Server), FFmpeg (Video processing/streaming), SSDP (DLNA device discovery).
- **Overall Architecture:** MVVM (Model-View-ViewModel) using Jetpack Compose for the UI, a local HTTP server for streaming, and UPnP/SSDP for device discovery.

# Directory Map

- `app/src/main/java/io/github/scovillo/playondlna/`
    - `ui/`: Jetpack Compose screens and UI components.
    - `server/`: Local HTTP server implementation for streaming video files.
    - `upnpdlna/`: DLNA/UPnP device discovery and communication logic.
    - `preparation/`: Video processing, downloading, and job management.
    - `model/`: Data models and state management.
    - `persistence/`: Data storage (Settings, favorites, etc.).
    - `download/`: Networking client for downloading video content.

# Key Entry Points

- `app/src/main/java/io/github/scovillo/playondlna/MainActivity.kt`: The main entry point. Initializes NewPipe, starts the background `WebServerService`, and sets up the Compose UI and dependency injection.
- `app/src/main/java/io/github/scovillo/playondlna/server/WebServerService.kt`: A foreground service that hosts the local HTTP server.
- `app/src/main/java/io/github/scovillo/playondlna/preparation/VideoJobModel.kt`: Manages the lifecycle of video preparation and streaming jobs.

# Architecture Overview

The app follows a layered architecture:
1. **UI Layer (Compose):** Reactive UI that observes state from ViewModels.
2. **Service Layer:** A foreground service runs the HTTP server to handle streaming requests from DLNA devices.
3. **Domain/Logic Layer:**
    - **Discovery:** Uses SSDP to find devices on the local network.
    - **Preparation:** Handles the complexity of downloading/processing YouTube videos for playback.
    - **Streaming:** Serves processed video files via a local web server.
4. **Data Layer:** Handles persistence (DataStore/SharedPreferences) and network requests (OkHttp).

# Important Conventions

- **UI:** Uses Jetpack Compose for all screens.
- **Concurrency:** Relies heavily on Kotlin Coroutines for asynchronous tasks (network, file I/O).
- **Service:** Uses a Foreground Service to ensure the HTTP server remains active during playback.

# Navigation Hints

- **Debugging UI:** Look in `ui/` for screen definitions.
- **Business Logic (Video):** Look in `preparation/` and `model/`.
- **DLNA/Device Discovery:** Look in `upnpdlna/`.
- **Streaming/HTTP Logic:** Look in `server/`.
- **Data Persistence:** Look in `persistence/`.
- **Tests:** Located in `app/src/test/` and `app/src/androidTest/`.

# Areas Requiring Caution

- `app/src/main/java/io/github/scovillo/playondlna/server/`: Changes here affect the availability of the stream to DLNA devices.
- `app/src/main/java/io/github/scovillo/playondlna/preparation/`: Changes here affect video download/processing stability.
- `AndroidManifest.xml`: Requires `usesCleartextTraffic="true"` for local network communication.
