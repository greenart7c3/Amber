/**
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.greenart7c3.nostrsigner.service.crashreports

import java.util.concurrent.TimeoutException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class UnexpectedCrashSaver(
    val cache: CrashReportCache,
    val scope: CoroutineScope,
) : Thread.UncaughtExceptionHandler {
    private val defaultUEH: Thread.UncaughtExceptionHandler? = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(
        t: Thread,
        e: Throwable,
    ) {
        if (!isJunkReport(e)) {
            scope.launch {
                cache.writeReport(ReportAssembler().buildReport(e))
            }
        }
        defaultUEH!!.uncaughtException(t, e)
    }

    companion object {
        /**
         * Crashes we never prompt the user to report because nobody can act on them:
         * - OutOfMemoryError: junk reports.
         * - Platform finalizer-watchdog timeouts (e.g.
         *   `BinderInternal$GcWatcher.finalize() timed out after 10 seconds`): a known
         *   AOSP issue raised on the FinalizerDaemon thread with no app frames. The OS
         *   kills the process regardless; app code cannot catch, prevent, or fix it.
         */
        fun isJunkReport(e: Throwable): Boolean = e is OutOfMemoryError || isPlatformFinalizerTimeout(e)

        private fun isPlatformFinalizerTimeout(e: Throwable): Boolean = e is TimeoutException &&
            (
                e.message?.contains("finalize() timed out") == true ||
                    e.stackTrace.any { it.className.startsWith("java.lang.Daemons\$FinalizerDaemon") }
                )
    }
}
