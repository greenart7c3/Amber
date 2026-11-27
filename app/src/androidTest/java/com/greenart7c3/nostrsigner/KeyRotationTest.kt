package com.greenart7c3.nostrsigner

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.security.KeyStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Wrapping-key rotation against the real AndroidKeyStore (GHSA-wv7c-hq2j-ppc5):
 * stored secrets survive each rotation, the key moves to the other alias, and
 * the previous key is deleted once the sweep has finished.
 */
@RunWith(AndroidJUnit4::class)
class KeyRotationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val aliases = listOf("AMBER_AES_KEY", "AMBER_AES_KEY_B")

    private fun presentAliases(): List<String> {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return aliases.filter { keyStore.containsAlias(it) }
    }

    @After
    fun tearDown() = runBlocking {
        DataStoreAccess.savePin(context, null)
    }

    @Test
    fun rotatingAwayAndBackKeepsSecretsReadableAndLeavesOneKey() = runBlocking {
        val originalPolicy = KeystorePolicy.requireUnlockedDevice(context)
        DataStoreAccess.savePin(context, "synthetic-pin-1234")
        val startAliases = presentAliases()
        assertEquals(1, startAliases.size)

        LockSettings.updateRequireUnlockedDevice(context, !originalPolicy)

        assertEquals("synthetic-pin-1234", DataStoreAccess.loadPin(context))
        assertEquals(!originalPolicy, KeystorePolicy.requireUnlockedDevice(context))
        val rotatedAliases = presentAliases()
        assertEquals(1, rotatedAliases.size)
        assertNotEquals(startAliases, rotatedAliases)

        LockSettings.updateRequireUnlockedDevice(context, originalPolicy)

        assertEquals("synthetic-pin-1234", DataStoreAccess.loadPin(context))
        assertEquals(originalPolicy, KeystorePolicy.requireUnlockedDevice(context))
        assertEquals(startAliases, presentAliases())
    }

    @Test
    fun completingWithNoRotationPendingIsANoOpThatKeepsDataReadable() = runBlocking {
        DataStoreAccess.savePin(context, "synthetic-pin-5678")

        SecureCryptoHelper.completePendingRotation(context)

        assertEquals("synthetic-pin-5678", DataStoreAccess.loadPin(context))
        assertEquals(1, presentAliases().size)
    }
}
