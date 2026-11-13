package com.greenart7c3.nostrsigner.service

import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.models.CompressionType
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.ReturnType
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import kotlin.io.encoding.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentResponseTest {
    private val peer = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"

    private fun request(
        type: SignerType,
        returnType: ReturnType = ReturnType.SIGNATURE,
        compression: CompressionType = CompressionType.NONE,
        nip44v3Kind: Int? = null,
        nip44v3Scope: String = "",
    ) = IntentData(
        data = "request-data",
        name = "",
        type = type,
        pubKey = peer,
        id = "1",
        callBackUrl = null,
        compression = compression,
        returnType = returnType,
        permissions = null,
        currentAccount = "",
        route = null,
        event = null,
        encryptedData = null,
        nip44v3Kind = nip44v3Kind,
        nip44v3Scope = nip44v3Scope,
    )

    // --- historyContent: never the plaintext ---

    @Test
    fun `an accepted encrypt stores its ciphertext`() {
        assertEquals("cipher", IntentResponse.historyContent(request(SignerType.NIP44_ENCRYPT), true, "event", "cipher"))
    }

    @Test
    fun `a rejected encrypt stores nothing`() {
        assertEquals("", IntentResponse.historyContent(request(SignerType.NIP04_ENCRYPT), false, "", ""))
        assertEquals("", IntentResponse.historyContent(request(SignerType.NIP44_V3_ENCRYPT), false, "", ""))
    }

    @Test
    fun `a decrypt stores the ciphertext it was asked to open`() {
        assertEquals("request-data", IntentResponse.historyContent(request(SignerType.NIP44_DECRYPT), true, "", "plaintext"))
        assertEquals("request-data", IntentResponse.historyContent(request(SignerType.NIP44_DECRYPT), false, "", ""))
    }

    @Test
    fun `an accepted signature stores the signed event and a rejected one the request`() {
        assertEquals("event", IntentResponse.historyContent(request(SignerType.SIGN_EVENT), true, "event", "sig"))
        assertEquals("request-data", IntentResponse.historyContent(request(SignerType.SIGN_EVENT), false, "", ""))
    }

    // --- historyEntry ---

    @Test
    fun `an encryption entry keeps the peer key`() {
        val entry = IntentResponse.historyEntry(request(SignerType.NIP04_DECRYPT), "app", 4, true, "cipher")

        assertEquals(peer, entry.encryptionPubKey)
        assertEquals(4, entry.kind)
        assertTrue(entry.accepted)
    }

    @Test
    fun `an entry without content or peer has no peer key`() {
        assertEquals("", IntentResponse.historyEntry(request(SignerType.NIP44_ENCRYPT), "app", null, false, "").encryptionPubKey)
        assertEquals("", IntentResponse.historyEntry(request(SignerType.DECRYPT_ZAP_EVENT), "app", null, true, "x").encryptionPubKey)
        assertEquals("", IntentResponse.historyEntry(request(SignerType.SIGN_EVENT), "app", 1, true, "x").encryptionPubKey)
    }

    @Test
    fun `a NIP-44 v3 entry records the context kind and scope`() {
        val v3 = request(SignerType.NIP44_V3_DECRYPT, nip44v3Kind = 30078, nip44v3Scope = "s")
        val entry = IntentResponse.historyEntry(v3, "app", 1, true, "cipher")

        assertEquals(30078, entry.kind)
        assertEquals("s", entry.encryptionScope)
        assertEquals("", IntentResponse.historyEntry(request(SignerType.NIP44_DECRYPT), "app", null, true, "c").encryptionScope)
        assertNull(IntentResponse.historyEntry(request(SignerType.NIP44_DECRYPT), "app", null, true, "c").kind)
    }

    // --- callbackPayload ---

    @Test
    fun `the callback gets the signature or the event as asked`() {
        assertEquals("sig", IntentResponse.callbackPayload(request(SignerType.SIGN_EVENT), "event", "sig"))
        assertEquals("event", IntentResponse.callbackPayload(request(SignerType.SIGN_EVENT, ReturnType.EVENT), "event", "sig"))
    }

    @Test
    fun `a gzip callback gets the compressed event`() {
        val payload = IntentResponse.callbackPayload(request(SignerType.SIGN_EVENT, ReturnType.EVENT, CompressionType.GZIP), "{\"kind\":1}", "sig")

        assertTrue(payload.startsWith("Signer1"))
        val bytes = Base64.decode(payload.removePrefix("Signer1"))
        assertEquals("{\"kind\":1}", GZIPInputStream(ByteArrayInputStream(bytes)).readBytes().decodeToString())
    }
}
