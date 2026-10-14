package com.greenart7c3.nostrsigner

import com.greenart7c3.nostrsigner.SecureCryptoHelper.CiphertextMigration
import java.nio.BufferUnderflowException
import javax.crypto.AEADBadTagException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Per-value decisions of the wrapping-key rotation sweep (GHSA-wv7c-hq2j-ppc5).
 * The previous key may only be deleted when every value is either already on
 * the current key or provably unreadable by both, so anything else must come
 * back as [CiphertextMigration.Failed].
 */
class KeyRotationMigrationTest {
    private val wrongKey: (String) -> String = { throw AEADBadTagException("tag mismatch") }
    private val deviceLocked: (String) -> String = { throw java.security.InvalidKeyException("device locked") }

    private fun migrate(
        decryptCurrent: (String) -> String,
        decryptPrevious: (String) -> String,
        encryptCurrent: (String) -> String = { "new:$it" },
    ) = SecureCryptoHelper.migrateCiphertext("ciphertext", decryptCurrent, decryptPrevious, encryptCurrent)

    @Test
    fun `value already on the current key is left alone`() {
        assertEquals(CiphertextMigration.Current, migrate({ "nsec" }, { error("must not be called") }))
    }

    @Test
    fun `value on the previous key is re-encrypted with the current key`() {
        assertEquals(CiphertextMigration.Rewrite("new:nsec"), migrate(wrongKey, { "nsec" }))
    }

    @Test
    fun `value neither key can decrypt is skipped`() {
        assertEquals(CiphertextMigration.Unreadable, migrate(wrongKey, wrongKey))
    }

    @Test
    fun `malformed value is skipped`() {
        val truncated: (String) -> String = { throw BufferUnderflowException() }
        val badBase64: (String) -> String = { throw IllegalArgumentException("bad base-64") }
        assertEquals(CiphertextMigration.Unreadable, migrate(truncated, badBase64))
    }

    @Test
    fun `current key unusable fails instead of guessing`() {
        assertTrue(migrate(deviceLocked, { "nsec" }) is CiphertextMigration.Failed)
    }

    @Test
    fun `previous key unusable fails so it is not deleted`() {
        assertTrue(migrate(wrongKey, deviceLocked) is CiphertextMigration.Failed)
    }

    @Test
    fun `re-encryption failure fails`() {
        assertTrue(migrate(wrongKey, { "nsec" }, { throw java.security.ProviderException("keystore") }) is CiphertextMigration.Failed)
    }
}
