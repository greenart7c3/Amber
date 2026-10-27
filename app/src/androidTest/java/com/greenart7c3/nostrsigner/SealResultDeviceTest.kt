package com.greenart7c3.nostrsigner

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.EncryptedDataKind
import com.greenart7c3.nostrsigner.service.AmberUtils
import com.greenart7c3.nostrsigner.service.EventNotificationConsumer
import com.greenart7c3.nostrsigner.service.IntentUtils
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device check that a kind-13 seal wrapping data the account encrypted to
 * itself never turns a NIP-55 intent or NIP-46 encrypt/decrypt request into a
 * disclosure of that plaintext. Uses throwaway in-memory keys only; no stored
 * account, permission or relay is touched.
 */
@RunWith(AndroidJUnit4::class)
class SealResultDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val signer = NostrSignerInternal(KeyPair())
    private val account = Account(
        signer = signer,
        hexKey = signer.keyPair.pubKey.toHexKey(),
        npub = "npub-device-test",
        name = MutableStateFlow(""),
        picture = MutableStateFlow(""),
        signPolicy = 0,
        didBackup = false,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        keyAccessCheck = {},
    )
    private val peer = NostrSignerInternal(KeyPair())
    private val peerHex = peer.keyPair.pubKey.toHexKey()
    private val privateTags = """[["word","device-private-bookmark"]]"""

    private suspend fun sealOfPrivateTags(): String = """{"id":"","pubkey":"${account.hexKey}","created_at":1,"kind":13,"tags":[],"content":"${account.nip44Encrypt(privateTags, account.hexKey)}","sig":""}"""

    private suspend fun intentKind(type: SignerType, result: String, data: String): EncryptedDataKind {
        val fn = IntentUtils::class.java.getDeclaredMethod(
            "getEncryptedDataKind",
            SignerType::class.java,
            String::class.java,
            String::class.java,
            Account::class.java,
            Continuation::class.java,
        ).apply { isAccessible = true }
        return suspendCoroutineUninterceptedOrReturn<Any?> { c -> fn.invoke(IntentUtils, type, result, data, account, c) } as EncryptedDataKind
    }

    private suspend fun bunkerKind(method: String, params: List<String>): EncryptedDataKind {
        val wire = JacksonMapper.mapper.writeValueAsString(mapOf("id" to "req", "method" to method, "params" to params))
        val request = JacksonMapper.mapper.readValue(wire, BunkerRequest::class.java)
        val fn = EventNotificationConsumer::class.java.getDeclaredMethod(
            "getEncryptedDataKind",
            BunkerRequest::class.java,
            Account::class.java,
            String::class.java,
            Continuation::class.java,
        ).apply { isAccessible = true }
        val consumer = EventNotificationConsumer(context)
        return suspendCoroutineUninterceptedOrReturn<Any?> { c -> fn.invoke(consumer, request, account, "wss://relay.invalid", c) } as EncryptedDataKind
    }

    /**
     * Logs what the caller would receive for one request, then returns whether it
     * is the right value. Every case is logged before anything is asserted, so a
     * run shows all of them.
     */
    private fun check(path: String, method: String, returned: String, expected: String, expectedLabel: String): Boolean {
        val leaked = returned == privateTags
        val ok = returned == expected
        Log.i(TAG, "$path $method -> returned=${returned.take(90)} | expected $expectedLabel | ${if (ok) "OK" else "WRONG"}${if (leaked) " (LEAKED self-encrypted plaintext)" else ""}")
        return ok
    }

    @Test
    fun intentEncryptAndDecryptReturnTheRealResult() = runBlocking {
        val seal = sealOfPrivateTags()
        val results = mutableListOf<Boolean>()
        for (type in listOf(SignerType.NIP44_ENCRYPT, SignerType.NIP04_ENCRYPT)) {
            val ciphertext = AmberUtils.encryptOrDecryptData(seal, type, account, peerHex)!!
            val kind = intentKind(type, ciphertext, seal)
            results += check("NIP-55 intent", type.name, kind.result, ciphertext, "ciphertext")
        }
        val incoming = peer.nip44Encrypt(seal, account.hexKey)
        val decrypted = AmberUtils.encryptOrDecryptData(incoming, SignerType.NIP44_DECRYPT, account, peerHex)!!
        results += check("NIP-55 intent", "NIP44_DECRYPT", intentKind(SignerType.NIP44_DECRYPT, decrypted, incoming).result, seal, "the seal")
        assertTrue("see logcat tag $TAG", results.all { it })
    }

    @Test
    fun bunkerEncryptAndDecryptReturnTheRealResult() = runBlocking {
        val seal = sealOfPrivateTags()
        val results = mutableListOf<Boolean>()
        for (method in listOf("nip44_encrypt", "nip04_encrypt")) {
            val returned = bunkerKind(method, listOf(peerHex, seal)).result
            val peerView = runCatching { peer.decrypt(returned, account.hexKey) }.getOrDefault("")
            // An encrypt result is right when it is ciphertext the peer can open back to the seal.
            results += check("NIP-46 bunker", method, returned, if (peerView == seal) returned else "<ciphertext of the seal>", "ciphertext")
        }
        val incoming = mapOf(
            "nip44_decrypt" to peer.nip44Encrypt(seal, account.hexKey),
            "nip04_decrypt" to peer.nip04Encrypt(seal, account.hexKey),
        )
        for ((method, ciphertext) in incoming) {
            results += check("NIP-46 bunker", method, bunkerKind(method, listOf(peerHex, ciphertext)).result, seal, "the seal")
        }
        assertTrue("see logcat tag $TAG", results.all { it })
    }

    private companion object {
        const val TAG = "SealResultDeviceTest"
    }
}
