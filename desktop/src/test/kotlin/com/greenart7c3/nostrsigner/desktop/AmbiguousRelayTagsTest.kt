package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.desktop.core.hasAmbiguousRelayTags
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NV-01: NIP-42 auth events name exactly one relay. Authorization decides on
 * the first relay tag, so a second relay tag must never be signed.
 */
class AmbiguousRelayTagsTest {
    @Test
    fun `a single relay tag is unambiguous`() {
        assertFalse(
            hasAmbiguousRelayTags(
                22242,
                arrayOf(arrayOf("relay", "wss://granted.example"), arrayOf("challenge", "c")),
            ),
        )
    }

    @Test
    fun `a second relay tag is ambiguous`() {
        assertTrue(
            hasAmbiguousRelayTags(
                22242,
                arrayOf(
                    arrayOf("relay", "wss://granted.example"),
                    arrayOf("relay", "wss://other.example"),
                    arrayOf("challenge", "c"),
                ),
            ),
        )
    }

    @Test
    fun `other kinds are never ambiguous`() {
        assertFalse(hasAmbiguousRelayTags(1, arrayOf(arrayOf("relay", "a"), arrayOf("relay", "b"))))
    }
}
