package com.greenart7c3.nostrsigner.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class TypedWordTest {
    @Test
    fun keepsTheWordBeingTypedLowerCased() {
        assertEquals(TypedWord("abo", movesOn = false), typedWord("  AbO"))
    }

    @Test
    fun anEmptyFieldStaysEmpty() {
        assertEquals(TypedWord("", movesOn = false), typedWord(""))
        assertEquals(TypedWord("", movesOn = false), typedWord("   "))
    }

    @Test
    fun aSpaceEndsTheWordAndMovesOn() {
        assertEquals(TypedWord("zoo", movesOn = true), typedWord("zoo "))
        assertEquals(TypedWord("zoo", movesOn = true), typedWord(" Zoo abandon"))
    }

    @Test
    fun aPastedPhraseKeepsOnlyItsFirstWord() {
        assertEquals(TypedWord("leader", movesOn = true), typedWord("leader monkey parrot ring"))
    }
}
