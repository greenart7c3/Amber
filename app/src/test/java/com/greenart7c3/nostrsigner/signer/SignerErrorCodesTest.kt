package com.greenart7c3.nostrsigner.signer

import org.junit.Assert.assertEquals
import org.junit.Test

class SignerErrorCodesTest {
    @Test
    fun `encode then decode round trips each code`() {
        listOf(
            SignerErrorCodes.GENERIC,
            SignerErrorCodes.NULL_RESULT,
            SignerErrorCodes.KEYSTORE_FAILED,
            SignerErrorCodes.NO_KEY,
        ).forEach { code ->
            assertEquals(code, SignerErrorCodes.codeOf(SignerErrorCodes.encode(code, "detail")))
        }
    }

    @Test
    fun `null message decodes to GENERIC`() {
        assertEquals(SignerErrorCodes.GENERIC, SignerErrorCodes.codeOf(null))
    }

    @Test
    fun `unencoded message decodes to GENERIC`() {
        // Incidental IllegalStateExceptions (no "code:" prefix) must not be
        // misread as a specific signer error.
        assertEquals(SignerErrorCodes.GENERIC, SignerErrorCodes.codeOf("user rejected"))
    }

    @Test
    fun `detail may contain colons without breaking the code`() {
        val msg = SignerErrorCodes.encode(SignerErrorCodes.NO_KEY, "npub1:abc:def")
        assertEquals(SignerErrorCodes.NO_KEY, SignerErrorCodes.codeOf(msg))
    }
}
