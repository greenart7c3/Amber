package com.greenart7c3.nostrsigner.models

import android.content.Context
import android.icu.text.DateFormat as IcuDateFormat
import android.icu.util.TimeZone as IcuTimeZone
import android.text.format.DateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

object TimeUtils {
    private data class FormatKey(val locale: Locale, val is24Hour: Boolean, val withSeconds: Boolean)

    // Building an ICU formatter resolves the locale's pattern, so cache one per
    // locale / clock style for the timestamps formatted during composition. ICU
    // formatters are not thread-safe: format() locks the instance.
    private val formatters = ConcurrentHashMap<FormatKey, IcuDateFormat>()

    /**
     * The first time zone lookup in the process lazily loads the tzdata file
     * from disk. Calling this from a background thread at app start keeps that
     * disk read off the main thread, where it would otherwise trip StrictMode
     * and jank the frame that first formats a timestamp.
     */
    fun warmUp(context: Context) {
        formatDateTime(context, System.currentTimeMillis())
    }

    /**
     * Formats [millis] as day, short month and time in the user's locale
     * (the app language, else the system's) and the system 12/24-hour setting.
     */
    fun formatDateTime(context: Context, millis: Long, withSeconds: Boolean = false): String {
        val key = FormatKey(
            locale = context.resources.configuration.locales[0] ?: Locale.getDefault(),
            is24Hour = DateFormat.is24HourFormat(context),
            withSeconds = withSeconds,
        )
        val formatter = formatters.getOrPut(key) {
            val skeleton = "dMMM" + (if (key.is24Hour) "Hm" else "hm") + (if (key.withSeconds) "s" else "")
            IcuDateFormat.getInstanceForSkeleton(skeleton, key.locale)
        }
        return synchronized(formatter) {
            // The device time zone can change while the app runs.
            formatter.timeZone = IcuTimeZone.getTimeZone(TimeZone.getDefault().id)
            formatter.format(Date(millis))
        }
    }
}
