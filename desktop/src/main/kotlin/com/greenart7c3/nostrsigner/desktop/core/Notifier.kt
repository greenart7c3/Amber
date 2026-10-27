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
 * `true` when it dispatched a notification. It returns `false` when there is
 * no external channel to use, so the caller falls back to the AWT tray
 * notification:
 * - Windows: the tray balloon/toast is the native channel.
 * - macOS: AWT posts the notification as Amber's own bundle, which macOS asks
 *   the user to allow on first launch. `osascript display notification` is
 *   attributed to Script Editor instead, which a fresh macOS never authorizes
 *   and never prompts for — the notification is silently dropped while
 *   osascript still exits 0, so it cannot be detected and must not be used.
 */
object Notifier {
    /**
     * A button on the notification. [key] is the action id reported back by
     * the notification daemon; [onInvoke] runs on a background thread.
     */
    data class Action(val key: String, val label: String, val onInvoke: () -> Unit)

    private val os = System.getProperty("os.name").lowercase()

    /**
     * Send a notification. Returns true if a native channel handled it.
     *
     * [onActivate], when provided, is invoked if the user clicks the
     * notification (Linux only, via libnotify's default action). [actions] are
     * shown as buttons (Linux only, on daemons that support actions). Both fire
     * on a background thread, so the callbacks must be thread-safe.
     */
    fun notify(
        title: String,
        message: String,
        actions: List<Action> = emptyList(),
        onActivate: (() -> Unit)? = null,
    ): Boolean = when {
        os.contains("linux") || os.contains("nix") || os.contains("nux") -> linux(title, message, actions, onActivate)
        else -> false // Windows/macOS: let the caller use the AWT tray notification.
    }

    /**
     * freedesktop.org notifications. Prefer libnotify's `notify-send`; if it is
     * not installed, talk to the notification daemon directly over D-Bus with
     * `gdbus` (part of glib, present on essentially every desktop Linux).
     */
    private fun linux(title: String, message: String, actions: List<Action>, onActivate: (() -> Unit)?): Boolean {
        // When a click handler or buttons are wanted, try an actionable
        // notification first: over D-Bus directly, else libnotify 0.8+
        // --wait/--action. Falls through if neither works.
        if (onActivate != null || actions.isNotEmpty()) {
            if (dbusWithActions(title, message, actions, onActivate)) return true
            if (notifySendWithActions(title, message, actions, onActivate)) return true
        }
        // `--` stops option parsing so a summary/body starting with '-' is safe.
        if (run("notify-send", "-a", BuildVariant.appName, "-u", "normal", "--", title, message)) return true
        return run(
            "gdbus", "call", "--session",
            "--dest", "org.freedesktop.Notifications",
            "--object-path", "/org/freedesktop/Notifications",
            "--method", "org.freedesktop.Notifications.Notify",
            BuildVariant.appName, "0", "", title, message, "[]", "{}", "5000",
        )
    }

    /**
     * Show an actionable notification straight over the session D-Bus (see
     * [DbusNotifications] for why not notify-send). Returns false when there
     * is no session bus or notification daemon, so notify-send is tried next.
     */
    private fun dbusWithActions(title: String, message: String, actions: List<Action>, onActivate: (() -> Unit)?): Boolean = try {
        val keys = buildList {
            if (onActivate != null) add("default" to "Open")
            actions.forEach { add(it.key to it.label) }
        }
        DbusNotifications.show(BuildVariant.appName, title, message, keys, timeoutMs = 20000) { key ->
            if (key == "default") onActivate?.invoke() else actions.firstOrNull { it.key == key }?.onInvoke?.invoke()
        }
        true
    } catch (e: Throwable) {
        // Also LinkageError: a runtime image without a module dbus-java needs.
        AmberLogger.d("Notifier", "actionable D-Bus notification failed: ${e.message}")
        false
    }

    /**
     * Show a clickable notification via `notify-send --wait --action`. With
     * `--wait` the process stays alive until the notification is closed and
     * prints the invoked action key ("default" when the body is clicked, else
     * an [Action.key]), which we watch on a daemon thread to run the matching
     * callback.
     *
     * Returns true once the notification is showing. Returns false when the
     * installed notify-send is too old to understand these options (it exits
     * non-zero immediately) so the caller can fall back to a plain notification.
     */
    private fun notifySendWithActions(title: String, message: String, actions: List<Action>, onActivate: (() -> Unit)?): Boolean = try {
        val command = mutableListOf(
            "notify-send",
            "-a",
            BuildVariant.appName,
            "-u",
            "normal",
            // Bound how long we wait so a never-expiring notification can't leak
            // the process/thread.
            "-t",
            "20000",
            "--wait",
        )
        if (onActivate != null) command += "--action=default=Open"
        actions.forEach { command += "--action=${it.key}=${it.label}" }
        command += listOf("--", title, message)
        val process = ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start()

        // Older notify-send rejects --wait/--action and exits fast & non-zero.
        if (process.waitFor(500, TimeUnit.MILLISECONDS)) {
            process.exitValue() == 0
        } else {
            // Still running: --wait is supported and blocking for interaction.
            Thread {
                runCatching {
                    val action = process.inputStream.bufferedReader().use { it.readLine() }?.trim()
                    if (action == "default") {
                        onActivate?.invoke()
                    } else {
                        actions.firstOrNull { it.key == action }?.onInvoke?.invoke()
                    }
                }
            }.apply {
                isDaemon = true
                name = "amber-notify-action"
            }.start()
            true
        }
    } catch (e: Exception) {
        AmberLogger.d("Notifier", "actionable notify-send failed: ${e.message}")
        false
    }

    /**
     * Notification daemons that accept actions (and advertise the "actions"
     * capability) but never draw them as buttons: omarchy's quickshell only
     * runs the "default" action on click, mako and dunst only offer actions
     * through a keybinding or menu.
     */
    private val daemonsWithoutButtons = setOf("quickshell", "mako", "dunst")

    /**
     * Whether the running Linux notification daemon shows action buttons, so
     * request notifications can carry Approve / Reject. False when the daemon
     * is one of [daemonsWithoutButtons] or cannot be asked, in which case the
     * caller draws its own popup instead. Blocks briefly: call off the UI thread.
     */
    fun linuxDaemonShowsButtons(): Boolean = try {
        val process = ProcessBuilder(
            "gdbus", "call", "--session",
            "--dest", "org.freedesktop.Notifications",
            "--object-path", "/org/freedesktop/Notifications",
            "--method", "org.freedesktop.Notifications.GetServerInformation",
        ).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        val reply = process.inputStream.bufferedReader().use { it.readText() }
        if (!process.waitFor(4, TimeUnit.SECONDS) || process.exitValue() != 0) {
            false
        } else {
            // Reply: "('gnome-shell', 'GNOME', '50.1', '1.2')"
            val name = Regex("""\('([^']*)'""").find(reply)?.groupValues?.get(1)?.lowercase()
            name != null && name !in daemonsWithoutButtons
        }
    } catch (e: Exception) {
        AmberLogger.d("Notifier", "notification daemon lookup failed: ${e.message}")
        false
    }

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
        !process.waitFor(4, TimeUnit.SECONDS) || process.exitValue() == 0
    } catch (e: Exception) {
        AmberLogger.d("Notifier", "notification command failed: ${command.firstOrNull()} — ${e.message}")
        false
    }
}
