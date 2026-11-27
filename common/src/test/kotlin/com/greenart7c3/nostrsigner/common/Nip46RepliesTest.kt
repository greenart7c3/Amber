package com.greenart7c3.nostrsigner.common

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Nip46RepliesTest {
    private val connection = KeyPair()
    private val client = NostrSignerInternal(KeyPair())
    private val key = ConnectionReplyKey(connection.privKey!!.toHexKey())
    private val response = BunkerResponse("req-1", "secret plaintext", null)

    private fun decrypted(event: Event, encryptionType: EncryptionType): BunkerResponse = runBlocking {
        val plain = if (encryptionType == EncryptionType.NIP44) client.nip44Decrypt(event.content, event.pubKey) else client.nip04Decrypt(event.content, event.pubKey)
        JacksonMapper.mapper.readValue(plain, BunkerResponse::class.java)
    }

    @Test
    fun `a reply is a signed kind-24133 event to the client, from the connection key`() = runBlocking {
        val event = nip46ReplyEvent(response, client.pubKey, EncryptionType.NIP44, key)

        assertEquals(NostrConnectEvent.KIND, event.kind)
        assertEquals(connection.pubKey.toHexKey(), event.pubKey)
        assertEquals(listOf("p", client.pubKey), event.tags.single().toList())
        assertTrue(event.verify())
    }

    @Test
    fun `a reply is encrypted the way the request came in`() = runBlocking {
        for (type in listOf(EncryptionType.NIP44, EncryptionType.NIP04)) {
            val event = nip46ReplyEvent(response, client.pubKey, type, key)
            assertFalse(event.content.contains("secret plaintext"))
            val back = decrypted(event, type)
            assertEquals("req-1", back.id)
            assertEquals("secret plaintext", back.result)
        }
    }

    @Test
    fun `each try publishes a freshly built reply until one is accepted, as a relay that refused one event may take the next`() = runBlocking {
        val tried = mutableListOf<Event>()
        val published = publishNip46Reply(response, client.pubKey, EncryptionType.NIP44, key, attempts = 3) { event ->
            tried += event
            tried.size == 2
        }

        assertTrue(published)
        assertEquals(2, tried.size)
        assertEquals(2, tried.map { it.id }.toSet().size)
    }

    @Test
    fun `a reply no relay accepts is reported after the last try`() = runBlocking {
        var tries = 0
        assertFalse(
            publishNip46Reply(response, client.pubKey, EncryptionType.NIP44, key, attempts = UNVERIFIED_REPLY_ATTEMPTS) {
                tries++
                false
            },
        )
        assertEquals(UNVERIFIED_REPLY_ATTEMPTS, tries)
    }

    @Test
    fun `the log line carries the id and error, never the result`() {
        assertEquals("id=req-1 ok", nip46ReplyLog(response))
        assertEquals("id=req-2 error=user rejected", nip46ReplyLog(BunkerResponse("req-2", "", "user rejected")))
    }
}
