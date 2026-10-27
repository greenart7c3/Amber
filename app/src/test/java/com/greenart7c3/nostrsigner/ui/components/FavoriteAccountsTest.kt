package com.greenart7c3.nostrsigner.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class FavoriteAccountsTest {
    private val accounts = (1..20).map { "npub$it" }

    @Test
    fun favoritesComeFirstAndEachGroupKeepsItsSavedOrder() {
        val ordered = accounts.favoritesFirst(setOf("npub19", "npub17")) { it }

        assertEquals(listOf("npub17", "npub19"), ordered.take(2))
        assertEquals(accounts - setOf("npub17", "npub19"), ordered.drop(2))
    }

    @Test
    fun noFavoritesKeepsTheSavedOrder() {
        assertEquals(accounts, accounts.favoritesFirst(emptySet()) { it })
    }

    @Test
    fun reorderedRowsKeepTheIndexOfTheirAccount() {
        val ordered = accounts.withIndex().toList().favoritesFirst(setOf("npub20")) { it.value }

        assertEquals(19, ordered.first().index)
        ordered.forEach { (index, npub) -> assertEquals(accounts[index], npub) }
    }

    @Test
    fun favoritesOfRemovedAccountsAreIgnored() {
        assertEquals(accounts, accounts.favoritesFirst(setOf("npub-gone")) { it })
    }
}
