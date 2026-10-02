package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.desktop.core.EncryptedContent
import com.greenart7c3.nostrsigner.desktop.core.EncryptedContentType
import com.greenart7c3.nostrsigner.desktop.core.SignerType
import com.greenart7c3.nostrsigner.desktop.core.contentPermissionType
import com.greenart7c3.nostrsigner.desktop.core.describe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EncryptedContentTest {
    @Test
    fun classifiesLikeTheAndroidEncryptedDataKind() {
        assertEquals(EncryptedContent(EncryptedContentType.CLEAR_TEXT), EncryptedContent.classify("hello"))
        assertEquals(EncryptedContent(EncryptedContentType.EVENT, 14), EncryptedContent.classify("""{"kind":14,"content":"hi","tags":[]}"""))
        assertEquals(EncryptedContent(EncryptedContentType.TAG_ARRAY), EncryptedContent.classify("""[["p","abc"],["e","def"]]"""))
        // Broken JSON (or JSON that is not an event / tag array) is just text.
        assertEquals(EncryptedContentType.CLEAR_TEXT, EncryptedContent.classify("{not json").type)
        assertEquals(EncryptedContentType.CLEAR_TEXT, EncryptedContent.classify("""{"a":1}""").type)
        assertEquals(EncryptedContentType.CLEAR_TEXT, EncryptedContent.classify("[1,2]").type)
    }

    @Test
    fun permissionTypesMatchTheAndroidNames() {
        val text = EncryptedContent(EncryptedContentType.CLEAR_TEXT)
        val event = EncryptedContent(EncryptedContentType.EVENT, 1)
        val tags = EncryptedContent(EncryptedContentType.TAG_ARRAY)
        assertEquals("ENCRYPT_CLEAR_TEXT", SignerType.NIP44_ENCRYPT.contentPermissionType(text))
        assertEquals("ENCRYPT_EVENT", SignerType.NIP04_ENCRYPT.contentPermissionType(event))
        assertEquals("DECRYPT_TAG_ARRAY", SignerType.NIP44_DECRYPT.contentPermissionType(tags))
        assertEquals("DECRYPT_CLEAR_TEXT", SignerType.NIP04_DECRYPT.contentPermissionType(null))
        assertEquals("SIGN_EVENT", SignerType.SIGN_EVENT.contentPermissionType(text))
    }

    @Test
    fun describesWhatIsEncrypted() {
        assertEquals("wants to encrypt this text with NIP44", SignerType.NIP44_ENCRYPT.describe(null, EncryptedContent(EncryptedContentType.CLEAR_TEXT), "en"))
        assertEquals("wants to read this list of tags from NIP04 encrypted content", SignerType.NIP04_DECRYPT.describe(null, EncryptedContent(EncryptedContentType.TAG_ARRAY), "en"))
        assertTrue(SignerType.NIP44_ENCRYPT.describe(null, EncryptedContent(EncryptedContentType.EVENT, 1), "en").contains("Short text note"))
    }
}
