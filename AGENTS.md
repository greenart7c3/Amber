# Repository instructions for coding agents

These instructions apply to every coding agent working in this repository (Codex, OpenCode, Claude Code, …). `CLAUDE.md` only points here.

Amber is a Nostr event signer (NIP-46 / NIP-55), package `com.greenart7c3.nostrsigner`. `settings.gradle.kts` includes two Gradle modules — `:app` (Android) and `:desktop` (see [Modules](#modules)). Despite top-level `lib/`, `commonMain/`, `androidMain/` dirs, those are not separate Gradle modules.

## Toolchain

- JDK 21 required (source/target compat 21). CI and `.codex/setup.sh` use Temurin 21.
- `compileSdk = 37`, `minSdk = 26`, R8 full mode enabled, Gradle parallel + configuration-cache.
- Room schemas are exported to `app/schemas` via KSP (`room.schemaLocation`).

## Build and validation commands

- `./gradlew ktlintCheck` — Kotlin style check (pre-commit hook runs this).
- `./gradlew ktlintFormat` — auto-fix formatting issues.
- `./gradlew lint` — Android Lint on the default variant; `warningsAsErrors` is on, so any new warning fails the build (pre-commit and pre-push hooks run this). Version-freshness checks (`GradleDependency` etc.) are disabled in `app/build.gradle.kts`; per-path exemptions live in `app/lint.xml`.
- `./gradlew test --no-daemon` — JVM unit tests for all variants (pre-push hook runs `./gradlew test lint`).
- Run one test: `./gradlew :app:testFreeDebugUnitTest --tests "com.greenart7c3.nostrsigner.SomeTest"` (or `--tests "*SomeTest.method"`).
- `./gradlew assembleDebug --no-daemon` — debug APK for both product flavors.
- `./gradlew assembleRelease --no-daemon` — release APK; signing only activates when env `SIGN_RELEASE` is set **and** `keystore.properties` exists. CI otherwise `touch`es an empty one.
- `./build.sh <version> <appName>` — builds free + offline release APKs/AABs into `~/release/` and calls `generate_manifest.sh`.
- Desktop: `./gradlew :desktop:run` (debug build), `:desktop:test`, `:desktop:packageDistributionForCurrentOs`.

Required order when pushing: `ktlintCheck` → `lint` → `test` → build (mirrored by the hooks, but run them directly; do not rely on hooks).

## Releases / version bumps

When asked to "bump the version" / cut a release, do **all** of the following in a single change — never omit the verification block:

1. Bump `versionCode` (+1) and `versionName` (new `X.Y.Z`) in `app/build.gradle.kts` (`defaultConfig`). These are the only places the version lives.
2. Prepend a new `## Amber X.Y.Z` block at the top of `CHANGELOG.md`, summarizing the non-merge commits since the previous `vX.Y.Z` tag (`git log vX.Y.Z..HEAD --no-merges`), grouped as user-facing bullets. End the block with the standard "Download it with …" line (update the `releases/tag/vX.Y.Z` URL) and the "If you like my work …" donation line.
3. **Always include the `## Verifying the release` block immediately after the donation line** (before the next `## Amber` heading). Copy it verbatim from the previous release entry and only change the `manifest-vX.Y.Z.txt` / `manifest-vX.Y.Z.txt.sig` filenames to the new version. Do **not** change the `gpg --recv-keys` key id, the `gpg: Signature made Fri 13 Sep 2024 …` block, or the surrounding prose — those are key-specific, not release-specific. This block must be part of the version-bump commit, not a follow-up.
4. Do not commit unless explicitly asked (per the global rule). Do not edit `build.sh` — it takes the version as an argument.

## Product flavors (dimension `version`)

| Flavor | Notes |
|--------|-------|
| `free` (default) | Online: OkHttp, Coil, built-in Tor (Arti, `src/tor`), relay connectivity. Owns `INTERNET`/network permissions. |
| `offline` | No network stack. `app/src/offline/AndroidManifest.xml` **removes** `INTERNET`/`CHANGE_NETWORK_STATE`/`ACCESS_NETWORK_STATE` with `tools:node="remove"`. |
| `benchmark` | Mirrors `free` network deps; `applicationIdSuffix=.benchmark`, `versionNameSuffix=-BENCHMARK`; CI builds a signed release per push for side-by-side install. |

- Guard any network-only code with `BuildFlavorChecker.isOfflineFlavor()`. There is also `BuildConfig.IS_FDROID_BUILD` (false by default; the F-Droid release workflow flips it true and strips `REQUEST_INSTALL_PACKAGES` via `sed`) — use it to disable self-update / Zapstore.
- **Offline permissions gate:** `check-offline-permissions.yml` runs `./gradlew processOfflineDebugManifest` and greps the merged manifest for the three network permissions. Any new dependency that leaks `INTERNET` etc. will fail this check — verify the offline merged manifest when adding network-capable deps.

## Modules

- `:app` — the Android app (everything in [Architecture](#architecture-android-app) refers to it).
- `:desktop` — Compose for Desktop (JVM) NIP-46 signer for Windows/macOS/Linux; standalone port that mirrors the Android permission model against `quartz-jvm` (no NIP-55). `./gradlew :desktop:run` launches a debug build (`core/BuildVariant.kt`: separate data dir, keyring entry, socket and "Amber Debug" name, so it never touches a release install).
  - **Bunker engine:** its core mirrors `NotificationSubscription`/`EventNotificationConsumer`/`BunkerRequestUtils` in `desktop/.../core/BunkerEngine.kt` — behavior changes to the Android bunker flow should be ported there too.
  - **Keys:** AES-encrypted via a PKCS12 Java KeyStore (`DesktopKeyStore`) whose password lives in the OS credential store (Keychain / Credential Manager / Secret Service via `java-keyring`, file fallback — see `KeystorePassword.resolve`). An opt-in `PassphraseLock` instead wraps the master key with Argon2id and adds a startup/auto-lock gate that evicts key material and gates the bunker engine — and, while set, encrypts the per-account database (apps/permissions/history/logs) at rest via `writeSecure`/`readSecure`. State is JSON files per account (no Room).
  - **Local socket signer:** an always-on NIP-5F local socket signer (`core/LocalSigner.kt` + `LocalSignerProtocol.kt`, `~/.local/share/nostr/signer.sock`) feeds CLI tools through the same `BunkerEngine` permission/approval pipeline as NIP-46: socket apps are `AppRecord`s with `transport = "socket"` and no relays, and their pending requests carry a `responder` that answers over the socket instead of a kind-24133 event. Protocol and internals: `desktop/LOCAL_SIGNER.md`.
  - **Notifications:** they go through the OS-native channel (`core/Notifier.kt`: freedesktop `notify-send`/`gdbus` on Linux incl. Wayland/Hyprland; AWT tray notification on Windows and macOS — never `osascript` on macOS, it posts as Script Editor, which is never authorized, and is silently dropped), decoupled from the tray. Request notifications carry Approve / Reject (Reject only for connect): on Linux daemons that draw action buttons they are posted over the session D-Bus with dbus-java (`core/DbusNotifications.kt` — the daemon sends `ActionInvoked` only to the posting connection, and notify-send 0.8.8 drops the buttons on GNOME 50); on Windows, macOS and button-less Linux daemons (quickshell, mako, dunst) Amber shows its own native-looking popup (`ui/RequestNotificationPopup.kt`). Request notifications are skipped while the main window is focused (the card is already in view), and focusing it closes a showing popup. On Linux the popup is a POPUP `JWindow` (override-redirect on X11), not a Compose `Window`/JFrame; AWT's peer still types it NORMAL on XWayland, which Hyprland focuses on map, so `markAsX11Popup` sets the peer's `windowType` by reflection (needs the `--add-opens=java.desktop/sun.awt.X11` JVM arg) — never let the popup take keyboard focus. The packaged runtime needs the `jdk.security.auth` module for dbus-java's bus login, which also enables the Secret Service keyring path.
  - **Tray:** the dorkbox SystemTray library on Linux (`NativeTray.kt`) publishes a StatusNotifierItem/AppIndicator that shows on Wayland compositors; Windows/macOS use the AWT/Compose `Tray`. `AMBER_DISABLE_TRAY=1` skips the native tray.
  - See `desktop/README.md`.

## Architecture (Android app)

### Request ingestion — three paths

External apps and relays reach the signer through three distinct paths:

1. **`nostrsigner://` / `nostrconnect://` Intent** → `SignerActivity` → parsed by `IntentUtils` → shown as an approval bottom sheet
2. **ContentProvider IPC** → `SignerProvider` → synchronous signing via `runBlocking`
3. **NIP-46 relay events** (kind 24133) → `NotificationSubscription` → `EventNotificationConsumer` → `BunkerRequestUtils`

All three paths converge on `Account.sign()` / encrypt/decrypt methods backed by `NostrSignerInternal`.

### Global state — `Amber.kt`

`Amber` is the Application class and acts as the DI container. Key singletons it owns:

- `applicationIOScope` — `CoroutineScope(Dispatchers.IO + SupervisorJob() + exceptionHandler)`, used for all background work
- `client: NostrClient` — the Quartz Nostr relay client
- `notificationSubscription` — keeps the NIP-46 filter alive in the background
- `profileSubscription` — per-account, throttled one-shot metadata fetch; first fetches the user's NIP-65 relay list (kind 10002) and saves it locally, then fetches the metadata (kind 0) from the default profile relays plus the saved user relays; started/stopped by the composables that display each account via `ProfileSubscriptionEffect` (not app-wide)
- `isStartingAppState: MutableStateFlow<Boolean>` — set to `true` during `runMigrations()`; code that must wait for startup calls `isStartingAppState.first { !it }`
- `settings.killSwitch` — when true, all relays are disconnected; checked before every relay operation

### Per-account isolation

Every `npub` gets its own:
- `SharedPreferences` file (`prefs_${npub}`)
- `AppDatabase` (`amber_db_${npub}`) — apps + permissions
- `LogDatabase` — operation logs
- `HistoryDatabase` — request history

All databases are lazy-loaded and cached in `ConcurrentHashMap`s in `Amber`. Account data (including decrypted keys) is loaded via `LocalPreferences.loadFromEncryptedStorage()` and cached in `LargeCache`.

### Permission system

Permissions are stored in `ApplicationEntity` + `ApplicationPermissionsEntity` (Room). Each permission entry has:
- `rememberType` — auto-accept, auto-reject, or always-ask
- `acceptUntil` / `rejectUntil` — time-bound grants
- `kind` — event-kind-specific rules

Before showing the approval UI, all three ingestion paths query the database; if a matching auto-accept rule exists, signing proceeds silently.

### Biometric / PIN lock is UI-only

The biometric/PIN prompt (`useAuth` / `usePin` in `AmberSettings`, configured in `SecurityScreen`) is **only an app-launch UI gate**, not a signing gate. It is rendered by `BiometricAuthScreen`, which is invoked exclusively from the two UI entry points — `MainActivity` and `SignerActivity` — to unlock the app's screens before any approval bottom sheet is shown.

It does **not** protect the signing operations themselves:

- **ContentProvider IPC** (`SignerProvider`) and **NIP-46 relay events** (`EventNotificationConsumer` → `BunkerRequestUtils`) never touch `BiometricAuthScreen`, `Biometrics`, `useAuth`, or `usePin`. They sign in the background based purely on the permission database.
- When an auto-accept rule matches, all three paths sign silently **without** triggering the biometric/PIN prompt — including `nostrsigner://` intents, which auto-finish before the UI is interacted with.

In other words, the lock controls who can open and navigate the app UI; it does not stand between a request and `Account.sign()`. Authorization for automatic signing is governed solely by the permission system (`rememberType` / `acceptUntil` / `kind`). Do not wire signing through the biometric prompt.

### The `requireUnlockedDevice` opt-in is a signing gate

Separate from the UI-only lock above, the user can enable **`requireUnlockedDevice`** (SecurityScreen). When enabled, every `Account` private-key operation (`sign`, `signSync`, `nip04*`/`nip44*`, `decrypt`, `signPsbt`, `getNsec`, `seedWords`, `nip49Encrypt`, and the backup derived signer) calls `DeviceUnlockPolicy.requireAccess` first and throws `SecurityException` while the device is locked. The flag is read synchronously from SharedPreferences (`LocalPreferences.getRequireUnlockedDevice`), so the gate is correct even before the async settings load and in the offline flavor.

Consequences to keep in mind when touching these paths:

- Background consumers of gated operations must handle the refusal explicitly: the relay AUTH coordinator isolates per-account failures, `BackupApplicationsWorker` returns `Result.retry()` on the lock refusal, and lock/settings refusals are rethrown (not swallowed) by `validateNip44v3Request` and `ApplicationBackup.decryptContent` so clients and callers see the real condition instead of a decrypt/corruption error.
- NIP-46 requests are checked before they are opened: while locked, `EventNotificationConsumer` answers clients that have their own connection key with a "locked" error (using only that key), and leaves requests from legacy clients, which are encrypted to the account key, unopened and unrecorded so a re-delivery after unlock is processed.
- This gate is independent of the biometric/PIN UI lock: with `requireUnlockedDevice=false` (the default), background signing behaves exactly as described above.

### Key files

| File | Purpose |
|------|---------|
| `Amber.kt` | Application singleton, Nostr client, relay connectivity |
| `LocalPreferences.kt` | Account/settings persistence, encrypted key storage |
| `IntentUtils.kt` | Parses `nostrsigner://` URIs, creates `IntentData` objects |
| `SignerProvider.kt` | ContentProvider IPC signing |
| `BunkerRequestUtils.kt` | NIP-46 protocol handling, relay responses |
| `AccountStateViewModel.kt` | Auth state, account switching, toast notifications |
| `ConnectivityService.kt` | Foreground service, network monitoring, relay reconnection |
| `BiometricAuthScreen.kt` | UI-only app-launch lock (biometric/PIN); not a signing gate |
| `Biometrics.kt` | Wraps `BiometricPrompt` / keyguard credential prompt |
| `SecurityScreen.kt` | Toggles `useAuth` / `usePin` and the re-prompt interval |

## Conventions

- ktlint `android_studio` code style (`.editorconfig`); star imports effectively disabled; trailing commas allowed; `@Composable` functions exempt from the function-naming rule. Run `ktlintFormat` rather than hand-formatting.
- Translations live in `app/src/main/res/values-<locale>/strings.xml`; `MissingTranslation` lint is intentionally disabled and the shipped locales are pinned by `androidResources.localeFilters` in `app/build.gradle.kts`.
- **Strings must be translated in the same change.** Whenever you add or change a string in `app/src/main/res/values/strings.xml` — including `event_kind_*` labels for new Nostr kinds — add the real translation to **every** `app/src/main/res/values-*/strings.xml` locale file (`ls app/src/main/res | grep '^values-'`, ignoring `values-night`) before reporting the work as done. Never stop at English and never copy English into a locale file — write the actual translation, matching the existing tone/register of that file. Because `MissingTranslation` lint is disabled, a passing build does **not** mean the translations are present. Escape apostrophes as `\'` in languages that use them (French, Italian, Turkish, …), exactly like neighboring entries.
- Git hooks (`git-hooks/pre-commit`, `pre-push`) are auto-installed by the root `build.gradle.kts` `installGitHook` task wired into `:app` `preBuild` — no manual setup needed, but do not rely on them as a substitute for running checks directly.

## Codex Web / cloud setup

Use the committed scripts for cloud environments:
- Setup: `bash .codex/setup.sh` — installs/verifies Java 21, bootstraps Android cmdline SDK, installs API 36 / build-tools 36.0.0, accepts licenses, and prewarms Gradle for `free` debug unit-test sources.
- Maintenance: `bash .codex/maintenance.sh` — refreshes Gradle metadata in cached containers.

## Reproducibility

`Dockerfile` + `apkdiff.py` verify reproducible builds: `docker build -t amber-repro --build-arg VERSION=vX.Y.Z --build-arg APK_TYPE=free-arm64-v8a .` then `docker run --rm amber-repro` (expect `APKs match!`).

## Collaboration

For repository collaboration, use ngit and follow `.agents/skills/ngit/SKILL.md` (mirrored at `.claude/skills/ngit/SKILL.md`).
