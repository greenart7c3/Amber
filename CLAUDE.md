# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & development commands

```bash
./gradlew assembleDebug          # debug build
./gradlew assembleRelease        # release build (requires signing keystore)
./gradlew ktlintCheck            # Kotlin style check (also runs on every git commit via pre-commit hook)
./gradlew ktlintFormat           # auto-fix Kotlin style issues
./gradlew lint                   # Android Lint, warnings fail the build (runs on commit and push via hooks)
./gradlew test --no-daemon       # unit tests (also runs on every git push via pre-push hook)
./build.sh                       # builds both offline and free release variants to ~/release/
```

Git hooks are auto-installed via the root `build.gradle.kts` preBuild task — no manual setup needed.

## Build flavors

| Flavor | Purpose |
|--------|---------|
| `free` (default) | Online variant with full networking (OkHttp, Coil, relay connectivity) |
| `offline` | No network stack; use `BuildFlavorChecker.isOfflineFlavor()` to guard network code |

## Architecture

### Request ingestion — three paths

External apps and relays reach the signer through three distinct paths:

1. **`nostrsigner://` Intent** → `SignerActivity` → parsed by `IntentUtils` → shown as bottom sheet
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

In other words, the lock controls who can open and navigate the app UI; it does not stand between a request and `Account.sign()`. Authorization for automatic signing is governed solely by the permission system (`rememberType` / `acceptUntil` / `kind`).

### The `requireUnlockedDevice` opt-in is a signing gate

Separate from the UI-only lock above, the user can enable **`requireUnlockedDevice`** (SecurityScreen). When enabled, every `Account` private-key operation (`sign`, `signSync`, `nip04*`/`nip44*`, `decrypt`, `signPsbt`, `getNsec`, `seedWords`, `nip49Encrypt`, and the backup derived signer) calls `DeviceUnlockPolicy.requireAccess` first and throws `SecurityException` while the device is locked. The flag is read synchronously from SharedPreferences (`LocalPreferences.getRequireUnlockedDevice`), so the gate is correct even before the async settings load and in the offline flavor.

Consequences to keep in mind when touching these paths:

- Background consumers of gated operations must handle the refusal explicitly: the relay AUTH coordinator isolates per-account failures, `BackupApplicationsWorker` returns `Result.retry()` on the lock refusal, and lock/settings refusals are rethrown (not swallowed) by `validateNip44v3Request` and `ApplicationBackup.decryptContent` so clients and callers see the real condition instead of a decrypt/corruption error.
- Opening a legacy NIP-46 request envelope for permission display is treated as transport: `EventNotificationConsumer` falls back to the ungated signer to decrypt the envelope while locked, but the requested operation inside is still gated.
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

- For repository collaboration, use ngit and follow `.claude/skills/ngit/SKILL.md`.
