package com.greenart7c3.nostrsigner.service

import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.models.CompressionType
import com.greenart7c3.nostrsigner.models.ReturnType
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NostrSignerRequestTest {
    private val pubKey = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"

    // --- fromUri (web apps) ---

    @Test
    fun `fromUri reads the query parameters`() {
        val request = NostrSignerRequestParser.fromUri(
            "nostrsigner:hello?type=nip44_encrypt&PubKey=$pubKey&compressionType=gzip&returnType=event&callbackUrl=https%3A%2F%2Fa.b%2F&appName=My+App",
        )!!

        assertEquals(SignerType.NIP44_ENCRYPT, request.type)
        assertEquals("hello", request.data)
        assertEquals(pubKey, request.pubKey)
        assertEquals(CompressionType.GZIP, request.compression)
        assertEquals(ReturnType.EVENT, request.returnType)
        assertEquals("https://a.b/", request.callbackUrl)
        assertEquals("My App", request.appName)
        assertEquals(emptyList<Any>(), request.permissions)
        assertNull(request.currentUser)
        assertTrue(request.fromUri)
        assertNull(request.rejection())
    }

    @Test
    fun `fromUri keeps an encoded ampersand inside one value`() {
        val request = NostrSignerRequestParser.fromUri("nostrsigner:x?type=sign_event&appName=A%26B")!!

        assertEquals("A&B", request.appName)
    }

    @Test
    fun `fromUri reads the parameters after a NIP-04 iv`() {
        val request = NostrSignerRequestParser.fromUri("nostrsigner:cipher?iv=abc&type=nip04_decrypt&pubkey=$pubKey")!!

        assertTrue(request.data.startsWith("cipher?iv=abc"))
        assertEquals(SignerType.NIP04_DECRYPT, request.type)
        assertEquals(pubKey, request.pubKey)
    }

    @Test
    fun `fromUri reads the NIP-44 v3 kind and scope`() {
        val request = NostrSignerRequestParser.fromUri("nostrsigner:x?type=nip44v3_encrypt&kind=30078&scope=a%3Ab")!!

        assertEquals(30078, request.nip44v3Kind)
        assertEquals("a:b", request.nip44v3Scope)
    }

    @Test
    fun `fromUri without parameters falls back to the extras`() {
        assertNull(NostrSignerRequestParser.fromUri("nostrsigner:hello"))
        assertNull(NostrSignerRequestParser.fromUri("nostrsigner:hello?"))
    }

    // --- fromExtras (native apps) ---

    @Test
    fun `fromExtras reads the intent extras`() {
        val extras = mapOf(
            "type" to "sign_psbt",
            "pubkey" to pubKey,
            "compression" to "gzip",
            "returnType" to "event",
            "appName" to "App",
            "current_user" to pubKey,
            "kind" to "7",
            "scope" to "s",
        )
        val request = NostrSignerRequestParser.fromExtras("nostrsigner:hello%20world", "com.app") { extras[it] }

        assertEquals(SignerType.SIGN_PSBT, request.type)
        assertEquals("hello world", request.data)
        assertEquals(pubKey, request.pubKey)
        assertEquals(CompressionType.GZIP, request.compression)
        assertEquals(ReturnType.EVENT, request.returnType)
        assertEquals("App", request.appName)
        assertEquals(IntentUtils.parsePubKey(pubKey), request.currentUser)
        assertEquals(7, request.nip44v3Kind)
        assertEquals("s", request.nip44v3Scope)
        assertNull(request.permissions)
        assertFalse(request.fromUri)
    }

    @Test
    fun `fromExtras prefers the pubKey extra`() {
        val extras = mapOf("type" to "nip04_encrypt", "pubKey" to "a", "pubkey" to "b")

        assertEquals("a", NostrSignerRequestParser.fromExtras("nostrsigner:x", "p") { extras[it] }.pubKey)
    }

    @Test
    fun `fromExtras drops kind-less sign_event and unknown nip permissions`() {
        val json = """[{"type":"sign_event"},{"type":"sign_event","kind":1},{"type":"nip","kind":4},{"type":"nip","kind":99999},{"type":" nip44_encrypt "}]"""
        val request = NostrSignerRequestParser.fromExtras("nostrsigner:x", "p") { if (it == "permissions") json else null }

        val permissions = request.permissions!!
        assertEquals(listOf("sign_event" to 1, "nip" to 4, "nip44_encrypt" to null), permissions.map { it.type to it.kind })
        assertTrue(permissions.all { it.checked })
    }

    @Test
    fun `fromExtras with invalid permissions json has no permissions`() {
        mockkObject(AmberLog)
        every { AmberLog.e(any(), any(), any()) } returns Unit
        try {
            assertNull(NostrSignerRequestParser.parsePermissions("not json"))
        } finally {
            unmockkObject(AmberLog)
        }
    }

    // --- rejection ---

    @Test
    fun `an unknown type is rejected`() {
        val request = NostrSignerRequestParser.fromExtras("nostrsigner:x", "p") { if (it == "type") "nope" else null }

        assertEquals(SignerType.INVALID, request.type)
        assertEquals("Unknown signer type: nope", request.rejection())
    }

    @Test
    fun `a zap decrypt is only served through the extras`() {
        assertNotNull(NostrSignerRequestParser.fromUri("nostrsigner:x?type=decrypt_zap_event")!!.rejection())
        assertNull(NostrSignerRequestParser.fromExtras("nostrsigner:x", "p") { if (it == "type") "decrypt_zap_event" else null }.rejection())
    }
}
