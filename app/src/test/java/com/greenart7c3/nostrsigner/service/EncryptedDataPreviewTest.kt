package com.greenart7c3.nostrsigner.service

import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.ClearTextEncryptedDataKind
import com.greenart7c3.nostrsigner.models.EventEncryptedDataKind
import com.greenart7c3.nostrsigner.models.TagArrayEncryptedDataKind
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EncryptedDataPreviewTest {
    private val account = mockk<Account> { every { hexKey } returns "ab".repeat(32) }
    private val errors = mutableListOf<String>()

    private fun event(kind: Int, content: String) = """{"id":"${"0".repeat(64)}","pubkey":"${"ab".repeat(32)}","created_at":1,"kind":$kind,"tags":[],"content":"$content","sig":"${"0".repeat(128)}"}"""

    private fun preview(text: String, data: String = "input", result: String = "output") = runBlocking { previewOf(text, data, result, account) { errors += it } }

    @Test
    fun `plain text shows the request input next to the result`() {
        val preview = preview("hello") as ClearTextEncryptedDataKind

        assertEquals("input", preview.text)
        assertEquals("output", preview.result)
    }

    @Test
    fun `a tag array is shown as tags`() {
        val preview = preview("""[["p","abc"]]""")

        assertTrue(preview is TagArrayEncryptedDataKind)
    }

    @Test
    fun `an event that is not a seal is shown without opening anything`() {
        val preview = preview(event(1, "note")) as EventEncryptedDataKind

        assertEquals(1, preview.event.kind)
        assertNull(preview.sealEncryptedDataKind)
    }

    @Test
    fun `a seal is opened for the preview`() {
        coEvery { account.decrypt("sealed", any()) } returns event(14, "dm")

        val preview = preview(event(13, "sealed")) as EventEncryptedDataKind

        assertEquals(14, (preview.sealEncryptedDataKind as EventEncryptedDataKind).event.kind)
    }

    @Test
    fun `malformed JSON falls back to plain text and is reported`() {
        val preview = preview("{not json")

        assertTrue(preview is ClearTextEncryptedDataKind)
        assertEquals(1, errors.size)
    }
}
