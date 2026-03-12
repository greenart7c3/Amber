# Security Review Report — Amber Android App

**Date:** 2026-03-12
**Reviewer:** Claude (automated security analysis)
**Scope:** Full codebase review of NIP-46 Nostr signer app

---

## Summary

| Severity | Count |
|----------|-------|
| Critical | 5 |
| High | 5 |
| Medium | 5 |
| Low | 4 |

The cryptographic core is solid (AES-256-GCM, hardware-backed keystore, parameterized Room queries, per-account isolation). The main weaknesses are in the **inter-process communication surface** (exported ContentProvider, intent handling) and **data egress controls** (cleartext traffic, unvalidated callback URLs, clipboard).

---

## Critical

### C1 — Cleartext Traffic Enabled Globally
**File:** `app/src/main/AndroidManifest.xml:30`

`android:usesCleartextTraffic="true"` allows HTTP for all domains, enabling MITM attacks on relay connections.

**Fix:**
- Set `usesCleartextTraffic="false"` in manifest.
- Create `res/xml/network_security_config.xml` with per-domain cleartext exceptions only if strictly required (e.g., `ws://` relay fallback).
- Prefer `wss://` for all relay connections.

---

### C2 — Exported ContentProvider Without Authentication
**File:** `app/src/main/AndroidManifest.xml:54-59`, `SignerProvider.kt`

`SignerProvider` is exported without a `android:permission` requirement. Any installed app can call it to sign arbitrary events, decrypt content, or retrieve the public key. `callingPackage` is not cryptographically verified.

**Fix:**
- Declare a custom signature-level permission and apply it to the provider:
  ```xml
  <permission android:name="com.greenart7c3.nostrsigner.SIGN"
      android:protectionLevel="signature|signatureOrSystem" />
  <provider android:permission="com.greenart7c3.nostrsigner.SIGN" ... />
  ```
- Verify caller identity via `Binder.getCallingUid()` + `getPackageManager().getNameForUid()`.
- Require explicit user approval for first-time callers.

---

### C3 — Account Spoofing via `current_user` Intent Extra
**File:** `SignerActivity.kt:130-150`

The `current_user` intent extra is accepted without verifying the caller owns that account. An attacker app can cause signing to occur under an arbitrary npub.

**Fix:**
- Validate `current_user` against `LocalPreferences.allSavedAccounts()`.
- Require user confirmation before switching accounts on behalf of an external caller.
- Bind package→account permissions so a package can only request its previously authorized account.

---

### C4 — Unvalidated Callback URL Sends Signed Data to Arbitrary Destinations
**File:** `IntentUtils.kt:796-820`

Results (signatures, decrypted content) are sent via `Intent.ACTION_VIEW` to a caller-supplied `callBackUrl` with no validation:

```kotlin
intent.data = (intentData.callBackUrl + Uri.encode(value)).toUri()
context.startActivity(intent)
```

An attacker can supply a callback pointing at their own app or a web page to harvest signed events.

**Fix:**
- Validate `callBackUrl` against an allowlist tied to the requesting package's declared redirect URIs.
- Use `setComponent()` with an explicit package/class instead of implicit `ACTION_VIEW`.
- Show the callback destination to the user before dispatching.

---

### C5 — Private Key Copied to Clipboard Without Auto-Clear
**File:** `Account.kt:113-131`

`nsec` is written to the system clipboard with no time limit, leaving it readable by any app that polls clipboard access.

**Fix:**
- Schedule clipboard clear after 60 seconds:
  ```kotlin
  Handler(Looper.getMainLooper()).postDelayed({
      clipboardManager.setPrimaryClip(ClipData.newPlainText("", ""))
  }, 60_000)
  ```
- On API 33+, use `ClipDescription.EXTRA_IS_SENSITIVE` to suppress clipboard previews.

---

## High

### H1 — TOCTOU Race Condition in Permission Check
**File:** `SignerProvider.kt:99-128`

Permission is read from the database and then signing proceeds in a separate step. Another thread could revoke the permission between the check and the cryptographic operation.

**Fix:** Wrap the check + sign operation in a `@Transaction`-annotated Room function or use a `Mutex` per account to serialize the check-use pair.

---

### H2 — Silent Account Switch Without User Confirmation
**File:** `SignerActivity.kt:137-156`

Accounts are switched automatically when a requesting app supplies a different `current_user`. The user has no visibility into this.

**Fix:** Show a confirmation dialog ("App X is requesting to sign as `npub1…`. Switch account?") before calling `switchUser()`.

---

### H3 — `runBlocking` / Busy-Wait in ContentProvider Query Thread
**File:** `SignerProvider.kt:44-56, 128, 355`

`Thread.sleep(1000)` polling and `runBlocking { account.signString(...) }` inside `query()` can cause ANR errors and resource exhaustion.

**Fix:** Return a pending cursor immediately; deliver results asynchronously. Alternatively, return `null` when the app is not ready and let callers retry with backoff.

---

### H4 — Cloud Backup Exclusions Not Configured
**File:** `app/src/main/res/xml/data_extraction_rules.xml`

The file is empty. Even with `android:allowBackup="false"`, device-to-device transfer rules are separate and may allow DataStore / SharedPreferences migration.

**Fix:**
```xml
<data-extraction-rules>
  <cloud-backup>
    <exclude domain="sharedpref" path="."/>
    <exclude domain="database" path="."/>
    <exclude domain="file" path="."/>
  </cloud-backup>
  <device-transfer>
    <exclude domain="sharedpref" path="."/>
    <exclude domain="database" path="."/>
    <exclude domain="file" path="."/>
  </device-transfer>
</data-extraction-rules>
```

---

### H5 — Exported Activity Accepts `nostrsigner://` Deep Links Without Origin Validation
**File:** `AndroidManifest.xml:72-89`

Any web page or app can fire `nostrsigner://…` and inject a signing request. There is no verification of the link origin.

**Fix:**
- Call `intent.getReferrer()` and validate it against a known-safe origin list.
- Log all deep link invocations with the referrer.
- Consider migrating to Android App Links (`https://` scheme + Digital Asset Links) for origin-verified deep links.

---

## Medium

### M1 — Input Size Not Bounded in `decodeData()`
**File:** `IntentUtils.kt:61-68`

Arbitrarily large strings are decoded without size limits, risking OOM and DoS.

**Fix:** Enforce a maximum payload size (e.g., 1 MB) before calling `URLDecoder.decode()`.

---

### M2 — `sortOrder` Abused as Account Identifier Parameter
**File:** `SignerProvider.kt:59-67`

The ContentProvider `sortOrder` column is repurposed to pass an npub hex, breaking the standard ContentProvider contract and creating confusion.

**Fix:** Use a dedicated `projection` column or a `Bundle` extras mechanism. Document the parameter contract explicitly.

---

### M3 — Magic String `"Amber"` Used as Access Control Gate
**File:** `SignerProvider.kt:399`

```kotlin
if (selection != "Amber") return null
```

Undocumented string check as a pseudo-permission is fragile and provides no real security.

**Fix:** Replace with a proper `android:permission` on the URI path, or verify the caller's signing certificate.

---

### M4 — Sensitive Relay URLs and Methods Written to Logcat Unconditionally
**File:** `BunkerRequestUtils.kt:142, 169, 174`

Relay URLs and NIP-46 method names are logged at `Log.d` level without a debug-build guard.

**Fix:**
```kotlin
if (BuildConfig.DEBUG) {
    Log.d(TAG, "Sending response to relays …")
}
```
Redact full URLs and method names from any crash-reporting logs.

---

### M5 — Crypto Operations Potentially on UI Thread
**File:** `IntentUtils.kt:184-207`

`encryptOrDecryptData()` is called without an explicit dispatcher. If invoked from a composable or UI handler, it can cause ANR.

**Fix:** Ensure all crypto calls are dispatched to `Dispatchers.IO` or `Amber.instance.applicationIOScope`.

---

## Low

### L1 — StrongBox Fallback Is Silent
**File:** `SecureCryptoHelper.kt:78-93`

Falling back from hardware-backed (StrongBox) to software key storage is silent. Users remain unaware of the degraded security level.

**Fix:** Log the fallback at `Log.w` level in debug builds and consider surfacing a one-time user notification ("This device does not have a Secure Enclave; keys are stored in software").

---

### L2 — PIN Stored as Plaintext in SharedPreferences
**File:** `LocalPreferences.kt:160-176`

The PIN is stored as a raw string, whereas private keys use `SecureCryptoHelper`.

**Fix:** Encrypt the PIN with `SecureCryptoHelper.encrypt()` before storing, or replace PIN auth with Android BiometricPrompt backed by a keystore key.

---

### L3 — Permission Expiry Uses Unadjusted Device Clock
**File:** `IntentUtils.kt:936-950`

`TimeUtils.now()` is the raw device system clock. A skewed or manipulated clock affects when time-bound permissions expire.

**Fix:** Add a ±5-minute grace period; log when a permission expires due to time to aid debugging.

---

### L4 — Single Global Mutex Serializes All Crypto Operations
**File:** `SecureCryptoHelper.kt:25-43`

One `Mutex` gates every encrypt/decrypt call across all accounts, creating a bottleneck.

**Fix:** Use a `ConcurrentHashMap<String, Mutex>` keyed by account npub to allow per-account parallelism.

---

## Positive Findings

The following controls are correctly implemented:

- **AES-256-GCM with proper IV generation** for DataStore encryption.
- **Hardware-backed keystore (StrongBox)** attempted before software fallback.
- **Per-account database isolation** (`amber_db_${npub}`) prevents cross-account data leakage.
- **`android:allowBackup="false"`** prevents naive adb backup of sensitive data.
- **Room parameterized queries** throughout — no raw SQL injection vectors found.
- **No hardcoded secrets or API keys** found in source.
- **`applicationIOScope`** with `SupervisorJob` used consistently for background work.
