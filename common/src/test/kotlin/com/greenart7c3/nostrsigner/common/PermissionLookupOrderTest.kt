package com.greenart7c3.nostrsigner.common

import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionLookupOrderTest {
    @Test
    fun `a sign request tries its kind, then the whole NIP the kind belongs to`() {
        assertEquals(
            listOf(PermissionKey("SIGN_EVENT", 1), PermissionKey("NIP", 1)),
            permissionLookupOrder(SignerType.SIGN_EVENT, 1, signNip = 1),
        )
        assertEquals(listOf(PermissionKey("SIGN_EVENT", 30078)), permissionLookupOrder(SignerType.SIGN_EVENT, 30078))
    }

    @Test
    fun `a NIP-44 v3 request falls back only to the all-kinds grant`() {
        assertEquals(
            listOf(PermissionKey("NIP44_V3_DECRYPT", 14), PermissionKey("NIP44_V3_DECRYPT")),
            permissionLookupOrder(SignerType.NIP44_V3_DECRYPT, 14),
        )
        assertEquals(listOf(PermissionKey("NIP44_V3_ENCRYPT")), permissionLookupOrder(SignerType.NIP44_V3_ENCRYPT, null))
    }

    @Test
    fun `a NIP-04 or NIP-44 request tries its content type, then the whole method, then the NIP`() {
        assertEquals(
            listOf(PermissionKey("DECRYPT_TAG_ARRAY"), PermissionKey("NIP44_DECRYPT"), PermissionKey("NIP", 44)),
            permissionLookupOrder(SignerType.NIP44_DECRYPT, null, contentType = "DECRYPT_TAG_ARRAY"),
        )
        assertEquals(
            listOf(PermissionKey("ENCRYPT_CLEAR_TEXT"), PermissionKey("NIP04_ENCRYPT"), PermissionKey("NIP", 4)),
            permissionLookupOrder(SignerType.NIP04_ENCRYPT, null, contentType = "ENCRYPT_CLEAR_TEXT"),
        )
    }

    @Test
    fun `other requests have the single kind-less grant of their type`() {
        listOf(SignerType.GET_PUBLIC_KEY, SignerType.PING, SignerType.SIGN_PSBT, SignerType.DECRYPT_ZAP_EVENT).forEach {
            assertEquals(listOf(PermissionKey(it.name)), permissionLookupOrder(it, null))
        }
    }
}
