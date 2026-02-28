# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & development commands

```bash
./gradlew assembleDebug          # debug build
./gradlew assembleRelease        # release build (requires signing keystore)
./gradlew ktlintCheck            # lint check (also runs on every git commit via pre-commit hook)
./gradlew ktlintFormat           # auto-fix lint issues
./gradlew test --no-daemon       # unit tests (also runs on every git push via pre-push hook)
./build.sh                       # builds both offline and free release variants to ~/release/
```

Git hooks are auto-installed via the root `build.gradle` preBuild task — no manual setup needed.

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
- `profileSubscription` — active only in the foreground (paused in background to save battery)
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