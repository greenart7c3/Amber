package com.greenart7c3.nostrsigner.data

import com.greenart7c3.nostrsigner.models.AmberSettings

/** The app-wide settings currently in effect; [com.greenart7c3.nostrsigner.LocalPreferences] persists them. */
interface SettingsStore {
    var settings: AmberSettings
}
