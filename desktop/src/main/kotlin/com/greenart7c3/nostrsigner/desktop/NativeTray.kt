package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.desktop.core.AmberLogger
import dorkbox.systemTray.MenuItem
import dorkbox.systemTray.Separator
import dorkbox.systemTray.SystemTray
import java.awt.event.ActionListener
import java.io.File
import java.nio.file.Files

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

        /**
         * dorkbox 4.4 only looks for the legacy `libappindicator3.so` family of
         * names, never the Ayatana fork. Modern distros (Arch/Omarchy, recent
         * Debian/Ubuntu, Fedora) ship only `libayatana-appindicator3.so.1`, so
         * dorkbox finds nothing and the AppIndicator backend fails.
         *
         * Bridge that by symlinking the Ayatana library under the GTK3 names
         * dorkbox probes into a private dir and prepending it to
         * `jna.library.path` (which JNA re-reads per load). No root needed.
         * Skipped when a real `libappindicator3.so.1` is already present.
         */
        private val SYSTEM_LIB_DIRS = listOf(
            "/usr/lib",
            "/usr/lib64",
            "/usr/local/lib",
            "/usr/lib/x86_64-linux-gnu",
            "/lib/x86_64-linux-gnu",
            "/usr/lib/aarch64-linux-gnu",
            "/lib/aarch64-linux-gnu",
        )

        private fun ensureAppIndicatorLibrary() {
            runCatching {
                val tmp = File(System.getProperty("java.io.tmpdir"), "amber-appindicator-shim")
                val shim = buildAppIndicatorShim(SYSTEM_LIB_DIRS, tmp) ?: return
                val prop = "jna.library.path"
                val existing = System.getProperty(prop)
                System.setProperty(prop, if (existing.isNullOrBlank()) shim else "$shim${File.pathSeparator}$existing")
                AmberLogger.i("NativeTray", "AppIndicator shim ready at $shim (jna.library.path)")
            }.onFailure { AmberLogger.e("NativeTray", "AppIndicator shim failed: ${it.message}") }
        }

        /**
         * If only the Ayatana appindicator library is present, symlink it under
         * the GTK3 names dorkbox probes into [shimDir] and return that dir; else
         * null (legacy lib already there, or no Ayatana lib at all). Package-
         * visible for testing.
         */
        internal fun buildAppIndicatorShim(libDirs: List<String>, shimDir: File): String? {
            // Nothing to do if the legacy library dorkbox wants already exists.
            if (libDirs.any { File(it, "libappindicator3.so.1").exists() || File(it, "libappindicator3.so").exists() }) return null

            val ayatana = libDirs.asSequence()
                .flatMap { dir -> sequenceOf("libayatana-appindicator3.so.1", "libayatana-appindicator3.so").map { File(dir, it) } }
                .firstOrNull { it.exists() } ?: return null

            shimDir.mkdirs()
            // Only the GTK3 names — we force PREFER_GTK3, and pointing a GTK2
            // name at a GTK3 lib would mismatch.
            listOf(
                "libappindicator3.so",
                "libappindicator3.so.1",
                "libappindicator3-1.so",
                "libappindicator-gtk3.so",
                "libappindicator-gtk3-1.so",
            ).forEach { name ->
                val link = File(shimDir, name).toPath()
                runCatching {
                    Files.deleteIfExists(link)
                    Files.createSymbolicLink(link, ayatana.toPath())
                }
            }
            return shimDir.absolutePath
        }

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
                SystemTray.PREFER_GTK3 = true
                if (System.getenv("AMBER_DEBUG") != null) SystemTray.DEBUG = true
            }
            if (type == SystemTray.TrayType.AppIndicator) ensureAppIndicatorLibrary()
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
