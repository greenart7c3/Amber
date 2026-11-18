package com.greenart7c3.nostrsigner

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavSettingsTest {
    private val prefs = FakePreferences()
    private val context = mockk<Context> { every { getSharedPreferences("prefs", Context.MODE_PRIVATE) } returns prefs }

    @Test
    fun `nothing saved reads as empty`() = runBlocking {
        assertEquals("", WebDavSettings.url(context))
        assertEquals("", WebDavSettings.username(context))
        assertEquals("", WebDavSettings.password(context))
    }

    // Key rotation re-encrypts the password; a save made meanwhile must not be overwritten.
    @Test
    fun `the encrypted password is replaced only while it is still the expected one`() {
        prefs.values["webdav_password_encrypted"] = "old"

        assertFalse(WebDavSettings.replaceEncryptedPassword(context, expected = "other", encryptedPassword = "new"))
        assertEquals("old", WebDavSettings.encryptedPassword(context))

        assertTrue(WebDavSettings.replaceEncryptedPassword(context, expected = "old", encryptedPassword = "new"))
        assertEquals("new", WebDavSettings.encryptedPassword(context))
    }
}
