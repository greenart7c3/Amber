package com.greenart7c3.nostrsigner.service

import com.greenart7c3.nostrsigner.database.ApplicationPermissionsEntity
import com.vitorpamplona.quartz.utils.TimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentUtilsTest {

    // --- decodeData ---

    @Test
    fun `decodeData removes nostrsigner prefix`() {
        val result = IntentUtils.decodeData("nostrsigner:hello")
        assertEquals("hello", result)
    }

    @Test
    fun `decodeData URL-decodes percent-encoded characters`() {
        val result = IntentUtils.decodeData("nostrsigner:hello%20world")
        assertEquals("hello world", result)
    }

    @Test
    fun `decodeData with replace=true preserves literal plus sign`() {
        // replace=true swaps '+' → '%2b' before URLDecoder runs, so '+' is kept
        val result = IntentUtils.decodeData("nostrsigner:hello+world", replace = true)
        assertEquals("hello+world", result)
    }

    @Test
    fun `decodeData with replace=false treats plus as space`() {
        // replace=false lets URLDecoder treat '+' as a space (standard form-encoding)
        val result = IntentUtils.decodeData("nostrsigner:hello+world", replace = false)
        assertEquals("hello world", result)
    }

    @Test
    fun `decodeData with decodeData=false and replace=false only strips prefix`() {
        val result = IntentUtils.decodeData("nostrsigner:hello%20world", replace = false, decodeData = false)
        assertEquals("hello%20world", result)
    }

    @Test
    fun `decodeData with decodeData=false and replace=true strips prefix and swaps plus`() {
        val result = IntentUtils.decodeData("nostrsigner:hello+world", replace = true, decodeData = false)
        assertEquals("hello%2bworld", result)
    }

    @Test
    fun `decodeData handles input without nostrsigner prefix`() {
        val result = IntentUtils.decodeData("hello%20world")
        assertEquals("hello world", result)
    }

    @Test
    fun `decodeData returns empty string for bare nostrsigner prefix`() {
        val result = IntentUtils.decodeData("nostrsigner:")
        assertEquals("", result)
    }

    @Test
    fun `decodeData decodes multiple percent-encoded sequences`() {
        val result = IntentUtils.decodeData("nostrsigner:foo%3Dbar%26baz%3D1")
        assertEquals("foo=bar&baz=1", result)
    }

    // --- isUrlEncoded ---

    @Test
    fun `isUrlEncoded returns true for lowercase hex percent-encoding`() {
        assertTrue(IntentUtils.isUrlEncoded("hello%20world"))
    }

    @Test
    fun `isUrlEncoded returns true for uppercase hex percent-encoding`() {
        assertTrue(IntentUtils.isUrlEncoded("hello%2Bworld"))
    }

    @Test
    fun `isUrlEncoded returns true for mixed-case hex percent-encoding`() {
        assertTrue(IntentUtils.isUrlEncoded("hello%3aworld"))
    }

    @Test
    fun `isUrlEncoded returns false for plain text`() {
        assertFalse(IntentUtils.isUrlEncoded("hello world"))
    }

    @Test
    fun `isUrlEncoded returns false for lone percent sign`() {
        assertFalse(IntentUtils.isUrlEncoded("100%"))
    }

    @Test
    fun `isUrlEncoded returns false for percent followed by non-hex characters`() {
        assertFalse(IntentUtils.isUrlEncoded("hello%gg"))
    }

    @Test
    fun `isUrlEncoded returns false for percent followed by only one hex digit`() {
        assertFalse(IntentUtils.isUrlEncoded("hello%2"))
    }

    @Test
    fun `isUrlEncoded returns true when encoded sequence appears in middle of string`() {
        assertTrue(IntentUtils.isUrlEncoded("nostrsigner%3Ahello"))
    }

    @Test
    fun `isUrlEncoded returns false for empty string`() {
        assertFalse(IntentUtils.isUrlEncoded(""))
    }

    // --- parsePubKey ---

    @Test
    fun `parsePubKey returns npub unchanged when already npub-prefixed`() {
        val npub = "npub1testvalue"
        assertEquals(npub, IntentUtils.parsePubKey(npub))
    }

    @Test
    fun `parsePubKey converts valid 32-byte hex key to npub format`() {
        // x-coordinate of secp256k1 generator point — a well-known 32-byte value
        val hex = "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798"
        val result = IntentUtils.parsePubKey(hex)
        assertNotNull(result)
        assertTrue(result!!.startsWith("npub1"))
    }

    @Test
    fun `parsePubKey returns null for odd-length hex string`() {
        // Odd-length strings cannot represent whole bytes
        assertNull(IntentUtils.parsePubKey("deadbeef1"))
    }

    // --- isRemembered ---

    @Test
    fun `isRemembered returns true when signPolicy is 2 regardless of permission`() {
        assertTrue(IntentUtils.isRemembered(signPolicy = 2, permission = null) == true)
    }

    @Test
    fun `isRemembered returns true when signPolicy is 2 even with rejecting permission`() {
        val permission = permissionWith(acceptable = false, rejectUntil = TimeUtils.now() - 3600)
        assertTrue(IntentUtils.isRemembered(signPolicy = 2, permission = permission) == true)
    }

    @Test
    fun `isRemembered returns null when permission is null and signPolicy is not 2`() {
        assertNull(IntentUtils.isRemembered(signPolicy = 0, permission = null))
    }

    @Test
    fun `isRemembered returns null when both time fields are zero`() {
        val permission = permissionWith(acceptUntil = 0L, rejectUntil = 0L)
        assertNull(IntentUtils.isRemembered(signPolicy = null, permission = permission))
    }

    @Test
    fun `isRemembered returns false when rejectUntil is in the future and acceptable is false`() {
        val permission = permissionWith(acceptable = false, rejectUntil = TimeUtils.now() + 3600)
        assertEquals(false, IntentUtils.isRemembered(signPolicy = null, permission = permission))
    }

    @Test
    fun `isRemembered returns true when acceptUntil is in the future and acceptable is true`() {
        val permission = permissionWith(acceptable = true, acceptUntil = TimeUtils.now() + 3600)
        assertEquals(true, IntentUtils.isRemembered(signPolicy = null, permission = permission))
    }

    @Test
    fun `isRemembered returns null when acceptUntil has expired`() {
        val permission = permissionWith(acceptable = true, acceptUntil = TimeUtils.now() - 3600)
        assertNull(IntentUtils.isRemembered(signPolicy = null, permission = permission))
    }

    @Test
    fun `isRemembered returns null when rejectUntil has expired`() {
        val permission = permissionWith(acceptable = false, rejectUntil = TimeUtils.now() - 3600)
        assertNull(IntentUtils.isRemembered(signPolicy = null, permission = permission))
    }

    @Test
    fun `isRemembered returns null when signPolicy is 1`() {
        assertNull(IntentUtils.isRemembered(signPolicy = 1, permission = null))
    }

    // Helper to build a minimal ApplicationPermissionsEntity for isRemembered tests
    private fun permissionWith(
        acceptable: Boolean = true,
        acceptUntil: Long = 0L,
        rejectUntil: Long = 0L,
    ) = ApplicationPermissionsEntity(
        id = 1,
        pkKey = "testKey",
        type = "sign_event",
        kind = 1,
        acceptable = acceptable,
        rememberType = 1,
        acceptUntil = acceptUntil,
        rejectUntil = rejectUntil,
    )
}
