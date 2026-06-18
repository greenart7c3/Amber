package com.greenart7c3.nostrsigner.ui

import android.content.ClipData
import android.content.ClipDescription
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import com.greenart7c3.nostrsigner.Amber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long a copied secret stays on the clipboard before it is cleared. */
const val SENSITIVE_CLIPBOARD_CLEAR_DELAY_MS = 60_000L

/**
 * Creates a [ClipData] flagged as sensitive so the system (Android 13+) avoids
 * showing the copied content in clipboard previews. Use this for secrets such as
 * the nsec, ncryptsec and seed words.
 */
fun newSensitivePlainText(label: CharSequence, text: CharSequence): ClipData {
    val clipData = ClipData.newPlainText(label, text)
    val extras = PersistableBundle().apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        } else {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
    }
    clipData.description.extras = extras
    return clipData
}

/**
 * Copies a secret to the clipboard flagged as sensitive content and schedules it
 * to be cleared after [clearAfterMillis]. The clipboard is only cleared if it
 * still contains the copied secret, so anything the user copies afterwards is
 * left untouched.
 */
suspend fun Clipboard.setSensitiveClip(
    label: CharSequence,
    text: CharSequence,
    scope: CoroutineScope = Amber.instance.applicationIOScope,
    clearAfterMillis: Long = SENSITIVE_CLIPBOARD_CLEAR_DELAY_MS,
) {
    setClipEntry(ClipEntry(newSensitivePlainText(label, text)))
    scheduleSensitiveClear(text, scope, clearAfterMillis)
}

private fun Clipboard.scheduleSensitiveClear(
    copiedValue: CharSequence,
    scope: CoroutineScope,
    delayMillis: Long,
) {
    scope.launch {
        delay(delayMillis)
        val currentText = getClipEntry()?.clipData?.let { clip ->
            if (clip.itemCount > 0) clip.getItemAt(0).text?.toString() else null
        }
        if (currentText == copiedValue.toString()) {
            clearClipboard()
        }
    }
}

private suspend fun Clipboard.clearClipboard() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        nativeClipboard.clearPrimaryClip()
    } else {
        setClipEntry(ClipEntry(ClipData.newPlainText("", "")))
    }
}
