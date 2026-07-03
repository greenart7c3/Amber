package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.desktop.core.AmberLogger
import dorkbox.systemTray.MenuItem
import dorkbox.systemTray.Separator
import dorkbox.systemTray.SystemTray
import java.awt.event.ActionListener

/**
 * A tray icon backed by the dorkbox SystemTray library, used on Linux.
 *
 * Unlike AWT's `SystemTray` (XEmbed only), dorkbox publishes a freedesktop
 * StatusNotifierItem / AppIndicator, which is what modern Wayland compositors
 * expose — so the icon and menu show up in waybar on Hyprland/Sway and in
 * GNOME/KDE, as well as on X11. It is imperative and lives outside Compose:
 * build it once, call [update] when state changes, and [shutdown] on exit.
 */
class NativeTray private constructor(
    private val tray: SystemTray,
    private val openItem: MenuItem,
    private val lockItem: MenuItem,
) {
    /** Reflect the current pending count, window visibility and lock state. */
    fun update(tooltip: String, openLabel: String, lockLabel: String, showLock: Boolean) {
        runCatching {
            tray.setTooltip(tooltip)
            openItem.text = openLabel
            lockItem.text = lockLabel
            lockItem.enabled = showLock
        }.onFailure { AmberLogger.d("NativeTray", "update failed: ${it.message}") }
    }

    fun shutdown() {
        runCatching { tray.shutdown() }
    }

    companion object {
        /**
         * Try to create the tray. Returns null when no tray backend is
         * available (dorkbox found neither AppIndicator/SNI nor GtkStatusIcon
         * nor a Swing tray), so the caller can fall back gracefully.
         */
        fun create(
            iconStream: () -> java.io.InputStream?,
            tooltip: String,
            openLabel: String,
            lockLabel: String,
            quitLabel: String,
            onToggle: () -> Unit,
            onLock: () -> Unit,
            onQuit: () -> Unit,
        ): NativeTray? {
            val tray = try {
                SystemTray.get()
            } catch (t: Throwable) {
                AmberLogger.e("NativeTray", "SystemTray.get() failed", t as? Exception)
                null
            } ?: return null

            return try {
                iconStream()?.use { tray.setImage(it) }
                tray.setTooltip(tooltip)
                val menu = tray.menu
                val openItem = MenuItem(openLabel, ActionListener { onToggle() })
                val lockItem = MenuItem(lockLabel, ActionListener { onLock() })
                val quitItem = MenuItem(quitLabel, ActionListener { onQuit() })
                menu.add(openItem)
                menu.add(lockItem)
                menu.add(Separator())
                menu.add(quitItem)
                NativeTray(tray, openItem, lockItem)
            } catch (t: Throwable) {
                AmberLogger.e("NativeTray", "Failed to build the tray", t as? Exception)
                runCatching { tray.shutdown() }
                null
            }
        }
    }
}
