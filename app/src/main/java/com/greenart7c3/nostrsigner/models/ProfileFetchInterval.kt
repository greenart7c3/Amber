package com.greenart7c3.nostrsigner.models

import com.greenart7c3.nostrsigner.R

enum class ProfileFetchInterval(val minutes: Long, val resourceId: Int) {
    ALWAYS(0, R.string.profile_fetch_interval_always),
    FIFTEEN_MINUTES(15, R.string.profile_fetch_interval_15_minutes),
    THIRTY_MINUTES(30, R.string.profile_fetch_interval_30_minutes),
    ONE_HOUR(60, R.string.profile_fetch_interval_1_hour),
    THREE_HOURS(180, R.string.profile_fetch_interval_3_hours),
    SIX_HOURS(360, R.string.profile_fetch_interval_6_hours),
    ONE_DAY(1440, R.string.profile_fetch_interval_1_day),
    NEVER(-1, R.string.profile_fetch_interval_never),
}
