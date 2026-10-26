package com.greenart7c3.nostrsigner.models

import com.greenart7c3.nostrsigner.common.SignerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * A NIP-55 result copied to the clipboard is flagged sensitive and cleared
 * when its type is in [decryptSignerTypes]: those results are the decrypted
 * plaintext itself.
 */
class DecryptSignerTypesTest {
    @Test
    fun everyDecryptTypeIsSensitive() {
        assertEquals(
            setOf(SignerType.NIP04_DECRYPT, SignerType.NIP44_DECRYPT, SignerType.NIP44_V3_DECRYPT, SignerType.DECRYPT_ZAP_EVENT),
            decryptSignerTypes,
        )
    }

    @Test
    fun nonSecretResultsAreNot() {
        // Ciphertext, signatures and public keys stay ordinary clips.
        (encryptSignerTypes + SignerType.SIGN_EVENT + SignerType.GET_PUBLIC_KEY).forEach {
            assertFalse(it.name, it in decryptSignerTypes)
        }
    }
}
