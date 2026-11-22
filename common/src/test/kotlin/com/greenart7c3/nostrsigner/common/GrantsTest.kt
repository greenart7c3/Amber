package com.greenart7c3.nostrsigner.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GrantsTest {
    private val basic = listOf(RequestedPermission("get_public_key", null), RequestedPermission("sign_event", 1))
    private val nothingStored: (String, Int?) -> Boolean = { _, _ -> false }

    private fun forever(type: String, kind: Int?) = Grant(type, kind, acceptable = true, rememberType = REMEMBER_ALWAYS, acceptUntil = REMEMBER_FOREVER, rejectUntil = 0)

    @Test
    fun `basic actions grant the basic permissions for good`() {
        assertEquals(listOf(forever("GET_PUBLIC_KEY", null), forever("SIGN_EVENT", 1)), signPolicyGrants(0, basic, emptyList(), nothingStored))
    }

    @Test
    fun `manual approval grants only the checked permissions, a generic encrypt under both NIPs`() {
        assertEquals(
            listOf(forever("NIP04_ENCRYPT", null), forever("NIP44_ENCRYPT", null)),
            signPolicyGrants(1, basic, listOf(RequestedPermission("encrypt", null)), nothingStored),
        )
    }

    @Test
    fun `full trust grants nothing and an unknown policy is not applied`() {
        assertEquals(emptyList<Grant>(), signPolicyGrants(SIGN_POLICY_FULLY_TRUST, basic, basic, nothingStored))
        assertNull(signPolicyGrants(9, basic, basic, nothingStored))
    }

    @Test
    fun `a permission already stored, or asked twice, is granted once`() {
        val stored: (String, Int?) -> Boolean = { type, kind -> type == "SIGN_EVENT" && kind == 1 }
        assertEquals(listOf(forever("GET_PUBLIC_KEY", null)), signPolicyGrants(0, basic + basic, emptyList(), stored))
    }

    @Test
    fun `a NIP-44 decrypt remembered for the whole NIP replaces the content-type grant`() {
        val remembered = rememberedGrant(SignerType.NIP44_DECRYPT, "DECRYPT_CLEAR_TEXT", wholeNip = true, kind = null, accepted = true, rememberType = REMEMBER_ALWAYS, relay = "", now = 100)

        assertEquals(Grant("NIP44_DECRYPT", null, true, REMEMBER_ALWAYS, REMEMBER_FOREVER, 0), remembered.grant)
        assertTrue(remembered.replaces("DECRYPT_CLEAR_TEXT", null, ""))
        assertTrue(remembered.replaces("NIP44_DECRYPT", null, ""))
        assertFalse(remembered.replaces("NIP04_DECRYPT", null, ""))
    }

    @Test
    fun `a NIP-44 decrypt remembered for its content type replaces the whole-NIP grant`() {
        val remembered = rememberedGrant(SignerType.NIP44_DECRYPT, "DECRYPT_TAG_ARRAY", wholeNip = false, kind = null, accepted = false, rememberType = 5, relay = "", now = 100)

        assertEquals(Grant("DECRYPT_TAG_ARRAY", null, false, 5, 0, 100 + 60 * 60), remembered.grant)
        assertTrue(remembered.replaces("NIP44_DECRYPT", null, ""))
        assertFalse(remembered.replaces("DECRYPT_CLEAR_TEXT", null, ""))
    }

    @Test
    fun `other types are stored under their own name, narrowed by kind and relay`() {
        val sign = rememberedGrant(SignerType.SIGN_EVENT, "SIGN_EVENT", wholeNip = false, kind = 22242, accepted = true, rememberType = REMEMBER_ALWAYS, relay = "relay.example", now = 0)
        assertEquals(Grant("SIGN_EVENT", 22242, true, REMEMBER_ALWAYS, REMEMBER_FOREVER, 0, "relay.example"), sign.grant)
        assertTrue(sign.replaces("SIGN_EVENT", 22242, "relay.example"))
        assertFalse(sign.replaces("SIGN_EVENT", 22242, "other.example"))
        assertFalse(sign.replaces("SIGN_EVENT", 1, "relay.example"))

        // A NIP-44 v3 grant is keyed by its context kind, whatever the scope argument says.
        assertEquals("NIP44_V3_ENCRYPT", rememberedGrant(SignerType.NIP44_V3_ENCRYPT, "NIP44_V3_ENCRYPT", wholeNip = false, kind = 7, accepted = true, rememberType = REMEMBER_ALWAYS, relay = "", now = 0).grant.type)
    }
}
