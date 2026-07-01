# Repository instructions for Codex / OpenCode

Amber is a single-module Android app (`:app`, package `com.greenart7c3.nostrsigner`) — a Nostr event signer (NIP-46 / NIP-55). Despite top-level `lib/`, `commonMain/`, `androidMain/` dirs, `settings.gradle.kts` includes only `:app`; those are not separate Gradle modules.

## Toolchain

- JDK 21 required (source/target compat 21). CI and `.codex/setup.sh` use Temurin 21.
- `compileSdk = 37`, `minSdk = 26`, R8 full mode enabled, Gradle parallel + configuration-cache.
- Room schemas are exported to `app/schemas` via KSP (`room.schemaLocation`).

## Build and validation commands

- `./gradlew ktlintCheck` — Kotlin style check (pre-commit hook runs this).
- `./gradlew ktlintFormat` — auto-fix formatting issues.
- `./gradlew test --no-daemon` — JVM unit tests for all variants (pre-push hook runs `./gradlew test`).
- Run one test: `./gradlew :app:testFreeDebugUnitTest --tests "com.greenart7c3.nostrsigner.SomeTest"` (or `--tests "*SomeTest.method"`).
- `./gradlew assembleDebug --no-daemon` — debug APK for both product flavors.
- `./gradlew assembleRelease --no-daemon` — release APK; signing only activates when env `SIGN_RELEASE` is set **and** `keystore.properties` exists. CI otherwise `touch`es an empty one.
- `./build.sh <version> <appName>` — builds free + offline release APKs/AABs into `~/release/` and calls `generate_manifest.sh`.

Required order when pushing: `ktlintCheck` → `test` → build (mirrored by the hooks, but run them directly; do not rely on hooks).

## Releases / version bumps

When asked to "bump the version" / cut a release, do **all** of the following in a single change — never omit the verification block:

1. Bump `versionCode` (+1) and `versionName` (new `X.Y.Z`) in `app/build.gradle.kts` (`defaultConfig`). These are the only places the version lives.
2. Prepend a new `## Amber X.Y.Z` block at the top of `CHANGELOG.md`, summarizing the non-merge commits since the previous `vX.Y.Z` tag (`git log vX.Y.Z..HEAD --no-merges`), grouped as user-facing bullets. End the block with the standard "Download it with …" line (update the `releases/tag/vX.Y.Z` URL) and the "If you like my work …" donation line.
3. **Always include the `## Verifying the release` block immediately after the donation line** (before the next `## Amber` heading). Copy it verbatim from the previous release entry and only change the `manifest-vX.Y.Z.txt` / `manifest-vX.Y.Z.txt.sig` filenames to the new version. Do **not** change the `gpg --recv-keys` key id, the `gpg: Signature made Fri 13 Sep 2024 …` block, or the surrounding prose — those are key-specific, not release-specific. This block must be part of the version-bump commit, not a follow-up.
4. Do not commit unless explicitly asked (per the global rule). Do not edit `build.sh` — it takes the version as an argument.

## Product flavors (dimension `version`)

| Flavor | Notes |
|--------|-------|
| `free` (default) | Online: OkHttp, Coil, kmptor, relay connectivity. Owns `INTERNET`/network permissions. |
| `offline` | No network stack. `app/src/offline/AndroidManifest.xml` **removes** `INTERNET`/`CHANGE_NETWORK_STATE`/`ACCESS_NETWORK_STATE` with `tools:node="remove"`. |
| `benchmark` | Mirrors `free` network deps; `applicationIdSuffix=.benchmark`, `versionNameSuffix=-BENCHMARK`; CI builds a signed release per push for side-by-side install. |

- Guard any network-only code with `BuildFlavorChecker.isOfflineFlavor()`. There is also `BuildConfig.IS_FDROID_BUILD` (false by default; the F-Droid release workflow flips it true and strips `REQUEST_INSTALL_PACKAGES` via `sed`) — use it to disable self-update / Zapstore.
- **Offline permissions gate:** `check-offline-permissions.yml` runs `./gradlew processOfflineDebugManifest` and greps the merged manifest for the three network permissions. Any new dependency that leaks `INTERNET` etc. will fail this check — verify the offline merged manifest when adding network-capable deps.

## Architecture notes (not obvious from filenames)

Three request ingestion paths all converge on `Account.sign()` / encrypt-decrypt:
1. `nostrsigner://` / `nostrconnect://` Intent → `SignerActivity` → `IntentUtils` → approval bottom sheet.
2. ContentProvider IPC → `SignerProvider` (synchronous, `runBlocking`).
3. NIP-46 relay kind 24133 → `NotificationSubscription` → `EventNotificationConsumer` → `BunkerRequestUtils`.

`Amber.kt` (the `Application` class) is the DI container: owns `applicationIOScope`, the Quartz `NostrClient`, `notificationSubscription`, `isStartingAppState` (set during `runMigrations()` — wait on `isStartingAppState.first { !it }`), and `settings.killSwitch` (disconnects all relays when true).

Per-account isolation: every `npub` gets its own `SharedPreferences` (`prefs_${npub}`), `AppDatabase` (`amber_db_${npub}`), `LogDatabase`, and `HistoryDatabase` — all lazily cached in `ConcurrentHashMap`s in `Amber`. Decrypted keys loaded via `LocalPreferences.loadFromEncryptedStorage()` and cached in `LargeCache`.

Biometric/PIN lock (`useAuth`/`usePin`, `SecurityScreen`, `BiometricAuthScreen`) is a **UI-only app-launch gate**, not a signing gate. `SignerProvider` and the NIP-46 path never touch it; auto-accept permission rules sign silently on all three paths. Authorization for automatic signing is governed solely by the permission system (`ApplicationEntity` / `ApplicationPermissionsEntity`: `rememberType`, `acceptUntil`/`rejectUntil`, `kind`). Do not wire signing through the biometric prompt.

See `CLAUDE.md` for the key-files table (verified accurate against the current tree).

## Conventions

- ktlint `android_studio` code style (`.editorconfig`); star imports effectively disabled; trailing commas allowed; `@Composable` functions exempt from the function-naming rule. Run `ktlintFormat` rather than hand-formatting.
- Translations live in `app/src/main/res/values-<locale>/strings.xml`; `MissingTranslation` lint is intentionally disabled and the shipped locales are pinned by `androidResources.localeFilters` in `app/build.gradle.kts`.
- Git hooks (`git-hooks/pre-commit`, `pre-push`) are auto-installed by the root `build.gradle.kts` `installGitHook` task wired into `:app` `preBuild`. Do not rely on them as a substitute for running checks directly.

## Codex Web / cloud setup

Use the committed scripts for cloud environments:
- Setup: `bash .codex/setup.sh` — installs/verifies Java 21, bootstraps Android cmdline SDK, installs API 36 / build-tools 36.0.0, accepts licenses, and prewarms Gradle for `free` debug unit-test sources.
- Maintenance: `bash .codex/maintenance.sh` — refreshes Gradle metadata in cached containers.

## Reproducibility

`Dockerfile` + `apkdiff.py` verify reproducible builds: `docker build -t amber-repro --build-arg VERSION=vX.Y.Z --build-arg APK_TYPE=free-arm64-v8a .` then `docker run --rm amber-repro` (expect `APKs match!`).
