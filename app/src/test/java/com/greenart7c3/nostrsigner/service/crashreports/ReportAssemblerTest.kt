package com.greenart7c3.nostrsigner.service.crashreports

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportAssemblerTest {
    private val npubHex = "3bf0c63fcb93463407af97a5e5ee64b88f1f2c73b1a21c4f33ac8f0c7c6a6e0b"

    @Test
    fun `redacts 64-char hex keys from exception messages and stack traces`() {
        val e = IllegalStateException("bad key $npubHex in input")

        val report = ReportAssembler().buildReport(e)

        assertFalse("hex key must be redacted", report.contains(npubHex))
        assertTrue("redaction marker present", report.contains("<hexkey>"))
    }

    @Test
    fun `redacts npub bech32 from exception messages`() {
        // npub1... derived from the hex key above
        val npub = "npub10qp7qrzdrqmax40ayhxm2vp6d8yurfr3lwxyh4df0sf5r8cjnrms2cv7aj"
        val e = IllegalArgumentException("could not parse $npub")

        val report = ReportAssembler().buildReport(e)

        assertFalse("npub must be redacted", report.contains(npub))
        assertTrue("redaction marker present", report.contains("<bech32>"))
    }

    @Test
    fun `redacts long base64 blobs from exception messages`() {
        val base64 = "kL9/abcdefghijklmnopqrstuvwxyz0123456789+/ABCDEFGHIJKLMNOPQRSTUVWXYZ+/"
        val e = RuntimeException("decryption failed for payload=$base64")

        val report = ReportAssembler().buildReport(e)

        assertFalse("base64 must be redacted", report.contains(base64))
        assertTrue("redaction marker present", report.contains("<base64>"))
    }

    @Test
    fun `preserves non-sensitive message content`() {
        val e = IllegalStateException("null pointer in feature X")

        val report = ReportAssembler().buildReport(e)

        assertTrue("non-sensitive content preserved", report.contains("null pointer in feature X"))
    }

    @Test
    fun `redacts cause chain too`() {
        val cause = IllegalArgumentException("bad hex $npubHex")
        val e = RuntimeException("wrapper", cause)

        val report = ReportAssembler().buildReport(e)

        assertFalse("cause hex must be redacted", report.contains(npubHex))
    }
}
