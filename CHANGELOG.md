# Changelog

## 6.1.0

### Added
- Sign PSBT (`sign_psbt`) support for NIP-55 and NIP-46 bunker requests, with a streamlined approval screen showing amount, change, addresses, and fee.
- Encrypted applications backup via NIP-78, opt-in and configurable per account, with separate read/write backup relay sets and automatic relay restart after a restore.
- Stable/beta update channel toggle and a periodic background update check via WorkManager.
- Reset bunker button to disconnect and regenerate the connection secret.
- Activity statistics card with accept/reject filtering on both the global Activities screen and per-app Activity screens.
- Per-caller rate limiting for incoming intents (by caller, type, and kind).
- Option to disable service start on boot, a dedicated service settings screen, and a Stop service action in the foreground notification.
- Account path (index) selection for mnemonic login.
- Select/deselect all toggle on the new-app permissions screen.
- Option to show full content in the event and tags sections of the approval screen.
- Support for additional Nostr event kinds (including 4454, 4455, 10044, and 30443).

### Changed
- Approval-screen option pickers converted to bottom sheets, and the remember-choice control moved to a modal bottom sheet.
- Restyled bunker connect and pubkey-login screens, and now show a shortened npub in the signing widget, account picker, and connect screens.
- Permission lookups are cached in front of Room for faster repeated checks.
- Missing-translation reports can be previewed before sending, and crash/translation report submissions now show a loading indicator.
- Invalid `nostrsigner` intents now display an error screen, and a close-app button is shown on external request approval screens.
- Migrated Gradle build scripts to the Kotlin DSL and enabled resource shrinking on release builds.

### Fixed
- Crash when expanding the raw PSBT card.
- Relay matching now preserves the port when checking against the auth whitelist.
- Statistics graph total no longer cut off for 4+ digit values.
- Crash when launching the `POST_NOTIFICATIONS` permission request.
- `IndexOutOfBoundsException` when the active account was not in the cached account list.
- Compose snapshot crash in the configuration screen.
- `ForegroundServiceStartNotAllowedException` now caught in the connectivity service.
- Relays no longer fail to reconnect after an initial connection failure on startup.
- Suggestion dropdown flash when pasting from the clipboard during login.
- `SignerActivity` now always closes after handling bunker requests.
- Enabled OkHttp WebSocket pings to detect half-closed connections, register the NIP-46 listening REQ before publishing the connect response, and always reconnect on a bunker response.
- Fall back to nos.lol and relay.damus.io when no inbox relays are found.
- Fixed memory leaks and hot-path inefficiencies.
