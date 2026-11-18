package com.greenart7c3.nostrsigner

import android.app.KeyguardManager
import android.content.Context
import com.greenart7c3.nostrsigner.data.AccountRepository
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkObject
import java.security.InvalidKeyException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AccountStorageTest {
    private val keyPair = KeyPair()
    private val npub = keyPair.pubKey.toNpub()
    private val appPrefs = FakePreferences(mapOf("all_saved_accounts" to npub))
    private val accountPrefs = FakePreferences(mapOf("nostr_pubkey" to keyPair.pubKey.toHexKey()))
    private val keyguard = mockk<KeyguardManager> { every { isDeviceLocked } returns false }
    private val context = mockk<Context>(relaxed = true) {
        every { applicationContext } returns this
        every { getSharedPreferences("prefs", Context.MODE_PRIVATE) } returns appPrefs
        every { getSharedPreferences("prefs_$npub", Context.MODE_PRIVATE) } returns accountPrefs
        every { getSystemService(KeyguardManager::class.java) } returns keyguard
    }
    private val failed = MutableStateFlow<List<String>>(emptyList())
    private val repository = mockk<AccountRepository> {
        every { keystoreFailedAccounts } returns failed
        every { closeDatabasesFor(any()) } just runs
    }

    @Before
    fun setUp() {
        mockkObject(DataStoreAccess, AmberLog)
        every { AmberLog.d(any(), any<String>()) } returns Unit
        every { AmberLog.d(any(), any(), any()) } returns Unit
        every { AmberLog.e(any(), any(), any()) } returns Unit
        AccountStorage.install(repository, CoroutineScope(Dispatchers.Unconfined))
        AccountStorage.invalidateCaches()
    }

    @After
    fun tearDown() {
        AccountStorage.invalidateCaches()
        unmockkObject(DataStoreAccess, AmberLog)
    }

    private fun keyIsRefused() {
        coEvery { DataStoreAccess.getEncryptedKey(any(), npub, DataStoreAccess.NOSTR_PRIVKEY) } throws InvalidKeyException("refused")
    }

    @Test
    fun `a broken key skips the account and reports it`() = runBlocking {
        keyIsRefused()

        assertNull(AccountStorage.load(context, npub))
        assertEquals(listOf(npub), failed.value)
    }

    // The policy refusing while locked is the setting working: reporting it would tell the user to log out.
    @Test
    fun `a key refused while the device is locked under the policy is not reported`() = runBlocking {
        keyIsRefused()
        appPrefs.values["require_unlocked_device"] = true
        every { keyguard.isDeviceLocked } returns true

        assertNull(AccountStorage.load(context, npub))
        assertTrue(failed.value.isEmpty())
    }

    @Test
    fun `a loaded account is cached and clears an earlier failure`() = runBlocking {
        failed.value = listOf(npub)
        coEvery { DataStoreAccess.getEncryptedKey(any(), npub, DataStoreAccess.NOSTR_PRIVKEY) } returns keyPair.privKey!!.toHexKey()

        val account = AccountStorage.load(context)
        assertEquals(keyPair.pubKey.toHexKey(), account?.hexKey)
        assertTrue(failed.value.isEmpty())

        AccountStorage.load(context, npub)
        coVerify(exactly = 1) { DataStoreAccess.getEncryptedKey(any(), any(), any()) }
        assertEquals(listOf(npub), AccountStorage.cached().map { it.npub })
    }

    @Test
    fun `an account that is not saved is never decrypted`() = runBlocking {
        assertNull(AccountStorage.load(context, "npub1notsaved"))
        coVerify(exactly = 0) { DataStoreAccess.getEncryptedKey(any(), any(), any()) }
    }

    @Test
    fun `logout zeroes the cached private key`() = runBlocking {
        coEvery { DataStoreAccess.getEncryptedKey(any(), npub, DataStoreAccess.NOSTR_PRIVKEY) } returns keyPair.privKey!!.toHexKey()
        coEvery { DataStoreAccess.clearCacheForNpub(any(), any()) } just runs
        val privKey = AccountStorage.load(context, npub)!!.signer.keyPair.privKey!!

        assertTrue(AccountStorage.logout(npub, context))

        assertTrue(privKey.all { it == 0.toByte() })
        assertTrue(AccountStorage.cached().isEmpty())
    }
}
