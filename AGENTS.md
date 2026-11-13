# Repository instructions for coding agents

These instructions apply to every coding agent working in this repository (Codex, OpenCode, Claude Code, …). `CLAUDE.md` only points here.

Amber is a Nostr event signer (NIP-46 / NIP-55), package `com.greenart7c3.nostrsigner`. `settings.gradle.kts` includes five product modules — `:app` (Android), `:desktop`, `:cli` (a NIP-46 client for scripts and agents), `:common`, the logic the apps and the CLI share, and `:commonsui`, the string resources the apps share (see [Modules](#modules)) — plus two build-time tool modules, `:unused-code` and `:architecture-tests`.

## Toolchain

- JDK 21 required (source/target compat 21). CI and `.codex/setup.sh` use Temurin 21.
- `compileSdk = 37`, `minSdk = 26`, R8 full mode enabled, Gradle parallel + configuration-cache.
- Room schemas are exported to `app/schemas` via KSP (`room.schemaLocation`).
- Dependency verification is on: `gradle/verification-metadata.xml` pins the SHA-256 of every dependency and plugin artifact, and the build fails on any artifact that is missing or doesn't match. After adding or bumping a dependency or plugin, regenerate it with `./gradlew --write-verification-metadata sha256 help <the tasks that use it>` (e.g. `assembleDebug test lint` or `:desktop:test :desktop:createDistributable`) and review the diff. The per-OS Compose Desktop runtimes (`desktop-jvm-*` / `skiko-awt-runtime-*` for windows-x64, macos-arm64, macos-x64) and `aapt2` `osx`/`windows` builds only resolve on their own OS, so bump their entries too, or desktop CI on Windows/macOS breaks.

## Build and validation commands

- `./gradlew ktlintCheck` — Kotlin style check (pre-commit hook runs this).
- `./gradlew ktlintFormat` — auto-fix formatting issues.
- `./gradlew detektAll` — unused imports, parameters, variables and private declarations, plus the size/complexity rules and the Compose rules (`config/detekt/detekt.yml`, `config/detekt/compose.yml`), in the app (every flavor, unit and instrumented tests), desktop, cli, common, commonsui and the tool modules, with detekt's type resolution (pre-commit hook runs this). Every rule not listed in those files is off; suppress a deliberate one at its site with `@Suppress("UnusedParameter")` etc. Existing violations of the complexity and Compose rules are grandfathered in each module's `detekt-baseline.xml`: **never add an entry by hand** — split the function or class instead. `./gradlew detektBaselineAll` regenerates the files from the current findings (so fixed code drops out); the per-variant findings are merged into one file per module. It also runs `./gradlew unusedCode` (`tools/unused-code`), which detekt cannot do because it sees one file at a time: across every module's sources it fails on a non-private function, property, class or typealias whose name nothing references (Kotlin sources including tests, XML, ProGuard rules, Gradle scripts and `tools/arti/src` for JNI callbacks), and on a catch parameter the block never reads (name it `_`). Overrides, operators, `main`, enum entries, constructor properties, `@…Preview` and test functions, and Room/serializer-read properties are never reported; mark code that only something outside the sources uses (reflection, the NDK) with `@Suppress("unused")`.
- Kotlin compiler warnings are errors (`allWarningsAsErrors` in the root `build.gradle.kts`): deprecated APIs, needless `?.`/`!!`/`?:`, unused expressions, unchecked casts, missing opt-ins. Fix them; when one is deliberate (e.g. the OkHttp `body` null checks the offline flavor's OkHttp 4 needs), suppress it at the statement.
- `./gradlew lint` — Android Lint on the default variant; `warningsAsErrors` is on, so any new warning fails the build (pre-commit and pre-push hooks run this). Version-freshness checks (`GradleDependency` etc.) are disabled in `app/build.gradle.kts`; per-path exemptions live in `app/lint.xml`.
- `./gradlew test --no-daemon` — JVM unit tests for all variants, including `:architecture-tests` (pre-push hook runs `./gradlew test lint`).
- `./gradlew :architecture-tests:test` — the architecture rules (Konsist): `:common`/`:commonsui` stay platform free, `ui/` does not import Room, `Amber.instance` cannot spread to a new file, `service/`/`database/` do not depend on Compose UI, the desktop `core/` does not import `ui/`. The allow-lists in `tools/architecture-tests/src/test/kotlin/.../ArchitectureTest.kt` may only shrink: the test fails when a listed file stops violating the rule, so delete the entry when you fix it. The target layering and the refactor roadmap are in `docs/architecture.md`.
- `./gradlew koverXmlReport koverHtmlReport` — one merged coverage report for app, cli, common, commonsui and desktop in `build/reports/kover` (generated code excluded; not part of `check`). `:common:koverVerify` enforces its minimum line coverage; raise the bound when it improves, never lower it. CI runs both after the tests and uploads the HTML report.
- Run one test: `./gradlew :app:testFreeDebugUnitTest --tests "com.greenart7c3.nostrsigner.SomeTest"` (or `--tests "*SomeTest.method"`).
- `./gradlew assembleDebug --no-daemon` — debug APK for both product flavors.
- `./gradlew assembleRelease --no-daemon` — release APK; signing only activates when env `SIGN_RELEASE` is set **and** `keystore.properties` exists. CI otherwise `touch`es an empty one.
- `./build.sh <version> <appName>` — builds free + offline release APKs/AABs into `~/release/` and calls `generate_manifest.sh`.
- Desktop: `./gradlew :desktop:run` (debug build), `:desktop:test`, `:desktop:packageDistributionForCurrentOs`.

Required order when pushing: `ktlintCheck` → `detektAll` → `lint` → `test` → build (mirrored by the hooks, but run them directly; do not rely on hooks).

## Releases / version bumps

When asked to "bump the version" / cut a release, do **all** the following in a single change — never omit the verification block:

1. Bump `amberVersionCode` (+1) and `amberVersionName` (new `X.Y.Z`) in `gradle/libs.versions.toml`. These are the only places the version lives: the Android `defaultConfig` and the desktop app (`:desktop`, installers and the runtime `-Damber.version`) both read them, and `desktop-release.yml` fails if the tag does not match `amberVersionName`. Never hard-code a version in `app/build.gradle.kts` or give the desktop its own.
2. Prepend a new `## Amber X.Y.Z` block at the top of `CHANGELOG.md`, summarizing the non-merge commits since the previous `vX.Y.Z` tag (`git log vX.Y.Z..HEAD --no-merges`) as user-facing bullets, and build the bullets exactly the way previous "Bump version to …" commits did — read one first (`git log --oneline -- CHANGELOG.md`, then `git show <commit> -- CHANGELOG.md`):
   - **One bullet per user-facing change**, ordered features → behavior changes → fixes → security hardening → dependency updates. Never merge unrelated changes into one bullet, and never squeeze several changes into a single mega-bullet; only join closely related aspects of the same change with ", and".
   - Start each bullet with a **present-tense verb**, name the concrete screen, setting or feature, and state the effect ("Show which account each batched request signs as", "Fix '+' in app names that clients form-encode in bunker URIs"). Prefix new features with `New: `.
   - Leave out internal-only work (CI, refactors, tests, lint, build tooling) unless users can observe it; collapse dependency bumps into one closing line ("Update Quartz to 2.3.0, OkHttp and the rest of the dependency stack").
   End the block with the standard "Download it with …" line (update the `releases/tag/vX.Y.Z` URL) and the "If you like my work …" donation line.
   **`CHANGELOG.md` must contain only the latest release**: delete the previous release's block from it (the notes already live in `docs/changelogs/`) and keep the `## Older versions` footer at the bottom.
   Also add the full notes as `docs/changelogs/<X.Y.Z>.md` — same bullets and download/donation/verifying text, but under an `# Amber X.Y.Z` H1 and without the footer — and insert a row `| X.Y.Z | [<X.Y.Z>.md](<X.Y.Z>.md) |` at the top of the table in `docs/changelogs/README.md` (most recent first, padded to the existing columns; pre-releases get an entry too).
3. **Always include the `## Verifying the release` block immediately after the donation line** (before the next `## Amber` heading). Copy it verbatim from the previous release entry and only change the `manifest-vX.Y.Z.txt` / `manifest-vX.Y.Z.txt.sig` filenames to the new version. Do **not** change the `gpg --recv-keys` key id, the `gpg: Signature made Fri 13 Sep 2024 …` block, or the surrounding prose — those are key-specific, not release-specific. This block must be part of the version-bump commit, not a follow-up.
4. Do not commit unless explicitly asked (per the global rule). Do not edit `build.sh` — it takes the version as an argument.

## Product flavors (dimension `version`)

| Flavor           | Notes                                                                                                                                                        |
| ---------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `free` (default) | Online: OkHttp, Coil, built-in Tor (Arti, `src/tor`), relay connectivity. Owns `INTERNET`/network permissions.                                               |
| `offline`        | No network stack. `app/src/offline/AndroidManifest.xml` **removes** `INTERNET`/`CHANGE_NETWORK_STATE`/`ACCESS_NETWORK_STATE` with `tools:node="remove"`; launcher name "Amber Offline". Keeps the online applicationId on purpose (as every offline APK released before 7.0.0 did), so installs from the releases page and Obtainium keep updating in place. |
| `offlinezapstore` | The same offline app (shares the `offline` manifest and sources) with `applicationIdSuffix=.offline`, because Zapstore identifies an app by its applicationId and the "Amber Offline" listing (`zapstore-offline.yaml`) cannot publish APKs carrying the online one. CI builds it and keeps only the arm64-v8a APK (`amber-offlinezapstore-arm64-v8a-<tag>.apk`), which `zapstore-offline.yaml` matches. |
| `benchmark`      | Mirrors `free` network deps; `applicationIdSuffix=.benchmark`, `versionNameSuffix=-BENCHMARK`; CI builds a signed release per push for side-by-side install. |

- Guard any network-only code with `BuildFlavorChecker.isOfflineFlavor()`. There is also `BuildConfig.IS_FDROID_BUILD` (false by default; the F-Droid release workflow flips it true and strips `REQUEST_INSTALL_PACKAGES` via `sed`) — read it through `BuildFlavorChecker.isFdroidBuild()` to disable self-update / Zapstore, never as the constant, which the IDE would simplify away.
- **Offline permissions gate:** `check-offline-permissions.yml` runs `./gradlew processOfflineDebugManifest processOfflineZapstoreDebugManifest` and greps both merged manifests for the three network permissions and the WorkManager components. Any new dependency that leaks `INTERNET` etc. will fail this check — verify the offline merged manifest when adding network-capable deps. Both offline flavors are the offline app: `BuildFlavorChecker.isOfflineFlavor()` is true for `offline` and `offlinezapstore`, so gate network-only code through it, never `BuildConfig.FLAVOR == "offline"`.

## Modules

- `:app` — the Android app (everything in [Architecture](#architecture-android-app) refers to it).
- `:common` — plain Kotlin/JVM library both apps depend on (package `com.greenart7c3.nostrsigner.common`) for logic that must behave the same on Android and desktop: `SignerType`/`EncryptionType`, the request policy (`connectsThroughOtherKey`, `hasAmbiguousRelayTags`, `sanitizeAppName`), the NIP-46 request basics (`nip46SignerType`, `nip44v3Kind`/`nip44v3Scope`, `isRepeatedConnect`, `connectSecretRejection` in `Nip46Requests.kt`), the remembered-permission decision (`activeDecision`, `rememberedDecision`) and the order grants are looked up in (`permissionLookupOrder`), per-connection keys, crash-report redaction (`CrashReports`), `Nip44v3`, `PsbtDecoder`/`PsbtSighashPolicy`, `RelayHealthTracker`, `SenderRateLimiter`, `TorSocksAuthenticator` and `LocalRelayServer` (the local bunker relay on `ws://127.0.0.1`: the desktop `LocalBunkerRelay` and the Android `LocalBunkerRelayServer` are thin wrappers that supply its logger and approved senders; plain threads, since Android has no virtual threads). Put shared logic here instead of copying it into both apps; each app keeps only its storage, UI and platform glue around it. It has no Android or desktop APIs and compiles against Quartz with `compileOnly(quartz-jvm)`: each app ships its own Quartz build (`quartz-android` / `quartz-jvm`), so use only Quartz API both builds have. Its tests run with `./gradlew :common:test` (also part of `./gradlew test`).
- `:commonsui` — what both apps show, from one source: Kotlin Multiplatform (Android + JVM targets, `com.android.kotlin.multiplatform.library`) with **every string** as Compose Multiplatform resources in `src/commonMain/composeResources/values*/strings.xml`, and the `Permission` model (JSON form, `kindsByNip`/`supportedKindNumbers`, and `Permission.description()`, the one table mapping a permission type and event kind to its string, so a new `event_kind_*` label is added there once). Strings exist only here: no Android `R.string` copy (the APK carries them once, as assets) — except the launcher label `app_name*` in `app/src/main/res/values/strings.xml`, which the manifest needs as an Android resource. Both apps read them the same way through the generated `Res.string.<name>` accessors (package `com.greenart7c3.nostrsigner.commonsui`, one import per accessor): `stringResource(Res.string.x)` (`org.jetbrains.compose.resources`, not `androidx.compose.ui.res`) in composables, suspend `getString` in coroutines, `getStringBlocking` (`commonsui/Strings.kt`) in plain code; permissions with `Permission.localized()` / `localizedBlocking()`. Outside composition the language is the JVM default locale: Android sets it with the in-app language in `AppLanguage.apply` (AppCompat alone updates it only on Android 13+), the desktop in `Strings.setLanguage`. Folder names are what `Locale.language`/`country` report: no region for a bare language (`values-it`, not `-rIT`) and Indonesian is `values-id`. Its tests run with `./gradlew :commonsui:jvmTest`; tests that read strings need Skiko, so they live in `:desktop` (`StringsTest`) and the app's instrumented `SharedStringsDeviceTest`.
- `:architecture-tests` — Konsist tests (`tools/architecture-tests`) that enforce the layering above and run under `./gradlew test`. Its tests read the repository from disk, so Gradle cannot track their inputs (the task never goes up to date). See `docs/architecture.md`.
- `:unused-code` — the cross-module unused-code checker in `tools/unused-code`, run by `detektAll` as the `unusedCode` task.
- `:cli` — the `amber` command line client (package `com.greenart7c3.nostrsigner.cli`), for scripts, apps and AI agents that need a NIP-46 signature without speaking the protocol: pairing (`login` with a `bunker://` URI, `connect` with a `nostrconnect://` URI/QR), `sign-event`, `get-public-key`, NIP-04/NIP-44 encrypt/decrypt, `ping`, `status`, `logout`, and an agent-oriented `help` plus stable exit codes (0 ok, 2 usage, 3 not paired, 4 refused, 5 timeout, 6 unreachable). It holds **no accounts and no user keys**: only throwaway per-connection client keys, saved in `~/.config/amber-cli` (`%APPDATA%` on Windows, `AMBER_CLI_HOME` overrides) in `connections.json` encrypted with AES-256-GCM, the key living in the OS credential store (`SecretStore`: java-keyring, with a 0600 key-file fallback where none runs); never create a keyring entry from tests, pass `FileSecretStore` through `CliEnvironment` instead. `RelayPool`/`Nip46Client` are its own minimal relay client and protocol layer (`com.greenart7c3.nostrsigner.common` for `LocalRelayServer` constants, `decodeQueryParamValue` and `RELAY_AUTH_KIND`); default relays mirror `defaultAppRelays` in the app and `DesktopSettings.defaultRelays`. Its docs are `cli/README.md`; `./gradlew :cli:test` runs the unit and end-to-end tests (a local relay plus a fake signer, no network), `:cli:packageCli` builds the self-contained `jpackage` archive that `.github/workflows/cli.yml` ships on every OS.
- `:desktop` — Compose for Desktop (JVM) NIP-46 signer for Windows/macOS/Linux; standalone port that mirrors the Android permission model against `quartz-jvm` (no NIP-55). `./gradlew :desktop:run` launches a debug build (`core/BuildVariant.kt`: separate data dir, keyring entry, socket and "Amber Debug" name, so it never touches a release install).
  - **Bunker engine:** its core mirrors `NotificationSubscription`/`EventNotificationConsumer`/`BunkerRequestUtils` in `desktop/.../core/BunkerEngine.kt` — behavior changes to the Android bunker flow should be ported there too.
  - **Keys:** AES-encrypted via a PKCS12 Java KeyStore (`DesktopKeyStore`) whose password lives in the OS credential store (Keychain / Credential Manager / Secret Service via `java-keyring`, file fallback — see `KeystorePassword.resolve`). That keystore only exists before first run: the passphrase is mandatory (`PassphraseLock`, setup screen before anything loads), and setting it wraps the master key with Argon2id — combined via HKDF with a random secret in the OS credential store when one works (`osSecretStore`, a second factor) — and deletes the keystore. It adds a startup/auto-lock gate that evicts key material and gates the bunker engine, and encrypts the per-account database (apps/permissions/history/logs) at rest via `writeSecure`/`readSecure`. Every master-key ciphertext is context-bound (GCM AAD, `DesktopKeyStore.encryptString(…, context)`), and while a passphrase is set unbound or plaintext data is rejected. `applications.json` is also rollback-protected: its header carries a generation (`AMBERENC3:<n>:`, bound into the AAD) whose latest value is kept in the OS credential store (`StoreGenerations`), and an older copy is refused. The desktop app has no released predecessor, so there are no migrations: only the current formats are read, and anything else is treated as tampered. Write files with `AppDirs.writeAtomically`; unreadable files are quarantined, never overwritten. Lock wipes key material (`WipeableAesKey`, `DesktopAccount.wipe`, Quartz NIP-04/44 caches): keep secrets in `ByteArray`s (`decryptBytes`), not Strings, and don't hand out an account outside `AmberDesktop.account` (only cached accounts get wiped). State is JSON files per account (no Room).
  - **Local socket signer:** an always-on NIP-5F local socket signer (`core/LocalSigner.kt` + `LocalSignerProtocol.kt`, `~/.local/share/nostr/signer.sock`) feeds CLI tools through the same `BunkerEngine` permission/approval pipeline as NIP-46: socket apps are `AppRecord`s with `transport = "socket"` and no relays, and their pending requests carry a `responder` that answers over the socket instead of a kind-24133 event. Protocol and internals: `desktop/LOCAL_SIGNER.md`.
  - **Notifications:** they go through the OS-native channel (`core/Notifier.kt`: freedesktop `notify-send`/`gdbus` on Linux incl. Wayland/Hyprland; AWT tray notification on Windows and macOS — never `osascript` on macOS, it posts as Script Editor, which is never authorized, and is silently dropped), decoupled from the tray. Request notifications carry Approve / Reject (Reject only for connect): on Linux daemons that draw action buttons they are posted over the session D-Bus with dbus-java (`core/DbusNotifications.kt` — the daemon sends `ActionInvoked` only to the posting connection, and notify-send 0.8.8 drops the buttons on GNOME 50); on Windows as a WinRT toast (`core/WindowsToasts.kt`, JNA: Amber registers its own AppUserModelID under HKCU and gets clicks through the toast's in-process `Activated` event — no COM activator or URL scheme, so nothing outside the process can answer a request); on macOS through `UNUserNotificationCenter` (`core/MacNotifications.kt`, JNA over the Objective-C runtime, buttons as notification categories, shown under the banner's "Options"). macOS only accepts that from a code-signed bundle (ad-hoc is enough; an unsigned one makes `usernotificationsd` log "requestAuthorization not allowed"): jpackage signs ad hoc on Apple silicon only, so `MacAdHocSignature` in `desktop/build.gradle.kts` signs the Intel app in `createDistributable`, which the DMG is made from; `:desktop:run` has no bundle and is not authorized. Where the native channel cannot show buttons — button-less Linux daemons (mako, dunst, other quickshell configs), a macOS app the user has not allowed to notify, or a post that failed — Amber shows its own native-looking popup (`ui/RequestNotificationPopup.kt`). Request notifications are skipped while the main window is focused (the card is already in view), and focusing it closes a showing popup. Answering a request — on its card, in the tray window, or from the notification itself — withdraws its notification (`Notifier.Notification.close`: `CloseNotification` on the posting D-Bus connection, or by id after `notify-send --print-id` in the fallback; `Hide` on Windows; `removeDeliveredNotifications` on macOS), and Amber's own popup closes itself the same way once its request leaves the queue. Omarchy's shell keeps a card its sender closed (only the buttons go), so there the card is also dismissed through the shell's IPC by title (`Notifier.omarchyDismissCommands`; a substring match, so titles under 3 characters are left alone). Notifications still open when Amber quits are closed by a shutdown hook, and macOS clears what a killed run left behind at the next start. On Linux the popup is a POPUP `JWindow` (override-redirect on X11), not a Compose `Window`/JFrame; AWT's peer still types it NORMAL on XWayland, which Hyprland focuses on map, so `markAsX11Popup` sets the peer's `windowType` by reflection (needs the `--add-opens=java.desktop/sun.awt.X11` JVM arg) — never let the popup take keyboard focus. The packaged runtime needs the `jdk.security.auth` module for dbus-java's bus login, which also enables the Secret Service keyring path, and `jdk.net` for the local socket signer's peer check. jlink only bundles the modules listed in `nativeDistributions { modules(...) }` plus what the plugin detects, and `:desktop:run` uses the full JDK, so a missing module only fails in the packaged app: check new JDK-module imports with `jdeps` on `lib/app/*.jar` of `createDistributable`. Focusing the main window closes Amber's own popup, except while the pointer is on it: on macOS clicking the popup activates the app and focuses the main window before the click lands, which used to swallow Approve.
  - **Crash reports:** `core/CrashReporter.kt` mirrors the Android `UnexpectedCrashSaver`/`ReportAssembler`/`ReportSender` (redacted `crash.trace`, NIP-17 DM to `DEVELOPER_HEX_KEY` on user consent, `ui/CrashReportDialog.kt` after unlock); the version comes from the launcher's `-Damber.version` (`BuildVariant.version`), i.e. `amberVersionName`.
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

NIP-44 v3 grants are keyed by the context **kind** only (a kind-K rule, or a kind-less "all kinds" rule); the context scope string (`params[2]`) is shown in the approval prompt but is not stored, so a remembered kind-K grant covers every scope. Desktop (`BunkerEngine`, `LocalSigner`) follows the same model; keep them in step if that ever changes.

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

| File                       | Purpose                                                     |
| -------------------------- | ----------------------------------------------------------- |
| `Amber.kt`                 | Application singleton, Nostr client, relay connectivity     |
| `LocalPreferences.kt`      | Account/settings persistence, encrypted key storage         |
| `IntentUtils.kt`           | Parses `nostrsigner://` URIs, creates `IntentData` objects  |
| `SignerProvider.kt`        | ContentProvider IPC signing                                 |
| `BunkerRequestUtils.kt`    | NIP-46 protocol handling, relay responses                   |
| `AccountStateViewModel.kt` | Auth state, account switching, toast notifications          |
| `ConnectivityService.kt`   | Foreground service, network monitoring, relay reconnection  |
| `BiometricAuthScreen.kt`   | UI-only app-launch lock (biometric/PIN); not a signing gate |
| `Biometrics.kt`            | Wraps `BiometricPrompt` / keyguard credential prompt        |
| `SecurityScreen.kt`        | Toggles `useAuth` / `usePin` and the re-prompt interval     |

## Conventions

- ktlint `android_studio` code style (`.editorconfig`); star imports effectively disabled; trailing commas allowed; `@Composable` functions exempt from the function-naming rule. Run `ktlintFormat` rather than hand-formatting.
- Translations live in `commonsui/src/commonMain/composeResources/values-<locale>/strings.xml` (Compose resources, shared by the Android and desktop apps). Android lint does not check them (they are not Android resources), so nothing flags a missing or broken translation. The text is **plain**: no Android string syntax — write apostrophes, quotes, `%` and line breaks literally (no `\'`, `\n`, `%%` or `"…"` quoting), and use positional `%1$s` / `%1$d` for arguments (Compose resources substitute nothing else). XML entities (`&amp;`, `&lt;`) are still needed.
- **Strings must be translated in the same change.** Whenever you add or change a string in `commonsui/src/commonMain/composeResources/values/strings.xml` — including `event_kind_*` labels for new Nostr kinds — add the real translation to **every** `commonsui/src/commonMain/composeResources/values-*/strings.xml` locale file (`ls commonsui/src/commonMain/composeResources`) before reporting the work as done. Never stop at English and never copy English into a locale file — write the actual translation, matching the existing tone/register of that file. A passing build does **not** mean the translations are present.
- Git hooks (`git-hooks/pre-commit`, `pre-push`) are auto-installed by the root `build.gradle.kts` `installGitHook` task wired into `:app` `preBuild` — no manual setup needed, but do not rely on them as a substitute for running checks directly.

## Codex Web / cloud setup

Use the committed scripts for cloud environments:
- Setup: `bash .codex/setup.sh` — installs/verifies Java 21, bootstraps Android cmdline SDK, installs API 36 / build-tools 36.0.0, accepts licenses, and prewarms Gradle for `free` debug unit-test sources.
- Maintenance: `bash .codex/maintenance.sh` — refreshes Gradle metadata in cached containers.

## Reproducibility

`Dockerfile` + `apkdiff.py` verify reproducible builds: `docker build -t amber-repro --build-arg VERSION=vX.Y.Z --build-arg APK_TYPE=free-arm64-v8a .` then `docker run --rm amber-repro` (expect `APKs match!`).

Desktop: every download is reproducible by content, checked with `desktopdiff.py` (files plus installer scripts/tables; timestamps and build dates ignored; `--jdk` prints the bundled JDK). `Dockerfile.desktop` rebuilds the Linux `.tar.xz`, AppImage, `.deb` and `.rpm` of a tag and compares them with the release; Windows (`.msi`/`.exe`) and macOS (`.dmg`) are rebuilt on that OS (README has the steps). What keeps it that way: `desktop.yml` makes setup-java download the official Temurin archive (an empty `RUNNER_TOOL_CACHE`), since the app bundles that JDK's runtime and the runner's preinstalled copy differs; `SkikoNatives` in `desktop/build.gradle.kts` ships skiko's native library as plain files instead of the skiko-awt-runtime jar, which Compose 1.12 rewrites with the build time (Compose 1.13 fixes that), and `ZipTimestamps` fixes the entry times of the jars the plugin re-zips on macOS; `.gitattributes` turns off line-ending conversion, which made Windows checkouts bundle CRLF resources. Run `python -m unittest discover -s tests` after changing `desktopdiff.py`.

## Collaboration

For repository collaboration, use ngit and follow `.agents/skills/ngit/SKILL.md` (mirrored at `.claude/skills/ngit/SKILL.md`).
