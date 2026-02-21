# Contributing

Thanks for contributing to Studio Camera.

Studio Camera is a KMP (Kotlin Multiplatform) mobile app targeting Android and iOS. Contributions must meet standards for
correctness, security, performance, and maintainability.

## Quick rules

- No placeholders. No TODO. No "example-only" code in production paths.
- Kotlin: strict typing, explicit dependencies, no platform leaks in `commonMain`.
- Compose: stateless composables where possible, state hoisting, Material 3 design system.
- Docs: English only.

## Development setup

### Prerequisites

- JDK 25+
- Android Studio (latest stable) or IntelliJ IDEA
- Android SDK (API 35+)
- Xcode 16+ (macOS only, for iOS builds)
- Git

### Install

```bash
git clone https://github.com/LennyObez/studio-camera.git
cd studio-camera
```

### Build

```bash
# Android debug build
./gradlew assembleDebug

# Run on connected Android device
./gradlew installDebug

# Run all checks
./gradlew check
```

### Quality gate

Run the same checks as CI before opening a PR:

```bash
./gradlew check
```

## Repository structure

```
androidApp/          Android application module
iosApp/              iOS application (Swift + KMP)
core/
  common/            Platform utilities (expect/actual)
  domain/            Models, interfaces, use cases
  data/              Repository implementations
  network/           Ktor HTTP/WebSocket client
  storage/           Multiplatform-settings persistence
  designsystem/      Theme, typography, color tokens
  ui/                Shared navigation (Decompose)
feature/
  pair/              QR/NFC/manual pairing
  discovery/         Network device discovery
  camera/            Live view and camera controls
  media/             Media library and transfer
  mock/              Interactive device simulator
build-logic/         Convention plugins
gradle/              Version catalog and wrapper
```

## Coding standards

### Kotlin

- Prefer explicit dependencies (constructor injection via Koin).
- Keep `commonMain` free of platform-specific code; use `expect`/`actual` declarations.
- Public APIs must be typed and documented.
- Exceptions must be typed; no silent failures.
- Use `kotlinx-serialization` for all serialization; avoid reflection-based alternatives.

### Compose

- Composables should be stateless where possible (state hoisting).
- Use the project design system (`core:designsystem`) for theming.
- Preview annotations for all significant composables.
- Follow Material 3 guidelines.

### Architecture

- Unidirectional data flow: UI -> ViewModel/Component -> Repository -> Data source.
- Navigation via Decompose components (not Compose Navigation).
- DI via Koin modules scoped per feature.
- Network layer uses Ktor with content negotiation and kotlinx-serialization.

## Testing

- Add tests for any new behavior.
- Cover edge cases and failure paths (especially connection/session handling).
- Prefer deterministic tests:
  - avoid real timeouts where possible
  - use fake/mock repositories for UI tests
- Use integration tests when behavior depends on multiple components.

## Static analysis & formatting (mandatory)

Before opening a PR, the full gate must pass:

- Kotlin: `./gradlew check` (includes lint and unit tests)
- Android lint: `./gradlew lintDebug`
- Never commit secrets, API keys, or device credentials.

## Performance

If your change affects:

- app startup
- navigation transitions
- live view rendering
- network session lifecycle
- media loading/transfer

...include:

- a brief performance note in the PR
- before/after measurements if available

## Documentation requirements

Any feature change requires:

- updating relevant module README or docs
- documenting API changes

## Branching and commits

### Branch naming

- `feat/<topic>`
- `fix/<topic>`
- `docs/<topic>`
- `perf/<topic>`
- `security/<topic>`
- `refactor/<topic>`

### Commit messages (Conventional Commits)

Format:

```
<type>(<scope>): <imperative summary>
```

Types: `feat`, `fix`, `docs`, `perf`, `refactor`, `test`, `ci`, `build`, `chore`, `security`

Scope examples: `core`, `network`, `pair`, `discovery`, `camera`, `media`, `mock`, `ui`, `domain`, `data`, `storage`,
`designsystem`, `android`, `ios`, `ci`

Examples:

- `feat(pair): add QR code scanning with ML Kit`
- `fix(network): handle WebSocket reconnect on session timeout`
- `perf(camera): reduce live view frame decode latency`
- `docs(readme): update build instructions`

## Pull requests

A PR must include:

- problem statement (what and why)
- what changed (key points)
- security impact (explicitly "none" if applicable)
- performance impact (explicitly "none" if applicable)
- tests added/updated
- docs updated

### PR checklist

- [ ] Tests added/updated
- [ ] `./gradlew check` passes locally
- [ ] No new placeholders / TODOs
- [ ] Docs updated
- [ ] Performance note included if relevant

## Security issues

Do **not** open public issues for vulnerabilities.
Follow `SECURITY.md` to report privately.

Thanks for helping build Studio Camera.
