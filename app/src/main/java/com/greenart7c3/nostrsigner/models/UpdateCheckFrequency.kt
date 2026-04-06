package com.greenart7c3.nostrsigner.models

import com.greenart7c3.nostrsigner.R

enum class UpdateCheckFrequency(val resourceId: Int) {
    ON_STARTUP(R.string.update_frequency_on_startup),
    DAILY(R.string.update_frequency_daily),
    WEEKLY(R.string.update_frequency_weekly),
}
