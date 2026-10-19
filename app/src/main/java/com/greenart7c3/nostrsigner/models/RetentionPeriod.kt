package com.greenart7c3.nostrsigner.models

import com.greenart7c3.nostrsigner.R

enum class RetentionPeriod(val days: Long, val resourceId: Int) {
    ONE_DAY(1, R.string.retention_1_day),
    THREE_DAYS(3, R.string.retention_3_days),
    SEVEN_DAYS(7, R.string.retention_7_days),
    THIRTY_DAYS(30, R.string.retention_30_days),
    NINETY_DAYS(90, R.string.retention_90_days),
    ;

    val millis: Long get() = days * 24 * 60 * 60 * 1000L
}
