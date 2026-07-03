package com.greenart7c3.nostrsigner.desktop.core

import java.util.concurrent.ConcurrentHashMap
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Localized strings, ported from the Android app's `res/values` strings.xml
 * files. The very same XML files are bundled under `resources/i18n` as
 * `strings_<lang>.xml`
 * and parsed at runtime, so the desktop app carries the same translations
 * (and event-kind descriptions) with no divergence.
 *
 * English (`strings_en.xml`) is always loaded as the fallback; the selected
 * language overlays it. Any key missing from a translation falls back to
 * English, then to the key itself.
 */
object Strings {
    /** Languages that ship a translation, by IETF-ish tag matching the file name. */
    val supportedLanguages = listOf(
        "en" to "English",
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

    private val english: Map<String, String> by lazy { parse("en") }
    private val cache = ConcurrentHashMap<String, Map<String, String>>()

    /** Current language tag; drives recomposition of localized text. */
    val currentLanguage = MutableStateFlow(resolveInitialLanguage())

    private fun resolveInitialLanguage(): String {
        val stored = SettingsStore.settings.value.language
        if (!stored.isNullOrBlank() && supportedLanguages.any { it.first == stored }) return stored
        return matchSystemLanguage()
    }

    /** Best-effort match of the OS locale to a shipped translation. */
    fun matchSystemLanguage(): String {
        val locale = java.util.Locale.getDefault()
        val tag = "${locale.language}-${locale.country}"
        supportedLanguages.firstOrNull { it.first.equals(tag, ignoreCase = true) }?.let { return it.first }
        supportedLanguages.firstOrNull { it.first.equals(locale.language, ignoreCase = true) }?.let { return it.first }
        return "en"
    }

    fun setLanguage(tag: String) {
        currentLanguage.value = tag
        SettingsStore.update { it.copy(language = tag) }
    }

    private fun table(tag: String): Map<String, String> = if (tag == "en") english else cache.getOrPut(tag) { parse(tag) }

    /** Raw lookup for the given language, English fallback, then the key. */
    fun get(key: String, language: String = currentLanguage.value): String = table(language)[key] ?: english[key] ?: key

    /** Lookup with positional `%1$s`/`%s` argument substitution. */
    fun format(key: String, vararg args: Any?, language: String = currentLanguage.value): String {
        val template = get(key, language)
        return try {
            String.format(template, *args)
        } catch (_: Exception) {
            template
        }
    }

    private fun parse(tag: String): Map<String, String> {
        val stream = javaClass.getResourceAsStream("/i18n/strings_$tag.xml") ?: return emptyMap()
        return try {
            val doc = DocumentBuilderFactory.newInstance()
                .apply { isNamespaceAware = false }
                .newDocumentBuilder()
                .parse(stream)
            val nodes = doc.getElementsByTagName("string")
            buildMap {
                for (i in 0 until nodes.length) {
                    val node = nodes.item(i)
                    val name = node.attributes?.getNamedItem("name")?.nodeValue ?: continue
                    put(name, unescapeAndroid(node.textContent ?: ""))
                }
            }
        } catch (e: Exception) {
            AmberLogger.e("Strings", "Failed to parse strings_$tag.xml", e)
            emptyMap()
        } finally {
            stream.close()
        }
    }

    /**
     * Reverses Android's string escaping. The XML parser already resolves
     * entities like `&amp;`; this handles the backslash escapes Android uses
     * (`\'`, `\"`, `\n`, `\t`, `\\`) and trims the optional surrounding quotes
     * Android allows around whitespace-significant strings.
     */
    private fun unescapeAndroid(raw: String): String {
        var s = raw
        if (s.length >= 2 && s.first() == '"' && s.last() == '"') {
            s = s.substring(1, s.length - 1)
        }
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val next = s[i + 1]) {
                    'n' -> out.append('\n')
                    't' -> out.append('\t')
                    '\'' -> out.append('\'')
                    '"' -> out.append('"')
                    '\\' -> out.append('\\')
                    else -> out.append(next)
                }
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }
}
