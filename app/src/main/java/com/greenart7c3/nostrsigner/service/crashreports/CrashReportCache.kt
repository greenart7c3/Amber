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

import android.content.Context
import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.common.CrashReports
import java.io.FileInputStream
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileNotFoundException

private const val STACK_TRACE_FILENAME = "stack.trace"

class CrashReportCache(
    val appContext: Context,
) {
    private fun outputStream() = appContext.openFileOutput(STACK_TRACE_FILENAME, Context.MODE_PRIVATE)

    private fun deleteReport() = appContext.deleteFile(STACK_TRACE_FILENAME)

    private fun inputStreamOrNull(): FileInputStream? = try {
        appContext.openFileInput(STACK_TRACE_FILENAME)
    } catch (_: FileNotFoundException) {
        null
    }

    private fun writeReport(report: String) {
        outputStream().use { it.write(report.toByteArray()) }
    }

    /**
     * Saves the report for [e] synchronously, after any report not yet shown:
     * an error the process survived (an OOM in a background job) must not
     * replace the report of an earlier crash. Never throws — it runs while the
     * process may be out of memory or about to die.
     */
    fun saveReport(e: Throwable) {
        if (CrashReports.isJunkReport(e)) return
        try {
            val report = ReportAssembler().buildReport(e)
            val existing = inputStreamOrNull()?.use { inStream -> InputStreamReader(inStream).use { it.readText() } }
            writeReport(if (existing.isNullOrBlank()) report else "$existing\n\n$report")
        } catch (reportError: Throwable) {
            AmberLog.e("CrashReportCache", "Could not save the crash report", reportError)
        }
    }

    suspend fun loadAndDelete(): String? = withContext(Dispatchers.IO) {
        val stack =
            inputStreamOrNull()?.use { inStream ->
                InputStreamReader(inStream).use { reader ->
                    reader.readText()
                }
            }
        deleteReport()
        stack
    }
}
