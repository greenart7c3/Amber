package com.greenart7c3.nostrsigner.models

import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.theme_amoled
import com.greenart7c3.nostrsigner.commonsui.theme_dark
import com.greenart7c3.nostrsigner.commonsui.theme_light
import com.greenart7c3.nostrsigner.commonsui.theme_system
import org.jetbrains.compose.resources.StringResource

enum class ThemeMode(val resourceId: StringResource) {
    SYSTEM(Res.string.theme_system),
    LIGHT(Res.string.theme_light),
    DARK(Res.string.theme_dark),
    AMOLED(Res.string.theme_amoled),
}
