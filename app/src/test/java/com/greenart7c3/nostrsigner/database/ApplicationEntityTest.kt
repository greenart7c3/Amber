package com.greenart7c3.nostrsigner.database

import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.Nip01Crypto
import org.junit.Assert.assertEquals
import org.junit.Test

class ApplicationEntityTest {

    @Test
    fun `localPubKey returns empty string for empty localKey`() {
        val app = ApplicationEntity.empty().copy(localKey = "")
        assertEquals("", app.localPubKey)
    }

    @Test
    fun `localPubKey returns empty string for invalid localKey`() {
        // "invalid-key" is not valid hex and not 32 bytes
        val app = ApplicationEntity.empty().copy(localKey = "invalid-key")
        assertEquals("", app.localPubKey)
    }

    @Test
    fun `localPubKey returns empty string for wrong sized hex localKey`() {
        // 31 bytes instead of 32
        val app = ApplicationEntity.empty().copy(localKey = "00".repeat(31))
        assertEquals("", app.localPubKey)
    }

    @Test
    fun `localPubKey returns valid pubkey for valid 32 byte hex localKey`() {
        val privKey = Nip01Crypto.privKeyCreate()
        val privKeyHex = privKey.toHexKey()
        val app = ApplicationEntity.empty().copy(localKey = privKeyHex)

        val expectedPubKey = KeyPair(privKey = privKey).pubKey.toHexKey()
        assertEquals(expectedPubKey, app.localPubKey)
    }
}
