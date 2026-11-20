package com.greenart7c3.nostrsigner.data

import com.greenart7c3.nostrsigner.models.AmberSettings
import com.greenart7c3.nostrsigner.models.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow

/** The app-wide settings currently in effect; [com.greenart7c3.nostrsigner.LocalPreferences] persists them. */
interface SettingsStore {
    var settings: AmberSettings

    /** The light/dark theme choice, stored apart from [settings]; the activities apply it as it changes. */
    val themeMode: MutableStateFlow<ThemeMode>
}
