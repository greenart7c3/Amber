package com.greenart7c3.nostrsigner.database

import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.SecureCryptoHelper

/**
 * Envelope-encrypts / envelope-decrypts the two NIP-46 secret columns of
 * [ApplicationEntity] ([secret] and [localKey]) using the Keystore-backed
 * AES-256-GCM key in [SecureCryptoHelper].
 *
 * Mitigates GHSA-5fjp-ghh8-wch8 (CWE-312): both columns previously lived as
 * cleartext Room TEXT on the per-account `amber_db_<npub>` database, so any
 * reader of the app's internal storage (rooted device, privilege-escalating
 * malware, or a future bug exporting the DB) recovered every bunker connection
 * secret and every locally generated connection private key.
 *
 * Sentinel rule for empty values: `""` stays `""` at rest. Mirrors the WebDAV
 * password idiom at `LocalPreferences.kt:661-681` and preserves:
 * - `ApplicationDao` `WHERE localKey != ''` semantics
 *   (used by `NotificationSubscription.kt:90-96` to enumerate connections).
 * - `ApplicationEntity.localPubKey` derivation
 *   (`if (localKey.isNotEmpty()) localPubKeyFromPrivKey(localKey) else ""`).
 * - `ApplicationEntity.shouldShowRelays` (`secret.isNotEmpty()`).
 *
 * The mappers are applied at the DAO boundary so every consumer of
 * `ApplicationEntity` / `ApplicationWithPermissions` continues to see the
 * plaintext values it already expects. The only at-rest reader is Room itself.
 *
 * Failure mode: if [SecureCryptoHelper.decryptBlocking] throws (e.g. the
 * AndroidKeyStore key has become permanently unusable after a device KeyMint
 * upgrade), the row is returned with the raw ciphertext in place. The caller
 * will then treat the secret as a non-matching string (lookup miss) and the
 * corrupted localKey as a non-hex value, which surfaces as a connection
 * failure rather than a crash — matching the `InvalidKeyException`/log-and-skip
 * convention in `LocalPreferences.loadFromEncryptedStorage`.
 */
private fun String.encryptField(): String = if (this.isEmpty()) this else SecureCryptoHelper.encryptBlocking(this)

private fun String.decryptField(): String {
    if (this.isEmpty()) return this
    return try {
        SecureCryptoHelper.decryptBlocking(this)
    } catch (e: Exception) {
        // Leave the ciphertext in place rather than crashing the DAO read.
        // See class kdoc for the recovery contract.
        AmberLog.w(Amber.TAG, "ApplicationEntityCrypto: failed to decrypt field", e)
        this
    }
}

/** Encrypts [secret] and [localKey] for Room persistence. Inverse of [decryptFromStorage]. */
fun ApplicationEntity.encryptForStorage(): ApplicationEntity = copy(
    secret = secret.encryptField(),
    localKey = localKey.encryptField(),
)

/** Decrypts [secret] and [localKey] after a Room read. Inverse of [encryptForStorage]. */
fun ApplicationEntity.decryptFromStorage(): ApplicationEntity = copy(
    secret = secret.decryptField(),
    localKey = localKey.decryptField(),
)

/** Decrypts the embedded [ApplicationWithPermissions.application] after a Room read. */
fun ApplicationWithPermissions.decryptFromStorage(): ApplicationWithPermissions = copy(application = application.decryptFromStorage())
