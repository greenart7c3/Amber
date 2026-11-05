package com.greenart7c3.nostrsigner

import android.content.res.Resources
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * The in-app language. AppCompat applies it to the activities (where the
 * shared Compose resources' `stringResource` reads it); strings read outside
 * composition (`getString`, `getStringBlocking`: notifications, toasts,
 * services) follow the JVM default locale instead, which AppCompat only
 * updates on Android 13+, so it is set here too.
 */
object AppLanguage {
    /** [tags] as stored in the settings; null or blank follows the system. */
    fun apply(tags: String?) {
        val locales = LocaleListCompat.forLanguageTags(tags.orEmpty())
        AppCompatDelegate.setApplicationLocales(locales)
        Locale.setDefault(locales[0] ?: Resources.getSystem().configuration.locales[0])
    }
}
