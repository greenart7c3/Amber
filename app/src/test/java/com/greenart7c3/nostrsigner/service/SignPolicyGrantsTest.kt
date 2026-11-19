package com.greenart7c3.nostrsigner.service

import com.greenart7c3.nostrsigner.commonsui.Permission
import com.greenart7c3.nostrsigner.database.ApplicationEntity
import com.greenart7c3.nostrsigner.database.ApplicationWithPermissions
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignPolicyGrantsTest {
    private fun app() = ApplicationWithPermissions(ApplicationEntity.empty().copy(key = "app"), mutableListOf())

    private fun ApplicationWithPermissions.grants() = permissions.map { it.type to it.kind }.toSet()

    @Test
    fun `checked connect permissions are granted under the types requests look up`() {
        val app = app()
        AmberUtils.configureSignPolicy(
            app,
            1,
            "app",
            listOf(
                Permission("encrypt", null, checked = true),
                Permission("nip44v3_decrypt", 1059, checked = true),
                Permission("sign_event", 1, checked = true),
                Permission("sign_event", 7, checked = false),
            ),
        )

        assertEquals(1, app.application.signPolicy)
        assertEquals(
            setOf("NIP04_ENCRYPT" to null, "NIP44_ENCRYPT" to null, "NIP44_V3_DECRYPT" to 1059, "SIGN_EVENT" to 1),
            app.grants(),
        )
    }

    @Test
    fun `basic actions are granted the same whatever the phone's language`() {
        val default = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"))
            val app = app()
            AmberUtils.configureSignPolicy(app, 0, "app", null)

            assertTrue("SIGN_EVENT" to 1 in app.grants())
            assertTrue("DECRYPT_ZAP_EVENT" to null in app.grants())
            assertTrue(app.permissions.none { it.type.contains('İ') })
        } finally {
            Locale.setDefault(default)
        }
    }

    @Test
    fun `a grant the app already has is not added twice`() {
        val app = app()
        AmberUtils.configureSignPolicy(app, 0, "app", null)
        val count = app.permissions.size
        AmberUtils.configureSignPolicy(app, 0, "app", null)

        assertEquals(count, app.permissions.size)
    }

    @Test
    fun `fully trust grants nothing, an unknown policy changes nothing`() {
        val app = app()
        AmberUtils.configureSignPolicy(app, 2, "app", listOf(Permission("sign_event", 1, checked = true)))
        assertEquals(2, app.application.signPolicy)
        assertEquals(emptySet<Pair<String, Int?>>(), app.grants())

        AmberUtils.configureSignPolicy(app, 9, "app", listOf(Permission("sign_event", 1, checked = true)))
        assertEquals(2, app.application.signPolicy)
        assertEquals(emptySet<Pair<String, Int?>>(), app.grants())
    }
}
