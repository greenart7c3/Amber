package com.greenart7c3.nostrsigner

import androidx.appcompat.app.AppCompatDelegate
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.greenart7c3.nostrsigner.commonsui.Permission
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.event_kind_1
import com.greenart7c3.nostrsigner.commonsui.export_failed
import com.greenart7c3.nostrsigner.commonsui.getStringBlocking
import com.greenart7c3.nostrsigner.commonsui.localizedBlocking
import com.greenart7c3.nostrsigner.commonsui.tor_connecting
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The strings are Compose resources shared with the desktop (:commonsui),
 * packed as Android assets: outside composition they follow the default
 * locale, which AppLanguage sets with the in-app language.
 */
@RunWith(AndroidJUnit4::class)
class SharedStringsDeviceTest {
    private val original = Locale.getDefault()
    private val originalAppLocales = AppCompatDelegate.getApplicationLocales()

    @After
    fun restoreLocale() {
        Locale.setDefault(original)
        AppCompatDelegate.setApplicationLocales(originalAppLocales)
    }

    private fun inLanguage(tag: String, block: () -> String): String {
        Locale.setDefault(Locale.forLanguageTag(tag))
        return block()
    }

    @Test
    fun stringsFollowTheDefaultLocale() {
        assertEquals("Short text note", inLanguage("en") { getStringBlocking(Res.string.event_kind_1) })
        assertEquals("Kurztext-Note", inLanguage("de") { getStringBlocking(Res.string.event_kind_1) })
        // Folders without a region (values-it) match a regional locale too.
        assertEquals("Nota di testo breve", inLanguage("it-IT") { getStringBlocking(Res.string.event_kind_1) })
        // Indonesian: Java and Android 15+ call it "id" (values-id).
        assertEquals("Catatan teks singkat", inLanguage("id") { getStringBlocking(Res.string.event_kind_1) })
    }

    @Test
    fun theInAppLanguageAppliesOutsideComposition() {
        AppLanguage.apply("de")
        assertEquals("Kurztext-Note", Permission("sign_event", 1).localizedBlocking())
    }

    @Test
    fun formatArgumentsAndPercentSigns() {
        assertEquals("Export failed: boom", inLanguage("en") { getStringBlocking(Res.string.export_failed, "boom") })
        assertEquals("Connecting to Tor: 45%", inLanguage("en") { getStringBlocking(Res.string.tor_connecting, 45) })
    }
}
