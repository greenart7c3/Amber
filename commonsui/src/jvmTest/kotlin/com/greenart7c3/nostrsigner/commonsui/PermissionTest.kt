package com.greenart7c3.nostrsigner.commonsui

import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionTest {

    @Test
    fun `serializes type and kind`() {
        val json = Permission.mapper.writeValueAsString(Permission("sign_event", 22242))
        assertEquals("""{"type":"sign_event","kind":22242}""", json)
    }

    @Test
    fun `omits kind when null`() {
        val json = Permission.mapper.writeValueAsString(Permission("connect", null))
        assertEquals("""{"type":"connect"}""", json)
    }

    @Test
    fun `does not serialize the checked flag`() {
        val json = Permission.mapper.writeValueAsString(Permission("sign_event", 1, checked = false))
        assertEquals("""{"type":"sign_event","kind":1}""", json)
    }

    @Test
    fun `deserializes type and kind`() {
        val permission = Permission.mapper.readValue<Permission>("""{"type":"sign_event","kind":22242}""")
        assertEquals(Permission("sign_event", 22242), permission)
        assertTrue(permission.checked)
    }

    @Test
    fun `deserializes missing kind as null`() {
        val permission = Permission.mapper.readValue<Permission>("""{"type":"connect"}""")
        assertEquals("connect", permission.type)
        assertNull(permission.kind)
        assertTrue(permission.checked)
    }

    @Test
    fun `deserializes explicit null kind as null`() {
        val permission = Permission.mapper.readValue<Permission>("""{"type":"connect","kind":null}""")
        assertEquals(Permission("connect", null), permission)
    }

    @Test
    fun `ignores unknown fields`() {
        val permission = Permission.mapper.readValue<Permission>("""{"type":"connect","extra":"ignored"}""")
        assertEquals(Permission("connect", null), permission)
    }

    @Test
    fun `parses kind given as a string of digits`() {
        val permission = Permission.mapper.readValue<Permission>("""{"type":"sign_event","kind":"22242"}""")
        assertEquals(Permission("sign_event", 22242), permission)
    }

    @Test
    fun `falls back to null kind for non numeric kind`() {
        val permission = Permission.mapper.readValue<Permission>("""{"type":"sign_event","kind":"abc"}""")
        assertEquals(Permission("sign_event", null), permission)
    }

    @Test
    fun `round trips a list of permissions`() {
        val permissions = listOf(
            Permission("sign_event", 22242),
            Permission("connect", null),
            Permission("nip04_encrypt", null, checked = false),
        )

        val json = Permission.mapper.writeValueAsString(permissions)
        val parsed = Permission.mapper.readValue<MutableList<Permission>>(json)

        assertEquals(
            listOf("sign_event" to 22242, "connect" to null, "nip04_encrypt" to null),
            parsed.map { it.type to it.kind },
        )
        assertTrue(parsed.all { it.checked })
    }

    @Test
    fun `describes a known kind by its string`() {
        assertEquals(PermissionText.Resource(Res.string.event_kind_1), Permission("sign_event", 1).description())
    }

    @Test
    fun `describes an unlisted kind by its NIP, then by number`() {
        // 30443 has its own string; a kind no table lists falls back to "Event kind N".
        assertEquals(PermissionText.Resource(Res.string.event_kind_30443), Permission("sign_event", 30443).description())
        assertEquals(PermissionText.Resource(Res.string.event_kind, "987654"), Permission("sign_event", 987654).description())
        assertEquals(PermissionText.Resource(Res.string.nip_44), Permission("nip", 44).description())
        assertEquals(PermissionText.Resource(Res.string.nip_kind, "12"), Permission("nip", 12).description())
    }

    @Test
    fun `describes connect as a sentence or as the short label`() {
        assertEquals(
            PermissionText.Resource(Res.string.would_like_your_permission_to_read_your_public_key_and_sign_events_on_your_behalf),
            Permission("connect", null).description(),
        )
        assertEquals(PermissionText.Resource(Res.string.connect), Permission("connect", null).description(shouldTranslateConnect = true))
    }

    @Test
    fun `shows an unknown permission type as is`() {
        assertEquals(PermissionText.Literal("frobnicate"), Permission("frobnicate", null).description())
    }
}
