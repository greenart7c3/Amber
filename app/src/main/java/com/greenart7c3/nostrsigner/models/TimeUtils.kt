package com.greenart7c3.nostrsigner.models

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

object TimeUtils {
    fun now() = System.currentTimeMillis() / 1000

    fun convertLongToDateTime(longValue: Long): String {
        val dateTime =
            LocalDateTime.ofInstant(
                Instant.ofEpochMilli(longValue),
                ZoneId.systemDefault(),
            )

        val formatter =
            DateTimeFormatter.ofLocalizedDateTime(
                FormatStyle.SHORT,
                FormatStyle.MEDIUM,
            )

        return dateTime.format(formatter)
    }
}
