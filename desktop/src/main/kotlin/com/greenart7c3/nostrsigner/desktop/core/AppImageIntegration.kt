package com.greenart7c3.nostrsigner.desktop.core

import java.io.File

/**
 * Integrates a running AppImage with the desktop the way AppImageLauncher /
 * Gear Lever do: a menu entry plus icon in the user's XDG data dirs, so Amber
 * shows up in the app launcher and docks match its window (StartupWMClass) to
 * Amber's icon instead of a generic one.
 *
 * Re-run on every launch so a moved AppImage is picked up. When Amber runs
 * from anything else and the AppImage the entry points at is gone, the entry
 * and icon are removed. The entry has its own name so it never shadows the
 * .deb/.rpm amber-Amber.desktop.
 */
object AppImageIntegration {
    /**
     * X11 class Main.kt gives the window: jpackage's .deb/.rpm entry is
     * amber-Amber.desktop with no StartupWMClass, so docks match it by window
     * class == desktop file name. The AppImage entries declare it explicitly.
     */
    const val WINDOW_CLASS = "amber-Amber"

    internal const val DESKTOP_FILE = "amber-appimage.desktop"
    private const val ICON_FILE = "amber.png"

    fun sync() {
        if (!AutoStart.isLinux) return
        runCatching {
            val entry = File(UriLaunch.xdgAppsDir(), DESKTOP_FILE)
            val icon = File(AppDirs.dataDir, ICON_FILE)
            val appImage = System.getenv("APPIMAGE")?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isFile }
            if (appImage == null) {
                val target = if (entry.exists()) execTarget(entry.readText()) else null
                if (target != null && !File(target).isFile) {
                    entry.delete()
                    icon.delete()
                    AmberLogger.d("AppImageIntegration", "Removed the entry for deleted AppImage $target")
                }
                return
            }

            val iconBytes = AppImageIntegration::class.java.getResourceAsStream("/icon.png")?.use { it.readBytes() } ?: return
            if (!icon.exists() || !icon.readBytes().contentEquals(iconBytes)) icon.writeBytes(iconBytes)
            entry.parentFile.mkdirs()
            val content = desktopEntry(appImage.absolutePath, icon.absolutePath)
            if (!entry.exists() || entry.readText() != content) {
                entry.writeText(content)
                runCatching { ProcessBuilder("update-desktop-database", entry.parentFile.absolutePath).start().waitFor() }
            }
            AmberLogger.d("AppImageIntegration", "Menu entry $entry -> $appImage")
        }.onFailure { AmberLogger.d("AppImageIntegration", "AppImage integration failed: ${it.message}") }
    }

    internal fun desktopEntry(appImagePath: String, iconPath: String): String = """
        [Desktop Entry]
        Type=Application
        Name=Amber
        Comment=Nostr event signer
        Exec=${UriLaunch.quoteForDesktopEntry(appImagePath)} %u
        Icon=$iconPath
        Categories=Network;
        Terminal=false
        StartupWMClass=$WINDOW_CLASS
    """.trimIndent() + "\n"

    /** The program an entry written by [desktopEntry] launches, unquoted. */
    internal fun execTarget(entry: String): String? {
        val exec = entry.lineSequence().firstOrNull { it.startsWith("Exec=") }?.removePrefix("Exec=")?.removeSuffix(" %u") ?: return null
        if (!exec.startsWith("\"")) return exec
        return exec.removeSurrounding("\"").replace("\\\"", "\"").replace("\\\\", "\\")
    }
}
