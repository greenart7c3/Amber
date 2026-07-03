package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.desktop.core.RememberType
import com.greenart7c3.nostrsigner.desktop.core.SignerDescriptions
import com.greenart7c3.nostrsigner.desktop.core.Strings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The localized string loader and the ported event-kind descriptions. */
class StringsTest {
    @Test
    fun everyShippedLanguageLoadsAndHasKindTranslations() {
        Strings.supportedLanguages.forEach { (tag, _) ->
            // event_kind_1 (short text note) is translated in every locale.
            val value = Strings.get("event_kind_1", tag)
            assertTrue("event_kind_1 missing for $tag", value.isNotBlank() && value != "event_kind_1")
        }
    }

    @Test
    fun translationsDifferAcrossLanguages() {
        assertEquals("Short text note", Strings.get("event_kind_1", "en"))
        assertEquals("Kurztext-Note", Strings.get("event_kind_1", "de"))
        assertEquals("Nota de texto corta", Strings.get("event_kind_1", "es"))
    }

    @Test
    fun missingKeyFallsBackToEnglishThenKey() {
        // "switch_relays" only exists in the English supplement -> other locales fall back.
        assertEquals("wants to switch relays", Strings.get("switch_relays", "en"))
        assertEquals(Strings.get("switch_relays", "en"), Strings.get("switch_relays", "de"))
        // A key that exists nowhere returns itself.
        assertEquals("totally_unknown_key", Strings.get("totally_unknown_key", "fr"))
    }

    @Test
    fun formatSubstitutesPositionalArgs() {
        // event_kind template is "Event kind %1$s".
        val text = Strings.format("event_kind", 12345, language = "en")
        assertTrue(text.contains("12345"))
    }

    @Test
    fun signEventDescriptionsMatchAndroidMapping() {
        assertEquals("Metadata", SignerDescriptions.signEventDescription(0, "en"))
        assertEquals("Zap", SignerDescriptions.signEventDescription(9735, "en"))
        assertEquals("Gift wrap", SignerDescriptions.signEventDescription(1059, "en"))
        // Range mapping (5000..5999).
        assertEquals(Strings.get("event_kind_5000_5999", "en"), SignerDescriptions.signEventDescription(5123, "en"))
        // 30443 maps to the 10443 string (an Android quirk we preserved).
        assertEquals(Strings.get("event_kind_10443", "en"), SignerDescriptions.signEventDescription(30443, "en"))
    }

    @Test
    fun unknownKindFallsBackToNipOrKindNumber() {
        val text = SignerDescriptions.signEventDescription(987654, "en")
        assertTrue("should mention the kind number", text.contains("987654"))
    }

    @Test
    fun desktopUiKeysResolveInEnglish() {
        assertEquals("Approve", Strings.get("d_approve", "en"))
        assertEquals("Incoming requests", Strings.get("d_route_incoming_title", "en"))
    }

    @Test
    fun desktopUiKeysAreTranslatedInEveryLanguage() {
        // The desktop-only d_ strings are now translated in all shipped
        // locales (no English fallback). Spot-check a representative key.
        Strings.supportedLanguages.forEach { (tag, _) ->
            val value = Strings.get("d_approve", tag)
            assertTrue("d_approve missing for $tag", value.isNotBlank() && value != "d_approve")
            if (tag != "en") {
                assertTrue(
                    "d_approve not translated for $tag (still English)",
                    Strings.get("d_approve", tag) != "Approve" || tag == "en",
                )
            }
        }
        // Format specifiers survive translation.
        assertTrue(Strings.format("d_public_key", "npub1abc", language = "de").contains("npub1abc"))
        assertTrue(Strings.format("d_public_key", "npub1abc", language = "ja").contains("npub1abc"))
    }

    @Test
    fun rememberTypeLabelsAreLocalized() {
        assertEquals("Just once", RememberType.NEVER.label("en"))
        assertEquals("Always", RememberType.ALWAYS.label("en"))
        assertEquals("1 day", RememberType.ONE_DAY.label("en"))
        assertEquals("1 week", RememberType.ONE_WEEK.label("en"))
    }

    @Test
    fun permissionTypesAreLocalized() {
        assertEquals("Read your public key", SignerDescriptions.permission("get_public_key", null, "en"))
        assertEquals(Strings.get("read_your_public_key", "de"), SignerDescriptions.permission("get_public_key", null, "de"))
    }
}
