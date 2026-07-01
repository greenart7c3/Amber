# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & development commands

```bash
./gradlew assembleDebug          # debug build (Android app, :app module)
./gradlew assembleRelease        # release build (requires signing keystore)
./gradlew ktlintCheck            # lint check (also runs on every git commit via pre-commit hook)
./gradlew ktlintFormat           # auto-fix lint issues
./gradlew test --no-daemon       # unit tests (also runs on every git push via pre-push hook)
./build.sh                       # builds both offline and free release variants to ~/release/

./gradlew :desktop:run                    # run the desktop bunker app locally
./gradlew :desktop:createDistributable    # build a runnable app image (no native installer)
./gradlew :desktop:packageDeb             # Linux .deb (packageMsi/packageDmg need to run on Windows/macOS hosts)
./gradlew :shared:desktopTest             # JVM unit tests for the shared bunker signing engine
```

Git hooks are auto-installed via the root `build.gradle.kts` preBuild task — no manual setup needed.

## Modules

| Module | Purpose |
|--------|---------|
| `:app` | The Android app — all three request-ingestion paths, full UI, Room-backed persistence. Unaffected by the `:shared`/`:desktop` split; still self-contained. |
| `:shared` | Kotlin Multiplatform module (`androidTarget()` + `jvm("desktop")`) holding the portable NIP-46 bunker signing engine used by `:desktop`. See "Desktop bunker app" below. |
| `:desktop` | Compose Multiplatform Desktop app — a focused bunker-only signer for Linux/Windows/Mac, not a port of the full Android UI. |

## Build flavors

| Flavor | Purpose |
|--------|---------|
| `free` (default) | Online variant with full networking (OkHttp, Coil, relay connectivity) |
| `offline` | No network stack; use `BuildFlavorChecker.isOfflineFlavor()` to guard network code |

`:desktop` always has networking (a bunker signer is meaningless offline) and does not have flavors.

## Architecture

### Request ingestion — three paths

External apps and relays reach the signer through three distinct paths:

1. **`nostrsigner://` Intent** → `SignerActivity` → parsed by `IntentUtils` → shown as bottom sheet
2. **ContentProvider IPC** → `SignerProvider` → synchronous signing via `runBlocking`
3. **NIP-46 relay events** (kind 24133) → `NotificationSubscription` → `EventNotificationConsumer` → `BunkerRequestUtils`

All three paths converge on `Account.sign()` / encrypt/decrypt methods backed by `NostrSignerInternal`. This is all `:app` (Android); the desktop app is a separate, fourth entry point — see below.

### Desktop bunker app (`:desktop` + `:shared`)

The desktop app only implements the NIP-46/bunker path (paths 1–2 above are Android-only concepts — no intent scheme or ContentProvider on desktop) and is intentionally **not** a port of the full Android app: single account, no Tor/proxy support yet. It does have its own multi-screen UI (home, connect, connected apps + per-app permission editor, activity log, settings) mirroring Android's look and key bunker-relevant flows — see below.

- `shared/src/commonMain/.../BunkerSigningEngine.kt` — decrypts an incoming kind-24133 event, resolves the requesting app's display name (from NIP-46 `connect` client metadata via `BunkerClientMetadata`, or an optional `appNameLookup` fallback for later requests), checks a `BunkerPermissionStore` for an auto-accept/reject rule (falling back to a `BunkerApprovalPort` prompt), performs the sign/nip04/nip44 operation via `BunkerSigner` (wraps Quartz's `NostrSignerInternal`), and returns the signed response event. This is new code written for the desktop use case — it does **not** replace or get called by `:app`'s `BunkerRequestUtils`/`EventNotificationConsumer`, which keep using their existing Room/Context-coupled implementation directly (rewiring the shipping Android signing path onto shared code was judged higher regression risk than the desktop use case warranted).
- `shared/.../SecureCryptoHelper.kt` is `expect`/`actual`: the desktop `actual` (`desktopMain`) stores an AES-256 master key in the OS keychain via `java-keyring` (Windows Credential Manager / macOS Keychain / Linux Secret Service — requires a running Secret Service provider, e.g. gnome-keyring, on Linux) and AES-GCM-encrypts secrets at rest with it, mirroring the shape of the Android `actual` (Keystore-backed, itself a from-scratch mirror of `:app`'s own `SecureCryptoHelper.kt` — not wired in, kept for parity/future adoption).
- `desktop/src/main/kotlin/.../data/` — `AccountStore` (generate/import the single desktop account, persisted encrypted), `SqliteBunkerPermissionStore`/`SqliteBunkerHistoryLogger`/`RelayStore`/`SettingsStore` (plain JDBC against `org.xerial:sqlite-jdbc`, schema created on first run under `~/.amber-bunker/`, not Room). Permissions and history are queryable per-app (`permissionsFor`/`deletePermission`, `recentHistoryFor`) for the app-detail screen, not just the account-wide `revokeAll`/`connectedApps` used at first.
- `desktop/src/main/kotlin/.../relay/BunkerRelayConnection.kt` — subscribes to kind-24133 events addressed to the account pubkey using Quartz's own `NostrClient` + `BasicOkHttpWebSocket` (both resolve from the multiplatform `com.vitorpamplona.quartz:quartz` coordinate's `quartz-jvm` variant — no vendored crypto or websocket code was needed), publishes engine responses back via `publishAndConfirm`, and exposes `connectedRelays: StateFlow<Set<NormalizedRelayUrl>>` for the Home screen's connection status.
- `desktop/src/main/kotlin/.../ui/AppShell.kt` — the app's shell: a `NavigationRail` + hand-rolled navigation (`ui/nav/Screen.kt` sealed class; no KMP navigation-compose library exists yet) across `HomeScreen`/`ConnectScreen`/`ConnectedAppsScreen`/`AppDetailScreen`/`ActivityScreen`/`SettingsScreen`, plus the `ApprovalDialog` overlay fed by `DesktopApprovalPort`'s pending-request queue. `ui/theme/DesktopTheme.kt` ports Amber's exact warm color scheme/shapes from `app/.../ui/theme/Theme.kt` (Compose Desktop has no reliable `isSystemInDarkTheme()`, so theme mode is a manual Light/Dark/System toggle persisted via `SettingsStore`, System defaulting to Light).

### Global state — `Amber.kt`

`Amber` is the Application class and acts as the DI container. Key singletons it owns:

- `applicationIOScope` — `CoroutineScope(Dispatchers.IO + SupervisorJob() + exceptionHandler)`, used for all background work
- `client: NostrClient` — the Quartz Nostr relay client
- `notificationSubscription` — keeps the NIP-46 filter alive in the background
- `profileSubscription` — per-account, throttled one-shot metadata (kind 0) fetch; started/stopped by the composables that display each account via `ProfileSubscriptionEffect` (not app-wide)
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

In other words, the lock controls who can open and navigate the app UI; it does not stand between a request and `Account.sign()`. Authorization for automatic signing is governed solely by the permission system (`rememberType` / `acceptUntil` / `kind`).

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
| `shared/.../BunkerSigningEngine.kt` | Desktop's NIP-46 request handler (decrypt → permission check → sign/encrypt → respond) |
| `shared/.../SecureCryptoHelper.kt` | `expect`/`actual` at-rest encryption: Android Keystore vs. desktop OS keychain |
| `desktop/.../relay/BunkerRelayConnection.kt` | Desktop's kind-24133 relay subscription + response publishing |
| `desktop/.../ui/AppShell.kt` | Desktop's nav rail + screen dispatch + approval dialog overlay |
| `desktop/.../ui/theme/DesktopTheme.kt` | Desktop theme, ported from `app/.../ui/theme/Theme.kt` |