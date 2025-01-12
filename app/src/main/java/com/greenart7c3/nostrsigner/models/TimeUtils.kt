package com.greenart7c3.nostrsigner.models

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object TimeUtils {
    fun formatLongToCustomDateTime(longValue: Long): String {
        val dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(longValue), ZoneId.systemDefault())
        val formatter = DateTimeFormatter.ofPattern("HH:mm - dd MMM")
        return dateTime.format(formatter)
    }

    fun formatLongToCustomDateTimeWithSeconds(longValue: Long): String {
        val dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(longValue), ZoneId.systemDefault())
        val formatter = DateTimeFormatter.ofPattern("HH:mm:ss - dd MMM")
        return dateTime.format(formatter)
    }
}
