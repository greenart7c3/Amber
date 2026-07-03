# Amber Desktop (Windows, macOS, Linux)

A Compose for Desktop port of Amber that turns your computer into a NIP-46
remote signer ("bunker"). It shares the same Nostr stack as the Android app
(the [Quartz](https://github.com/vitorpamplona/amethyst) library, published
for the JVM) and mirrors the mobile UI and permission model.

## Features

- Multiple accounts: create a new key (NIP-06 seed words) or import an
  `nsec`, `ncryptsec` (NIP-49), raw hex key, or mnemonic
- NIP-46 signing over relays: `connect`, `sign_event`, `get_public_key`,
  `ping`, `nip04_encrypt/decrypt`, `nip44_encrypt/decrypt`,
  `nip44v3_encrypt/decrypt`, `decrypt_zap_event`, `sign_psbt`,
  `switch_relays`, `logout`
- Connect applications with a `nostrconnect://` URI or by generating a
  `bunker://` URI (with QR code) — each connection gets its own local key
- The same permission model as mobile: auto-accept / auto-reject rules per
  request type and event kind, time-bound grants (5 minutes … always), and
  per-application sign policies (basic / manual / sign everything)
- Per-application activity history and relay logs
- Default bunker relays management
- System tray: closing the window minimizes Amber to the tray so it keeps
  answering requests (with Open / Lock now / Quit menu), and new approval
  requests raise a system notification and bring the window back — both
  configurable under Settings → Desktop
- Optional passphrase lock (see Key storage below)
- Native desktop layout: sidebar navigation with an account switcher, dense
  list views, and keyboard shortcuts
- Light/dark theme using the Amber palette

## Keyboard shortcuts

Ctrl on Windows/Linux, ⌘ on macOS:

| Shortcut | Action |
|----------|--------|
| Ctrl/⌘ + 1–4 | Switch between Incoming requests / Applications / Relays / Settings |
| ↑ / ↓ | Select a pending request (Incoming requests) |
| ← / → | Cycle the selected request's "Remember" duration |
| Ctrl/⌘ + Enter | Approve the selected request with the chosen duration |
| Ctrl/⌘ + Shift + Enter | Reject the selected request |
| Escape | Leave the application detail view |
| Ctrl/⌘ + L | Lock (when a passphrase is set) |
| Ctrl/⌘ + W | Hide the window (to the tray when enabled) |
| Ctrl/⌘ + Q | Quit |

Not included: NIP-55 (`nostrsigner:` intents and the content provider) —
that is Android IPC and does not exist on desktop. Web apps and other
clients connect through NIP-46 instead.

## Key storage

Private keys are encrypted at rest with AES-256-GCM. The AES key is held in
a Java KeyStore (PKCS12) file under the application data directory:

- Windows: `%APPDATA%\Amber`
- macOS: `~/Library/Application Support/Amber`
- Linux: `$XDG_DATA_HOME/amber` (or `~/.local/share/amber`)

The keystore password is kept in the operating system's credential store:

- macOS: Keychain
- Windows: Credential Manager
- Linux: the freedesktop Secret Service (GNOME Keyring / KWallet over D-Bus)

so copying the data directory (or a backup of it) is not enough to unlock
the keys. On systems without a secret daemon (headless Linux, minimal
window managers) the password falls back to an owner-only file next to the
keystore, and is migrated into the credential store automatically the
first time one becomes available. The Settings screen shows which backend
is in use.

Note the trust model: any process running as your OS user can request the
secret from the credential store, so this protects against offline attacks
(disk theft, leaked backups, copied data directories) rather than against
malware running in your session. Use full-disk encryption and OS login
protection too. If you move the data directory to another machine, also
transfer the `com.greenart7c3.nostrsigner` entry from the credential store
(or keep the legacy `keystore.pass` file).

### Passphrase lock (stronger, opt-in)

Enable a passphrase under **Settings → Security** for defence that does not
depend on the OS credential store. The AES master key is then stored only
wrapped (AES-256-GCM) under a key derived from your passphrase with
**Argon2id**, in `master.key.enc`; the plain keystore and its
credential-store/file password are deleted. The passphrase is never written
anywhere.

With the lock on:

- Copying the data directory (or the credential store) is useless — without
  the passphrase there is no way to decrypt the keys.
- The per-account **database** (connected applications, permission grants,
  request history, relay logs) is also encrypted at rest with the master
  key — AES-256-GCM, with an `AMBERENC1:` header — so the metadata about
  which apps you sign for stays private too. Enabling the passphrase
  re-encrypts existing data immediately; removing it rewrites plaintext.
  (`settings.json` and `accounts.json` stay plaintext, but the private keys
  inside `accounts.json` are always encrypted with the master key.)
- Amber asks for the passphrase at startup and can auto-lock after an idle
  timeout (5 min / 15 min / 1 hour / never) or immediately via **Lock now**.
  Locking evicts all key material from memory and disconnects the relays, so
  no request can be signed until you unlock again.

Residual risk it cannot remove: while unlocked, the keys are in the
process's memory, so malware that can scrape another process's memory or
log your keystrokes in your session could still capture them — that is
inherent to any software signer on a general-purpose OS. Hardware-backed,
per-signature consent (Touch ID / Windows Hello / TPM) would be the next
step and is tracked as future work.

**If you forget the passphrase there is no recovery** — restore your keys
from their nsec or seed-word backup instead.

## Run and build

```bash
./gradlew :desktop:run                                # run from source
./gradlew :desktop:createDistributable                # runnable app image
./gradlew :desktop:packageDeb                         # Linux .deb
./gradlew :desktop:packageRpm                         # Linux .rpm
./gradlew :desktop:packageMsi                         # Windows .msi (build on Windows)
./gradlew :desktop:packageExe                         # Windows .exe (build on Windows)
./gradlew :desktop:packageDmg                         # macOS .dmg (build on macOS)
./gradlew :desktop:packageDistributionForCurrentOs    # whatever fits the host
```

jpackage can only produce installers for the OS it runs on, so release
builds are made per-platform. Linux packaging needs `fakeroot` (deb) or
`rpm-build` (rpm) installed.

## Tests

```bash
./gradlew :desktop:test              # unit tests
AMBER_E2E=1 ./gradlew :desktop:test  # + a NIP-46 round-trip over a public relay
```
