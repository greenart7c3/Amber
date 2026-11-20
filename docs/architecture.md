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

### The dependency container

`Amber` is the Application class and the dependency container: `client`,
`notificationSubscription`, `profileSubscription`, the per-account databases and
`settings`. It implements three narrow interfaces in `data/` — `SettingsStore`,
`AccountRepository` and `RelayClient` — and code takes those (or the app scope)
through its constructor or parameters. There is no static accessor: Android
entry points (activities, services, receivers, workers, the provider) read the
container from their own context with `Context.amber`, and composables read the
interfaces from the CompositionLocals the two activities provide
(`ui/AppDependencies.kt`). There are two ViewModels.

### Where the pain is

- Six files over 950 lines: `ui/LoginScreen.kt` 1548, the desktop
  `core/BunkerEngine.kt` 1502, `service/IntentUtils.kt` 1222,
  `service/EventNotificationConsumer.kt` 1087, `LocalPreferences.kt` 1016,
  `Amber.kt` 982.
- The Android bunker flow (`BunkerRequestUtils` + `EventNotificationConsumer`)
  and the desktop one (`BunkerEngine`) implement the same permission and
  response logic twice.
- `ui/` reached Room DAOs directly in 10 files; since step 6 it does not.

## Target layering

```
ui/            Composables + ViewModels: render state, send events
  ↓
domain/        use cases and the permission/request policy (pure; in :common
               when both apps need it)
  ↓
data/          Room, SharedPreferences, relays, files — behind narrow interfaces
```

The UI never touches a database, a relay client or the `Amber` container: it talks to
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
  `:commonsui` stay platform free, `ui/` does not import Room or call a DAO, `Context.amber` cannot spread to a new file,
  `service/` and `database/` do not depend on
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

### 1. Share the bunker request logic (done)

Move the platform-free core of the desktop `core/BunkerEngine.kt` and the Android
`BunkerRequestUtils`/`EventNotificationConsumer` request handling into `:common`
(kind-24133 parsing, permission decision, response construction, rate limiting),
with the apps keeping only storage, transport and approval UI around it. This
removes duplicated security logic. Shrinks `app/detekt-baseline.xml`
(`LongMethod`, `CyclomaticComplexMethod` in `EventNotificationConsumer`),
`desktop/detekt-baseline.xml` (`BunkerEngine`).

Done so far: `common/Nip46Requests.kt` holds the method → `SignerType` mapping,
the NIP-44 v3 kind/scope params, the repeated-connect check and the
connect-secret refusal, used by both apps. `permissionLookupOrder` (in
`common/PermissionDecision.kt`) is the order in which stored grants decide a
request, resolved by the desktop `lookupPermission` and `SignerProviderQuery`
against their own storage. `common/Retry.kt` holds the reply retry
(`retryWithBackoff`) and how often a reply is published: an error reply to a
sender Amber has not verified goes out once (`UNVERIFIED_REPLY_ATTEMPTS`) on
both apps. Encrypting and signing the reply stays in each app: on Android it
goes through `Account`, whose `requireUnlockedDevice` gate a shared helper
handed the raw key would bypass.

The admission gate is `common/Nip46Admission.kt`: `admitNip46Request` decides, from
the connection saved under the sender's key, the key the request arrived on and,
for a `connect`, its secret and the connection that secret names, whether the
request is dropped (another connection's key), acked (a repeated connect),
refused before the account key is used (a refused secret, or "no permission")
or admitted. `EventNotificationConsumer.notify` and `BunkerEngine.routeRequest`
both call it and keep only their storage lookups, logs and replies;
`Nip46AdmissionTest` covers each outcome.

What an encrypt/decrypt approval previews (an event, an opened seal, a tag array
or plain text) is `service/EncryptedDataPreview.kt`'s `previewOf`, shared by the
intent and bunker paths on Android (the desktop shows no such preview);
`EncryptedDataPreviewTest` covers it.

`BunkerResponder.sendResult` / `sendRejection` are split by job: attaching a
bunker:// connection to the client key, the new-connection row, what a connect
sets (its own key, the URI's relays, the client metadata, the default grants),
the history entry, publishing the reply and undoing the grants when it fails;
`sendBunkerResponse` builds the reply once (`replyEvent`) instead of twice.

`common/Nip46Freshness.kt` holds the request freshness rule both apps apply
(`nip46StaleReason`: five minutes either way, and the NIP-40 expiration) and the
`switch_relays` answer (`switchRelaysResult`, null when there is nothing to move
to). On Android, `consume` finds the request's account (`recipient`: a verified
kind-24133 event tagged to a logged-in account or one of its connections,
`addressee`), drops what is rate-limited, stale or already seen (`isNewRequest`),
then answers a locked device or hands the request on, and `notify`
refuses a malformed request before any key is used (`readParams`), admits the
sender (`admit`, which only then signs or reads with the account key) and hands
an admitted request to one handler per method (`switchRelays`, `logout`,
`getPublicKey`, `answerFromGrants`). The desktop `BunkerEngine` uses the same two
rules and splits `handleRequest` the same way (`switchRelays`, `logout`,
`refusedUpFront`, `answerOrQueue`).

`common/NostrConnectUri.kt` parses a `nostrconnect://` URI (client key, relays,
name, URL, image, secret and the requested permissions, with the NIP-46
metadata JSON overriding what it sets) for both apps, which had drifted apart;
`NostrConnectUriTest` pins it. Android's `NostrConnectUtils` keeps only the
intent extras and the queueing, and the desktop's `addNostrConnect` only the queueing.

The desktop's `doApprove` is split like Android's `sendResult`: the chosen account
(`approvingAccount`), the connection it is approved on (`savedConnection`, which
attaches a bunker:// placeholder), where the reply goes (`approvalRelays`), a new
connection's record (`newApplication`, with its own key), what a connect sets
(`applyConnect`) and the reply over the socket or the relays (`answer`). Like
Android, a remembered approval whose reply fails to publish is taken back
(`revertApproval`, pinned by `ApprovalRevertTest`).

`BunkerEngine` itself is now only the relay listener, the subscription filters and
request intake; the rest lives beside it in `core/`, one file per job: approving
and rejecting with the grant updates they make (`BunkerApproval.kt`), the approval
queue (`PendingQueue.kt`), connections made from this side, a nostrconnect://
URI, a new bunker:// URI and their expiry (`BunkerConnections.kt`), and publishing
a reply (`BunkerReplies.kt`). Its desktop baseline entries are gone.

The local socket signer follows the same steps: `LocalSigner.resolveOrConnect`
finds the tool's approved connection and otherwise asks the user (`connect`), and
`evaluate` reads the grant's kind (`permissionKind`), refuses a relay outside the
auth whitelist, computes the answer (`computeOrRefuse`) and answers from the
remembered decision or asks the user (`ask`). With that, the bunker request code
of both apps has no baseline entries left (only one long test in
`EventNotificationConsumerTest`).

What a remembered decision stores is shared too. `common/PermissionDecision.kt`
holds how long each "remember" choice lasts (`rememberUntil`, keyed on the
`RememberType` screen code both apps store; Android had kept "10 minutes" for 15)
and which stored grants a new one replaces (`replacesGrant`), used by Android's
`AmberUtils.updatePermission` and the desktop's `acceptOrRejectPermission`.
`expandPermissionTypes` (`common/Nip46Requests.kt`) maps a connect's requested
permissions to the types requests look up, and `basicPermissions` (in
`:commonsui`, next to `Permission`) is what "approve basic actions" grants; Android
had stored some of those under names nothing looked up.

Step 1 is done. What stays in each app on purpose: decrypting a request and
encrypting and signing the reply (on Android through `Account`, so the
`requireUnlockedDevice` gate applies), storage (Room on Android, JSON files on the
desktop, so `ApplicationDao.insertPermissions` keeps its own delete queries), the
relay subscriptions, and the approval screens with their scope choices.

### 2. Split `IntentUtils.kt` (done)

Separate parsing (`nostrsigner://` / `nostrconnect://` → a data object),
validation and `IntentData` construction, with unit tests for each. Removes its
`LongMethod`/`CyclomaticComplexMethod` baseline entries.

Done so far: `service/NostrSignerRequest.kt` parses both request forms (the
URI query of web apps, the intent extras of native apps) into a
`NostrSignerRequest` without touching a key, and its `rejection()` is the
validation; `IntentUtils.buildIntentData` is the one place that signs or
encrypts and builds the `IntentData`. On the response side,
`service/IntentResponse.kt` decides what the history stores (never a
plaintext) and what a callback URL receives, and `sendResult` only saves and
delivers. How a request is read (`decodeData`, `isUrlEncoded`, `parsePubKey`,
`isWebRequest`, `isBrowserReferrer`) is `service/IntentParsing.kt`, and `isRemembered` sits
with the grant lookups in `IntentPermissionLookup.kt`, so `IntentUtils` keeps the
queue, the request building and the replies. `sendResult` takes what to answer
(`IntentReply`) and what the user chose (`IntentApproval`) instead of nineteen
parameters, and derives the grant key from the package name. Step 2 has no
production baseline entries left.

### 3. Replace `Amber.instance` with constructor injection (done)

`Amber.instance` is gone. `SettingsStore`, `AccountRepository` and `RelayClient`
(`data/`) are implemented by `Amber` and passed in: through constructors
(`AccountStateViewModel`, the relay services, `SignerProviderQuery`,
`BunkerResponder`, `EventNotificationConsumer`, `WebDavService`, ...), as
parameters of the `object` helpers, or as CompositionLocals for composables.
What only the Application has (activity tracking, crash reports, relay stats)
is read through a context the code is already given (`Context.amber`); step 7
takes that out of `ui/`. The
Room DAOs that need settings take their database, which carries them, and
`SecureCryptoHelper`, which Room converters call without a context, gets the
application context installed by `Amber.onCreate`. Moving these dependencies
behind ViewModels is step 4.

### 4. Give the big screens ViewModels (done)

`LoginScreen`, `MainScreen`, the `Bunker*`/`Intent*` home screens and
`SettingsScreen`: hoist state into a `…ViewModel` and keep a stateless content
composable. This also makes them previewable and testable, and removes their
`LongMethod`/`ComposableParamOrder` baseline entries.

Done so far: `SettingsScreen` (`SettingsViewModel`, `SettingsUiState` and a
stateless `SettingsContent` split into one composable per section). The
ViewModel takes its dependencies through `SettingsViewModel.factory`, used as
the screen's default `viewModel(...)` argument, as the Compose
`ViewModelInjection` rule requires.

Done so far: `IntentSingleEventHomeScreen` (`IntentSingleEventViewModel` loads
the saved app for each request and the screen uses it only for that request,
since the calling app chooses the request id; one composable per request type; the grant lookup is
`service/intentPermission`, unit-tested). ViewModels may import `database/`
types (the `databaseInUi` rule exempts `*ViewModel.kt`), since loading data is
their job.

`IntentMultiEventHomeScreen` (`IntentMultiEventViewModel` confirms the batch, per
account; `RequestGroupChoices` holds the per-group options; the request list is a
stateless composable). Previews get an in-memory `SettingsStore` from
`AmberPreview`.

`BunkerMultiEventHomeScreen` (`BunkerMultiEventViewModel` confirms the batch,
each request under its own account and app; `BunkerGroupChoices` keys the
options per app and group; the request cards are stateless composables).

`BunkerSingleEventHomeScreen` (`BunkerSingleEventViewModel` loads the saved app,
by connect secret or client key, and approves a connect, asking first when it
would replace a saved connection; `BunkerSingleRequest` holds the app name,
icon and remembered decision, so the screen no longer imports `database/`; one
composable per request type; the grant lookup is `service/bunkerPermission`,
sharing the intent lookup's order, unit-tested). The ViewModel keeps only the
loaded app and the replace prompt; the app is loaded for each request and used
only for that request, since a client chooses the JSON-RPC id and a later
request can reuse the key (a grant revoked in between must not still apply).

`LoginScreen` (one ViewModel per page in `LoginViewModels.kt`:
`AccountImportViewModel` restores a backup, `AddKeyViewModel` checks a typed key
or recovery phrase with `parseLoginKey` (unit-tested), `SignUpViewModel` draws
the new key; the pages take callbacks into `AccountStateViewModel` instead of the
ViewModel itself, and the sign policy / Tor step both flows end on is one
`ConnectionSetupPage`).

`MainScreen` (`MainScreenViewModel` switches Tor/Orbot off the composition scope;
where navigation starts is the tested `startRoute`; the destinations are grouped
`NavGraphBuilder` functions in `MainNavGraph.kt` laid out by a `MainLayout`; the
accounts sheet, profile editor and restore prompt take callbacks instead of
`AccountStateViewModel`). What is left of `AccountStateViewModel` forwarding is
`AccountScreen` handing it to `MainScreen`, which calls its account operations.

### 5. Break up `LocalPreferences.kt` (done)

Split settings, account storage and key storage by concern; the key part is
where the `requireUnlockedDevice` gate lives, so it deserves its own tested
class.

Done so far: the saved-account list, current account and favorites are
`SavedAccounts`, the per-account preferences (backup consent, user relays, fetch
times) `AccountPreferences`, and both read the preference files through
`appPreferences`/`accountPreferences` (`PreferenceFiles.kt`). Neither touches the
container; `SavedAccountsTest` pins the stored format. The key policy is
`KeystorePolicy`: the synchronous `requireUnlockedDevice` read, the active
Keystore alias committed with it, the signing gate (`requireAccess`, fail-closed)
and the locked-device check that keeps a locked device from being reported as a
broken key; `KeystorePolicyTest` covers them. The accounts with their keys are
`AccountStorage`: load (one `load(context, npub = null)` for the current or a
given account, `loadSync` for the ContentProvider), login, logout, the
decrypted-account cache and the account names. It gets the `AccountRepository`
and the app scope from Amber at construction (`install`, before any
ContentProvider runs) instead of reaching the container through a context;
`AccountStorageTest` covers the broken-key report, the locked-device case, the
cache and the key zeroing at logout. The WebDAV server for cloud backups is
`WebDavSettings`. What stays in `LocalPreferences` is the app settings; reading
them is split into small helpers (`enumOr`, the `use_proxy` → `TorMode`
migration, `applyProxy`) and `SettingsReadTest` pins how stored values are read.
Its `TooManyFunctions` entry stays: one updater per setting.

### 6. Keep `ui/` off the database (done)

The target layering has the UI talk only to ViewModels, but six screens still
used Room types or DAOs directly (the `databaseInUi` allow-list in
`ArchitectureTest.kt`). Move each one's loading and writing into its ViewModel,
which may use `database/` (the rule exempts `*ViewModel.kt`), hand the screen only
what it shows, and delete the screen from the allow-list.

Done so far: `IntentSingleEventHomeScreen`. `LoadedIntentApplication` keeps the
saved app private and gives the screen its `name` and `remembered(key, relayUrl)`.
`ActivityStatsCard`: `ActivityStatsViewModel` loads the counts once, when the card
is first opened (`ActivityStatsViewModelTest`). `ActivitiesScreen` and the per-app
`actions/ActivityScreen`: `ActivityViewModel` pages the history as `ActivityEntry` rows
and looks up app names. `LogsScreen` and the relay log in `EditRelaysDialog`: `LogsViewModel`
pages the log as `LogEntry` lines, reads it for the export and clears it. `LogoutButton`
deleted the account's apps one by one before logging out, which the logout already
does with its databases: that code is gone. `ApplicationsScreen`: `ApplicationsViewModel`
pages the app list (its `ApplicationListItem` projection is already the list's shape).
`BunkerMultiEventHomeScreen`: `BunkerMultiEventViewModel.savedApp` looks up each requesting
app's saved name and icon. `BunkerConnectRequestScreen`: `BunkerSingleEventViewModel.connectionName`
finds a bunker:// connection's saved name by its secret, in the chosen account.
`AmberTopAppBar`: `TopAppBarViewModel.savedApp` looks up the app the title names.
`EditConfigurationScreen`: `EditConfigurationViewModel` loads the app as an `AppConfiguration`
and updates, resets or deletes it. `EditPermission`: `EditPermissionViewModel` loads the app's
grants as `PermissionGrant`s (and the app as a `PermissionsApp`) and saves the changes.
`NewNsecBunkerScreen` and `NewNsecBunkerCreatedScreen`: `NewBunkerViewModel` creates the
bunker:// connection and prepares its URI.
The rule also counts DAO calls (`.dao(`, `getDatabase(` and the like), since Kotlin
needs no import for the types it infers: `actions/ActivityScreen` slipped past the
import check that way, and seven more files went on the allow-list when the rule
started counting calls. None is left: the `databaseInUi` allow-list is empty, so the
rule now fails on any `ui/` file outside a ViewModel that touches the database.

### 7. Keep `Context.amber` out of `ui/` (done)

`Context.amber` hands a composable the whole container. The `contextAmberUsers`
allow-list in `ArchitectureTest.kt` lists every file that still reaches it; step 7
removes the `ui/` ones, one kind of dependency per change. The rule has no ViewModel
exemption, so everything arrives through `appDependencies(amber)` in
`ui/AppDependencies.kt`: an existing local (`LocalSettingsStore`,
`LocalAccountRepository`, `LocalRelayClient`, `LocalAppScope`), a plain `Context`
where that is all the code needs, or a new local of a narrow type (never `Amber` or
`Context`), which also goes on `allowedCompositionLocals` in
`config/detekt/compose.yml` and must be provided wherever the others are.

Out of scope: the entry points the system constructs (activities, workers,
receivers, the ContentProvider), which keep `Context.amber`, and the non-UI
`LocalPreferences`, `SecureCryptoHelper` and `service/IntentUtils`.

Done so far: `FeedbackScreen` (the existing locals), `AccountBackupScreen` and
`ApplicationsScreen` (the application context). `BunkerSingleEventHomeScreen` and
`BunkerMultiEventHomeScreen`: `LocalBunkerResponder`. `NewNsecBunkerScreen` and `RelaysScreen`:
`LocalBunkerRelay`. `AmberTopAppBar` and `EditRelaysDialog`: the relay status (`stats`, `relayStats`)
is part of `RelayClient`, and `onAddRelay` takes the saved relays as a lookup.
`ProfileSubscriptionEffect` and `LoginScreen`: `LocalProfileSubscription`, `LocalNotificationSubscription`.
`EventData`, `CrashReportScreen` and `TranslationReportScreen`: `LocalPendingReports`, a `PendingReports`
(`data/`) that `Amber` holds as `AppPendingReports` instead of two fields of its own.
`SettingsScreen`, `UpdateSettingsScreen` and `ApplicationsBackupScreen`: `SettingsStore.themeMode`,
`LocalZapstoreUpdater`, and `BackupApplicationsWorker.schedule`/`cancel` (moved off `Amber`).
`MainScreen`: the existing and new locals; `DisplayCrashMessages` takes the application context and
the `PendingReports`. No `ui/` file is left on the allow-list: what remains there is the entry
points and the three non-UI files above.

### 8. Finish the service layer's ratchets

Two allow-lists in `ArchitectureTest.kt` still name files the system does not
construct: `contextAmberUsers` (`LocalPreferences`, `SecureCryptoHelper`,
`service/IntentUtils`) and `composeUiInService` (`service/IntentUtils`,
`service/ReportSender`). Once they are gone, both lists hold only the entry points
(activities, workers, receivers, the ContentProvider). The `object`s that are called
with a `Context` from everywhere get their narrow dependencies once at startup
(`install`, as `AccountStorage` does since step 5), and a Compose `Clipboard` becomes
a plain copy function the composable passes in.

Done so far: `ReportSender` takes a `copyToClipboard` function instead of a Compose
`Clipboard`. Left: `SecureCryptoHelper`, `LocalPreferences`, `IntentUtils`.
