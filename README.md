# Studio Camera

Control your professional camera from your phone. Studio Camera connects to Sony, Canon, Nikon, Fujifilm, Panasonic/Lumix, and OM System cameras over Wi-Fi Direct, giving you live view, exposure control, remote capture, and media management in one app.

Built with Kotlin Multiplatform for Android and iOS.

## What it does

- **Remote camera control**: adjust ISO, shutter speed, aperture, and EV from your phone
- **Live view**: see what your camera sees with low-latency streaming
- **Multi-brand support**: works with six major camera brands out of the box
- **Easy pairing**: connect via QR code, NFC (Android), or manual SSID entry
- **Overlays**: grids, aspect ratio guides, histogram, focus peaking, zebra, and safe zones
- **Media management**: browse, preview, download, share, and delete photos and videos
- **Mock mode**: a built-in simulator for development and testing without a real camera

## Supported cameras

| Brand | Protocol | Live view | Capture | Exposure |
|-------|----------|-----------|---------|----------|
| Sony | JSON-RPC / HTTP | Yes | Yes | Yes |
| Canon | CCAPI (REST) | Yes | Yes | Yes |
| Panasonic | cam.cgi + UDP | Yes | Yes | Yes |
| Nikon | PTP/IP | Yes | Yes | Yes |
| Fujifilm | PTP/IP variant | Yes | Yes | Yes |
| OM System | HTTP CGI + MJPEG | Yes | Yes | Yes |

## Tech stack

| Layer | Technology |
|-------|------------|
| Language | Kotlin 2.2.10 (Kotlin Multiplatform) |
| UI | Compose Multiplatform 1.10.1, Material 3 |
| Navigation | Decompose 3.3.0 |
| Networking | Ktor 3.1.1 (OkHttp on Android, Darwin on iOS) |
| Serialization | kotlinx-serialization 1.8.1 |
| DI | Koin 4.1.0 |
| Storage | Multiplatform Settings 1.3.0 |
| Image loading | Coil 3.1.0 |
| Logging | Kermit 2.0.5 |
| Camera preview | CameraX 1.5.0 (Android), ML Kit Barcode 17.3.0 |
| Media playback | Media3 / ExoPlayer 1.6.0 |
| Build system | Gradle 9.1.0, AGP 9.0.1, convention plugins |

## Getting started

You'll need JDK 25+, Android Studio (or IntelliJ IDEA), and Android SDK API 35+. For iOS builds, you'll also need macOS with Xcode 16+.

```bash
git clone https://github.com/LennyObez/studio-camera.git
cd studio-camera

# Build a debug APK
./gradlew assembleDebug

# Install on a connected device
./gradlew installDebug

# Run lint and tests
./gradlew check
```

> iOS builds require macOS with Xcode. Open `iosApp/` in Xcode or use KMP tooling.

## Project structure

```
studio-camera/
  androidApp/              Android entry point
  iosApp/                  iOS entry point (Swift + KMP framework)
  core/
    common/                Platform utilities (expect/actual)
    domain/                Models, repository interfaces, use cases
    data/                  Repository implementations
      camera/              Brand-specific camera control
        sony/              Sony Camera Remote API (JSON-RPC)
        canon/             Canon CCAPI (REST)
        panasonic/         Panasonic cam.cgi (HTTP CGI)
        nikon/             Nikon PTP/IP
        fujifilm/          Fujifilm PTP/IP variant
        omsystem/          OM System HTTP CGI
    network/               HTTP client and TCP/UDP sockets
    storage/               Encrypted key-value persistence
    designsystem/          Theme, typography, and color tokens
    ui/                    Navigation components (Decompose)
  feature/
    pair/                  Home screen and Wi-Fi Direct pairing
    discovery/             mDNS device discovery
    camera/                Live view and camera controls
    media/                 Media library and transfer
    mock/                  Device simulator
  build-logic/             Gradle convention plugins
  gradle/                  Version catalog and wrapper
```

## How it works

The phone connects to the camera's Wi-Fi network, then Studio Camera probes brand-specific API endpoints to discover what it's talking to. Each brand has its own protocol implementation, and a central router delegates to the right one.

Data flows in one direction: UI → navigation component → repository → data source. State is managed with Kotlin `StateFlow`, and the DI layer (Koin) keeps things modular with per-feature scopes.

The entire business logic lives in shared Kotlin Multiplatform code (`commonMain`), with platform-specific implementations only where needed (networking engines, camera preview, NFC).

## Contributing

Contributions are welcome. Please read [CONTRIBUTING.md](.github/CONTRIBUTING.md) before opening a PR. All contributions require acceptance of the Contributor License Agreement (CLA).

## Security

Don't post secrets, tokens, or device credentials in issues. If you find a vulnerability, please report it privately. See [SECURITY.md](.github/SECURITY.md).

## License

This project uses a source-available license that allows personal and educational use while restricting commercial use and redistribution. See [LICENSE](LICENSE) for the full terms.

For commercial licensing, contact the maintainer.

## Trademarks

"Studio Camera" and associated branding are trademarks of the project owner.
