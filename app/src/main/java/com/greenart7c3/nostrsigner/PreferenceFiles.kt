package com.greenart7c3.nostrsigner

import android.content.Context
import android.content.SharedPreferences

internal fun appPreferences(context: Context): SharedPreferences = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)

internal fun accountPreferences(context: Context, npub: String): SharedPreferences = context.getSharedPreferences("prefs_$npub", Context.MODE_PRIVATE)
