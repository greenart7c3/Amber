package com.greenart7c3.nostrsigner.cli

import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.LogLevel
import com.vitorpamplona.quartz.utils.LogSink
import java.io.PrintStream

/**
 * Quartz logs through its own `Log`, which by default writes every DEBUG line
 * to the console. That is the right default inside the apps and noise for a CLI
 * whose stderr a script reads: keep errors, and send everything to stderr only
 * with `--verbose`.
 */
object QuartzLogs {
    fun install(
        err: PrintStream,
        verbose: Boolean,
    ) {
        Log.minLevel = if (verbose) LogLevel.DEBUG else LogLevel.ERROR
        Log.sink = object : LogSink {
            override fun log(
                level: LogLevel,
                tag: String,
                message: String,
                throwable: Throwable?,
            ) {
                val text = message.ifBlank { throwable?.message ?: "" }
                err.println("$level $tag: $text")
            }
        }
    }
}
