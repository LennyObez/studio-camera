# Copilot Code Review Instructions

You are reviewing a **Kotlin Multiplatform (KMP)** mobile app that controls professional cameras (Sony, Canon, Nikon, Fujifilm, Panasonic/Lumix, OM System) over Wi-Fi Direct. Targets Android (primary) and iOS via Compose Multiplatform.

## Architecture

### Module boundaries (enforced strictly)

| Module | Purpose | Key constraint |
|--------|---------|----------------|
| `core/common` | Platform utils (`expect`/`actual`) | **No domain types** |
| `core/domain` | Models, repository interfaces, use cases | Pure Kotlin, no framework deps |
| `core/data` | Repository impls, brand camera APIs | Needs **direct** Ktor deps (not transitive) |
| `core/network` | Ktor HTTP client, `TcpSocket`, `UdpReceiver` | Platform-specific `expect`/`actual` |
| `core/storage` | Encrypted persistence | multiplatform-settings |
| `core/ui` | Navigation (Decompose) | No feature module deps |
| `feature/*` | Feature screens | May depend on `core/*`, never on other features |
| `androidApp` | Android entry point | `RootContent.kt` lives here, not `core/ui` |

### Key patterns to enforce

- **Navigation**: Decompose components only. Flag any use of Jetpack Compose Navigation.
- **DI**: Koin with constructor injection. Named qualifiers for brand-specific implementations (e.g., `named("sonyCamera")`). Bridge pattern routes `named("real")` vs `named("mock")` via `MockModeManager`.
- **State**: `StateFlow` for reactive state. `sealed class`/`sealed interface` for state types and results.
- **Serialization**: `kotlinx-serialization` only. No reflection-based serializers.
- **Camera repos**: `BrandCameraRepositoryRouter` delegates to brand-specific `CameraRepository` implementations via `Map<CameraBrand, CameraRepository>`.
- **Live view**: `Flow<ByteArray>` is the universal frame abstraction. No platform-specific stream types in shared code.

## Build system (AGP 9.0 + Kotlin 2.2)

Flag violations of these AGP 9.0 rules:

- `org.jetbrains.kotlin.android` plugin must NOT appear in app modules (built-in with AGP 9.0)
- KMP library modules use `com.android.kotlin.multiplatform.library`, not `com.android.library`
- Android config in KMP modules must be inside `kotlin { androidLibrary { ... } }`, not top-level `android {}`
- `BuildConfig` requires explicit `buildFeatures { buildConfig = true }`
- `kotlinOptions` is deprecated; use `kotlin { compilerOptions {} }`

## Review focus areas

### Security (highest priority)

- **Never commit secrets**: API keys, pairing tokens, device credentials, `.env` files
- **Pairing tokens must never be logged**, even at debug level
- **TLS required** for all network communication. Flag any `http://` URLs used for actual data transfer (note: camera Wi-Fi Direct APIs like Sony, Canon, Panasonic use `http://` over local network by design — this is expected)
- **TLS certificate pinning**: Android uses `FingerprintTrustManager`, iOS uses `SecTrust` + `CC_SHA256`. Flag any pinning bypass or `TrustAllCerts`
- **Encrypted storage**: Sensitive data must use `androidx.security:security-crypto` (Android) or Keychain (iOS)
- **OWASP top 10**: Watch for injection, XSS in WebView content, improper certificate validation
- **PTP/IP sessions**: Must clean up sockets on failure (close in catch/finally blocks). Watch for socket leaks

### Performance

- **Blocking I/O on wrong dispatcher**: Any blocking I/O (TCP sockets, UDP sockets, file I/O) must use `Dispatchers.IO`, not `Dispatchers.Default` or `Dispatchers.Main`
- **Flow collection**: Ensure `Flow<ByteArray>` from live view is collected on appropriate scope; watch for unbounded buffer growth
- **Socket readExact patterns**: When reading exact N bytes from TCP, the temp buffer must be sized to `remaining` bytes, not the total. Over-reading corrupts the TCP stream
- **Exponential backoff**: Reconnection logic should use capped exponential backoff, not fixed delays
- **Memory**: Large byte arrays (camera frames, PTP data) should be bounded. Watch for `dataPayload = dataPayload + data` patterns that create excessive intermediate arrays in hot paths

### Correctness

- **CancellationException**: Must always be re-thrown in coroutine code. Flag `catch (e: Exception)` blocks inside `suspend` functions that don't re-throw `CancellationException`
- **PTP/IP protocol**: Packet structure is length(4 LE) + type(4 LE) + payload. Response code is at payload offset 4 (after 4-byte transaction ID). Flag any off-by-one errors
- **Thread safety**: `@Volatile` on mutable state accessed from multiple coroutines. `StateFlow` for observable state
- **Resource cleanup**: Sockets, sessions, and receivers must be closed in `finally` blocks. iOS POSIX sockets need idempotent close (guard against double-close)
- **Infinite loops**: Any `while(true)` reading packets must have a max iteration guard
- **`expect`/`actual`**: Every `expect` declaration in `commonMain` must have `actual` implementations for all targets (android, ios, jvm)

### Code quality

- **UI text: sentence case always** — capitalize only the first word. "Aspect ratio guide", not "Aspect Ratio Guide"
- **Namespace**: Must follow `com.studiocamera.${project.path}` convention
- **No `java.*` in commonMain**: Only in `androidMain`/`jvmMain`. Use `expect`/`actual` for platform APIs
- **No `kotlin.system.getTimeMillis()`**: Use project's `expect/actual currentTimeMillis()`
- **Imports**: No unused imports. No wildcard imports in production code
- **Conventional Commits**: `<type>(<scope>): <summary>`. Types: feat, fix, docs, perf, refactor, test, ci, build, chore, security

### Camera protocol specifics

| Brand | Protocol | Transport | Key ports |
|-------|----------|-----------|-----------|
| Sony | Camera Remote API (JSON-RPC) | HTTP | 8080, 10000 |
| Canon | CCAPI (REST) | HTTP | 8080, 80 |
| Panasonic | cam.cgi (HTTP CGI) | HTTP + UDP (live view) | 80 + UDP 49152 |
| OM System | OPC (HTTP CGI) | HTTP | 80 |
| Nikon | PTP/IP (CIPA DC-005) | TCP | 15740 |
| Fujifilm | PTP/IP variant | TCP | 55740-55742 |

Flag any protocol implementation that:
- Uses wrong port for the brand
- Sends HTTP to a PTP/IP brand or vice versa
- Doesn't handle the specific response format (JSON-RPC for Sony, XML for Panasonic/OM System, binary PTP/IP for Nikon/Fujifilm)
- Missing SSDP discovery for Sony
- Missing two-stage shutter for OM System (1st2ndpush + 2nd1strelease)

## Testing

- All KMP modules have a `jvm()` target for unit testing via `./gradlew jvmTest`
- Fake repositories for all `core/domain` interfaces
- Deterministic tests: no real timeouts, seeded randomness
- Critical paths: pairing lifecycle, session state machine, media transfer
- Flag any test that depends on real network, real camera, or non-deterministic timing
