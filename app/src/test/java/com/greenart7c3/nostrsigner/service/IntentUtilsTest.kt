package com.greenart7c3.nostrsigner.service

import android.content.Context
import com.greenart7c3.nostrsigner.data.AccountRepository
import com.greenart7c3.nostrsigner.database.ApplicationEntity
import com.greenart7c3.nostrsigner.database.ApplicationPermissionsEntity
import com.greenart7c3.nostrsigner.database.ApplicationWithPermissions
import com.greenart7c3.nostrsigner.database.relaxedApplicationDaos
import com.vitorpamplona.quartz.utils.TimeUtils
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentUtilsTest {

    @Test
    fun `decodeData removes nostrsigner prefix`() {
        val result = decodeData("nostrsigner:hello")
        assertEquals("hello", result)
    }

    @Test
    fun `decodeData URL-decodes percent-encoded characters`() {
        val result = decodeData("nostrsigner:hello%20world")
        assertEquals("hello world", result)
    }

    @Test
    fun `decodeData with replace=true escapes plus before URL-decoding so it stays literal`() {
        val result = decodeData("nostrsigner:hello+world", replace = true)
        assertEquals("hello+world", result)
    }

    @Test
    fun `decodeData with replace=false treats plus as a form-encoded space`() {
        val result = decodeData("nostrsigner:hello+world", replace = false)
        assertEquals("hello world", result)
    }

    @Test
    fun `decodeData with decodeData=false and replace=false only strips prefix`() {
        val result = decodeData("nostrsigner:hello%20world", replace = false, decodeData = false)
        assertEquals("hello%20world", result)
    }

    @Test
    fun `decodeData with decodeData=false and replace=true strips prefix and swaps plus`() {
        val result = decodeData("nostrsigner:hello+world", replace = true, decodeData = false)
        assertEquals("hello%2bworld", result)
    }

    @Test
    fun `decodeData handles input without nostrsigner prefix`() {
        val result = decodeData("hello%20world")
        assertEquals("hello world", result)
    }

    @Test
    fun `decodeData returns empty string for bare nostrsigner prefix`() {
        val result = decodeData("nostrsigner:")
        assertEquals("", result)
    }

    @Test
    fun `decodeData decodes multiple percent-encoded sequences`() {
        val result = decodeData("nostrsigner:foo%3Dbar%26baz%3D1")
        assertEquals("foo=bar&baz=1", result)
    }

    @Test
    fun `isUrlEncoded returns true for lowercase hex percent-encoding`() {
        assertTrue(isUrlEncoded("hello%20world"))
    }

    @Test
    fun `isUrlEncoded returns true for uppercase hex percent-encoding`() {
        assertTrue(isUrlEncoded("hello%2Bworld"))
    }

    @Test
    fun `isUrlEncoded returns true for mixed-case hex percent-encoding`() {
        assertTrue(isUrlEncoded("hello%3aworld"))
    }

    @Test
    fun `isUrlEncoded returns false for plain text`() {
        assertFalse(isUrlEncoded("hello world"))
    }

    @Test
    fun `isUrlEncoded returns false for lone percent sign`() {
        assertFalse(isUrlEncoded("100%"))
    }

    @Test
    fun `isUrlEncoded returns false for percent followed by non-hex characters`() {
        assertFalse(isUrlEncoded("hello%gg"))
    }

    @Test
    fun `isUrlEncoded returns false for percent followed by only one hex digit`() {
        assertFalse(isUrlEncoded("hello%2"))
    }

    @Test
    fun `isUrlEncoded returns true when encoded sequence appears in middle of string`() {
        assertTrue(isUrlEncoded("nostrsigner%3Ahello"))
    }

    @Test
    fun `isUrlEncoded returns false for empty string`() {
        assertFalse(isUrlEncoded(""))
    }

    @Test
    fun `parsePubKey returns npub unchanged when already npub-prefixed`() {
        val npub = "npub1testvalue"
        assertEquals(npub, parsePubKey(npub))
    }

    @Test
    fun `parsePubKey converts valid 32-byte hex key to npub format`() {
        val secp256k1GeneratorX = "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798"
        val result = parsePubKey(secp256k1GeneratorX)
        assertNotNull(result)
        assertTrue(result!!.startsWith("npub1"))
    }

    @Test
    fun `parsePubKey returns null for odd-length hex string, which has no whole bytes`() {
        assertNull(parsePubKey("deadbeef1"))
    }

    @Test
    fun `isRemembered returns true when signPolicy is 2 regardless of permission`() {
        assertTrue(isRemembered(signPolicy = 2, permission = null) == true)
    }

    @Test
    fun `isRemembered returns true when signPolicy is 2 and the rejection has expired`() {
        val permission = permissionWith(acceptable = false, rejectUntil = TimeUtils.now() - 3600)
        assertTrue(isRemembered(signPolicy = 2, permission = permission) == true)
    }

    @Test
    fun `isRemembered returns false when signPolicy is 2 but an active rejection exists`() {
        val permission = permissionWith(acceptable = false, rejectUntil = TimeUtils.now() + 3600)
        assertEquals(false, isRemembered(signPolicy = 2, permission = permission))
    }

    @Test
    fun `isRemembered returns null when permission is null and signPolicy is not 2`() {
        assertNull(isRemembered(signPolicy = 0, permission = null))
    }

    @Test
    fun `isRemembered returns null when both time fields are zero`() {
        val permission = permissionWith(acceptUntil = 0L, rejectUntil = 0L)
        assertNull(isRemembered(signPolicy = null, permission = permission))
    }

    @Test
    fun `isRemembered returns false when rejectUntil is in the future and acceptable is false`() {
        val permission = permissionWith(acceptable = false, rejectUntil = TimeUtils.now() + 3600)
        assertEquals(false, isRemembered(signPolicy = null, permission = permission))
    }

    @Test
    fun `isRemembered returns true when acceptUntil is in the future and acceptable is true`() {
        val permission = permissionWith(acceptable = true, acceptUntil = TimeUtils.now() + 3600)
        assertEquals(true, isRemembered(signPolicy = null, permission = permission))
    }

    @Test
    fun `isRemembered returns null when acceptUntil has expired`() {
        val permission = permissionWith(acceptable = true, acceptUntil = TimeUtils.now() - 3600)
        assertNull(isRemembered(signPolicy = null, permission = permission))
    }

    @Test
    fun `isRemembered returns null when rejectUntil has expired`() {
        val permission = permissionWith(acceptable = false, rejectUntil = TimeUtils.now() - 3600)
        assertNull(isRemembered(signPolicy = null, permission = permission))
    }

    @Test
    fun `isRemembered returns null when signPolicy is 1`() {
        assertNull(isRemembered(signPolicy = 1, permission = null))
    }

    private fun isWebRequest(
        hasApplicationIdExtra: Boolean = false,
        hasBrowsableCategory: Boolean = false,
        referrerIsBrowser: Boolean = false,
        typeExtra: String? = null,
        dataString: String? = "nostrsigner:",
    ) = com.greenart7c3.nostrsigner.service.isWebRequest(hasApplicationIdExtra, hasBrowsableCategory, referrerIsBrowser, typeExtra, dataString)

    @Test
    fun `isWebRequest is true for Chrome application id extra`() {
        assertTrue(isWebRequest(hasApplicationIdExtra = true, typeExtra = "sign_event"))
    }

    @Test
    fun `isWebRequest is true for browsable category`() {
        assertTrue(isWebRequest(hasBrowsableCategory = true))
    }

    @Test
    fun `isWebRequest is true when referrer is a browser`() {
        assertTrue(isWebRequest(referrerIsBrowser = true))
    }

    @Test
    fun `isWebRequest is true for type query parameter without browser markers`() {
        assertTrue(isWebRequest(dataString = "nostrsigner:?type=get_public_key&callbackUrl=https://example.com/?event="))
        assertTrue(isWebRequest(dataString = "nostrsigner:%7B%22kind%22%3A1%7D?compressionType=none&type=sign_event"))
    }

    @Test
    fun `isWebRequest is true for url encoded type query parameter`() {
        assertTrue(isWebRequest(dataString = "nostrsigner:abc%3Ftype%3Dnip04_encrypt%26pubkey%3D123"))
    }

    @Test
    fun `isWebRequest is false for native app with type extra`() {
        assertFalse(isWebRequest(typeExtra = "sign_event", dataString = "nostrsigner:{\"content\":\"what?type=x\"}"))
        assertFalse(isWebRequest(hasBrowsableCategory = true, referrerIsBrowser = true, typeExtra = "sign_event"))
    }

    @Test
    fun `isWebRequest is false without markers or query`() {
        assertFalse(isWebRequest())
        assertFalse(isWebRequest(dataString = null))
        assertFalse(isWebRequest(dataString = "nostrsigner:?pubkey=123"))
    }

    @Test
    fun `persistNativeAppMetadata skips repeated lookups once name and icon are populated`() = runBlocking {
        IntentUtils.clearNativeAppMetadataCache()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val daos = relaxedApplicationDaos()
            val repo = mockk<AccountRepository>(relaxed = true)
            every { repo.daos(any()) } returns daos
            val account = newTestAccount(scope)
            val saved = ApplicationWithPermissions(
                application = ApplicationEntity.empty().copy(
                    key = "com.example.app",
                    name = "Example",
                    icon = "/tmp/icon.png",
                ),
                permissions = mutableListOf(),
            )
            coEvery { daos.applicationDao.getByKey("com.example.app") } returns saved
            IntentUtils.install(repo, scope, EphemeralPayloadStore(scope)) { null }
            val context = mockk<Context>(relaxed = true)

            IntentUtils.persistNativeAppMetadata(context, account, "com.example.app")
            IntentUtils.persistNativeAppMetadata(context, account, "com.example.app")

            coVerify(exactly = 1) { daos.applicationDao.getByKey("com.example.app") }
        } finally {
            IntentUtils.clearNativeAppMetadataCache()
            scope.cancel()
        }
    }

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
