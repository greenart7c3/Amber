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
    private fun NostrSignerInternal.withKeyPairReadsNotCountedAsKeyUse(): NostrSignerInternal = apply {
        every { keyPair } returns KeyPair()
        excludeRecords { keyPair }
    }

    private fun newAccount(signer: NostrSignerInternal, scope: CoroutineScope, appContext: Context? = null): Account = Account(
        signer = signer.withKeyPairReadsNotCountedAsKeyUse(),
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
            signer = signer.withKeyPairReadsNotCountedAsKeyUse(),
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

    private fun withDefaultGate(requireUnlockedDevice: Boolean, deviceLocked: Boolean, onAccount: (Account) -> Unit) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val amber = mockk<Amber>()
        val keyguard = mockk<KeyguardManager>()
        every { amber.getSystemService(KeyguardManager::class.java) } returns keyguard
        mockkObject(KeystorePolicy)
        try {
            every { KeystorePolicy.requireUnlockedDevice(any()) } returns requireUnlockedDevice
            every { keyguard.isDeviceLocked } returns deviceLocked
            onAccount(newAccount(mockk<NostrSignerInternal>(), scope, amber))
        } finally {
            scope.cancel()
            unmockkAll()
        }
    }

    @Test
    fun `default gate with the stored preference off allows a locked device`() {
        withDefaultGate(requireUnlockedDevice = false, deviceLocked = true) { it.requirePrivateKeyAccess() }
    }

    @Test
    fun `default gate with the stored preference on allows an unlocked device`() {
        withDefaultGate(requireUnlockedDevice = true, deviceLocked = false) { it.requirePrivateKeyAccess() }
    }

    @Test
    fun `default gate with the stored preference on refuses a locked device`() {
        withDefaultGate(requireUnlockedDevice = true, deviceLocked = true) { account ->
            assertThrows(SecurityException::class.java) { account.requirePrivateKeyAccess() }
        }
    }
}
