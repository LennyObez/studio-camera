# Studio Camera - iOS app

## Requirements

- **macOS** 14+ (Sonoma or later)
- **Xcode** 16+
- **CocoaPods** (if using pods for additional native deps)
- Android Studio / IntelliJ with KMP plugin (for shared code editing)

## Build steps

### 1. Build the shared KMP framework

```bash
./gradlew linkDebugFrameworkIosSimulatorArm64   # For Apple Silicon simulator
./gradlew linkDebugFrameworkIosX64              # For Intel simulator
./gradlew linkReleaseFrameworkIosArm64          # For physical devices
```

### 2. Open in Xcode

```bash
open iosApp/iosApp.xcodeproj
```

### 3. Select target and run

- Choose an iOS 16+ simulator or connected device
- Build and run (Cmd+R)

## iOS `actual` implementations needed

The following `expect` declarations in shared KMP code need iOS `actual` implementations:

| Module | Expect | Status |
|--------|--------|--------|
| `core:common` | `currentTimeMillis()` | Implemented |
| `core:storage` | `SecureStorage` (Keychain) | Implemented |
| `feature:camera` | `LiveViewSurface` (UIKit) | Stub only |
| `core:data` | `MdnsDiscoveryEngine` (Bonjour) | Stub only |
| `core:data` | `PlatformDownloader` (Photos) | Stub only |

### Priority for iOS v1

1. **SecureStorage**: Keychain Services wrapper (done)
2. **LiveViewSurface**: UIKit `UIImageView` with MJPEG frame rendering
3. **MdnsDiscoveryEngine**: `NWBrowser` or `NetServiceBrowser` for Bonjour
4. **PlatformDownloader**: `PHPhotoLibrary` for saving to camera roll

## Architecture notes

- Shared UI is Compose Multiplatform, renders natively on iOS via Skiko
- Navigation uses Decompose (works cross-platform)
- DI uses Koin. iOS entry point initializes Koin in `StudioCameraApp.swift`
- NFC pairing: iOS uses Core NFC (`NFCNDEFReaderSession`), needs separate implementation
- Camera permission: iOS uses `AVCaptureDevice.requestAccess`

## CI

The iOS CI job runs on `macos-latest` and builds the debug framework:

```yaml
./gradlew linkDebugFrameworkIosSimulatorArm64
```

This validates that shared code compiles for iOS without requiring a full Xcode build.
