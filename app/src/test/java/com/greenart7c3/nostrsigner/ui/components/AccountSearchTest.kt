package com.greenart7c3.nostrsigner.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountSearchTest {
    private val npub = "npub1sg6plzptd64u62a878hep2kev88swjh3tw00gjsfl8f237lmu63q0uf63m"

    @Test
    fun blankQueryMatchesEverything() {
        assertTrue(accountMatchesSearch("", npub, ""))
        assertTrue(accountMatchesSearch("   ", npub, "Alice"))
    }

    @Test
    fun nameMatchesIgnoringCaseAndSurroundingWhitespace() {
        assertTrue(accountMatchesSearch("  aLiCe ", npub, "Alice Work"))
        assertTrue(accountMatchesSearch("work", npub, "Alice Work"))
        assertFalse(accountMatchesSearch("bob", npub, "Alice Work"))
    }

    @Test
    fun fullPartialAndPastedNpubsMatch() {
        assertTrue(accountMatchesSearch(npub, npub, ""))
        assertTrue(accountMatchesSearch(" nostr:$npub\n", npub, "Alice"))
        assertTrue(accountMatchesSearch("q0uf63m", npub, ""))
        assertTrue(accountMatchesSearch(npub.uppercase(), npub, ""))
        assertFalse(accountMatchesSearch("npub1zzz", npub, ""))
    }

    @Test
    fun filteredRowsKeepTheIndexOfTheirAccount() {
        val accounts = (1..20).map { "npub1account$it" to if (it % 2 == 0) "Same" else "" }

        val shown = accounts.withIndex().toList()
            .filter { (_, acc) -> accountMatchesSearch("account19", acc.first, acc.second) }

        assertEquals(listOf(18), shown.map { it.index })
        assertEquals(10, accounts.count { accountMatchesSearch("same", it.first, it.second) })
    }
}
