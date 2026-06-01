# Repository instructions for Codex

## Codex Web environment

Use the committed setup scripts when creating a Codex Web cloud environment:

- Setup script: `bash .codex/setup.sh`
- Maintenance script: `bash .codex/maintenance.sh`

The setup script installs or verifies Java 21, bootstraps the Android command-line SDK when needed, installs Android API 36/build-tools 36.0.0, accepts SDK licenses, and warms Gradle dependencies for the default amber debug unit-test sources.

## Build and validation commands

- `./gradlew ktlintCheck` — Kotlin style check.
- `./gradlew test --no-daemon` — JVM unit tests for all variants.
- `./gradlew assembleDebug --no-daemon` — debug APK build for both product flavors.
- `./gradlew assembleRelease --no-daemon` — release APK build; only expect signing when `SIGN_RELEASE` and `keystore.properties` are present.

## Project notes

- The `amber` flavor is the default online build.
- The `offline` flavor must not introduce network-only behavior.
- Git hooks are installed by the root Gradle `preBuild` wiring; do not rely on hooks as a substitute for running checks directly.
