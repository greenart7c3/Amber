package com.greenart7c3.nostrsigner.service

import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.EncryptedDataKind
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import io.mockk.every
import io.mockk.mockk
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * NIP-46 counterpart of IntentUtilsSealResultTest: a kind-13 seal wrapping data
 * the account encrypted to itself must not turn an encrypt or decrypt request
 * into a disclosure of that self-encrypted plaintext.
 */
class EventNotificationConsumerSealResultTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val signer = NostrSignerInternal(KeyPair())
    private val account = Account(
        signer = signer,
        hexKey = signer.keyPair.pubKey.toHexKey(),
        npub = "npub-test",
        name = MutableStateFlow(""),
        picture = MutableStateFlow(""),
        signPolicy = 0,
        didBackup = false,
        scope = scope,
        keyAccessCheck = {},
    )
    private val peer = NostrSignerInternal(KeyPair())
    private val peerHex = peer.keyPair.pubKey.toHexKey()
    private val privateTags = """[["word","private-bookmark"]]"""

    @Before
    fun setUp() {
        val amber = mockk<Amber>(relaxed = true)
        every { amber.applicationIOScope } returns scope
        installAmberInstance(amber)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private suspend fun encryptedDataKind(method: String, params: List<String>): EncryptedDataKind {
        val wire = JacksonMapper.mapper.writeValueAsString(mapOf("id" to "req", "method" to method, "params" to params))
        val request = JacksonMapper.mapper.readValue(wire, BunkerRequest::class.java)
        val fn = EventNotificationConsumer::class.java.getDeclaredMethod(
            "getEncryptedDataKind",
            BunkerRequest::class.java,
            Account::class.java,
            String::class.java,
            Continuation::class.java,
        ).apply { isAccessible = true }
        val consumer = EventNotificationConsumer(mockk(relaxed = true))
        return suspendCoroutineUninterceptedOrReturn<Any?> { c -> fn.invoke(consumer, request, account, "wss://relay.invalid", c) } as EncryptedDataKind
    }

    private suspend fun sealOfPrivateTags(): String = """{"kind":13,"content":"${account.nip44Encrypt(privateTags, account.hexKey)}","tags":[],"created_at":1,"pubkey":"${account.hexKey}"}"""

    @Test
    fun `encrypt of a seal wrapping self-encrypted tags returns ciphertext`() = runBlocking {
        val seal = sealOfPrivateTags()
        for (method in listOf("nip44_encrypt", "nip04_encrypt")) {
            val kind = encryptedDataKind(method, listOf(peerHex, seal))
            assertEquals(method, seal, peer.decrypt(kind.result, account.hexKey))
        }
    }

    @Test
    fun `decrypt of a seal wrapping self-encrypted tags returns the seal, not its contents`() = runBlocking {
        val seal = sealOfPrivateTags()
        val ciphertexts = mapOf(
            "nip44_decrypt" to peer.nip44Encrypt(seal, account.hexKey),
            "nip04_decrypt" to peer.nip04Encrypt(seal, account.hexKey),
        )
        for ((method, ciphertext) in ciphertexts) {
            val kind = encryptedDataKind(method, listOf(peerHex, ciphertext))
            assertEquals(method, seal, kind.result)
        }
    }
}
