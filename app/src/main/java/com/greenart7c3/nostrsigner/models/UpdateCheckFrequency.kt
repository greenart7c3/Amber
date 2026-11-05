package com.greenart7c3.nostrsigner.models

import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.update_frequency_daily
import com.greenart7c3.nostrsigner.commonsui.update_frequency_on_startup
import com.greenart7c3.nostrsigner.commonsui.update_frequency_weekly
import org.jetbrains.compose.resources.StringResource

enum class UpdateCheckFrequency(val resourceId: StringResource) {
    ON_STARTUP(Res.string.update_frequency_on_startup),
    DAILY(Res.string.update_frequency_daily),
    WEEKLY(Res.string.update_frequency_weekly),
}
