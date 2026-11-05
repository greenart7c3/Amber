package com.greenart7c3.nostrsigner.models

import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.update_channel_beta
import com.greenart7c3.nostrsigner.commonsui.update_channel_stable
import org.jetbrains.compose.resources.StringResource

enum class UpdateChannel(val resourceId: StringResource) {
    STABLE(Res.string.update_channel_stable),
    BETA(Res.string.update_channel_beta),
}
