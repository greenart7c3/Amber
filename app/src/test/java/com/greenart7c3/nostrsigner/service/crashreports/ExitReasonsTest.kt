package com.greenart7c3.nostrsigner.service.crashreports

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exit records are the only trace of ANRs, native crashes and low-memory kills. The filter must
 * include those and keep ordinary user actions out of the report.
 */
class ExitReasonsTest {
    private val foregroundService = ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE
    private val cached = ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED

    @Test
    fun abnormalReasonsAreLabelled() {
        assertEquals("ANR", ExitReasons.reasonLabel(ApplicationExitInfo.REASON_ANR))
        assertEquals("LOW_MEMORY", ExitReasons.reasonLabel(ApplicationExitInfo.REASON_LOW_MEMORY))
        assertEquals("CRASH_NATIVE", ExitReasons.reasonLabel(ApplicationExitInfo.REASON_CRASH_NATIVE))
        assertEquals("CRASH", ExitReasons.reasonLabel(ApplicationExitInfo.REASON_CRASH))
        assertEquals("OTHER", ExitReasons.reasonLabel(ApplicationExitInfo.REASON_OTHER))
    }

    @Test
    fun abnormalReasonInAForegroundProcessIsReported() {
        assertTrue(ExitReasons.isAbnormal(ApplicationExitInfo.REASON_ANR, foregroundService))
        assertTrue(ExitReasons.isAbnormal(ApplicationExitInfo.REASON_CRASH, foregroundService))
        assertTrue(ExitReasons.isAbnormal(ApplicationExitInfo.REASON_LOW_MEMORY, foregroundService))
    }

    @Test
    fun ordinaryExitIsNotReported() {
        assertFalse(ExitReasons.isAbnormal(ApplicationExitInfo.REASON_EXIT_SELF, foregroundService))
        assertFalse(ExitReasons.isAbnormal(ApplicationExitInfo.REASON_USER_REQUESTED, foregroundService))
        assertFalse(ExitReasons.isAbnormal(ApplicationExitInfo.REASON_OTHER, foregroundService))
    }

    @Test
    fun backgroundKillIsNotReported() {
        assertFalse(ExitReasons.isAbnormal(ApplicationExitInfo.REASON_ANR, cached))
        assertFalse(ExitReasons.isAbnormal(ApplicationExitInfo.REASON_LOW_MEMORY, cached))
    }
}
