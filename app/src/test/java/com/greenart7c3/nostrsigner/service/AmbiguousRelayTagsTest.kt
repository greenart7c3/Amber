package com.greenart7c3.nostrsigner.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NV-01: NIP-42 auth events name exactly one relay. Authorization, the
 * whitelist and the consent screen decide on the first relay tag, so a
 * second relay tag would be signed without ever being granted.
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
    fun `tag order does not matter for the second relay tag`() {
        assertTrue(
            hasAmbiguousRelayTags(
                22242,
                arrayOf(
                    arrayOf("relay", "wss://other.example"),
                    arrayOf("relay", "wss://granted.example"),
                ),
            ),
        )
    }

    @Test
    fun `other kinds are never ambiguous`() {
        assertFalse(
            hasAmbiguousRelayTags(
                1,
                arrayOf(arrayOf("relay", "a"), arrayOf("relay", "b")),
            ),
        )
    }

    @Test
    fun `malformed relay tags are ignored`() {
        assertFalse(
            hasAmbiguousRelayTags(
                22242,
                arrayOf(arrayOf("relay"), arrayOf("relay", "wss://one.example"), arrayOf("relay")),
            ),
        )
    }
}
