package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.desktop.core.AppDirs
import com.greenart7c3.nostrsigner.desktop.core.CrashReporter
import com.greenart7c3.nostrsigner.desktop.core.ReportAssembler
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

class CrashReporterTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun isolateDataDir() {
            val tmp = File.createTempFile("amber-crash-test", "").apply {
                delete()
                mkdirs()
                deleteOnExit()
            }
            System.setProperty("user.home", tmp.absolutePath)
        }
    }

    @Test
    fun reportRedactsKeysPayloadsAndHomeDirectory() {
        val npub = "npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5"
        val hex = "7579076d9aff0a4cfdefa7e2045f2486c7e5d8bc63bfc6b45397233e1bbfcb19"
        val blob = "QUJDREVGR0hJSktMTU5PUFFSU1RVVldYWVphYmNkZWZnaGlqa2xtbm9w"
        val path = System.getProperty("user.home") + "/secret/accounts.json"
        val report = ReportAssembler.buildReport(IllegalStateException("bad $npub $hex $blob at $path"))

        assertFalse(report.contains(npub))
        assertFalse(report.contains(hex))
        assertFalse(report.contains(blob))
        assertFalse(report.contains(System.getProperty("user.home")))
        assertTrue(report.contains("<bech32>"))
        assertTrue(report.contains("<hexkey>"))
        assertTrue(report.contains("<base64>"))
        assertTrue(report.contains("~/secret/accounts.json"))
        assertTrue(report.startsWith("IllegalStateException: "))
        assertTrue(report.contains("-DESKTOP"))
    }

    @Test
    fun reportIncludesTheWholeCauseChainAndSurvivesCycles() {
        val root = IllegalArgumentException("root cause")
        val middle = RuntimeException("middle", root)
        val top = IllegalStateException("top", middle)
        assertTrue(ReportAssembler.buildReport(top).let { it.contains("middle") && it.contains("root cause") })

        val a = RuntimeException("a")
        val b = RuntimeException("b", a)
        a.initCause(b)
        val cyclic = ReportAssembler.buildReport(a)
        assertEquals(1, Regex("RuntimeException: b").findAll(cyclic).count())
    }

    @Test
    fun savedReportPersistsUntilDismissed() {
        val file = File(AppDirs.dataDir, "crash.trace")
        CrashReporter.save(IllegalStateException("boom"))
        assertTrue(file.exists())
        assertTrue(CrashReporter.pending.value!!.contains("boom"))

        // A restart picks the saved report up again: at launch, or (stored
        // encrypted when another test class has set a passphrase) at unlock.
        CrashReporter.pending.value = null
        CrashReporter.install()
        CrashReporter.loadAfterUnlock()
        assertTrue(CrashReporter.pending.value!!.contains("boom"))

        CrashReporter.dismiss()
        assertFalse(file.exists())
        assertNull(CrashReporter.pending.value)

        // Out-of-memory reports are never saved.
        CrashReporter.save(OutOfMemoryError("junk"))
        assertFalse(file.exists())
        assertNull(CrashReporter.pending.value)
    }

    @Test
    fun aMessagelessReportKeepsTypesAndFramesOnly() {
        val e = IllegalStateException("short secret abc123", IllegalArgumentException("decrypted: hello"))
        val report = ReportAssembler.buildReport(e, includeMessages = false)

        assertTrue(report.contains("java.lang.IllegalStateException"))
        assertTrue(report.contains("java.lang.IllegalArgumentException"))
        assertTrue(report.contains("CrashReporterTest"))
        assertFalse(report.contains("abc123"))
        assertFalse(report.contains("hello"))
    }
}
