package com.greenart7c3.nostrsigner

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The saved-account list lives in the app-wide preference file under keys every
 * install already has; these pin the stored format so a refactor cannot lose accounts.
 */
class SavedAccountsTest {
    private val prefs = FakePreferences()
    private val context = mockk<Context> { every { getSharedPreferences("prefs", Context.MODE_PRIVATE) } returns prefs }

    @Before
    fun setUp() {
        mockkObject(AmberLog)
        every { AmberLog.d(any(), any<String>()) } returns Unit
        SavedAccounts.invalidate()
    }

    @After
    fun tearDown() {
        SavedAccounts.invalidate()
        unmockkObject(AmberLog)
    }

    @Test
    fun `accounts stored by an existing install are read`() {
        prefs.values["all_saved_accounts"] = "npub1a,npub1b"
        prefs.values["currently_logged_in_account"] = "npub1b"

        assertEquals(listOf("npub1a", "npub1b"), SavedAccounts.all(context).map { it.npub })
        assertEquals("npub1b", SavedAccounts.current(context))
        assertTrue(SavedAccounts.contains(context, "npub1a"))
    }

    @Test
    fun `without a stored current account the first saved one is current`() {
        prefs.values["all_saved_accounts"] = "npub1a,npub1b"

        assertEquals("npub1a", SavedAccounts.current(context))
    }

    @Test
    fun `selecting an account adds it once and makes it current`() {
        SavedAccounts.select(context, "npub1a")
        SavedAccounts.select(context, "npub1b")
        SavedAccounts.select(context, "npub1a")

        assertEquals("npub1a,npub1b", prefs.values["all_saved_accounts"])
        assertEquals("npub1a", prefs.values["currently_logged_in_account"])
    }

    @Test
    fun `removing the last account clears the stored list`() {
        prefs.values["all_saved_accounts"] = "npub1a,npub1b"

        SavedAccounts.remove(context, "npub1a")
        assertEquals("npub1b", prefs.values["all_saved_accounts"])

        SavedAccounts.remove(context, "npub1b")
        assertFalse(prefs.values.containsKey("all_saved_accounts"))
        assertTrue(SavedAccounts.all(context).isEmpty())
    }

    @Test
    fun `a favorite is stored by npub and unsetting the last one removes the key`() {
        SavedAccounts.setFavorite(context, "npub1fav", true)
        assertTrue("npub1fav" in SavedAccounts.favorites(context).value)
        @Suppress("UNCHECKED_CAST")
        assertTrue("npub1fav" in (prefs.values["favorite_accounts"] as Set<String>))

        SavedAccounts.setFavorite(context, "npub1fav", false)
        assertFalse("npub1fav" in SavedAccounts.favorites(context).value)
        assertNull(prefs.values["favorite_accounts"])
    }
}
