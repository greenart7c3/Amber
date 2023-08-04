package com.greenart7c3.nostrsigner.models

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object TimeUtils {
    fun now() = System.currentTimeMillis() / 1000
    fun format(date: Long): String {
        val sdf = SimpleDateFormat(
            "dd/MM/yyyy HH:mm:ss",
            Locale.getDefault()
        )
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(Date(date * 1000))
    }
}
