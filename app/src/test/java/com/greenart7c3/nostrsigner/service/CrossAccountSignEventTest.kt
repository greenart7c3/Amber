package com.greenart7c3.nostrsigner.service

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.IntentData
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * F-11: a sign_event request whose JSON names a second account stored on the
 * device must be signed by the account that authorizes the request, never by
 * the named one, and the approval guard must block that mismatch even for
 * anonymous zap requests (kind 9734 with an "anon" tag).
 */
class CrossAccountSignEventTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val kpA = KeyPair()
    private val accountA = Account(
        signer = NostrSignerInternal(kpA),
        hexKey = kpA.pubKey.toHexKey(),
        npub = kpA.pubKey.toNpub(),
        name = MutableStateFlow(""),
        picture = MutableStateFlow(""),
        signPolicy = 0,
        didBackup = false,
        scope = scope,
        keyAccessCheck = {},
    )

    private val kpB = KeyPair()

    // Spied signer: the test must prove B's key never performs a signature.
    private val signerB = mockk<NostrSignerInternal>(relaxed = true)
    private val accountB = Account(
        signer = signerB,
        hexKey = kpB.pubKey.toHexKey(),
        npub = kpB.pubKey.toNpub(),
        name = MutableStateFlow(""),
        picture = MutableStateFlow(""),
        signPolicy = 0,
        didBackup = false,
        scope = scope,
        keyAccessCheck = {},
    )

    @Before
    fun setUp() {
        mockkObject(AmberLog)
        every { AmberLog.e(any(), any(), any()) } returns Unit
        every { AmberLog.e(any(), any()) } returns Unit
        installAmberInstance(mockk<Amber>(relaxed = true))
        // accountB is stored on this device; the old code loaded it here and
        // switched the signing key to it.
        mockkObject(LocalPreferences)
        every { LocalPreferences.loadFromEncryptedStorageSync(any(), any()) } returns null
        coEvery { LocalPreferences.loadAccount(any(), any()) } returns null
        every { LocalPreferences.loadFromEncryptedStorageSync(any(), eq(accountB.npub)) } returns accountB
        coEvery { LocalPreferences.loadAccount(any(), eq(accountB.npub)) } returns accountB
    }

    @After
    fun tearDown() {
        unmockkObject(AmberLog)
        unmockkObject(LocalPreferences)
    }

    private fun anonZapJson(pubKey: String) = """{"id":"","pubkey":"$pubKey","created_at":1,"kind":9734,"tags":[["anon",""],["p","3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"]],"content":"{}","sig":""}"""

    private fun plainEventJson(pubKey: String) = """{"id":"","pubkey":"$pubKey","created_at":1,"kind":1,"tags":[],"content":"hello","sig":""}"""

    private fun intentFor(json: String): Intent {
        val uri = mockk<Uri>()
        every { uri.toString() } returns json
        val extras = mockk<Bundle>()
        every { extras.getString(any()) } returns null
        every { extras.getString("type") } returns "sign_event"
        val intent = mockk<Intent>(relaxed = true)
        every { intent.data } returns uri
        every { intent.extras } returns extras
        every { intent.getStringExtra("current_user") } returns null
        return intent
    }

    private suspend fun getIntentData(intent: Intent): IntentData? {
        val method = IntentUtils::class.declaredMemberFunctions
            .first { it.name == "getIntentDataFromIntent" }
        method.isAccessible = true
        return method.callSuspend(
            IntentUtils,
            intent,
            "com.attacker.app",
            null,
            accountA,
        ) as IntentData?
    }

    @Test
    fun `intent naming a stored second account never signs with it`() = runBlocking {
        val result = getIntentData(intentFor(anonZapJson(accountB.hexKey)))

        assertNotNull(result)
        assertNotNull(result!!.event)
        // Quartz gift-wraps anon zaps under an ephemeral key, so the wrapper
        // pubkey is neither account; what matters is that B's key is unused.
        verify { signerB wasNot Called }
        assertTrue(result.event!!.pubKey != accountB.hexKey)
    }

    @Test
    fun `non-anon event naming a stored second account is signed by the approving account`() = runBlocking {
        val result = getIntentData(intentFor(plainEventJson(accountB.hexKey)))

        assertNotNull(result)
        assertNotNull(result!!.event)
        verify { signerB wasNot Called }
        assertEquals(accountA.hexKey, result.event!!.pubKey)
        assertEquals(accountA.npub, result.currentAccount)
    }

    @Test
    fun `event naming the approving account keeps signing it`() = runBlocking {
        val result = getIntentData(intentFor(plainEventJson(accountA.hexKey)))

        assertNotNull(result)
        assertNotNull(result!!.event)
        assertEquals(accountA.hexKey, result.event!!.pubKey)
    }

    @Test
    fun `guard blocks an anon zap naming a stored account`() {
        assertTrue(
            isCrossAccountSignEvent(
                accountB.hexKey,
                accountA.hexKey,
                9734,
                arrayOf(arrayOf("anon", "")),
                listOf(accountA.npub, accountB.npub),
            ),
        )
    }

    @Test
    fun `guard allows an anon zap with an ephemeral key`() {
        val ephemeral = KeyPair().pubKey.toHexKey()
        assertFalse(
            isCrossAccountSignEvent(
                ephemeral,
                accountA.hexKey,
                9734,
                arrayOf(arrayOf("anon", "")),
                listOf(accountA.npub, accountB.npub),
            ),
        )
    }

    @Test
    fun `guard blocks a non-anon pubkey mismatch regardless of storage`() {
        assertTrue(
            isCrossAccountSignEvent(
                accountB.hexKey,
                accountA.hexKey,
                1,
                emptyArray(),
                emptyList(),
            ),
        )
    }
}
