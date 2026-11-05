package com.greenart7c3.nostrsigner.models

import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.profile_fetch_interval_15_minutes
import com.greenart7c3.nostrsigner.commonsui.profile_fetch_interval_1_day
import com.greenart7c3.nostrsigner.commonsui.profile_fetch_interval_1_hour
import com.greenart7c3.nostrsigner.commonsui.profile_fetch_interval_30_minutes
import com.greenart7c3.nostrsigner.commonsui.profile_fetch_interval_3_hours
import com.greenart7c3.nostrsigner.commonsui.profile_fetch_interval_6_hours
import com.greenart7c3.nostrsigner.commonsui.profile_fetch_interval_always
import com.greenart7c3.nostrsigner.commonsui.profile_fetch_interval_never
import org.jetbrains.compose.resources.StringResource

enum class ProfileFetchInterval(val minutes: Long, val resourceId: StringResource) {
    ALWAYS(0, Res.string.profile_fetch_interval_always),
    FIFTEEN_MINUTES(15, Res.string.profile_fetch_interval_15_minutes),
    THIRTY_MINUTES(30, Res.string.profile_fetch_interval_30_minutes),
    ONE_HOUR(60, Res.string.profile_fetch_interval_1_hour),
    THREE_HOURS(180, Res.string.profile_fetch_interval_3_hours),
    SIX_HOURS(360, Res.string.profile_fetch_interval_6_hours),
    ONE_DAY(1440, Res.string.profile_fetch_interval_1_day),
    NEVER(-1, Res.string.profile_fetch_interval_never),
}
