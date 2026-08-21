# PlayOnDlna agent guide

PlayOnDlna is a single-module Android app (`io.github.scovillo.playondlna`) that prepares and manages media for playback on DLNA/UPnP MediaRenderers. It can extract and prepare YouTube media, serve media over a local HTTP server, and control compatible renderers via UPnP AVTransport. The app uses Kotlin, Jetpack Compose, coroutines, NewPipeExtractor, FFmpegKit, NanoHTTPD, and hand-written SSDP/SOAP code.

## Sources of truth

- `app/build.gradle.kts`: Android compatibility, version, packaging, dependencies, signing, and README generation.
- `gradle/libs.versions.toml` and `gradle/wrapper/gradle-wrapper.properties`: tool and library versions.
- `.github/workflows/test.yml`: required CI checks and Java version.
- `app/src/main/AndroidManifest.xml`: permissions, cleartext policy, foreground service, and share intent.
- `THIRD_PARTY_LICENSES.md`: dependency licensing relevant to distribution.

Do not copy SDK or dependency versions into this guide; read the build files when they matter.

## Architectural boundaries

- `MainActivity.kt` is the composition root. It creates repositories/models directly and wires Compose navigation; there is no dependency-injection framework.
- `ui/` contains Compose screens. State and side effects belong in the existing ViewModel/model layer (`model/`, `preparation/`, and `upnpdlna/`), not in composables.
- `SettingsRepository` uses Preferences DataStore. `LibraryManager` stores metadata, thumbnails, and completed media in `cacheDir`; the library and cache cleanup therefore share a lifecycle.
- The media path is: NewPipe extraction and stream selection -> ranged downloads -> FFmpeg mux/transcode -> `VideoFile` registration in the in-process `VideoHttpServer` -> DIDL-Lite metadata -> `SetAVTransportURI` -> `Play`.
- `StreamServer.kt` contains both the NanoHTTPD server and `WebServerService`. The foreground service keeps local serving alive; its notification channel, manifest service declaration, and Android-version-specific startup behavior must stay coordinated.
- `Upnp.kt` owns SSDP discovery, device-description parsing, and AVTransport URL resolution. `Play.kt` owns synchronous SOAP commands, which callers run on `Dispatchers.IO`.

## Project-specific constraints

### Android and UI

- Preserve the configured `minSdk`; guard APIs introduced later with SDK checks. CI/build runs on JDK 17 even though app bytecode compatibility is configured separately.
- Use the existing Compose/Material 3, navigation, state, Flow, and one-shot `ToastEvent` patterns.
- Put user-visible text in `app/src/main/res/values/strings.xml`. Update every existing locale (`values-de` and `values-uk`) when adding or changing translatable text; keep URLs and other locale-independent values `translatable="false"` in the default file only. Preserve formatting placeholders across locales.
- Cleartext HTTP and the foreground media-playback service are intentional LAN requirements. Do not remove `usesCleartextTraffic`, related permissions, or foreground-service behavior without replacing that transport/lifecycle design.

### DLNA and HTTP streaming

Treat changes across `upnpdlna/`, `server/`, and media preparation as one compatibility-sensitive path. Before changing playback, trace discovery and description parsing, AVTransport URL resolution, DIDL-Lite/XML escaping, `SetAVTransportURI`, `Play`, and the served URL.

- Prefer small compatibility fixes over replacing the UPnP stack.
- Preserve byte-range semantics and renderer-facing headers: `200`/`206`, `Content-Length`, `Content-Range`, `Accept-Ranges`, DLNA content features/transfer mode, and correct MIME types for MP4 and SRT.
- Keep the advertised `VideoFile` URL, metadata `protocolInfo`, server route, and actual response MIME type consistent. Renderers fetch the phone's IPv4 LAN address and process-local random port.
- Preserve multicast-lock acquisition/release and URL resolution for relative device control URLs.
- Treat renderer-specific fixes as potential regressions for other devices. Keep generic behavior as the default and isolate device-specific workarounds when possible.

### Media, cleanup, and distribution

- Stream downloads assume HTTP Range support and merge chunks in order. Preserve inclusive range boundaries, cancellation, temporary-file cleanup, FFmpeg session tracking, and the rule that only a successful current session publishes a playable file.
- Video compatibility selection and `PlayOnDlnaFfmpegCommand` are renderer-oriented: MP4/H.264 is preferred, audio may be absent, incompatible audio is converted to AAC, and subtitles may be external SRT and/or embedded. Change these together with focused tests where possible.
- The FFmpeg dependency is a custom, Maven-resolved build chosen for F-Droid-compatible licensing. Do not replace it with a different binary bundle or checked-in AAR without reviewing its bundled libraries against F-Droid policy and updating `THIRD_PARTY_LICENSES.md`.
- Preserve release/reproducibility settings in `app/build.gradle.kts`, notably disabled dependency metadata in APK/AAB and repository-resolved dependencies. Avoid timestamps, machine-specific paths, or other nondeterministic build inputs.

## Generated and store metadata

`README.md` is generated by `./gradlew generateReadme`; do not edit it directly. Its source is the `generateReadme` task in `app/build.gradle.kts`, including `fastlane/metadata/android/en-US/full_description.txt`. Fastlane localized descriptions and changelogs are store metadata, not Android string resources.

## Working approach

- For bug fixes and focused features, trace the existing implementation path first and make the smallest change that solves the problem.
- Prefer existing abstractions and patterns over introducing new architecture.
- Avoid unrelated refactoring unless it is necessary for the requested change.
- Do not leave placeholder code such as `// ... existing code ...` or truncate existing files.
- After the relevant flow is understood, implement and validate instead of continuing broad repository exploration.

## Validation

Use the narrowest relevant tests while iterating. Before finishing application or build changes, run the same checks as CI plus an APK build:

```sh
./gradlew ktlintCheck testDebugUnitTest assembleDebug
```

Instrumentation tests require an Android device/emulator:

```sh
./gradlew connectedDebugAndroidTest
```

After changing README inputs, run `./gradlew generateReadme` and verify the generated diff. Release builds additionally require the signing properties/environment variables defined in `app/build.gradle.kts`.
