package com.greenart7c3.nostrsigner.ui

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import android.os.SystemClock
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import com.greenart7c3.nostrsigner.Amber
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** How long a copied secret stays on the clipboard before it is cleared. */
val SENSITIVE_CLIPBOARD_CLEAR_DELAY = 60.seconds

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
 * Copies a secret to the clipboard flagged as sensitive content and schedules
 * [SensitiveClipboardClearReceiver] to clear it after [clearAfter]. The alarm
 * is held by the system, so the clear still runs if the process is killed in
 * the meantime. Copying another secret replaces the pending clear.
 */
suspend fun Clipboard.setSensitiveClip(
    label: CharSequence,
    text: CharSequence,
    clearAfter: Duration = SENSITIVE_CLIPBOARD_CLEAR_DELAY,
) {
    setClipEntry(ClipEntry(newSensitivePlainText(label, text)))
    scheduleSensitiveClear(Amber.instance, text, clearAfter)
}

private fun scheduleSensitiveClear(
    context: Context,
    copiedValue: CharSequence,
    clearAfter: Duration,
) {
    // The alarm only gets a salted hash to recognize the secret by, never the
    // secret itself.
    val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
    val intent = Intent(context, SensitiveClipboardClearReceiver::class.java)
        .putExtra(SensitiveClipboardClearReceiver.EXTRA_SALT, salt)
        .putExtra(SensitiveClipboardClearReceiver.EXTRA_HASH, sensitiveClipHash(salt, copiedValue.toString()))
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    // Inexact so no exact-alarm permission is needed. The system may defer it
    // by up to 75% of the delay (about 45s for the default), also in Doze.
    // JobScheduler/WorkManager were not used: they batch jobs of an app that
    // is not running and can hold the clear back for ~30 minutes.
    context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(
        AlarmManager.ELAPSED_REALTIME_WAKEUP,
        SystemClock.elapsedRealtime() + clearAfter.inWholeMilliseconds,
        pendingIntent,
    )
}

internal fun sensitiveClipHash(salt: ByteArray, text: String): ByteArray = MessageDigest.getInstance("SHA-256").run {
    update(salt)
    digest(text.toByteArray(Charsets.UTF_8))
}

/**
 * Whether the scheduled clear should wipe the clipboard. Android 10+ blocks
 * clipboard reads from an app without input focus, which is the usual state
 * once the user has switched away to paste the secret. An unreadable clipboard
 * is therefore cleared, at the cost of also wiping anything copied since. A
 * readable clipboard is only cleared while it still holds the copied secret.
 */
internal fun shouldClearSensitiveClip(
    clipReadable: Boolean,
    currentText: String?,
    salt: ByteArray,
    copiedHash: ByteArray,
): Boolean {
    if (!clipReadable) return true
    if (currentText == null) return false
    return MessageDigest.isEqual(sensitiveClipHash(salt, currentText), copiedHash)
}

class SensitiveClipboardClearReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val salt = intent.getByteArrayExtra(EXTRA_SALT) ?: return
        val copiedHash = intent.getByteArrayExtra(EXTRA_HASH) ?: return
        val manager = context.getSystemService(ClipboardManager::class.java) ?: return

        val clip = runCatching { manager.primaryClip }.getOrNull()
        val currentText = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
        if (shouldClearSensitiveClip(clip != null, currentText, salt, copiedHash)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                manager.clearPrimaryClip()
            } else {
                manager.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
    }

    companion object {
        const val EXTRA_SALT = "salt"
        const val EXTRA_HASH = "hash"
    }
}
