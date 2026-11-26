package com.greenart7c3.nostrsigner.service

import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.models.Account
import com.vitorpamplona.quartz.experimental.decoupling.EncryptionKeyDerivation
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApplicationBackupCryptoTest {
    private val payload = """{"v":1,"ts":1,"applications":[]}"""
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var account: Account

    @Suppress("NoComments")
    @Before
    fun setUp() {
        // decryptContent's failure path logs; android.util.Log is not mocked on the JVM.
        mockkObject(AmberLog)
        every { AmberLog.e(any(), any(), any()) } returns Unit
        val identity = KeyPair()
        account = Account(
            signer = NostrSignerInternal(identity),
            name = MutableStateFlow(""),
            picture = MutableStateFlow(""),
            signPolicy = 1,
            didBackup = true,
            scope = scope,
            keyAccessCheck = {},
        )
    }

    @After
    fun tearDown() {
        unmockkObject(AmberLog)
    }

    @Test
    fun `backup key derivation is deterministic and domain separated`() {
        val identity = KeyPair().privKey!!
        val first = backupKeyPair(identity)
        val second = backupKeyPair(identity)
        assertTrue(first.privKey.contentEquals(second.privKey))
        assertTrue(KeyPair(privKey = first.privKey).pubKey.contentEquals(first.pubKey))
    }

    @Test
    fun `identity key cannot decrypt backup content`() {
        val identityPrivKey = KeyPair().privKey!!
        val backupSigner = NostrSignerInternal(backupKeyPair(identityPrivKey))
        val derivedPubKey = backupSigner.keyPair.pubKey.toHexKey()

        val ciphertext = runBlocking { backupSigner.nip44Encrypt(payload, derivedPubKey) }
        assertEquals(payload, runBlocking { backupSigner.nip44Decrypt(ciphertext, derivedPubKey) })

        assertThrows(SignerExceptions.CouldNotPerformException::class.java) {
            runBlocking {
                NostrSignerInternal(KeyPair(privKey = identityPrivKey)).nip44Decrypt(ciphertext, derivedPubKey)
            }
        }

        val identitySigner = NostrSignerInternal(KeyPair(privKey = identityPrivKey))
        val encryptedToSelf = runBlocking { identitySigner.nip44Encrypt(payload, identitySigner.keyPair.pubKey.toHexKey()) }
        assertEquals(
            "content encrypted to the identity key itself is readable by any decrypt-capable client, the leak the derived key removes",
            payload,
            runBlocking { identitySigner.nip44Decrypt(encryptedToSelf, identitySigner.keyPair.pubKey.toHexKey()) },
        )
    }

    @Test
    fun `backup key is outside the app-requestable derive_key domain`() {
        val identityPrivKey = KeyPair().privKey!!
        val backupPrivKey = backupKeyPair(identityPrivKey).privKey

        val futureDeriveKeyNonces = listOf(
            "amber-app-backup",
            "amber-app-backup-salt-v1",
            "amber-app-backup-key",
            "nip44kd",
            "nip44kdamber-app-backup",
            "",
        )
        for (nonce in futureDeriveKeyNonces) {
            val derived = EncryptionKeyDerivation.derivePrivateKey(identityPrivKey, nonce.encodeToByteArray())
            assertFalse(
                "nonce='$nonce', which a client could pass to a NIP-55/NIP-46 derive_key, collides with the backup key",
                backupPrivKey.contentEquals(derived),
            )
        }
    }

    @Test
    fun `decryptContent reads derived-key backups and rejects legacy and garbage`() = runBlocking {
        val backupSigner = backupSigner(account)
        val newCiphertext = backupSigner.nip44Encrypt(payload, backupSigner.keyPair.pubKey.toHexKey())
        val legacyCiphertext = account.crypto.nip44Encrypt(payload, account.hexKey)

        assertEquals(payload, ApplicationBackupPayloads.decryptContent(newCiphertext, account))
        assertNull(
            "content any client with an encrypt grant can get from nip44_encrypt to the user's own pubkey must not restore",
            ApplicationBackupPayloads.decryptContent(legacyCiphertext, account),
        )
        assertNull(ApplicationBackupPayloads.decryptContent("not-a-backup-payload", account))
    }
}
