package com.greenart7c3.nostrsigner.desktop.core

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

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
 * a handle when it dispatched a notification. A notification with buttons or
 * a click handler (a request) goes out on every OS: freedesktop on Linux, a
 * WinRT toast on Windows ([WindowsToasts]), the notification center on macOS
 * ([MacNotifications]). It returns `null` when that failed, so the caller
 * shows the request itself, and for a plain notice on Windows and macOS, so
 * the caller falls back to the AWT tray notification:
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

    /**
     * A notification that was posted, so it can be taken down again. [close]
     * withdraws it on a background thread (callers are UI callbacks, and the
     * gdbus fallback blocks for a moment) and also disarms its buttons, so a
     * banner outliving its request can never approve or reject it. Closing
     * twice, or a notification the daemon already dropped, is a no-op.
     */
    class Notification internal constructor(tracked: Boolean = true, private val withdraw: () -> Unit) {
        init {
            if (tracked) open += this
        }

        fun close() {
            if (open.remove(this)) withdrawals.execute(withdraw)
        }

        /** [close], on the calling thread: for the shutdown hook, where the executor's daemon thread is no use. */
        internal fun closeNow() {
            if (open.remove(this)) runCatching(withdraw)
        }
    }

    private val withdrawals = Executors.newSingleThreadExecutor { r ->
        Thread(r, "amber-notify-close").apply { isDaemon = true }
    }

    /**
     * Notifications not closed yet. A request does not outlive the process, so
     * when Amber quits with some still showing they are taken down: buttons
     * left behind would answer nothing.
     */
    private val open: MutableSet<Notification> = ConcurrentHashMap.newKeySet<Notification>().also { open ->
        Runtime.getRuntime().addShutdownHook(Thread({ open.toList().forEach { it.closeNow() } }, "amber-notify-shutdown"))
    }

    private val os = System.getProperty("os.name").lowercase()

    /**
     * Send a notification. Returns a handle to withdraw it again (see
     * [Notification.close]), or null when no channel delivered it.
     *
     * [onActivate], when provided, is invoked if the user clicks the
     * notification. [actions] are shown as buttons (on Linux only by daemons
     * that draw them, see [nativeButtons]). Both fire
     * on a background thread, so the callbacks must be thread-safe.
     */
    fun notify(
        title: String,
        message: String,
        actions: List<Action> = emptyList(),
        onActivate: (() -> Unit)? = null,
    ): Notification? = when {
        isLinux -> linux(title, message, actions, onActivate)
        // A plain notice has nothing to click or take down: the caller's AWT
        // tray notification does for it on Windows and macOS.
        actions.isEmpty() && onActivate == null -> null
        isWindows -> windows(title, message, actions, onActivate)
        isMac -> mac(title, message, actions, onActivate)
        else -> null
    }

    private val isLinux = os.contains("linux") || os.contains("nix") || os.contains("nux")
    private val isWindows = os.contains("win")
    private val isMac = os.contains("mac")

    /**
     * Whether a request notification can carry Approve / Reject through the
     * OS-native channel here: a Linux daemon that draws buttons, Windows, and
     * macOS once the user allowed Amber's notifications (the first call there
     * makes macOS ask, see [MacNotifications.authorized]). Blocks briefly:
     * call off the UI thread.
     */
    fun nativeButtons(): Boolean = when {
        isLinux -> linuxDaemonShowsButtons()
        isWindows -> true
        isMac -> MacNotifications.usable() && MacNotifications.authorized()
        else -> false
    }

    /** A macOS notification (see [MacNotifications]); null when it could not be posted. */
    private fun mac(title: String, message: String, actions: List<Action>, onActivate: (() -> Unit)?): Notification? = try {
        val close = MacNotifications.show(title, message, actions.map { it.key to it.label }) { key ->
            if (key == MacNotifications.DEFAULT_ACTION) onActivate?.invoke() else actions.firstOrNull { it.key == key }?.onInvoke?.invoke()
        }
        Notification(withdraw = close)
    } catch (e: Throwable) {
        AmberLogger.d("Notifier", "macOS notification failed: ${e.message}")
        null
    }

    /** A Windows toast (see [WindowsToasts]); null when it could not be posted. */
    private fun windows(title: String, message: String, actions: List<Action>, onActivate: (() -> Unit)?): Notification? = try {
        val close = WindowsToasts.show(title, message, actions.map { it.key to it.label }) { key ->
            if (key == WindowsToasts.DEFAULT_ACTION) onActivate?.invoke() else actions.firstOrNull { it.key == key }?.onInvoke?.invoke()
        }
        Notification(withdraw = close)
    } catch (e: Throwable) {
        // Also LinkageError: JNA or WinRT missing on this machine.
        AmberLogger.d("Notifier", "Windows toast failed: ${e.message}")
        null
    }

    /**
     * freedesktop.org notifications. Prefer libnotify's `notify-send`; if it is
     * not installed, talk to the notification daemon directly over D-Bus with
     * `gdbus` (part of glib, present on essentially every desktop Linux).
     *
     * Only an actionable notification can be taken down again — those carry the
     * request to answer. The plain fallbacks below never report an id to close,
     * so their handle's [Notification.close] does nothing.
     */
    private fun linux(title: String, message: String, actions: List<Action>, onActivate: (() -> Unit)?): Notification? {
        // When a click handler or buttons are wanted, try an actionable
        // notification first: over D-Bus directly, else libnotify 0.8+
        // --wait/--action. Falls through if neither works.
        if (onActivate != null || actions.isNotEmpty()) {
            dbusWithActions(title, message, actions, onActivate)?.let { return it }
            notifySendWithActions(title, message, actions, onActivate)?.let { return it }
        }
        // `--` stops option parsing so a summary/body starting with '-' is safe.
        if (run("notify-send", "-a", BuildVariant.appName, "-u", "normal", "--", title, message)) return Notification(tracked = false) {}
        if (run(
                "gdbus", "call", "--session",
                "--dest", "org.freedesktop.Notifications",
                "--object-path", "/org/freedesktop/Notifications",
                "--method", "org.freedesktop.Notifications.Notify",
                BuildVariant.appName, "0", "", title, message, "[]", "{}", "5000",
            )
        ) {
            return Notification(tracked = false) {}
        }
        return null
    }

    /**
     * Show an actionable notification straight over the session D-Bus (see
     * [DbusNotifications] for why not notify-send). Returns null when there
     * is no session bus or notification daemon, so notify-send is tried next.
     */
    private fun dbusWithActions(title: String, message: String, actions: List<Action>, onActivate: (() -> Unit)?): Notification? = try {
        val keys = buildList {
            if (onActivate != null) add("default" to "Open")
            actions.forEach { add(it.key to it.label) }
        }
        val id = DbusNotifications.show(BuildVariant.appName, title, message, keys, timeoutMs = 20000) { key ->
            if (key == "default") onActivate?.invoke() else actions.firstOrNull { it.key == key }?.onInvoke?.invoke()
        }
        Notification { withdraw(id, title) }
    } catch (e: Throwable) {
        // Also LinkageError: a runtime image without a module dbus-java needs.
        AmberLogger.d("Notifier", "actionable D-Bus notification failed: ${e.message}")
        null
    }

    /**
     * Take down a notification posted over D-Bus: through the connection that
     * posted it (which also forgets its buttons), else straight to the daemon
     * with gdbus when that connection is gone. Omarchy's shell then still
     * shows the card, so it is also dismissed there by [title].
     */
    private fun withdraw(id: Long, title: String) {
        if (!DbusNotifications.close(id)) {
            run(
                "gdbus", "call", "--session",
                "--dest", "org.freedesktop.Notifications",
                "--object-path", "/org/freedesktop/Notifications",
                "--method", "org.freedesktop.Notifications.CloseNotification",
                id.toString(),
            )
        }
        dismissOnOmarchy(title)
    }

    /**
     * The config directory of Omarchy's shell while it is the notification
     * daemon, else null. Refreshed by [linuxDaemonShowsButtons], which runs
     * before every request notification, so a daemon change is picked up.
     */
    @Volatile
    private var omarchyShellDir: File? = null

    /**
     * Omarchy's shell keeps a card on screen after its sender closes it: a
     * `CloseNotification` only takes the buttons off (its `watchNotification`
     * drops the live object and leaves the visible row). Only the shell's own
     * IPC removes the card, so ask it too. A no-op on every other daemon.
     */
    private fun dismissOnOmarchy(title: String) {
        val dir = omarchyShellDir ?: return
        omarchyDismissCommands(dir, title).firstOrNull { run(*it.toTypedArray()) }
    }

    /**
     * The commands that dismiss the card titled [title] in the Omarchy shell
     * running [shellDir], in the order to try them: Omarchy's wrapper, then
     * quickshell's IPC client when the wrapper is not on the PATH. [title] is
     * always one argument, never part of a shell string.
     *
     * The shell matches cards by summary *substring*, so a very short title
     * would sweep unrelated cards off the screen, and one that starts with a
     * dash could be read as an option: neither is dismissed (the card then
     * leaves on the shell's own timer).
     */
    internal fun omarchyDismissCommands(shellDir: File, title: String): List<List<String>> {
        if (title.length < OMARCHY_MIN_TITLE || title.startsWith("-")) return emptyList()
        return listOf(
            listOf("omarchy-shell", "-q", "notifications", "dismiss", title),
            listOf("qs", "ipc", "-p", shellDir.path, "call", "notifications", "dismiss", title),
        )
    }

    private const val OMARCHY_MIN_TITLE = 3

    /**
     * Show a clickable notification via `notify-send --wait --action`. With
     * `--wait` the process stays alive until the notification is closed and
     * prints the invoked action key ("default" when the body is clicked, else
     * an [Action.key]), which we watch on a daemon thread to run the matching
     * callback. `--print-id` prints the id first, which [Notification.close]
     * hands to the daemon so an answered request's banner goes away.
     *
     * Returns the notification once it is showing. Returns null when the
     * installed notify-send is too old to understand these options (it exits
     * non-zero immediately) so the caller can fall back to a plain notification.
     */
    private fun notifySendWithActions(title: String, message: String, actions: List<Action>, onActivate: (() -> Unit)?): Notification? = try {
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
            "--print-id",
        )
        if (onActivate != null) command += "--action=default=Open"
        actions.forEach { command += "--action=${it.key}=${it.label}" }
        command += listOf("--", title, message)
        val process = ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        // The id (--print-id) and, once the user picks one, the action key.
        val output = process.inputStream.bufferedReader()
        val id = AtomicLong(0)
        val posted = Notification {
            process.destroy()
            id.get().takeIf { it > 0 }?.let { withdraw(it, title) }
        }

        // Older notify-send rejects --wait/--action and exits fast & non-zero.
        if (process.waitFor(500, TimeUnit.MILLISECONDS)) {
            if (process.exitValue() == 0) posted else null
        } else {
            // Still running: --wait is supported and blocking for interaction.
            Thread {
                runCatching {
                    val first = output.readLine()?.trim()
                    // notify-send 0.7.6+ prints the id ahead of the action; an
                    // older build that ignores --print-id prints the action only.
                    val action = if (first?.toLongOrNull() != null) {
                        id.set(first.toLong())
                        output.readLine()?.trim()
                    } else {
                        first
                    }
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
            posted
        }
    } catch (e: Exception) {
        AmberLogger.d("Notifier", "actionable notify-send failed: ${e.message}")
        null
    }

    /**
     * Notification daemons that accept actions (and advertise the "actions"
     * capability) but never draw them as buttons: mako and dunst only offer
     * actions through a keybinding or menu.
     */
    private val daemonsWithoutButtons = setOf("mako", "dunst")

    /**
     * Whether the running Linux notification daemon shows action buttons, so
     * request notifications can carry Approve / Reject. False when the daemon
     * is one of [daemonsWithoutButtons], a quickshell config not known to draw
     * them (see [omarchyShell]), or cannot be asked, in which case
     * the caller draws its own popup instead. Blocks briefly: call off the UI
     * thread.
     */
    fun linuxDaemonShowsButtons(): Boolean = try {
        val reply = gdbus("org.freedesktop.Notifications", "/org/freedesktop/Notifications", "org.freedesktop.Notifications.GetServerInformation")
        // Reply: "('gnome-shell', 'GNOME', '50.1', '1.2')"
        val name = reply?.let { Regex("""\('([^']*)'""").find(it) }?.groupValues?.get(1)?.lowercase()
        val omarchy = if (name == "quickshell") omarchyShell() else null
        omarchyShellDir = omarchy
        when (name) {
            null -> false
            "quickshell" -> omarchy != null
            else -> name !in daemonsWithoutButtons
        }
    } catch (e: Exception) {
        AmberLogger.d("Notifier", "notification daemon lookup failed: ${e.message}")
        omarchyShellDir = null
        false
    }

    /**
     * quickshell is a toolkit, and every shell built on it reports the same
     * server name, so ask the running one. Omarchy's shell (4.5+) draws every
     * sender action except "default" as a button on the front card of a stack
     * (the card click runs "default"); older Omarchy and other quickshell
     * configs may not draw them, so they keep Amber's popup. Omarchy's
     * notifications plugin is recognized by its button component. Returns that
     * shell's config directory, or null for any other quickshell config.
     */
    private fun omarchyShell(): File? {
        val reply = gdbus("org.freedesktop.DBus", "/org/freedesktop/DBus", "org.freedesktop.DBus.GetConnectionUnixProcessID", "org.freedesktop.Notifications")
        val pid = reply?.let { parsePidReply(it) } ?: return null
        val argv = File("/proc/$pid/cmdline").readText().split('\u0000').filter { it.isNotEmpty() }
        val configDir = quickshellConfigDir(argv, System.getenv("XDG_CONFIG_HOME"), System.getProperty("user.home")) ?: return null
        return configDir.takeIf { drawsOmarchyButtons(it) }
    }

    /** "(uint32 2258,)" → 2258. */
    internal fun parsePidReply(reply: String): Long? = Regex("""\(uint32 (\d+),\)""").find(reply)?.groupValues?.get(1)?.toLongOrNull()

    /**
     * The config directory a quickshell command line runs: `-p`/`--path <dir>`,
     * `-c`/`--config <name>` under `$XDG_CONFIG_HOME/quickshell`, else that
     * directory itself.
     */
    internal fun quickshellConfigDir(argv: List<String>, xdgConfigHome: String?, home: String): File? {
        val configRoot = File(xdgConfigHome?.takeIf { it.isNotBlank() } ?: "$home/.config", "quickshell")
        fun valueOf(vararg flags: String): String? {
            argv.forEachIndexed { i, arg ->
                flags.forEach { flag ->
                    if (arg == flag) return argv.getOrNull(i + 1)
                    if (arg.startsWith("$flag=")) return arg.substringAfter('=')
                }
            }
            return null
        }
        valueOf("-p", "--path")?.let { return File(it) }
        valueOf("-c", "--config")?.let { return File(configRoot, it) }
        return configRoot
    }

    internal fun drawsOmarchyButtons(configDir: File): Boolean = File(configDir, "plugins/notifications/DeedButton.qml").isFile

    /** A `gdbus call` reply, or null when it fails or times out. */
    private fun gdbus(dest: String, path: String, method: String, vararg args: String): String? {
        val process = ProcessBuilder(
            listOf("gdbus", "call", "--session", "--dest", dest, "--object-path", path, "--method", method) + args,
        ).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        val reply = process.inputStream.bufferedReader().use { it.readText() }
        return if (process.waitFor(4, TimeUnit.SECONDS) && process.exitValue() == 0) reply else null
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
