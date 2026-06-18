package com.greenart7c3.nostrsigner.ui

import android.content.ClipData
import android.content.ClipDescription
import android.os.Build
import android.os.PersistableBundle

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
