package com.greenart7c3.nostrsigner.ui.components

import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.models.ClearTextEncryptedDataKind
import com.greenart7c3.nostrsigner.models.PrivateZapEncryptedDataKind
import org.junit.Assert.assertEquals
import org.junit.Test

class PlainCardTextTest {
    private val zapRequest = """{"id":"${"0".repeat(64)}","pubkey":"${"ab".repeat(32)}","created_at":1,"kind":9733,"tags":[["p","${"cd".repeat(32)}"]],"content":"thanks for the post","sig":"${"0".repeat(128)}"}"""

    @Test
    fun `a private zap shows its message, not the event JSON`() {
        assertEquals("thanks for the post", plainCardText(SignerType.DECRYPT_ZAP_EVENT, PrivateZapEncryptedDataKind(zapRequest)))
    }

    @Test
    fun `a private zap result that is not an event is shown as is`() {
        assertEquals("Could not decrypt the message", plainCardText(SignerType.DECRYPT_ZAP_EVENT, PrivateZapEncryptedDataKind("Could not decrypt the message")))
    }

    @Test
    fun `an encrypt shows its plaintext and a decrypt its result`() {
        assertEquals("hello", plainCardText(SignerType.NIP44_ENCRYPT, ClearTextEncryptedDataKind("hello", "ciphertext")))
        assertEquals("plain", plainCardText(SignerType.NIP44_DECRYPT, ClearTextEncryptedDataKind("ciphertext", "plain")))
        assertEquals("", plainCardText(SignerType.NIP44_DECRYPT, null))
    }
}
