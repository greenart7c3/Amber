package com.greenart7c3.nostrsigner.service

import com.greenart7c3.nostrsigner.models.Account
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class AccountExportSeedRestoreTest {
    private val words = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

    @Test
    fun `restore returns decrypted words instead of storing ciphertext as a mnemonic`() = runBlocking {
        val account = mockk<Account>()
        every { account.hexKey } returns "owner"
        coEvery { account.nip44Decrypt("encrypted-seed", "owner") } returns words
        assertEquals(words, AccountExportService.decryptExportedSeedWords(account, "encrypted-seed"))
    }

    @Test
    fun `absent seed does not overwrite existing words`() = runBlocking {
        assertNull(AccountExportService.decryptExportedSeedWords(mockk(), ""))
    }

    @Test
    fun `invalid decrypted words fail before account storage`() {
        val account = mockk<Account>()
        every { account.hexKey } returns "owner"
        coEvery { account.nip44Decrypt("encrypted-seed", "owner") } returns "not a mnemonic"
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { AccountExportService.decryptExportedSeedWords(account, "encrypted-seed") }
        }
    }
}
