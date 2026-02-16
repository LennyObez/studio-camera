# Studio Camera

Studio Camera is a premium mobile companion app for pairing with a Studio Camera Box/Bridge to control cameras, monitor live view, and manage media. It focuses on fast onboarding (QR/NFC), reliable session handling, and a polished pro workflow on Android and iOS.

## Key Features

- **Pairing & binding**: QR scan (primary), NFC tap (Android), manual entry (fallback)
- **Robust connection lifecycle**: timeouts, cancel/retry, reconnect, capability negotiation
- **Camera control**: live view, capture/record, exposure + focus controls (device capability dependent)
- **Media library**: browse, preview, download, share, and delete (if supported)
- **Discovery**: find devices on the local network via mDNS/UDP
- **Mock mode**: fully interactive simulator for UI development and testing

## Project Status

- **Goal**: General Availability (GA) release on Google Play and Apple App Store
- **Current phase**: Core scaffold complete, feature implementation in progress
- This repository is **source-available** to enable community contributions while preserving commercial control (see [License](#license))

## Tech Stack

| Layer          | Technology                                                      |
| -------------- | --------------------------------------------------------------- |
| Language       | Kotlin 2.2.10 (Kotlin Multiplatform)                           |
| UI             | Compose Multiplatform 1.10.1 + Material 3                      |
| Navigation     | Decompose 3.3.0                                                |
| Networking     | Ktor 3.1.1 (OkHttp on Android, Darwin on iOS)                  |
| Serialization  | kotlinx-serialization 1.8.1                                    |
| DI             | Koin 4.1.0                                                     |
| Storage        | Multiplatform Settings 1.3.0                                   |
| Image loading  | Coil 3.1.0                                                     |
| Logging        | Kermit 2.0.5                                                   |
| Camera         | CameraX 1.5.0 (Android), ML Kit Barcode 17.3.0                |
| Media playback | Media3 / ExoPlayer 1.6.0                                       |
| Build system   | Gradle 9.1.0, AGP 9.0.1, convention plugins in `build-logic/` |

## Requirements

- JDK 25+
- Android Studio (latest stable) or IntelliJ IDEA
- Android SDK (API 35+)
- Xcode 16+ (macOS only, for iOS builds)

## Build & Run

```bash
# Clone
git clone https://github.com/LennyObez/studio-camera.git
cd studio-camera

# Android debug build
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Run all checks (lint, tests)
./gradlew check
```

> iOS builds require macOS with Xcode. Open `iosApp/` in Xcode or use KMP tooling.

## Module Structure

```
studio-camera/
  androidApp/              Android application entry point
  iosApp/                  iOS application (Swift + KMP framework)
  core/
    common/                Platform utilities (expect/actual)
    domain/                Models, repository interfaces, use cases
    data/                  Repository implementations
    network/               Ktor HTTP/WebSocket client
    storage/               Encrypted key-value persistence
    designsystem/          Theme, typography, color tokens (Material 3)
    ui/                    Shared navigation components (Decompose)
  feature/
    pair/                  QR/NFC/manual device pairing
    discovery/             Network device discovery (mDNS)
    camera/                Live view streaming + camera controls
    media/                 Media library browsing + transfer
    mock/                  Interactive device simulator
  build-logic/             Gradle convention plugins
  gradle/                  Version catalog (libs.versions.toml) + wrapper
```

## Architecture (High Level)

```
UI Layer (Compose)
    |
Navigation (Decompose Components)
    |
Domain Layer (Use Cases, Repository Interfaces, Models)
    |
Data Layer (Repository Implementations)
    |
  +---------+-----------+
  |         |           |
Network   Storage    Platform
(Ktor)    (Settings) (CameraX, NFC, mDNS)
```

- **Unidirectional data flow**: UI -> Component -> Repository -> Data source
- **Session Manager**: pairing, auth/bind, keepalive, reconnect
- **Connection State Machine**: idle -> connecting -> handshake -> paired -> active (with error/retry states)
- **DI**: Koin modules scoped per feature
- **KMP**: shared `commonMain` code with `androidMain`/`iosMain` platform implementations

## Contributing

We welcome contributions via pull requests.

- Please read: [CONTRIBUTING.md](.github/CONTRIBUTING.md)
- All contributions require acceptance of the Contributor License Agreement (CLA)

## Security

- Do not post secrets, tokens, or device credentials in issues
- Report vulnerabilities privately (see [SECURITY.md](.github/SECURITY.md))

## License

This project is released under a **source-available** license intended to allow collaboration while restricting commercial use and redistribution. See [LICENSE](LICENSE) for details.

Commercial licensing is available — contact the maintainer.

## Trademarks

"Studio Camera" and associated branding are trademarks of the project owner.
