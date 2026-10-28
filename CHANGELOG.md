# Changelog

## Amber 7.0.0-pre1

- New: Amber for desktop (Windows, macOS and Linux) — a NIP-46 signer that mirrors the Android permission model, with a mandatory passphrase lock, keys kept in the OS credential store and each account's data encrypted at rest
- New: approve or reject desktop requests from the notification, plus a tray icon, start-on-boot, Tor support, nostrconnect:// links and per-app relay management
- New: a NIP-5F local socket signer on desktop so CLI tools sign through the same permission and approval pipeline as bunker apps
- New: a pure-black AMOLED theme and an appearance setting to pick the theme, and a fix for unreadable light-on-amber elements in the dark theme
- New: search saved accounts by name or npub in the account switcher and the approval account picker, and keep favorite accounts at the top
- New: list the backed-up applications before a restore and pick which ones to restore, refusing encrypt-to-self permission backups
- Show the request description in redacted bunker notifications
- Show which account each batched request signs as, never show a connect request on the batch screens, and apply the auth whitelist to relay auth there
- Keep each calling app's NIP-55 requests on its own screen, and sign intents with the account that authorizes them
- Show decoded outputs, change and fee on desktop sign_psbt approvals
- Keep logs and activity longer with a size budget
- Copy decrypt results to the clipboard as sensitive and clear them
- Add keyboard selection and remember-cycling for pending requests on desktop
- Fix '+' in app names that clients form-encode in bunker URIs, and sanitize client-chosen app names on both apps so they stay out of notification bodies
- Return the real result for NIP-46 and NIP-55 seal encrypt/decrypt requests
- Never let an older kind-0 overwrite a newer profile, and only accept profile events the fetch actually asked for
- Keep the NIP-46 subscription 'since' from following future-dated events
- Handle revoked and expired NIP-46 connections: never resurrect a revoked one from its queued requests, treat an expired one as absent, and replace a saved connection when a nostrconnect reuses its key
- Answer a rejected or malformed NIP-46 request with an error instead of crashing or staying silent
- Verify the NIP-46 sender's key before using the account key with it, require a lowercase hex client key in nostrconnect URIs, and rate-limit NIP-46 events from unknown senders
- Handle a locked device: answer each locked request once even when several relays deliver it, keep requests the lock interrupted queued for redelivery, check the lock before opening NIP-46 requests, and explain the software-only unlock gate on Android 8
- Keep desktop crash reports unreadable on disk while a passphrase is set, and keep the keyring second factor across passphrase changes
- Redeliver NIP-46 requests interrupted by the passphrase lock, enforce connection expiry at request time, and drop requests a stored app sends to another connection's key (desktop)
- Refuse to sign PSBTs whose sighash does not commit to all outputs, and label PSBT change by the output script instead of requester metadata
- Only accept Zapstore release events signed by the developer key, refuse an update without a download hash, and never treat an unparsable release version as newer
- Refuse WebDAV redirects that leave HTTPS, and connect directly when Tor is not used
- Require per-listener credentials on the built-in Tor SOCKS port, and fail closed to a proxy when built-in Tor releases its port
- Decide Tor bypass on the parsed host instead of a URL substring, and resolve .localhost and .local relay names without the system resolver
- Serve only share/ and updates/ from the FileProvider, load client icons only over https and native icons only from Amber's store, and accept notification routes only from Amber's own intents
- Record encrypt/decrypt intents under the account that handled them, make the generic permission lookup exact and deterministic, and treat blank-relay auth grants as all relays on the approval screens
- Scope the provider's v3 decrypt pre-check to the requested kind, and only let accepting grants past it
- Delete an account's databases at logout
- Make all PendingIntents immutable
- Load settings before anything can read them on a cold start, and start the connectivity functions after every startup
- Refresh relay subscriptions when the device is unlocked, and don't report a locked Keystore as a broken key
- Show failed backup imports in a dialog instead of a toast, and translate the failed key export and account import messages
- Add event kind labels used by ngit, divine, Staircase and White Noise
- Update Quartz to 2.3.0, OkHttp and the rest of the dependency stack, and port Quartz's relay socket fixes to both apps
- Make the release APKs and the desktop installers reproducible, and verify the checksum of every dependency and plugin

Download it with [Zapstore](https://zapstore.dev/apps/com.greenart7c3.nostrsigner), [Obtainium](https://github.com/ImranR98/Obtainium), [f-droid](https://f-droid.org/packages/com.greenart7c3.nostrsigner) or download it directly in the [releases page](https://github.com/greenart7c3/Amber/releases/tag/v7.0.0-pre1)

If you like my work consider making a [donation](https://greenart7c3.com)

## Verifying the release

In order to verify the release, you'll need to have `gpg` or `gpg2` installed on your system. Once you've obtained a copy (and hopefully verified that as well), you'll first need to import the keys that have signed this release if you haven't done so already:

``` bash
gpg --keyserver hkps://keys.openpgp.org --recv-keys 44F0AAEB77F373747E3D5444885822EED3A26A6D
```

Once you have his PGP key you can verify the release (assuming `manifest-v7.0.0-pre1.txt` and `manifest-v7.0.0-pre1.txt.sig` are in the current directory) with:

``` bash
gpg --verify manifest-v7.0.0-pre1.txt.sig manifest-v7.0.0-pre1.txt
```

You should see the following if the verification was successful:

``` bash
gpg: Signature made Fri 13 Sep 2024 08:06:52 AM -03
gpg:                using RSA key 44F0AAEB77F373747E3D5444885822EED3A26A6D
gpg: Good signature from "greenart7c3 <greenart7c3@proton.me>"
```

That will verify the signature on the main manifest page which ensures integrity and authenticity of the binaries you've downloaded locally. Next, depending on your operating system you should then re-calculate the sha256 sum of the binary, and compare that with the following hashes:

``` bash
cat manifest-v7.0.0-pre1.txt
```

One can use the `shasum -a 256 <file name here>` tool in order to re-compute the `sha256` hash of the target binary for your operating system. The produced hash should be compared with the hashes listed above and they should match exactly.

## Older versions

Release notes for previous versions live in [docs/changelogs](docs/changelogs/README.md).
