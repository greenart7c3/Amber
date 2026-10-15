package com.greenart7c3.nostrsigner.service

import com.greenart7c3.nostrsigner.models.Account
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.Nip01Crypto
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip06KeyDerivation.Nip06
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountExportSeedRestoreTest {
    private val words = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
    private val otherWords = "legal winner thank year wave sausage worth useful legal winner thank yellow"

    private fun realAccount(privKeyHex: String): Account {
        val privKey = privKeyHex.hexToByteArray()
        val hexKey = Nip01Crypto.pubKeyCreate(privKey).toHexKey()
        return Account(
            hexKey = hexKey,
            npub = hexKey.toNpub(),
            name = MutableStateFlow(""),
            picture = MutableStateFlow(""),
            signPolicy = 1,
            didBackup = false,
            // Explicit scope: the default reads Amber.instance, uninitialized in JVM tests.
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            signer = NostrSignerInternal(KeyPair(privKey)),
        )
    }

    @Test
    fun `seed round trips through a real signer`() = runBlocking {
        val privKeyHex = Nip06().privateKeyFromMnemonic(words).toHexKey()
        val account = realAccount(privKeyHex)
        val encrypted = account.nip44Encrypt(words, account.hexKey)

        assertEquals(words, AccountExportService.decryptExportedSeedWords(account, encrypted, privKeyHex))
    }

    @Test
    fun `absent seed does not overwrite existing words`() = runBlocking {
        assertNull(AccountExportService.decryptExportedSeedWords(mockk(), ""))
    }

    @Test
    fun `invalid decrypted words are dropped instead of failing the account`() {
        val account = mockk<Account>()
        every { account.hexKey } returns "owner"
        coEvery { account.nip44Decrypt("encrypted-seed", "owner") } returns "not a mnemonic"
        assertNull(runBlocking { AccountExportService.decryptExportedSeedWords(account, "encrypted-seed") })
    }

    @Test
    fun `undecryptable seed words are dropped instead of failing the account`() {
        val account = mockk<Account>()
        every { account.hexKey } returns "owner"
        coEvery { account.nip44Decrypt("ciphertext-as-seed", "owner") } throws RuntimeException("bad envelope")
        assertNull(runBlocking { AccountExportService.decryptExportedSeedWords(account, "ciphertext-as-seed") })
    }

    @Test
    fun `a valid mnemonic deriving a different key is dropped`() = runBlocking {
        val privKeyHex = Nip06().privateKeyFromMnemonic(words).toHexKey()
        val account = realAccount(privKeyHex)
        // otherWords is a valid mnemonic for a DIFFERENT identity, encrypted
        // under this account's key: storing it would let a later "recovery"
        // produce someone else's key.
        val encryptedOther = account.nip44Encrypt(otherWords, account.hexKey)

        assertNull(AccountExportService.decryptExportedSeedWords(account, encryptedOther, privKeyHex))
    }
}
