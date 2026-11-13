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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.ui.NavHostControllerWrapper
import com.greenart7c3.nostrsigner.ui.navigation.Route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The user sent or dismissed the report: drop it and the exit records it included. */
fun Amber.clearPendingCrashReport() {
    pendingCrashReport = null
    ExitReasons.clearPending(this)
}

@Composable
fun DisplayCrashMessages(
    account: Account,
    navController: NavHostControllerWrapper,
) {
    LaunchedEffect(account) {
        withContext(Dispatchers.IO) {
            val exceptions = Amber.instance.crashReportCache.loadAndDelete()
            // ANRs, low-memory kills and native crashes only appear in the exit records.
            // The startup detection may still be running: finish it first, or a dismiss
            // would clear records written after this read, unseen.
            ExitReasons.detect(Amber.instance)
            val exits = ExitReasons.pending(Amber.instance)
            val stack = listOfNotNull(exceptions, exits)
                .filter { it.isNotBlank() }
                .joinToString("\n\n")
                .ifBlank { null }
            if (stack != null) {
                Amber.instance.pendingCrashReport = stack
                withContext(Dispatchers.Main) {
                    navController.navController.navigate(Route.CrashReport.route)
                }
            }
        }
    }
}
