package com.greenart7c3.nostrsigner.models

import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.retention_1_day
import com.greenart7c3.nostrsigner.commonsui.retention_30_days
import com.greenart7c3.nostrsigner.commonsui.retention_3_days
import com.greenart7c3.nostrsigner.commonsui.retention_7_days
import com.greenart7c3.nostrsigner.commonsui.retention_90_days
import org.jetbrains.compose.resources.StringResource

enum class RetentionPeriod(val days: Long, val resourceId: StringResource) {
    ONE_DAY(1, Res.string.retention_1_day),
    THREE_DAYS(3, Res.string.retention_3_days),
    SEVEN_DAYS(7, Res.string.retention_7_days),
    THIRTY_DAYS(30, Res.string.retention_30_days),
    NINETY_DAYS(90, Res.string.retention_90_days),
    ;

    val millis: Long get() = days * 24 * 60 * 60 * 1000L
}
