package com.greenart7c3.nostrsigner.models

import android.app.KeyguardManager
import android.content.Context
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.KeystorePolicy
import com.greenart7c3.nostrsigner.service.DeviceUnlockPolicy
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import io.mockk.Called
import io.mockk.every
import io.mockk.excludeRecords
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Test

class AccountCachedKeyLockTest {
    // The account reads its public key from the signer's key pair. That is not key
    // use, so wasNot Called ignores it.
    private fun NostrSignerInternal.withKeyPair(): NostrSignerInternal = apply {
        every { keyPair } returns KeyPair()
        excludeRecords { keyPair }
    }

    private fun newAccount(signer: NostrSignerInternal, scope: CoroutineScope, appContext: Context? = null): Account = Account(
        signer = signer.withKeyPair(),
        name = MutableStateFlow(""),
        picture = MutableStateFlow(""),
        signPolicy = 1,
        didBackup = false,
        scope = scope,
        appContext = appContext,
    )

    @Test
    fun `cached account cannot sign or decrypt after device lock`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val signer = mockk<NostrSignerInternal>()
        var locked = false
        val account = Account(
            signer = signer.withKeyPair(),
            name = MutableStateFlow(""),
            picture = MutableStateFlow(""),
            signPolicy = 1,
            didBackup = false,
            scope = scope,
            keyAccessCheck = { DeviceUnlockPolicy.requireAccess(true) { locked } },
        )
        try {
            account.requirePrivateKeyAccess()
            locked = true
            assertThrows(SecurityException::class.java) {
                account.signSync<Event>(0, 1, emptyArray(), "synthetic-event")
            }
            assertThrows(SecurityException::class.java) {
                runBlocking { account.crypto.nip44Decrypt("synthetic-ciphertext", "peer") }
            }
            assertThrows(SecurityException::class.java) {
                runBlocking { account.keyExport.seedWords() }
            }
            verify { signer wasNot Called }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `default gate refuses when the account has no context to check with`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            assertThrows(IllegalStateException::class.java) {
                newAccount(mockk<NostrSignerInternal>(), scope).requirePrivateKeyAccess()
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `default gate reads the stored preference and the keyguard state`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val amber = mockk<Amber>()
        val keyguard = mockk<KeyguardManager>()
        every { amber.getSystemService(KeyguardManager::class.java) } returns keyguard
        mockkObject(KeystorePolicy)
        try {
            // Opt-in off: allowed regardless of lock state, keyguard untouched.
            every { KeystorePolicy.requireUnlockedDevice(any()) } returns false
            every { keyguard.isDeviceLocked } returns true
            newAccount(mockk<NostrSignerInternal>(), scope, amber).requirePrivateKeyAccess()

            // Opt-in on, device unlocked: allowed.
            every { KeystorePolicy.requireUnlockedDevice(any()) } returns true
            every { keyguard.isDeviceLocked } returns false
            newAccount(mockk<NostrSignerInternal>(), scope, amber).requirePrivateKeyAccess()

            // Opt-in on, device locked: refused.
            every { keyguard.isDeviceLocked } returns true
            assertThrows(SecurityException::class.java) {
                newAccount(mockk<NostrSignerInternal>(), scope, amber).requirePrivateKeyAccess()
            }
        } finally {
            scope.cancel()
            unmockkAll()
        }
    }
}
