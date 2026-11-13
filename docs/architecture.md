# Architecture and the clean-code guardrails

This document describes how Amber is put together today, where it is going, and
which checks keep it moving in that direction. The checks are ratchets: they
pass on today's code, new code cannot make things worse, and the debt is listed
in files that may only shrink.

## Today

### Modules

```
:app        Android app: UI (Compose), Room, foreground service, NIP-55 provider
:desktop    Compose Desktop NIP-46 signer, same permission model on JSON files
:cli        `amber` command line NIP-46 client; holds no accounts or user keys
:common     plain Kotlin/JVM logic the apps and the CLI share (no platform APIs)
:commonsui  every user-visible string, as Compose Multiplatform resources
:architecture-tests   Konsist rules for the layering below (test-only module)
:unused-code          cross-module "nothing references this" checker (build-only)
```

`AGENTS.md` describes each module in detail.

### The three ingestion paths

Every request — intent, ContentProvider IPC or NIP-46 relay event — converges on
`Account.sign()` / encrypt / decrypt:

1. `nostrsigner://` / `nostrconnect://` → `SignerActivity` → `IntentUtils` →
   approval bottom sheet
2. ContentProvider IPC → `SignerProvider` → `runBlocking`
3. kind 24133 → `NotificationSubscription` → `EventNotificationConsumer` →
   `BunkerRequestUtils`

Authorization is the permission database (`ApplicationEntity` +
`ApplicationPermissionsEntity`), queried by all three paths before the UI shows
anything. The biometric/PIN prompt is a UI gate only.

### The service locator

`Amber` is the Application class and the dependency container: `client`,
`notificationSubscription`, `profileSubscription`, the per-account databases and
`settings`. `Amber.instance` is called 581 times in 86 production files, so
almost every class is coupled to the Android Application class and cannot be
constructed in a test without the framework. There are two ViewModels.

### Where the pain is

- Six files over 950 lines: `ui/LoginScreen.kt` 1548, the desktop
  `core/BunkerEngine.kt` 1502, `service/IntentUtils.kt` 1222,
  `service/EventNotificationConsumer.kt` 1087, `LocalPreferences.kt` 1016,
  `Amber.kt` 982.
- The Android bunker flow (`BunkerRequestUtils` + `EventNotificationConsumer`)
  and the desktop one (`BunkerEngine`) implement the same permission and
  response logic twice.
- `ui/` reaches Room DAOs directly in 9 files and `Amber.instance` in dozens.

## Target layering

```
ui/            Composables + ViewModels: render state, send events
  ↓
domain/        use cases and the permission/request policy (pure; in :common
               when both apps need it)
  ↓
data/          Room, SharedPreferences, relays, files — behind narrow interfaces
```

The UI never touches a database, a relay client or `Amber.instance`: it talks to
a ViewModel, which talks to an interface (`AccountRepository`, `RelayClient`,
`SettingsStore`) implemented in `data/`. Manual constructor injection is enough;
there is no Hilt, which keeps the reproducible-build and dependency-verification
footprint small.

## The guardrails

- **detekt** (`config/detekt/detekt.yml`, `config/detekt/compose.yml`) — size and
  complexity (`LongMethod` 60, `LargeClass` 600, `CyclomaticComplexMethod` 15,
  `NestedBlockDepth` 4, `LongParameterList`, `TooManyFunctions`,
  `ComplexCondition`, `ReturnCount`, `ThrowsCount`), the Compose rules
  (`ModifierMissing`, `ViewModelForwarding`, `ViewModelInjection`, `MutableParams`,
  …) and the unused-code rules. Existing violations are in each module's
  `detekt-baseline.xml`; regenerate with `./gradlew detektBaselineAll`, which
  rewrites the files from the current findings, so fixed code disappears from
  them. Never add an entry by hand.
- **Architecture tests** (`tools/architecture-tests`, Konsist) — `:common` and
  `:commonsui` stay platform free, `ui/` does not import Room, `Amber.instance`
  cannot spread to a new file, `service/` and `database/` do not depend on
  Compose UI, the desktop `core/` does not import `ui/`, ViewModels are named
  `ViewModel`. The allow-lists at the bottom of `ArchitectureTest.kt` are
  ratchets too: they fail when an entry no longer violates the rule, so they
  cannot silently rot.
- **Coverage** (Kover) — `./gradlew koverXmlReport koverHtmlReport` writes one
  merged report to `build/reports/kover` (generated code excluded);
  `:common:koverVerify` enforces its minimum line bound. CI runs both and
  uploads the HTML report.

## Roadmap

Each step is a small, separately mergeable change, and each one should delete
the baseline or allow-list lines it makes obsolete. Do not add to them.

### 1. Share the bunker request logic (highest value)

Move the platform-free core of the desktop `core/BunkerEngine.kt` and the Android
`BunkerRequestUtils`/`EventNotificationConsumer` request handling into `:common`
(kind-24133 parsing, permission decision, response construction, rate limiting),
with the apps keeping only storage, transport and approval UI around it. This
removes duplicated security logic. Shrinks `app/detekt-baseline.xml`
(`LongMethod`, `CyclomaticComplexMethod` in `EventNotificationConsumer`),
`desktop/detekt-baseline.xml` (`BunkerEngine`) and the `amberInstanceUsers` set.

Done so far: `common/Nip46Requests.kt` holds the method → `SignerType` mapping,
the NIP-44 v3 kind/scope params, the repeated-connect check and the
connect-secret refusal, used by both apps. `permissionLookupOrder` (in
`common/PermissionDecision.kt`) is the order in which stored grants decide a
request, resolved by `BunkerEngine.lookupPermission` and `SignerProviderQuery`
against their own storage. Next: response construction.

### 2. Split `IntentUtils.kt`

Separate parsing (`nostrsigner://` / `nostrconnect://` → a data object),
validation and `IntentData` construction, with unit tests for each. Removes its
`LongMethod`/`CyclomaticComplexMethod` baseline entries.

### 3. Replace `Amber.instance` with constructor injection

Introduce narrow interfaces (`AccountRepository`, `RelayClient`, `SettingsStore`)
implemented next to the current singletons, and pass them in. One class per
change; every class converted stops using `Amber.instance` and its line leaves
`amberInstanceUsers`, which the architecture test then requires you to delete.

### 4. Give the big screens ViewModels

`LoginScreen`, `MainScreen`, the `Bunker*`/`Intent*` home screens and
`SettingsScreen`: hoist state into a `…ViewModel` and keep a stateless content
composable. This also makes them previewable and testable, and removes their
`LongMethod`/`ComposableParamOrder` baseline entries.

### 5. Break up `LocalPreferences.kt`

Split settings, account storage and key storage by concern; the key part is
where the `requireUnlockedDevice` gate lives, so it deserves its own tested
class.
