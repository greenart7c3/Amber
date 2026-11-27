package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.desktop.core.KeystorePassword
import com.greenart7c3.nostrsigner.desktop.core.PasswordStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeStore(
    var value: String? = null,
    var available: Boolean = true,
    override val description: String = "fake",
) : PasswordStore {
    override fun load(): String? = if (available) value else null

    override fun store(secret: String): Boolean {
        if (!available) return false
        value = secret
        return true
    }

    override fun delete() {
        value = null
    }
}

class KeystorePasswordTest {
    @Test
    fun freshInstallPrefersOsStore() {
        val os = FakeStore()
        val file = FakeStore()
        val resolved = KeystorePassword.resolve(os, file, keystoreExists = false) { true }
        assertEquals(os, resolved.source)
        assertEquals(resolved.password, os.value)
        assertNull(file.value)
    }

    @Test
    fun freshInstallFallsBackToFile() {
        val os = FakeStore(available = false)
        val file = FakeStore()
        val resolved = KeystorePassword.resolve(os, file, keystoreExists = false) { true }
        assertEquals(file, resolved.source)
        assertEquals(resolved.password, file.value)
    }

    @Test
    fun existingKeystoreUsesOsValueAndDropsFileCopy() {
        val os = FakeStore(value = "os-secret")
        val file = FakeStore(value = "os-secret")
        val resolved = KeystorePassword.resolve(os, file, keystoreExists = true) { it == "os-secret" }
        assertEquals(os, resolved.source)
        assertEquals("os-secret", resolved.password)
        assertNull(file.value)
    }

    @Test
    fun legacyPasswordFileMigratesIntoOsStore() {
        val os = FakeStore(value = null)
        val file = FakeStore(value = "legacy-secret")
        val resolved = KeystorePassword.resolve(os, file, keystoreExists = true) { it == "legacy-secret" }
        assertEquals(os, resolved.source)
        assertEquals("legacy-secret", resolved.password)
        assertEquals("legacy-secret", os.value)
        assertNull(file.value)
    }

    @Test
    fun legacyPasswordFileStaysWhenOsStoreUnavailable() {
        val os = FakeStore(available = false)
        val file = FakeStore(value = "legacy-secret")
        val resolved = KeystorePassword.resolve(os, file, keystoreExists = true) { it == "legacy-secret" }
        assertEquals(file, resolved.source)
        assertEquals("legacy-secret", file.value)
    }

    @Test
    fun `a wrong OS value, such as a stale entry from a wiped install, falls back to the working file value`() {
        val os = FakeStore(value = "stale-secret")
        val file = FakeStore(value = "real-secret")
        val resolved = KeystorePassword.resolve(os, file, keystoreExists = true) { it == "real-secret" }
        assertEquals("real-secret", resolved.password)
        assertEquals("the working password replaces the stale credential-store entry", "real-secret", os.value)
        assertNull(file.value)
    }

    @Test
    fun noWorkingPasswordThrows() {
        val os = FakeStore(value = "wrong")
        val file = FakeStore(value = null)
        assertThrows(IllegalStateException::class.java) {
            KeystorePassword.resolve(os, file, keystoreExists = true) { false }
        }
    }

    @Test
    fun generatedPasswordsAreUniqueAndLong() {
        val a = KeystorePassword.generate()
        val b = KeystorePassword.generate()
        assertFalse(a == b)
        assertTrue(a.length >= 40)
    }
}
