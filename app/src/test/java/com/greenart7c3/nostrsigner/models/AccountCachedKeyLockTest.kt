package com.greenart7c3.nostrsigner.models

import com.greenart7c3.nostrsigner.service.DeviceUnlockPolicy
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import io.mockk.Called
import io.mockk.mockk
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
            keyAccessCheck = { DeviceUnlockPolicy.requireAccess(true, locked) },
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
}
