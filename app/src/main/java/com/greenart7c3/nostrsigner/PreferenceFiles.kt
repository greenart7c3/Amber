package com.greenart7c3.nostrsigner

import android.content.Context
import android.content.SharedPreferences

/** The app-wide preference file (`prefs`): settings and the saved-account list. */
internal fun appPreferences(context: Context): SharedPreferences = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)

/** The npub-specific preference file (`prefs_<npub>`). */
internal fun accountPreferences(context: Context, npub: String): SharedPreferences = context.getSharedPreferences("prefs_$npub", Context.MODE_PRIVATE)
