package com.greenart7c3.nostrsigner.service

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.IntentData
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import io.mockk.every
import io.mockk.mockk
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
 * An encrypt/decrypt intent without current_user used to record the peer's
 * npub as its account, which is not a local account at all, so the batch
 * confirm could not resolve it and skipped the request.
 */
class EncryptIntentAccountTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val kp = KeyPair()
    private val account = Account(
        signer = NostrSignerInternal(kp),
        hexKey = kp.pubKey.toHexKey(),
        npub = kp.pubKey.toNpub(),
        name = MutableStateFlow(""),
        picture = MutableStateFlow(""),
        signPolicy = 0,
        didBackup = false,
        scope = scope,
        keyAccessCheck = {},
    )
    private val peer = KeyPair().pubKey.toHexKey()

    @Before
    fun setUp() {
        mockkObject(AmberLog)
        every { AmberLog.e(any(), any(), any()) } returns Unit
        every { AmberLog.e(any(), any()) } returns Unit
        installAmberInstance(mockk<Amber>(relaxed = true))
    }

    @After
    fun tearDown() {
        unmockkObject(AmberLog)
    }

    private fun encryptIntent(currentUser: String?): Intent {
        val uri = mockk<Uri>()
        every { uri.toString() } returns "hello"
        val extras = mockk<Bundle>()
        every { extras.getString(any()) } returns null
        every { extras.getString("type") } returns "nip44_encrypt"
        every { extras.getString("pubkey") } returns peer
        val intent = mockk<Intent>(relaxed = true)
        every { intent.data } returns uri
        every { intent.extras } returns extras
        every { intent.getStringExtra("current_user") } returns currentUser
        return intent
    }

    private suspend fun parse(intent: Intent): IntentData? {
        val method = IntentUtils::class.declaredMemberFunctions.first { it.name == "getIntentDataFromIntent" }
        method.isAccessible = true
        return method.callSuspend(IntentUtils, intent, "com.example.app", null, account) as IntentData?
    }

    @Test
    fun withoutCurrentUserTheRequestBelongsToTheAccountThatHandledIt() = runBlocking {
        val result = parse(encryptIntent(currentUser = null))!!
        assertEquals(account.npub, result.currentAccount)
    }

    @Test
    fun currentUserStillWinsWhenGiven() = runBlocking {
        val result = parse(encryptIntent(currentUser = account.npub))!!
        assertEquals(account.npub, result.currentAccount)
    }
}
