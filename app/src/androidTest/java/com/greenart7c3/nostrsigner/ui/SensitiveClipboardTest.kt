package com.greenart7c3.nostrsigner.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.NativeClipboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs with no Amber activity open, so the app does not hold input focus, the
 * same as a user who copied a secret and switched to another app to paste it.
 * On Android 10+ an unfocused app cannot read the clipboard; the test reads it
 * back through the shell's READ_CLIPBOARD_IN_BACKGROUND permission instead.
 */
@RunWith(AndroidJUnit4::class)
class SensitiveClipboardTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private val clipboard = object : Clipboard {
        override val nativeClipboard: NativeClipboard = manager

        override suspend fun getClipEntry(): ClipEntry? = manager.primaryClip?.let { ClipEntry(it) }

        override suspend fun setClipEntry(clipEntry: ClipEntry?) {
            if (clipEntry == null) manager.clearPrimaryClip() else manager.setPrimaryClip(clipEntry.clipData)
        }
    }

    @Before
    fun setUp() {
        manager.clearPrimaryClip()
    }

    @After
    fun tearDown() {
        manager.clearPrimaryClip()
    }

    private fun readClipboardText(): String? {
        instrumentation.uiAutomation.adoptShellPermissionIdentity(READ_CLIPBOARD_IN_BACKGROUND)
        try {
            val clip: ClipData = manager.primaryClip ?: return null
            return if (clip.itemCount > 0) clip.getItemAt(0).text?.toString()?.ifEmpty { null } else null
        } finally {
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }

    @Test
    fun unfocusedAppCannotReadClipboardButShellCan() {
        manager.setPrimaryClip(ClipData.newPlainText("", SECRET))
        assertNull(manager.primaryClip)
        assertEquals(SECRET, readClipboardText())
    }

    @Test
    fun copiedSecretIsClearedWhileAppIsInBackground() = runBlocking {
        clipboard.setSensitiveClip("", SECRET, 3.seconds)
        Thread.sleep(8_000)
        assertNull(readClipboardText())
    }

    companion object {
        // Hidden system permission held by the shell, not in the public SDK.
        private const val READ_CLIPBOARD_IN_BACKGROUND = "android.permission.READ_CLIPBOARD_IN_BACKGROUND"
        private const val SECRET = "nsec1syntheticsecretfortestingonly000000000000000000000000000"
    }
}
