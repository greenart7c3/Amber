package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.commonsui.Permission
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.account_backup_tips_md
import com.greenart7c3.nostrsigner.commonsui.crash_report_send_prompt
import com.greenart7c3.nostrsigner.commonsui.d_approve
import com.greenart7c3.nostrsigner.commonsui.d_keep_in_tray_sub
import com.greenart7c3.nostrsigner.commonsui.d_public_key
import com.greenart7c3.nostrsigner.commonsui.d_route_incoming_title
import com.greenart7c3.nostrsigner.commonsui.d_socket_name_unverified
import com.greenart7c3.nostrsigner.commonsui.event_kind
import com.greenart7c3.nostrsigner.commonsui.event_kind_1
import com.greenart7c3.nostrsigner.commonsui.event_kind_10008
import com.greenart7c3.nostrsigner.commonsui.event_kind_10012
import com.greenart7c3.nostrsigner.commonsui.event_kind_30443
import com.greenart7c3.nostrsigner.commonsui.event_kind_5000_5999
import com.greenart7c3.nostrsigner.commonsui.export_failed
import com.greenart7c3.nostrsigner.commonsui.getStringBlocking
import com.greenart7c3.nostrsigner.commonsui.invalid_request
import com.greenart7c3.nostrsigner.commonsui.localizedBlocking
import com.greenart7c3.nostrsigner.commonsui.read_your_public_key
import com.greenart7c3.nostrsigner.commonsui.switch_relays
import com.greenart7c3.nostrsigner.commonsui.tor_connecting
import com.greenart7c3.nostrsigner.commonsui.tor_starting
import com.greenart7c3.nostrsigner.desktop.core.RememberType
import com.greenart7c3.nostrsigner.desktop.core.Strings
import java.util.Locale
import org.jetbrains.compose.resources.StringResource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shared :commonsui strings as Compose resources, the language selection
 * and the ported event-kind descriptions.
 */
class StringsTest {
    private val original = Locale.getDefault()

    @After
    fun restoreLocale() {
        Locale.setDefault(original)
    }

    /** Reads [block] with [tag] as the UI language, as Strings.setLanguage applies it. */
    private fun <T> inLanguage(tag: String, block: () -> T): T {
        Locale.setDefault(Locale.forLanguageTag(tag))
        return block()
    }

    private fun string(tag: String, resource: StringResource, vararg args: Any) = inLanguage(tag) { getStringBlocking(resource, *args) }

    /** en-GB only overrides spellings, so most of its strings legitimately match English. */
    private fun isEnglish(tag: String) = tag == "en" || tag.startsWith("en-")

    private fun assertTranslatedEverywhere(resource: StringResource) {
        val english = string("en", resource)
        Strings.supportedLanguages.forEach { (tag, _) ->
            val value = string(tag, resource)
            assertTrue("$resource missing for $tag", value.isNotBlank())
            if (!isEnglish(tag)) assertNotEquals("$resource not translated for $tag", english, value)
        }
    }

    @Test
    fun everyShippedLanguageHasItsTranslation() {
        // Includes the languages selected without their region (it, ko, vi)
        // and Indonesian, stored as "in" but "id" (values-id) on Java 17+.
        assertTranslatedEverywhere(Res.string.event_kind_1)
    }

    @Test
    fun translationsDifferAcrossLanguages() {
        assertEquals("Short text note", string("en", Res.string.event_kind_1))
        assertEquals("Kurztext-Note", string("de", Res.string.event_kind_1))
        assertEquals("Nota de texto corta", string("es", Res.string.event_kind_1))
    }

    @Test
    fun britishEnglishOverridesSpellingAndFallsBackToEnglish() {
        assertEquals("Favourite relays list", string("en-GB", Res.string.event_kind_10012))
        assertEquals("Favorite relays list", string("en", Res.string.event_kind_10012))
        // Strings the en-GB file doesn't override come from values/strings.xml.
        assertEquals("Short text note", string("en-GB", Res.string.event_kind_1))
        assertEquals(
            "Closing the window minimises Amber to the system tray so it keeps answering requests.",
            string("en-GB", Res.string.d_keep_in_tray_sub),
        )
    }

    @Test
    fun androidEscapesAreDecoded() {
        // The XML holds "Starting Tor…"; it must render as an ellipsis.
        assertEquals("Starting Tor…", string("en", Res.string.tor_starting))
        assertEquals("Tor wird gestartet…", string("de", Res.string.tor_starting))
        // \' is an apostrophe.
        assertTrue(string("en", Res.string.d_socket_name_unverified).contains("Amber can't verify it"))
        assertTrue(string("fr", Res.string.d_socket_name_unverified).contains("N'approuvez"))
    }

    @Test
    fun formatArgumentsAreSubstituted() {
        assertTrue(string("en", Res.string.event_kind, "12345").contains("12345"))
        assertTrue(string("de", Res.string.d_public_key, "npub1abc").contains("npub1abc"))
        assertTrue(string("ja", Res.string.d_public_key, "npub1abc").contains("npub1abc"))
        // Single-argument strings use positional %1$s, which Compose resources substitute.
        assertEquals("Export failed: boom", string("en", Res.string.export_failed, "boom"))
    }

    @Test
    fun theXmlTextIsShownAsIs() {
        // Plain text (Compose resources): line breaks and apostrophes as written.
        assertTrue(string("en", Res.string.account_backup_tips_md).contains("\n\nYour account is secured"))
        assertTrue(string("en", Res.string.d_socket_name_unverified).contains("Amber can't verify it"))
    }

    @Test
    fun aDoubledPercentIsALiteralPercent() {
        assertEquals("Connecting to Tor: 45%", string("en", Res.string.tor_connecting, 45))
    }

    @Test
    fun signEventDescriptionsMatchAndroidMapping() = inLanguage("en") {
        assertEquals("Metadata", Permission("sign_event", 0).localizedBlocking())
        assertEquals("Zap", Permission("sign_event", 9735).localizedBlocking())
        assertEquals("Gift wrap", Permission("sign_event", 1059).localizedBlocking())
        // Range mapping (5000..5999).
        assertEquals(getStringBlocking(Res.string.event_kind_5000_5999), Permission("sign_event", 5123).localizedBlocking())
        // Kinds the old desktop copy of the table lacked resolve like on Android.
        assertEquals(getStringBlocking(Res.string.event_kind_30443), Permission("sign_event", 30443).localizedBlocking())
        assertEquals(getStringBlocking(Res.string.event_kind_10008), Permission("sign_event", 10008).localizedBlocking())
    }

    @Test
    fun unknownKindFallsBackToNipOrKindNumber() = inLanguage("en") {
        val text = Permission("sign_event", 987654).localizedBlocking()
        assertTrue("should mention the kind number", text.contains("987654"))
    }

    @Test
    fun desktopUiStringsResolveInEnglish() {
        assertEquals("Approve", string("en", Res.string.d_approve))
        assertEquals("Incoming requests", string("en", Res.string.d_route_incoming_title))
    }

    @Test
    fun desktopUiStringsAreTranslatedInEveryLanguage() {
        assertTranslatedEverywhere(Res.string.d_approve)
        assertTranslatedEverywhere(Res.string.d_socket_name_unverified)
    }

    @Test
    fun requestDescriptionsAreTranslatedInEveryLanguage() {
        assertTranslatedEverywhere(Res.string.switch_relays)
        assertTranslatedEverywhere(Res.string.invalid_request)
    }

    @Test
    fun rememberTypeLabelsAreLocalized() = inLanguage("en") {
        assertEquals("Just once", RememberType.NEVER.label())
        assertEquals("Always", RememberType.ALWAYS.label())
        assertEquals("1 day", RememberType.ONE_DAY.label())
        assertEquals("1 week", RememberType.ONE_WEEK.label())
    }

    @Test
    fun permissionTypesAreLocalized() {
        assertEquals("Read your public key", inLanguage("en") { Permission("get_public_key", null).localizedBlocking() })
        assertEquals(
            string("de", Res.string.read_your_public_key),
            inLanguage("de") { Permission("get_public_key", null).localizedBlocking() },
        )
    }

    @Test
    fun theUnverifiedSocketNameWarningIsTranslatedEverywhere() {
        assertEquals(
            "This tool chose its name itself and Amber can't verify it. Approve only if you just started it.",
            string("en", Res.string.d_socket_name_unverified),
        )
        assertTranslatedEverywhere(Res.string.d_socket_name_unverified)
    }

    @Test
    fun theCrashReportPromptSaysTheReportComesFromTheAccount() {
        assertTrue(string("en", Res.string.crash_report_send_prompt).contains("your public key"))
        assertTranslatedEverywhere(Res.string.crash_report_send_prompt)
    }

    @Test
    fun theOsLocaleMatchesAShippedLanguage() {
        assertEquals("in", Strings.matchSystemLanguage(Locale.forLanguageTag("id-ID")))
        assertEquals("pt-BR", Strings.matchSystemLanguage(Locale.forLanguageTag("pt-BR")))
        assertEquals("it", Strings.matchSystemLanguage(Locale.forLanguageTag("it-IT")))
        assertEquals("en", Strings.matchSystemLanguage(Locale.forLanguageTag("nl-NL")))
    }
}
