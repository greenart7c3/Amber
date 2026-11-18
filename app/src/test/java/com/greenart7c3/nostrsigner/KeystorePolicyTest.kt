package com.greenart7c3.nostrsigner

import android.app.KeyguardManager
import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class KeystorePolicyTest {
    private val prefs = FakePreferences()
    private val keyguard = mockk<KeyguardManager>()
    private val context = mockk<Context> {
        every { getSharedPreferences("prefs", Context.MODE_PRIVATE) } returns prefs
        every { getSystemService(KeyguardManager::class.java) } returns keyguard
    }

    private fun policyOn() {
        prefs.values["require_unlocked_device"] = true
    }

    @Test
    fun `with the policy off a locked device can still sign`() {
        every { keyguard.isDeviceLocked } returns true

        KeystorePolicy.requireAccess(context)
        assertFalse(KeystorePolicy.isLockedOut(context))
    }

    @Test
    fun `with the policy on only an unlocked device can sign`() {
        policyOn()
        every { keyguard.isDeviceLocked } returns false
        KeystorePolicy.requireAccess(context)

        every { keyguard.isDeviceLocked } returns true
        assertThrows(SecurityException::class.java) { KeystorePolicy.requireAccess(context) }
        assertTrue(KeystorePolicy.isLockedOut(context))
    }

    // The gate fails closed; a key failure without a keyguard is still reported as broken.
    @Test
    fun `without a keyguard the gate refuses but the failure is not blamed on the lock`() {
        policyOn()
        every { context.getSystemService(KeyguardManager::class.java) } returns null

        assertThrows(SecurityException::class.java) { KeystorePolicy.requireAccess(context) }
        assertFalse(KeystorePolicy.isLockedOut(context))
    }

    @Test
    fun `the alias and the policy are committed together, and the policy only when given`() {
        assertNull(KeystorePolicy.activeAlias(context))

        assertTrue(KeystorePolicy.commitActiveAlias(context, "alias-b", requireUnlockedDevice = true))
        assertEquals("alias-b", KeystorePolicy.activeAlias(context))
        assertTrue(KeystorePolicy.requireUnlockedDevice(context))

        assertTrue(KeystorePolicy.commitActiveAlias(context, "alias-a"))
        assertEquals("alias-a", KeystorePolicy.activeAlias(context))
        assertTrue(KeystorePolicy.requireUnlockedDevice(context))
    }
}
