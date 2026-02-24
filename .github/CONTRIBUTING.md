# Contributing

Thanks for your interest in contributing to Studio Camera.

Studio Camera is a Kotlin Multiplatform app targeting Android and iOS. We care about correctness, security, performance, and keeping the codebase clean, and we expect contributions to meet those standards.

## Ground rules

- No placeholders, no TODOs, no "example-only" code in production paths.
- Strict typing, explicit dependencies, no platform leaks in `commonMain`.
- Stateless composables where possible, state hoisting, Material 3 design system.
- English only for code, comments, and documentation.

## Getting set up

### What you'll need

- JDK 25+
- Android Studio (latest stable) or IntelliJ IDEA
- Android SDK (API 35+)
- Xcode 16+ (macOS only, for iOS builds)
- Git

### Clone and build

```bash
git clone https://github.com/LennyObez/studio-camera.git
cd studio-camera

# Build a debug APK
./gradlew assembleDebug

# Run on a connected device
./gradlew installDebug

# Run the full quality gate
./gradlew check
```

Make sure `./gradlew check` passes before opening a PR. It's the same gate CI runs.

## Repository layout

```
androidApp/          Android app module
iosApp/              iOS app (Swift + KMP)
core/
  common/            Platform utilities (expect/actual)
  domain/            Models, interfaces, use cases
  data/              Repository implementations
  network/           Ktor HTTP/WebSocket client
  storage/           Multiplatform-settings persistence
  designsystem/      Theme, typography, color tokens
  ui/                Navigation (Decompose)
feature/
  pair/              QR/NFC/manual pairing
  discovery/         Network device discovery
  camera/            Live view and camera controls
  media/             Media library and transfer
  mock/              Device simulator
build-logic/         Convention plugins
gradle/              Version catalog and wrapper
```

## Coding standards

### Kotlin

- Use constructor injection via Koin for dependencies.
- Keep `commonMain` free of platform-specific code. Use `expect`/`actual` declarations instead.
- Public APIs should be typed and documented.
- Handle errors explicitly; no silent failures.
- Use `kotlinx-serialization` for all serialization (no reflection-based alternatives).

### Compose

- Prefer stateless composables with state hoisting.
- Use the project design system (`core:designsystem`) for theming.
- Add preview annotations for significant composables.
- Follow Material 3 guidelines.

### Architecture

- Data flows in one direction: UI -> component -> repository -> data source.
- Navigation uses Decompose components (not Compose Navigation).
- DI uses Koin modules scoped per feature.
- Networking uses Ktor with content negotiation and kotlinx-serialization.

## Testing

- Add tests for any new behavior.
- Cover edge cases and failure paths, especially around connection and session handling.
- Keep tests deterministic. Avoid real timeouts, use fake/mock repositories.
- Use integration tests when behavior spans multiple components.

## Before you open a PR

The full quality gate must pass locally:

```bash
./gradlew check        # Android lint + unit tests
./gradlew lintDebug    # Android lint only (if you want a quick check)
```

There's no separate formatter or detekt config at the moment. `./gradlew check` runs Android lint and unit tests.

Never commit secrets, API keys, or device credentials.

## Performance

If your change touches app startup, navigation transitions, live view rendering, session lifecycle, or media loading, include a brief performance note in the PR. Before/after measurements are appreciated when available.

## Documentation

Update relevant docs when you change features or APIs.

## Branches and commits

### Branch naming

Use one of these prefixes: `feat/`, `fix/`, `docs/`, `perf/`, `security/`, `refactor/`

### Commit messages

We use [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <imperative summary>
```

**Types:** `feat`, `fix`, `docs`, `perf`, `refactor`, `test`, `ci`, `build`, `chore`, `security`

**Scopes:** `core`, `network`, `pair`, `discovery`, `camera`, `media`, `mock`, `ui`, `domain`, `data`, `storage`, `designsystem`, `android`, `ios`, `ci`

Examples:
- `feat(pair): add QR code scanning with ML Kit`
- `fix(network): handle WebSocket reconnect on session timeout`
- `perf(camera): reduce live view frame decode latency`

## Pull requests

Your PR should explain:
- What problem it solves and why
- What changed (key points)
- Security impact (say "none" if not applicable)
- Performance impact (say "none" if not applicable)
- What tests were added or updated

### Checklist

- [ ] Tests added or updated
- [ ] `./gradlew check` passes locally
- [ ] No new placeholders or TODOs
- [ ] Docs updated if needed
- [ ] Performance note included if relevant

## Security issues

Don't open public issues for vulnerabilities. Report them privately. See [SECURITY.md](SECURITY.md).

Thanks for helping build Studio Camera.
