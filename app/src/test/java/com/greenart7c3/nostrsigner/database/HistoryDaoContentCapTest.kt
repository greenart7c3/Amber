package com.greenart7c3.nostrsigner.database

import com.greenart7c3.nostrsigner.common.SignerType
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryDaoContentCapTest {
    private fun entity(type: String, content: String) = HistoryEntity(
        id = 0,
        pkKey = "com.example.app",
        type = type,
        kind = null,
        time = 1_000L,
        accepted = true,
        content = content,
    )

    @Test
    fun `contentToStore keeps normal sized ciphertexts and drops oversized ones`() {
        val withinCap = "x".repeat(HistoryDao.MAX_STORED_CIPHERTEXT_LENGTH)
        val overCap = "y".repeat(HistoryDao.MAX_STORED_CIPHERTEXT_LENGTH + 1)

        assertEquals(withinCap, HistoryDao.contentToStore(entity(SignerType.NIP44_V3_ENCRYPT.name, withinCap)))
        assertEquals("", HistoryDao.contentToStore(entity(SignerType.NIP44_V3_ENCRYPT.name, overCap)))
        assertEquals("", HistoryDao.contentToStore(entity(SignerType.NIP44_DECRYPT.name, overCap)))
    }

    @Test
    fun `contentToStore truncates non-crypto content at 500 chars`() {
        val longEvent = "e".repeat(800)
        assertEquals(longEvent.take(500), HistoryDao.contentToStore(entity(SignerType.SIGN_EVENT.name, longEvent)))
    }
}
