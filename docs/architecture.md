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

`Amber` is the Application class and the dependency container: `relayClient`,
`notificationSubscription`, `profileSubscription`, the per-account databases and
`settings`. It implements one of the narrow interfaces in `data/`, `SettingsStore`, and
holds the other two: `AccountRepository` (`accountDatabases`) and `RelayClient` (`relayClient`); code
takes those (or the app scope) through its constructor or parameters. There is no static accessor: Android
entry points (activities, services, receivers, workers, the provider) read the
container from their own context with `Context.amber`, and composables read the
interfaces from the CompositionLocals the two activities provide
(`ui/AppDependencies.kt`). There are two ViewModels.

### Where the pain is

- Six files were over 950 lines (`ui/LoginScreen.kt` 1548, the desktop
  `core/BunkerEngine.kt` 1502, `service/IntentUtils.kt` 1222,
  `service/EventNotificationConsumer.kt` 1087, `LocalPreferences.kt` 1016,
  `Amber.kt` 982). After steps 1–9 none is: the largest are the desktop
  `ui/SettingsScreen.kt` 948, `ui/actions/AccountBackupScreen.kt` 899 and
  `service/EventNotificationConsumer.kt` 846; `Amber.kt` is 393.
- The Android bunker flow (`BunkerRequestUtils` + `EventNotificationConsumer`)
  and the desktop one (`BunkerEngine`) implemented the same permission and
  response logic twice; step 1 moved the policy to `:common` and step 11 the rest
  of the decisions (params, admission, replies, first steps, grants and their revert).
- `ui/` reached Room DAOs directly in 10 files; since step 6 it does not.
- The detekt baselines held 306 (app) and 78 (desktop) grandfathered findings
  after step 9, about half of the app's from mechanical Compose rules (step 10).

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
desktop, so `PermissionWriteDao.insertPermissions` keeps its own delete queries), the
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

### 8. Finish the service layer's ratchets (done)

Two allow-lists in `ArchitectureTest.kt` still name files the system does not
construct: `contextAmberUsers` (`LocalPreferences`, `SecureCryptoHelper`,
`service/IntentUtils`) and `composeUiInService` (`service/IntentUtils`,
`service/ReportSender`). Once they are gone, both lists hold only the entry points
(activities, workers, receivers, the ContentProvider). The `object`s that are called
with a `Context` from everywhere get their narrow dependencies once at startup
(`install`, as `AccountStorage` does since step 5), and a Compose `Clipboard` becomes
a plain copy function the composable passes in.

Done: `ReportSender` takes a `copyToClipboard` function instead of a Compose
`Clipboard`, and `IntentUtils` one that `rememberResultCopier` builds (a decrypted
plaintext goes through `setSensitiveClip`). `SecureCryptoHelper`, `LocalPreferences`
and `IntentUtils` get their dependencies (`AccountRepository`, `SettingsStore`, the
app scope, the main activity) from `Amber`'s constructor, which runs before the
ContentProvider can take a query. `composeUiInService` is empty, and
`contextAmberUsers` lists only the entry points.

### 9. Split `Amber` (done)

`Amber` is the Application, the container and most of the app's plumbing at once:
the per-account databases, the relay client and its reconnect logic, the network
state, the cleanup and update-check alarms, the image loader and the Tor startup
wait. Each of these moves into a class of its own that `Amber` constructs and holds,
so `Amber` is left with wiring and the Application callbacks. Code that took `Amber`
as one of the `data/` interfaces takes the new object instead (the field is created
before the `init` blocks that hand it on, and anything that reads the Application's
context waits until it is attached).

Done: the per-account databases are `database/AccountDatabases`, the
`AccountRepository` that `Amber` holds as `accountDatabases` instead of implementing
it. The cleanup and update-check schedules are `ClearLogsWorker.schedule` and
`UpdateCheckWorker.schedule`, like `BackupApplicationsWorker.schedule`. Coil's loader
is built by `service/ImageLoaderFactory`, which `Amber` registers with
`SingletonImageLoader.setSafe` instead of implementing the factory itself. The
network the device is on is `service/NetworkState` (plain fields: only a log reads
them) instead of four Compose states. The relay client is `relays/AppRelayClient`, the
`RelayClient` that `Amber` holds as `relayClient`; the subscriptions it refreshes are
passed in as two functions, since they take the `RelayClient` themselves. The built-in
Tor start (bounded attempts, the recovery observer, the wait) is `service/TorStartup`.
`Amber` is left with wiring, migrations and the Application callbacks, and is under
detekt's function limit.

### 10. Clear the mechanical Compose findings (done)

About half of the app's `detekt-baseline.xml` is Compose rules whose fix does not
change what a screen does: `PreviewPublic` (a preview is private), `ModifierMissing`
and `ModifierWithoutDefault` (a composable that emits UI takes `modifier: Modifier =
Modifier`), `ComposableParamOrder` and `LambdaParameterEventTrailing` (required
parameters first, the event lambda last) and `ParameterNaming` (`onXChange`, not
`onXChanged`). They go one rule (or one screen group) per change, and the baseline is
regenerated each time so the fixed entries drop out. `LongMethod` and
`CyclomaticComplexMethod` are not part of this step: splitting a function is a
refactor of its own.

Done: `PreviewPublic` (50 previews made private; detekt's
`UnusedPrivateFunction` ignores `@Preview` and `@PreviewsLightDark` functions, which only
the IDE calls, as `unusedCode` already did) and `ParameterNaming` (13 callbacks in the
present tense: `onSelect`, `onPress`, `onApproveChange`, `onPinEnter`, `onChange`,
`onBackupFinish`, ...). `ModifierWithoutDefault` and `ComposableParamOrder` go together:
giving `modifier` its default makes it an optional parameter ahead of required ones, so
it also moves after them (positional callers name the argument instead); 24 + 18
entries, and 8 `LambdaParameterEventTrailing` ones the move fixed too.
The remaining `ComposableParamOrder` (12) and `LambdaParameterEventTrailing` (7) go
the same way: required parameters (an event callback among them) move ahead of the
optional ones, and callers that passed the callback as a trailing lambda name it
(`onDismiss = { ... }`, `onChange = { ... }`).
`ModifierMissing` in the app (35): each composable takes `modifier: Modifier = Modifier`
and hands it to its root layout (`ChooseSignPolicy`, which emitted one row per option,
gets a `Column` around them), and the same in the desktop app (14). The app baseline
went from 306 entries to 142 and the desktop one from 78 to 60: what is left is mostly
the long-function and complexity rules (`LongMethod`,
`CyclomaticComplexMethod`, ...).

### 11. Share the rest of the bunker flow (done)

What `EventNotificationConsumer`/`BunkerRequestUtils` and the desktop `BunkerEngine`
still do twice moves to `:common`, as step 1 did with the policy. Planned after
step 10, since it changes signing code and needs its own tests on both apps.

Done so far: the permissions a bunker `connect` asks for (its third parameter) are
read by `parseRequestedPermissions` (`NostrConnectUri.kt`), the same reader as a
nostrconnect:// URI's `perms`. Before, the desktop read a non-numeric kind as none, so
`nip44v3_encrypt:abc` asked for every kind, and the app kept blank entries and
`sign_event`/`nip` entries without a kind; now an entry that grants nothing is
dropped in both apps and in the URI. The NIP-44 v3 request basics are shared too:
`nip44v3SignerTypes`, `nip44v3Payload()` next to `nip44v3Kind()`/`nip44v3Scope()`,
and the two refusal messages (`NIP44V3_KIND_REQUIRED`, `NIP44V3_DECRYPT_FAILED`).

A relay auth event with more than one relay tag is refused at the same point in both
apps: once the sender is admitted, before anything is signed. The app used to refuse
it before admission, so any sender got the retried refusal, against the rule that an
error reply to an unverified sender is published once; now an unknown one gets the
single "no permission".

A NIP-46 `decrypt_zap_event` is read the Android way in both apps: `[pubkey, zap
request]`, the layout of the other decrypts (`zapRequestParam()`). The desktop used to
read the zap request from `params[0]`. Its local socket signer keeps its documented
`[zap request, account?]` call and moves the zap request to the NIP-46 slot when it
builds the request.

Whether a request carries the params its method needs is one check,
`hasRequiredParams()`: the peer pubkey of an encrypt/decrypt, the zap request, the
PSBT, and an event that parses for `sign_event`. Both apps refuse a request without
them with "invalid params" before admission, before any key is used. The desktop used
to admit it and fail computing the answer ("could not process the request"), and the
app did not check a `sign_psbt`'s PSBT.

Where each method keeps its params is read in one place too: `peerPubKeyParam()`,
`encryptionTextParam()`, `eventParam()` and `psbtParam()` sit next to the NIP-44 v3
and zap readers, and the app's `getDataFromBunker`/`requestParams` and the desktop's
`computeResult` read through them instead of by index.

What the admission alone decides is one mapping, `Nip46Admission.reply()`: a repeated
connect is acknowledged on its connection's relays (where it arrived when it has none;
the app used to send that ack nowhere), a refused connect answered once on the relays
of the connection its secret names, an unknown sender "no permission" once where it
arrived, and a request to another connection's key dropped. Each app sends it under
its own key and records its history.

A reply is built and published by the same code too (`Nip46Replies.kt`):
`nip46ReplyEvent` encrypts the response the way the request came in and signs the
kind-24133 event with the connection's key (`ConnectionReplyKey`) or the account's (each
app adapts its account to `Nip46ReplyKey`), `publishNip46Reply` retries with a freshly
built event, and `nip46ReplyLog` is the log line, without the result. Each app keeps
its relay client, the no-relays outcome, its logs, and on Android the Tor check and the
error toast.

What an admitted request does first is one decision too, `nip46Step()`
(`Nip46Steps.kt`): `switch_relays` replies with the default relays and `logout` with an
ack, each saving its change only once the client has the reply; a relay auth event
naming more than one relay, or one outside a non-empty auth whitelist (kept in the
history as rejected), and a NIP-44 v3 request without a kind are refused before
anything is signed or decrypted; anything else is decided on the grants. On Android the
whitelist check used to happen inside `SignerProviderQuery`, which keeps it for the
intent path.

The grants approving writes are computed by shared code too (`Grants.kt`):
`signPolicyGrants` gives a connection's sign policy its grants (each requested
permission under every type it is looked up by, accepted for good), and
`rememberedGrant` the grant remembering one decision, with the stored grants it
replaces (for NIP-04/NIP-44 the whole NIP and the content type replace each other).
Each app stores the result in its own records (`ApplicationPermissionsEntity`,
`AppPermissionRecord`).

Taking an approval back when its reply never reaches the client is one rule too:
`revertedGrants` removes exactly what approving added and restores what it replaced,
over grants re-read so a change made meanwhile survives, and `revertedConnectKey` puts a
bunker:// connection back under its secret. The desktop already did this; the app used
to delete every grant of the request's type, which could delete a grant from before
the approval and keep the content-type grant it had just added.

With that, what both bunker paths still do separately is their storage, UI and
platform glue: the decisions are in `:common`.

### 12. Clear the remaining detekt baseline findings

What step 10 left out: about 260 grandfathered findings, most of them `LongMethod`
(106) and `CyclomaticComplexMethod` (31). They go in this order, one rule or one
function per change, regenerating the baselines each time (no new entries):

1. The Compose findings that can be real bugs (`LambdaParameterInRestartableEffect`,
   `ModifierReused`, `ModifierNotUsedAtRoot`, `ModifierClickableOrder`,
   `RememberMissing`, `MutableStateAutoboxing`, `MultipleEmitters`).
2. The small logic rules outside the UI (`ReturnCount`, `ComplexCondition`,
   `ThrowsCount`, `NestedBlockDepth`, `LongParameterList`).
3. `CyclomaticComplexMethod`, then `LongMethod`, one screen or function at a time.

Done so far: `LambdaParameterInRestartableEffect` (7). An effect that outlives
recompositions (a one-shot `LaunchedEffect`, a layout listener, the desktop popup
timeout) called the callback it captured first; it now reads it through
`rememberUpdatedState`, so it always calls the latest one. Then `ModifierReused`,
`ModifierNotUsedAtRoot`, `ModifierClickableOrder`, `RememberMissing` and
`MutableStateAutoboxing` (10): the search bar no longer gives its modifier to both its
box and its field (and its suggestions lose a leftover red background), `RelayCard`
takes its modifier at the root, the sign-policy and account-picker rows clip before
the click so the ripple keeps their rounded shape, the main screen remembers its
pending route (it was recreated on every recomposition, bringing a consumed route
back), and the desktop tabs use `mutableIntStateOf`. `LoginWithPubKey` keeps its
`ModifierNotUsedAtRoot` entry on purpose: the screen's modifier scrolls the content
while the Cancel/Connect row stays pinned, so it cannot move to the root without a
separate content modifier.

`ComplexCondition` (13): conditions became named predicates or lookups (the top bar's
content-titled routes, a backup subscription's own events, a public domain name, the
systemd quoting characters, the mDNS address lengths, a local address, the lock
screen's enter keys). The "switch to the requested account" block, copied in
`MainActivity`, `SignerActivity` and `MainViewModel`, is one
`MainViewModel.switchToRequestedAccount`.

`ReturnCount` / `ThrowsCount` / `LongParameterList` in `:common` and `:cli` (11):
the local relay's checks became functions that name the refusal (`upgradeRejection`,
`dataFrameError`, `checkControlFrame`, `readPayloadLength`), with `LocalRelayFramesTest`
covering the handshake and every frame error byte by byte; `RelayFilter`'s fields
are optional, as in NIP-01. The CLI checks an option once (`Args.optionError`), reads
one tag at a time and dispatches commands in `Session.runCommand`.

App `ReturnCount` / `ThrowsCount`, first batch (5): `MainViewModel.getAccount` is
"the named saved account, else the requested one, else the fallback"
(`savedAccount`, `requestedAccount`, `fallbackAccount`); `onAddRelay` checks the typed
address before it shows the spinner (`relayUrlToAdd`, which also stops an address
that is not a relay URL from leaving the spinner on); the relay status notification
and the applications backup split the decision from the try/catch around it; the
nsec QR export and encryption are `exportNsec` / `encryptForQr`.

Key handling (3): the rotation sweep reports a `SweepOutcome` per pass
(`sweepUntilSettled`), `migrateCiphertext` checks the current key before
`migrateFromPreviousKey`, and the decrypt pre-check walks one lazy list of grant
lookups in the order the final lookup uses (`SignerProviderQueryTest` now checks each
grant alone).

`SignerProviderQuery.query` (`ReturnCount`, `NestedBlockDepth`, `CyclomaticComplexMethod`,
`LongMethod`): the 570-line function is a dispatcher over one function per operation
(`signEvent`, `encryptOrDecrypt` with its `encrypt` / `decrypt` paths, `signPsbt`,
`ping`), sharing the account lookup, the grant decision, the history record and the
answers (`requestAccount`, `isAllowed`, `recordHistory`, `rejected`, `resultCursor`).

Desktop `ReturnCount` / `ThrowsCount` / `NestedBlockDepth` (8): `App` is one `when` over
its gates (loading, locked, passphrase setup, account loading, login) with the signed-in
layout in `SignedInApp`; `handleShortcut` splits into the plain keys and the Ctrl/⌘ keys
(`navigationKeys`, `answerSelectedRequest`); `readSecure` reads through `storeJson`,
`rollbackProtectedJson` and `boundPayload`; `KeystorePassword.resolve` tries
`fromOsStore`, then `fromFileStore`, or `storeFresh`; and the keystore, the profile
picture download, the Linux notification and the URI forwarding each lose a branch to a
named helper.

`LongParameterList` (6, none left): the main screen, the incoming-requests screen and
the top bar take one `IncomingRequests`, and the NIP-44 v3 approval a `Nip44v3Prompt`;
`Account` reads `hexKey` and `npub` from its signer's key pair instead of taking copies
that could disagree with the key that signs; `AmberEvent` keeps the seven NIP-01 fields
under a commented `@Suppress`.

`MultipleEmitters` (none left): a section that emits several rows into its parent's
`Column` is a `ColumnScope` extension instead of being wrapped in a `Column` of its own,
which would drop the parent's spacing between the rows. That covers the desktop request
details (`DetailSection`, `TagsSection`, `PsbtSections`, `PsbtOutputs`), the Android
`PsbtBody` and `LocalBunkerServerSettings`, which also loses its `LongMethod` entry by
splitting into its switch row and its port field.

`LongMethod` goes one screen or component per change, keeping the state and actions in
the original function and moving each block it lays out to a private composable that
takes plain values and callbacks. Repeated blocks become one composable: the request
group options build their three scope toggle boxes with one `ScopeToggles`, each caller
keeping an exhaustive `when` for its labels.
