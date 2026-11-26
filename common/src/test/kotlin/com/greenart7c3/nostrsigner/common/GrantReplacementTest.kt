package com.greenart7c3.nostrsigner.common

import org.junit.Assert.assertEquals
import org.junit.Test

class GrantReplacementTest {
    private data class Grant(val type: String, val kind: Int? = null, val relay: String = "")

    private val grantsAnAppMayHold = listOf(
        Grant("SIGN_EVENT", 1),
        Grant("SIGN_EVENT", 7),
        Grant("SIGN_EVENT", 22242, "wss://a.example"),
        Grant("SIGN_EVENT", 22242, "wss://b.example"),
        Grant("SIGN_EVENT", 22242, "*"),
        Grant("SIGN_EVENT", 22242),
        Grant("SIGN_EVENT"),
        Grant("NIP44_DECRYPT"),
        Grant("DECRYPT_CLEAR_TEXT"),
        Grant("DECRYPT_EVENT"),
        Grant("NIP44_V3_ENCRYPT", 1059),
        Grant("NIP44_V3_ENCRYPT", 14),
        Grant("NIP44_V3_ENCRYPT"),
        Grant("GET_PUBLIC_KEY"),
    )

    private fun replaced(type: String, kind: Int? = null, relay: String = "", otherScopeType: String? = null) = grantsAnAppMayHold.filter { replacesGrant(type, kind, relay, otherScopeType, it.type, it.kind, it.relay) }

    @Test
    fun `a sign grant replaces only its own kind`() {
        assertEquals(listOf(Grant("SIGN_EVENT", 1)), replaced("SIGN_EVENT", 1))
    }

    @Test
    fun `relay auth for all relays replaces every relay's grant of that kind`() {
        assertEquals(
            listOf(
                Grant("SIGN_EVENT", 22242, "wss://a.example"),
                Grant("SIGN_EVENT", 22242, "wss://b.example"),
                Grant("SIGN_EVENT", 22242, "*"),
                Grant("SIGN_EVENT", 22242),
            ),
            replaced("SIGN_EVENT", 22242, "*"),
        )
    }

    @Test
    fun `relay auth for one relay replaces only that relay's grant`() {
        assertEquals(listOf(Grant("SIGN_EVENT", 22242, "wss://a.example")), replaced("SIGN_EVENT", 22242, "wss://a.example"))
    }

    @Test
    fun `a kind grant without a relay replaces only the grant without one`() {
        assertEquals(listOf(Grant("SIGN_EVENT", 22242)), replaced("SIGN_EVENT", 22242))
    }

    @Test
    fun `a kind-less sign grant replaces nothing, sign grants are kept per kind`() {
        assertEquals(emptyList<Grant>(), replaced("SIGN_EVENT"))
    }

    @Test
    fun `a whole-NIP grant replaces its own type and the content type it widens`() {
        assertEquals(listOf(Grant("NIP44_DECRYPT"), Grant("DECRYPT_CLEAR_TEXT")), replaced("NIP44_DECRYPT", otherScopeType = "DECRYPT_CLEAR_TEXT"))
    }

    @Test
    fun `a content-type grant replaces its own type and the whole-NIP grant`() {
        assertEquals(listOf(Grant("NIP44_DECRYPT"), Grant("DECRYPT_CLEAR_TEXT")), replaced("DECRYPT_CLEAR_TEXT", otherScopeType = "NIP44_DECRYPT"))
    }

    @Test
    fun `a NIP-44 v3 kind grant replaces only that kind, the all-kinds grant every kind`() {
        assertEquals(listOf(Grant("NIP44_V3_ENCRYPT", 14)), replaced("NIP44_V3_ENCRYPT", 14))
        assertEquals(
            listOf(Grant("NIP44_V3_ENCRYPT", 1059), Grant("NIP44_V3_ENCRYPT", 14), Grant("NIP44_V3_ENCRYPT")),
            replaced("NIP44_V3_ENCRYPT", otherScopeType = "NIP44_V3_ENCRYPT"),
        )
    }

    @Test
    fun `any other kind-less grant replaces only its own type`() {
        assertEquals(listOf(Grant("GET_PUBLIC_KEY")), replaced("GET_PUBLIC_KEY"))
    }
}
