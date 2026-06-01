# Security Policy

Amber is a Nostr signer for Android that holds user private keys on the
device and signs / encrypts / decrypts on behalf of other apps over
`nostrsigner://` intents, a `ContentProvider` (NIP-55), and NIP-46 bunker
relays. We take security reports seriously and appreciate responsible
disclosure.

## Supported Versions

Only the latest release receives security fixes. We do not backport patches to
older versions. Fixes also land on the `master` branch ahead of the next
release.

| Version        | Supported |
| -------------- | --------- |
| Latest release | ✅        |
| `master`       | ✅        |
| Older          | ❌        |

This covers all artifacts built from this repository, including both the
`amber` (online) and `offline` build flavors of the Android app.

## Reporting a Vulnerability

**Please do not report security vulnerabilities through public GitHub issues.**

Use GitHub's private vulnerability reporting instead:

👉 [Report a vulnerability](https://github.com/greenart7c3/Amber/security/advisories/new)

This keeps the details private until a fix is ready and coordinates disclosure
between you and the maintainers.

### What to include

To help us triage quickly, please provide:

- A clear description of the vulnerability and its impact (what an attacker
  could achieve — e.g. private key exposure, unauthorized signing,
  bypassing the permission/approval UI, leaking decrypted DMs).
- Affected ingestion path: `nostrsigner://` intent, `ContentProvider` (NIP-55),
  or NIP-46 bunker relay.
- Affected build flavor (`amber` or `offline`), version, commit SHA, and
  Android version / device.
- Steps to reproduce, a proof of concept, or a failing test. A minimal
  caller app or relay event payload is especially helpful.
- Any suggested remediation.

### What to expect

- **Acknowledgement within 48 hours** of your report.
- We will investigate and keep you informed of progress.
- We will coordinate a release and disclosure timeline with you. We aim to
  ship a fix within 90 days for high and critical issues, faster when
  private key material, signing authorization, or DM confidentiality is at
  risk.
- Credit will be given to reporters in the security advisory and release
  notes (unless you prefer to remain anonymous).

## Scope

In scope:

- Source code in this repository.
- Released APKs (both `amber` and `offline` flavors) built from this
  repository and distributed via GitHub Releases, F-Droid, Obtainium, or
  Zap Store.
- Key storage and the encrypted account database (per-`npub`
  `SharedPreferences`, encrypted secret-key handling, in-memory caches).
- Cryptographic handling: Schnorr signing, NIP-04 / NIP-44 / NIP-17
  encryption, and NIP-49 encrypted-key import/export.
- Authorization logic: the permission model
  (`ApplicationEntity` / `ApplicationPermissionsEntity`), auto-accept /
  auto-reject rules, kind-specific permissions, and the approval UI.
- All three request-ingestion paths and their convergence on
  `Account.sign()` / encrypt / decrypt:
  - `nostrsigner://` intents via `SignerActivity` / `IntentUtils`.
  - `ContentProvider` IPC via `SignerProvider` (NIP-55).
  - NIP-46 relay events (kind 24133) via `NotificationSubscription` /
    `EventNotificationConsumer` / `BunkerRequestUtils`.
- Relay client behavior that could leak private data, bypass the
  permission system, or sign without the user's authorization.

Out of scope:

- Vulnerabilities in third-party Nostr clients, relays, bridges, or other
  signers not built from this repository.
- Issues that require a rooted device, a compromised host OS, malware with
  accessibility / overlay privileges already granted, or physical access
  with the device unlocked.
- Weaknesses inherent to the Nostr protocol or its NIPs — please report
  these upstream at <https://github.com/nostr-protocol/nips>.
- Denial-of-service caused by a malicious NIP-46 relay the user has
  explicitly connected to.
- Social-engineering and phishing that does not exploit an app-level flaw
  (e.g. tricking a user into approving a signing request they read and
  accepted).

## Disclosure Policy

We follow a coordinated disclosure model. We ask that you:

- Give us reasonable time to investigate and release a fix before any public
  disclosure.
- Avoid accessing or modifying other users' data during research.
- Only interact with accounts and keys you own or have explicit permission
  to test.
- Act in good faith.

We will not pursue or support legal action against researchers who follow
this policy. We commit to responding promptly and treating all reports
seriously.

Thank you for helping keep Amber and its users safe.
