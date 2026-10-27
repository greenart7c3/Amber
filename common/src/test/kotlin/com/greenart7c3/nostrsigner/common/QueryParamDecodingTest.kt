package com.greenart7c3.nostrsigner.common

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Query values of `nostrconnect://` and NIP-55 URIs: clients that encode with
 * URLSearchParams / URLEncoder.encode send a space as '+', so "My+App" must
 * show its space, while a literal '+' arrives as '%2B' and survives.
 */
class QueryParamDecodingTest {

    @Test
    fun `form encoded space in app name becomes a space`() {
        assertEquals("My App", decodeQueryParamValue("My+App"))
    }

    @Test
    fun `percent encoded space still becomes a space`() {
        assertEquals("My App", decodeQueryParamValue("My%20App"))
    }

    @Test
    fun `literal plus arrives percent encoded and survives`() {
        assertEquals("C++", decodeQueryParamValue("C%2B%2B"))
    }

    @Test
    fun `encoded ampersand and equals stay inside the value`() {
        assertEquals("a=1&b=2", decodeQueryParamValue("a%3D1%26b%3D2"))
    }

    @Test
    fun `plain value is unchanged`() {
        assertEquals("Amethyst", decodeQueryParamValue("Amethyst"))
    }

    @Test
    fun `stray percent that is not an escape keeps the raw value`() {
        assertEquals("100% sure", decodeQueryParamValue("100% sure"))
    }
}
