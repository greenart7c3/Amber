package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.desktop.core.AmberLogger
import dorkbox.systemTray.MenuItem
import dorkbox.systemTray.Separator
import dorkbox.systemTray.SystemTray
import java.awt.event.ActionListener

/**
 * A tray icon backed by the dorkbox SystemTray library, used on Linux.
 *
 * Unlike AWT's `SystemTray` (XEmbed only), dorkbox can publish a freedesktop
 * StatusNotifierItem via libayatana-appindicator, which is what modern Wayland
 * compositors expose — so the icon and menu show up in waybar on Hyprland/Sway
 * and in GNOME/KDE, as well as on X11.
 *
 * On Wayland dorkbox's auto-detection tends to pick the GtkStatusIcon backend,
 * which needs a legacy XEmbed tray host and is therefore invisible there, so we
 * force the AppIndicator (SNI) backend. `AMBER_TRAY_TYPE` overrides the choice
 * (AppIndicator / Gtk / AutoDetect) for debugging.
 *
 * It is imperative and lives outside Compose: build it once, call [update] when
 * state changes, and [shutdown] on exit.
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
        private fun isWayland(): Boolean = !System.getenv("WAYLAND_DISPLAY").isNullOrBlank() ||
            System.getenv("XDG_SESSION_TYPE").equals("wayland", ignoreCase = true)

        private fun chosenTrayType(): SystemTray.TrayType {
            System.getenv("AMBER_TRAY_TYPE")?.let { name ->
                runCatching { return SystemTray.TrayType.valueOf(name) }
                    .onFailure { AmberLogger.e("NativeTray", "Unknown AMBER_TRAY_TYPE=$name; ignoring") }
            }
            // On Wayland the GtkStatusIcon backend is invisible; AppIndicator
            // (SNI) is the one waybar and the Wayland shells actually host.
            return if (isWayland()) SystemTray.TrayType.AppIndicator else SystemTray.TrayType.AutoDetect
        }

        /**
         * Try to create the tray. Returns null when no tray backend is
         * available (e.g. libayatana-appindicator is not installed), so the
         * caller can fall back gracefully.
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
            val type = chosenTrayType()
            runCatching {
                if (type != SystemTray.TrayType.AutoDetect) SystemTray.FORCE_TRAY_TYPE = type
                if (System.getenv("AMBER_DEBUG") != null) SystemTray.DEBUG = true
            }
            AmberLogger.i("NativeTray", "initializing tray (wayland=${isWayland()}, backend=$type)")

            val tray = try {
                SystemTray.get("Amber")
            } catch (t: Throwable) {
                AmberLogger.e("NativeTray", "SystemTray.get() threw", t as? Exception)
                null
            }
            if (tray == null) {
                AmberLogger.e(
                    "NativeTray",
                    "No system tray available (backend=$type). On Wayland/Hyprland install " +
                        "libayatana-appindicator (and enable waybar's tray module), or set " +
                        "AMBER_TRAY_TYPE=Gtk / AutoDetect. AMBER_DISABLE_TRAY=1 skips the tray.",
                )
                return null
            }

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
                AmberLogger.i("NativeTray", "tray ready (backend=$type)")
                NativeTray(tray, openItem, lockItem)
            } catch (t: Throwable) {
                AmberLogger.e("NativeTray", "Failed to build the tray", t as? Exception)
                runCatching { tray.shutdown() }
                null
            }
        }
    }
}
