package com.greenart7c3.nostrsigner.desktop.core

import java.util.concurrent.TimeUnit

/**
 * Cross-platform desktop notifications.
 *
 * The Compose/AWT tray notification (`TrayIcon.displayMessage`) only works
 * where AWT has a usable system tray — which excludes Wayland compositors
 * such as Hyprland, Sway or GNOME/Wayland, where `SystemTray.isSupported()`
 * is false. There, and on X11 in general, the right thing is to talk to the
 * freedesktop.org notification daemon (mako, dunst, swaync, GNOME Shell, …).
 *
 * [notify] delivers through the native channel for the current OS and returns
 * `true` when it dispatched a notification. It returns `false` only when no
 * native channel is available (notably Windows, where the caller should fall
 * back to the AWT tray notification) so the caller can decide what to do next.
 */
object Notifier {
    private val os = System.getProperty("os.name").lowercase()

    /** Send a notification. Returns true if a native channel handled it. */
    fun notify(title: String, message: String): Boolean = when {
        os.contains("linux") || os.contains("nix") || os.contains("nux") -> linux(title, message)
        os.contains("mac") || os.contains("darwin") -> mac(title, message)
        else -> false // Windows: let the caller use the AWT tray notification.
    }

    /**
     * freedesktop.org notifications. Prefer libnotify's `notify-send`; if it is
     * not installed, talk to the notification daemon directly over D-Bus with
     * `gdbus` (part of glib, present on essentially every desktop Linux).
     */
    private fun linux(title: String, message: String): Boolean {
        // `--` stops option parsing so a summary/body starting with '-' is safe.
        if (run("notify-send", "-a", "Amber", "-u", "normal", "--", title, message)) return true
        return run(
            "gdbus", "call", "--session",
            "--dest", "org.freedesktop.Notifications",
            "--object-path", "/org/freedesktop/Notifications",
            "--method", "org.freedesktop.Notifications.Notify",
            "Amber", "0", "", title, message, "[]", "{}", "5000",
        )
    }

    private fun mac(title: String, message: String): Boolean {
        val script = "display notification ${appleScriptString(message)} with title ${appleScriptString(title)}"
        return run("osascript", "-e", script)
    }

    private fun appleScriptString(s: String): String = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    /** Test hook: exercises the process plumbing without depending on the OS. */
    internal fun tryCommand(command: List<String>): Boolean = run(*command.toTypedArray())

    /**
     * Launch a command, returning true if it ran and exited successfully (or is
     * still running after a short grace period). A missing binary throws
     * IOException and yields false so the next channel can be tried.
     */
    private fun run(vararg command: String): Boolean = try {
        val process = ProcessBuilder(*command)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        if (process.waitFor(4, TimeUnit.SECONDS)) process.exitValue() == 0 else true
    } catch (e: Exception) {
        AmberLogger.d("Notifier", "notification command failed: ${command.firstOrNull()} — ${e.message}")
        false
    }
}
