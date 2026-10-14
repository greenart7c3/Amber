package com.greenart7c3.nostrsigner.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveClipboardClearTest {
    private val salt = ByteArray(16) { it.toByte() }
    private val secret = "nsec1syntheticsecret"
    private val copiedHash = sensitiveClipHash(salt, secret)

    @Test
    fun `unreadable clipboard is cleared`() {
        assertTrue(shouldClearSensitiveClip(clipReadable = false, currentText = null, salt, copiedHash))
    }

    @Test
    fun `clipboard still holding the secret is cleared`() {
        assertTrue(shouldClearSensitiveClip(clipReadable = true, currentText = secret, salt, copiedHash))
    }

    @Test
    fun `text copied afterwards is left alone`() {
        assertFalse(shouldClearSensitiveClip(clipReadable = true, currentText = "something else", salt, copiedHash))
    }

    @Test
    fun `non-text clip copied afterwards is left alone`() {
        assertFalse(shouldClearSensitiveClip(clipReadable = true, currentText = null, salt, copiedHash))
    }

    @Test
    fun `hash depends on the salt`() {
        val otherSalt = ByteArray(16) { (it + 1).toByte() }
        assertFalse(shouldClearSensitiveClip(clipReadable = true, currentText = secret, otherSalt, copiedHash))
    }
}
