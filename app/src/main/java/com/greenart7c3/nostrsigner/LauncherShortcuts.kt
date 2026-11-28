package com.greenart7c3.nostrsigner

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.add_a_new_application
import com.greenart7c3.nostrsigner.commonsui.getStringBlocking
import com.greenart7c3.nostrsigner.commonsui.shortcut_add_application
import com.greenart7c3.nostrsigner.ui.navigation.Route

/** The shortcuts the launcher shows on a long press of Amber's icon. */
object LauncherShortcuts {
    private const val ADD_APPLICATION_ID = "add_application"

    /**
     * Publishes the shortcuts in the current language, or removes them in the offline
     * flavors, which cannot add an application. Call it again after the language changes.
     */
    fun publish(context: Context) {
        try {
            if (BuildFlavorChecker.isOfflineFlavor()) {
                ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(ADD_APPLICATION_ID))
            } else {
                ShortcutManagerCompat.pushDynamicShortcut(context, addApplication(context))
            }
        } catch (e: Exception) {
            AmberLog.e(Amber.TAG, "Error publishing launcher shortcuts", e)
        }
    }

    private fun addApplication(context: Context): ShortcutInfoCompat = ShortcutInfoCompat.Builder(context, ADD_APPLICATION_ID)
        .setShortLabel(getStringBlocking(Res.string.shortcut_add_application))
        .setLongLabel(getStringBlocking(Res.string.add_a_new_application))
        .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_add_application))
        .setIntent(
            Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .putExtra("route", Route.NewApplication.route)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
        .build()
}
