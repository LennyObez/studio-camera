# Studio Camera

Studio Camera is a premium mobile companion app for controlling professional cameras (Sony, Canon, Nikon, Fujifilm, Panasonic/Lumix, OM System) via Wi-Fi Direct. Monitor live view, control exposure settings, capture photos and videos, and manage your camera's media library — all from your phone.

## Key Features

- **Multi-brand camera control**: Sony, Canon, Panasonic (live view, capture, exposure); Nikon, Fujifilm, OM System (stubs)
- **Wi-Fi Direct pairing**: QR scan (primary), NFC tap (Android), manual SSID entry (fallback)
- **Home screen**: Camera cards with connection status, quick reconnect, rename/remove, tips & guides
- **Live view streaming**: Brand-specific protocols (Sony JSON-RPC, Canon CCAPI, Panasonic cam.cgi)
- **Camera overlays**: Grid types, aspect ratio guides, histogram, focus peaking, zebra, safe zones
- **Exposure controls**: ISO, shutter speed, aperture, EV compensation, AF/MF toggle
- **Media library**: Browse, preview, download, share, and delete (if supported)
- **Subscription**: Google Play Billing with 7-day trial, monthly + lifetime plans
- **Mock mode**: Fully interactive simulator for UI development and testing

## Supported Cameras

| Brand | Protocol | Live View | Capture | Exposure | Status |
|-------|----------|-----------|---------|----------|--------|
| **Sony** | JSON-RPC/HTTP | Yes | Yes | Yes | Full |
| **Canon** | CCAPI REST | Yes (MJPEG) | Yes | Yes | Full |
| **Panasonic** | cam.cgi HTTP | Partial (UDP) | Yes | Yes | Partial |
| **Nikon** | PTP/IP | No | No | No | Stub |
| **Fujifilm** | Custom binary | No | No | No | Stub |
| **OM System** | HTTP CGI + XML | No | No | No | Stub |

## Project Status

- **Goal**: General Availability (GA) release on Google Play and Apple App Store
- **Current phase**: Feature-complete for Android, preparing for release
- Sony, Canon, Panasonic camera APIs fully integrated; Nikon, Fujifilm, OM System stubbed for future implementation
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
    data/                  Repository implementations + brand camera APIs
      camera/              Brand-specific camera control
        sony/              Sony Camera Remote API (JSON-RPC)
        canon/             Canon CCAPI (REST)
        panasonic/         Panasonic cam.cgi (HTTP CGI)
        stub/              Stub for unsupported brands
    network/               Ktor HTTP client
    storage/               Encrypted key-value persistence
    designsystem/          Theme, typography, color tokens (Material 3)
    ui/                    Shared navigation components (Decompose)
  feature/
    pair/                  Home screen + QR/NFC/manual Wi-Fi Direct pairing
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
  +---------+-----------+-----------------+
  |         |           |                 |
Network   Storage    Platform          Brand APIs
(Ktor)    (Settings) (CameraX, NFC)   (Sony/Canon/Panasonic)
```

- **Wi-Fi Direct**: Phone connects to the camera's Wi-Fi network, then probes brand-specific API endpoints
- **Unidirectional data flow**: UI -> Component -> Repository -> Data source
- **Brand routing**: `BrandCameraRepositoryRouter` delegates to Sony, Canon, Panasonic, or Stub based on connected camera brand
- **Session Manager**: Wi-Fi Direct connection, brand API discovery, reconnect
- **DI**: Koin modules scoped per feature, bridge pattern for mock/real switching
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
