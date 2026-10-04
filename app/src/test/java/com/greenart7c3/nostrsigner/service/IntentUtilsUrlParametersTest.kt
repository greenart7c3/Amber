package com.greenart7c3.nostrsigner.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentUtilsUrlParametersTest {

    private val payload = "nostrsigner:%7B%22kind%22%3A22242%7D"

    // --- NIP-55 web form: parameters in the URL query ---

    @Test
    fun `web form sign_event uri is detected`() {
        val uri = "$payload?type=sign_event&returnType=event&callbackUrl=https%3A%2F%2Fexample.com%2Fcb"
        assertTrue(IntentUtils.urlHasSignerParameters(uri))
    }

    @Test
    fun `web form uri with type not first is detected`() {
        val uri = "$payload?returnType=signature&type=get_public_key"
        assertTrue(IntentUtils.urlHasSignerParameters(uri))
    }

    @Test
    fun `url encoded payload keeps the query separate`() {
        // the payload is percent-encoded, so a literal '?' only starts the query
        val uri = "nostrsigner:%7B%22content%22%3A%22a%3Fb%22%7D?type=sign_event"
        assertTrue(IntentUtils.urlHasSignerParameters(uri))
    }

    // --- native form: parameters in intent extras, data has no query ---

    @Test
    fun `native form uri without query is not detected`() {
        assertFalse(IntentUtils.urlHasSignerParameters(payload))
    }

    @Test
    fun `uri with empty query is not detected`() {
        assertFalse(IntentUtils.urlHasSignerParameters("$payload?"))
    }

    @Test
    fun `uri with unrelated parameter is not detected`() {
        assertFalse(IntentUtils.urlHasSignerParameters("$payload?appName=foo"))
    }

    @Test
    fun `parameter whose name only starts with type is not detected`() {
        assertFalse(IntentUtils.urlHasSignerParameters("$payload?typeface=foo"))
    }

    // --- degenerate inputs ---

    @Test
    fun `null data is not detected`() {
        assertFalse(IntentUtils.urlHasSignerParameters(null))
    }

    @Test
    fun `empty data is not detected`() {
        assertFalse(IntentUtils.urlHasSignerParameters(""))
    }

    @Test
    fun `bare scheme is not detected`() {
        assertFalse(IntentUtils.urlHasSignerParameters("nostrsigner:"))
    }
}
