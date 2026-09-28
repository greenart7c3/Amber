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
  configurable under Settings → Desktop. The tray uses the freedesktop
  StatusNotifierItem / AppIndicator protocol on Linux (via the dorkbox
  SystemTray library), so the icon shows on Wayland compositors such as
  Hyprland/Sway (through waybar's tray module) and on GNOME/KDE, not just
  X11. It needs an SNI host (e.g. waybar's `tray` module — note it may sit
  inside a `group`/drawer that you expand to reveal the icon) and the
  `libayatana-appindicator` runtime library; Amber automatically bridges the
  Ayatana library to the legacy `libappindicator3` names dorkbox looks for,
  so no compat symlink is required. When no SNI host is on the session bus
  (or tray init takes too long), Amber skips the tray and logs why instead of
  blocking startup. `AMBER_TRAY_TYPE=Gtk|AppIndicator|AutoDetect`
  forces the backend and `AMBER_DISABLE_TRAY=1` skips the tray entirely.
- Notifications go through the OS-native channel: the freedesktop
  notification daemon (mako, dunst, swaync, GNOME Shell, …) via `notify-send`
  or `gdbus` on Linux — so they work on Hyprland/Wayland — `osascript` on
  macOS, and the AWT tray notification on Windows
- Mandatory passphrase lock (see Key storage below); on Linux Amber
  additionally runs under its own dedicated OS user, set up in-app on first
  open (see Running under a dedicated user below)
- Native desktop layout: sidebar navigation with an account switcher, dense
  list views, and keyboard shortcuts
- Light/dark theme using the Amber palette
- Fully localized UI in 14 languages, switchable live under Settings →
  Language. The Android `strings.xml` translations (and event-kind
  descriptions) are bundled verbatim under `resources/i18n/strings_<lang>.xml`
  and loaded by `core/Strings.kt`; desktop-only strings live in
  `strings_en.xml` (prefixed `d_`) and fall back to English in other locales

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
| Ctrl/⌘ + L | Lock |
| Ctrl/⌘ + M | Minimize to tray (keep running in the background) |
| Ctrl/⌘ + W | Same as Ctrl/⌘ + M |
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

### Passphrase lock (mandatory)

Amber requires a passphrase: on first run a setup screen asks for one before
anything else can be used, and installs that already have only the OS
credential store are migrated to it on the next launch. The AES master key
is then stored only wrapped (AES-256-GCM) under a key derived from your
passphrase with **Argon2id**, in `master.key.enc`; the plain keystore and its
credential-store/file password are deleted. The passphrase is never written
anywhere. You can change it under **Settings → Security**, but there is no
way to remove it and go back to unprotected storage.

With the lock on:

- Copying the data directory (or the credential store) is useless — without
  the passphrase there is no way to decrypt the keys.
- The per-account **database** (connected applications, permission grants,
  request history, relay logs) is also encrypted at rest with the master
  key — AES-256-GCM, with an `AMBERENC1:` header — so the metadata about
  which apps you sign for stays private too. Enabling the passphrase
  re-encrypts existing data immediately.
  (`settings.json` and `accounts.json` stay plaintext, but the private keys
  inside `accounts.json` are always encrypted with the master key.)
- Amber asks for the passphrase at startup and auto-locks after an idle
  timeout — 1 hour by default, selectable (5 min / 15 min / 1 hour / never)
  under **Settings → Security** — or immediately via **Lock now**. Locking
  evicts all key material from memory and disconnects the relays, so no
  request can be signed until you unlock again.
- Logging out of an account (which deletes its key from this device) asks
  for the passphrase first.

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

### Running under a dedicated user (Linux)

On Linux, Amber does not run as your login user: the first time it opens, it
asks for your password (sudo), creates a dedicated OS user, and re-launches
itself under that user. The process that holds your keys is then walled off
from the rest of your desktop session by the OS — other apps can no longer
read Amber's memory or files, closing the same-user-malware residual risk
described above (a process running as that user can still be attacked, of
course — this is isolation, not a security boundary against root).

What the first-open setup does (as root, once):

1. creates the dedicated user `amber` with its own home directory
   (`AMBER_USER=name` picks a different name),
2. moves your existing Amber data (`~/.local/share/amber`) into that home,
3. installs a root-owned launcher (`/usr/local/bin/amber-runas-<name>`) that
   execs exactly the Amber binary with only your session's socket locations
   (Wayland, X11/XWayland, D-Bus for tray and notifications) passed as
   arguments — never arbitrary code or environment,
4. installs a narrow sudoers rule (`/etc/sudoers.d/amber-runas-<name>`,
   validated with `visudo`) allowing your user to run that launcher as the
   dedicated user without a password,
5. re-launches Amber under the dedicated user.

Afterwards every launch switches to the dedicated user silently. If the
installed binary path changes (reinstall, update), the next open asks for
your password once to regenerate the launcher. Set
`AMBER_DISABLE_DEDICATED_USER=1` to skip the whole flow (useful for
`./gradlew :desktop:run`, which is skipped automatically since it launches a
bare `java` binary), and requires the `acl` package for `setfacl`.

## Tests

```bash
./gradlew :desktop:test              # unit tests
AMBER_E2E=1 ./gradlew :desktop:test  # + a NIP-46 round-trip over a public relay
```
