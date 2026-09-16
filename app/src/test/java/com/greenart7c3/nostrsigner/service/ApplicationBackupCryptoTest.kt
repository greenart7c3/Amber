package com.greenart7c3.nostrsigner.service

import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.models.Account
import com.vitorpamplona.quartz.experimental.decoupling.EncryptionKeyDerivation
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip19Bech32.toNpub
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

    @Before
    fun setUp() {
        // decryptContent's failure path logs; android.util.Log is not mocked on the JVM.
        mockkObject(AmberLog)
        every { AmberLog.e(any(), any(), any()) } returns Unit
        val identity = KeyPair()
        account = Account(
            signer = NostrSignerInternal(identity),
            hexKey = identity.pubKey.toHexKey(),
            npub = identity.pubKey.toNpub(),
            name = MutableStateFlow(""),
            picture = MutableStateFlow(""),
            signPolicy = 1,
            didBackup = true,
            scope = scope,
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

        // The pre-fix scheme encrypted to the identity key itself (encrypt-to-self);
        // that content IS readable by any decrypt-capable client, which is the leak
        // the derived key removes.
        val identitySigner = NostrSignerInternal(KeyPair(privKey = identityPrivKey))
        val leakedCiphertext = runBlocking { identitySigner.nip44Encrypt(payload, identitySigner.keyPair.pubKey.toHexKey()) }
        assertEquals(payload, runBlocking { identitySigner.nip44Decrypt(leakedCiphertext, identitySigner.keyPair.pubKey.toHexKey()) })
    }

    @Test
    fun `backup key is outside the app-requestable derive_key domain`() {
        val identityPrivKey = KeyPair().privKey!!
        val backupPrivKey = backupKeyPair(identityPrivKey).privKey

        // Nonces a client could pass to a future NIP-55/NIP-46 derive_key: the
        // backup labels themselves, the scheme prefix, and empty. None may land
        // on the backup key — the derivations must stay independent so shipping
        // derive_key later cannot expose backup key material.
        val appRequestableNonces = listOf(
            "amber-app-backup",
            "amber-app-backup-salt-v1",
            "amber-app-backup-key",
            "nip44kd",
            "nip44kdamber-app-backup",
            "",
        )
        for (nonce in appRequestableNonces) {
            val derived = EncryptionKeyDerivation.derivePrivateKey(identityPrivKey, nonce.encodeToByteArray())
            assertFalse("nonce='$nonce' collides with the backup key", backupPrivKey.contentEquals(derived))
        }
    }

    @Test
    fun `decryptContent reads new and legacy backups and rejects garbage`() = runBlocking {
        val backupSigner = backupSigner(account)
        val newCiphertext = backupSigner.nip44Encrypt(payload, backupSigner.keyPair.pubKey.toHexKey())
        val legacyCiphertext = account.nip44Encrypt(payload, account.hexKey)

        assertEquals(payload, ApplicationBackup.decryptContent(newCiphertext, account))
        assertEquals(payload, ApplicationBackup.decryptContent(legacyCiphertext, account))
        assertNull(ApplicationBackup.decryptContent("not-a-backup-payload", account))
    }
}
