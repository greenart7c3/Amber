package com.greenart7c3.nostrsigner.models

import android.app.KeyguardManager
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.service.DeviceUnlockPolicy
import com.greenart7c3.nostrsigner.service.installAmberInstance
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import io.mockk.Called
import io.mockk.every
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
    private fun newAccount(signer: NostrSignerInternal, scope: CoroutineScope): Account = Account(
        signer = signer,
        hexKey = "owner",
        npub = "npub-owner",
        name = MutableStateFlow(""),
        picture = MutableStateFlow(""),
        signPolicy = 1,
        didBackup = false,
        scope = scope,
    )

    @Test
    fun `cached account cannot sign or decrypt after device lock`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val signer = mockk<NostrSignerInternal>()
        var locked = false
        val account = Account(
            signer = signer,
            hexKey = "owner",
            npub = "npub-owner",
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
                runBlocking { account.nip44Decrypt("synthetic-ciphertext", "peer") }
            }
            assertThrows(SecurityException::class.java) {
                runBlocking { account.seedWords() }
            }
            verify { signer wasNot Called }
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
        installAmberInstance(amber)
        mockkObject(LocalPreferences)
        try {
            // Opt-in off: allowed regardless of lock state, keyguard untouched.
            every { LocalPreferences.getRequireUnlockedDevice(any()) } returns false
            every { keyguard.isDeviceLocked } returns true
            newAccount(mockk<NostrSignerInternal>(), scope).requirePrivateKeyAccess()

            // Opt-in on, device unlocked: allowed.
            every { LocalPreferences.getRequireUnlockedDevice(any()) } returns true
            every { keyguard.isDeviceLocked } returns false
            newAccount(mockk<NostrSignerInternal>(), scope).requirePrivateKeyAccess()

            // Opt-in on, device locked: refused.
            every { keyguard.isDeviceLocked } returns true
            assertThrows(SecurityException::class.java) {
                newAccount(mockk<NostrSignerInternal>(), scope).requirePrivateKeyAccess()
            }
        } finally {
            scope.cancel()
            unmockkAll()
        }
    }
}
