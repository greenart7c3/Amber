package com.greenart7c3.nostrsigner.service

import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.EncryptedDataKind
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.jvm.isAccessible
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * A kind-13 seal wrapping data the account encrypted to itself (e.g. private
 * NIP-51 list items) must never turn an encrypt or decrypt request into a
 * disclosure of that self-encrypted plaintext.
 */
class IntentUtilsSealResultTest {
    private val kp = KeyPair()
    private val account = Account(
        signer = NostrSignerInternal(kp),
        hexKey = kp.pubKey.toHexKey(),
        npub = kp.pubKey.toNpub(),
        name = MutableStateFlow(""),
        picture = MutableStateFlow(""),
        signPolicy = 1,
        didBackup = true,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        keyAccessCheck = {},
    )
    private val callerKp = KeyPair()
    private val callerPub = callerKp.pubKey.toHexKey()
    private val privateTags = """[["word","private-muted-word"]]"""

    @Before
    fun setUp() {
        mockkObject(AmberLog)
        every { AmberLog.e(any(), any(), any()) } returns Unit
        every { AmberLog.e(any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkObject(AmberLog)
    }

    private suspend fun encryptedDataKind(type: SignerType, result: String, data: String): EncryptedDataKind {
        val fn = IntentUtils::class.declaredMemberFunctions.first { it.name == "getEncryptedDataKind" }
        fn.isAccessible = true
        return fn.callSuspend(IntentUtils, type, result, data, account) as EncryptedDataKind
    }

    private suspend fun sealOfPrivateTags(selfEncrypt: suspend (String) -> String): String = """{"id":"","pubkey":"$callerPub","created_at":1,"kind":13,"tags":[],"content":"${selfEncrypt(privateTags)}","sig":""}"""

    @Test
    fun `encrypt of a seal wrapping self-encrypted tags returns the ciphertext`() = runBlocking {
        val selfEncrypts: List<suspend (String) -> String> = listOf(
            { account.nip44Encrypt(it, account.hexKey) },
            { account.nip04Encrypt(it, account.hexKey) },
        )
        for (type in listOf(SignerType.NIP44_ENCRYPT, SignerType.NIP04_ENCRYPT)) {
            for (selfEncrypt in selfEncrypts) {
                val seal = sealOfPrivateTags(selfEncrypt)
                val ciphertext = AmberUtils.encryptOrDecryptData(seal, type, account, callerPub)!!

                val kind = encryptedDataKind(type, ciphertext, seal)

                assertEquals(ciphertext, kind.result)
                assertEquals(seal, NostrSignerInternal(callerKp).decrypt(kind.result, account.hexKey))
            }
        }
    }

    @Test
    fun `decrypt of a seal wrapping self-encrypted tags returns the seal, not its contents`() = runBlocking {
        val seal = sealOfPrivateTags { account.nip44Encrypt(it, account.hexKey) }
        val ciphertext = NostrSignerInternal(callerKp).nip44Encrypt(seal, account.hexKey)
        val decrypted = AmberUtils.encryptOrDecryptData(ciphertext, SignerType.NIP44_DECRYPT, account, callerPub)!!

        val kind = encryptedDataKind(SignerType.NIP44_DECRYPT, decrypted, ciphertext)

        assertEquals(seal, kind.result)
    }
}
