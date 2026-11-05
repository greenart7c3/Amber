package com.greenart7c3.nostrsigner.desktop.core

import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Formats timestamps the way the OS does: the format locale, and on Windows
 * and macOS the user's own date/time settings (12/24-hour clock, custom
 * patterns) through the HOST locale provider that `build.gradle.kts` selects.
 */
object DateTimes {
    /** [millis] since the epoch as a medium date and time. */
    fun format(millis: Long): String = // java.text.DateFormat is not thread-safe, so build one per call.
        DateFormat
            .getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, Locale.getDefault(Locale.Category.FORMAT))
            .format(Date(millis))
}
