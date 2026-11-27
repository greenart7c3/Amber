package com.greenart7c3.nostrsigner.common

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionTypesTest {
    @Test
    fun `a NIP-46 method is granted under the type the request path looks up`() {
        assertEquals(listOf("NIP44_ENCRYPT"), expandPermissionTypes("nip44_encrypt"))
        assertEquals(listOf("SIGN_EVENT"), expandPermissionTypes("sign_event"))
        assertEquals(listOf("GET_PUBLIC_KEY"), expandPermissionTypes("get_public_key"))
        assertEquals(listOf("NIP44_V3_ENCRYPT"), expandPermissionTypes("nip44v3_encrypt"))
        assertEquals(listOf("NIP44_V3_DECRYPT"), expandPermissionTypes("NIP44v3_Decrypt"))
        assertEquals(listOf("SIGN_PSBT"), expandPermissionTypes("sign_psbt"))
    }

    @Test
    fun `content types and anything unknown are kept, upper-cased`() {
        assertEquals(listOf("ENCRYPT_EVENT"), expandPermissionTypes("encrypt_event"))
        assertEquals(listOf("DECRYPT_CLEAR_TEXT"), expandPermissionTypes("decrypt_clear_text"))
        assertEquals(listOf("ENCRYPT_TAG_ARRAY"), expandPermissionTypes("encrypt_tag_array"))
        assertEquals(listOf("SOMETHING_NEW"), expandPermissionTypes("something_new"))
    }

    @Test
    fun `a generic encrypt or decrypt grants both NIPs`() {
        assertEquals(listOf("NIP04_ENCRYPT", "NIP44_ENCRYPT"), expandPermissionTypes("encrypt"))
        assertEquals(listOf("NIP04_DECRYPT", "NIP44_DECRYPT"), expandPermissionTypes("decrypt"))
    }

    @Test
    fun `the type does not depend on the device language`() {
        val default = Locale.getDefault()
        try {
            val turkishUpperCasesIToADottedI = Locale.forLanguageTag("tr")
            Locale.setDefault(turkishUpperCasesIToADottedI)
            assertEquals(listOf("SIGN_EVENT"), expandPermissionTypes("sign_event"))
            assertEquals(listOf("DECRYPT_ZAP_EVENT"), expandPermissionTypes("decrypt_zap_event"))
            assertEquals(listOf("ENCRYPT_CLEAR_TEXT"), expandPermissionTypes("encrypt_clear_text"))
        } finally {
            Locale.setDefault(default)
        }
    }
}
