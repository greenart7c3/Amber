package com.greenart7c3.nostrsigner.desktop.core

import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The UI language. Strings are the Compose resources of the `:commonsui`
 * module, shared with the Android app: composables read them with
 * `stringResource(Res.string.<name>)`, other code with `getString` or
 * `getStringBlocking`.
 *
 * Compose resources pick the translation from the JVM default locale, so the
 * selected language is applied with [Locale.setDefault]; the
 * [Locale.Category.FORMAT] locale stays the OS one, so dates and numbers keep
 * the user's regional format. The window content is keyed on
 * [currentLanguage], so a change recomposes every string.
 */
object Strings {
    /** Languages that ship a translation, by IETF-ish tag (the stored setting). */
    val supportedLanguages = listOf(
        "en" to "English",
        "en-GB" to "English (UK)",
        "de" to "Deutsch",
        "es" to "Español",
        "fr" to "Français",
        "in" to "Bahasa Indonesia",
        "it" to "Italiano",
        "ja" to "日本語",
        "ko" to "한국어",
        "pt-BR" to "Português (Brasil)",
        "ru" to "Русский",
        "th" to "ไทย",
        "tr" to "Türkçe",
        "vi" to "Tiếng Việt",
        "zh" to "中文",
    )

    private val systemLocale: Locale = Locale.getDefault()
    private val formatLocale: Locale = Locale.getDefault(Locale.Category.FORMAT)

    /** Current language tag; the window content recomposes when it changes. */
    val currentLanguage = MutableStateFlow(resolveInitialLanguage())

    private fun resolveInitialLanguage(): String {
        val stored = SettingsStore.settings.value.language
        if (!stored.isNullOrBlank() && supportedLanguages.any { it.first == stored }) return stored
        return matchSystemLanguage()
    }

    /** Best-effort match of [locale] (the OS one) to a shipped translation. */
    fun matchSystemLanguage(locale: Locale = systemLocale): String {
        // forLanguageTag normalizes legacy codes ("in" is "id" on Java 17+).
        fun Locale.matches(tag: String, withCountry: Boolean): Boolean {
            val other = Locale.forLanguageTag(tag)
            return language == other.language && (!withCountry || (other.country.isNotEmpty() && country == other.country))
        }
        supportedLanguages.firstOrNull { locale.matches(it.first, withCountry = true) }?.let { return it.first }
        supportedLanguages.firstOrNull { locale.matches(it.first, withCountry = false) }?.let { return it.first }
        return "en"
    }

    /** Applies the stored (or OS-matched) language; call before any string is read. */
    fun install() {
        applyLocale(currentLanguage.value)
    }

    fun setLanguage(tag: String) {
        applyLocale(tag)
        currentLanguage.value = tag
        SettingsStore.update { it.copy(language = tag) }
    }

    private fun applyLocale(tag: String) {
        Locale.setDefault(Locale.forLanguageTag(tag))
        Locale.setDefault(Locale.Category.FORMAT, formatLocale)
    }
}
