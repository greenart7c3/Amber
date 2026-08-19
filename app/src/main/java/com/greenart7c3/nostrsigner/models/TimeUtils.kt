package com.greenart7c3.nostrsigner.models

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object TimeUtils {
    // DateTimeFormatter is immutable and thread-safe; building it parses the
    // pattern on every call, so cache the two instances used during composition.
    private val formatter = DateTimeFormatter.ofPattern("HH:mm - dd MMM")
    private val formatterWithSeconds = DateTimeFormatter.ofPattern("HH:mm:ss - dd MMM")

    /**
     * The first ZoneId.systemDefault() call in the process lazily loads the
     * tzdata file from disk (ZoneInfoDb's class initializer). Calling this from
     * a background thread at app start keeps that disk read off the main
     * thread, where it would otherwise trip StrictMode and jank the frame that
     * first formats a timestamp.
     */
    fun warmUp() {
        LocalDateTime.ofInstant(Instant.ofEpochMilli(System.currentTimeMillis()), ZoneId.systemDefault()).format(formatter)
    }

    fun formatLongToCustomDateTime(longValue: Long): String {
        val dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(longValue), ZoneId.systemDefault())
        return dateTime.format(formatter)
    }

    fun formatLongToCustomDateTimeWithSeconds(longValue: Long): String {
        val dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(longValue), ZoneId.systemDefault())
        return dateTime.format(formatterWithSeconds)
    }
}
