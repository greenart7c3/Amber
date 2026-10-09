package com.greenart7c3.nostrsigner.desktop.core

import java.awt.Desktop
import java.io.File
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Makes `nostrconnect://` links open Amber (desktop).
 *
 * - Linux: the OS handler (see [registerSchemeHandler]) launches the Amber
 *   binary with the URI as an argument.
 * - macOS: the scheme is declared in the app bundle's Info.plist and
 *   LaunchServices delivers the URI as an Apple event, to the running app if
 *   there is one (see [installMacOpenUriHandler]).
 * - A file lock makes the app single-instance: a second launch forwards the
 *   URI over a private unix socket in the runtime dir to the running
 *   instance and exits.
 * - The running instance listens on that socket and exposes forwarded URIs
 *   as [pending]; the UI processes each one through `addNostrConnect` once
 *   an account is unlocked (a link clicked while locked waits for unlock).
 */
object UriLaunch {
    private const val PREFIX = "nostrconnect://"
    private val SOCKET_NAME = "amber${BuildVariant.suffix}-nostrconnect.sock"
    private val HANDLER_DESKTOP_FILE = "amber${BuildVariant.suffix}-nostrconnect.desktop"

    val isLinux: Boolean = System.getProperty("os.name").lowercase().let {
        it.contains("linux") || it.contains("nix") || it.contains("nux")
    }

    val isMac: Boolean = System.getProperty("os.name").lowercase().contains("mac")

    private var lockFile: FileChannel? = null
    private var instanceLock: java.nio.channels.FileLock? = null

    /** Latest nostrconnect:// URI this process received but has not handled yet. */
    val pending = MutableStateFlow<String?>(null)

    /** First nostrconnect:// URI among the launch arguments, if any. */
    fun extract(args: Array<String>): String? = args.firstOrNull { it.startsWith(PREFIX) }

    private val FORWARD_FILE_NAME = "amber${BuildVariant.suffix}-nostrconnect.forward"

    /**
     * URI dropped by the dev wrapper for this launch (see
     * [handlerExecutable]). Read-and-delete: empty when absent or invalid.
     */
    fun readForwardedUri(): String? = runCatching {
        val file = Path.of(xdgRuntimeDir(), FORWARD_FILE_NAME).toFile()
        val uri = if (file.exists()) file.readText().trim() else ""
        if (file.exists()) file.delete()
        if (uri.startsWith(PREFIX)) uri else null
    }.getOrNull()

    private fun xdgRuntimeDir(): String = System.getenv("XDG_RUNTIME_DIR")?.takeIf { it.isNotBlank() }
        ?: AppDirs.dataDir.absolutePath

    /**
     * Takes the single-instance lock. False means another instance is
     * running (the OS releases the lock automatically if that instance dies).
     */
    fun tryAcquireSingleInstance(): Boolean {
        AppDirs.dataDir.mkdirs()
        return runCatching {
            val channel = FileChannel.open(
                Path.of(AppDirs.dataDir.absolutePath, "amber.lock"),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
            )
            AppDirs.restrictToOwner(File(AppDirs.dataDir, "amber.lock"))
            val lock = channel.tryLock()
            if (lock == null) {
                runCatching { channel.close() }
                false
            } else {
                lockFile = channel
                instanceLock = lock
                true
            }
        }.getOrDefault(false)
    }

    /**
     * Receives `nostrconnect://` links on macOS. LaunchServices routes a
     * clicked link to the running Amber (starting it if needed) as an Apple
     * event; AWT queues that event until a handler is installed, so calling
     * this before Compose starts also catches the link that launched the app.
     * Call it only in the primary instance (see main).
     */
    fun installMacOpenUriHandler() {
        if (!isMac) return
        runCatching {
            if (!Desktop.isDesktopSupported()) return
            val desktop = Desktop.getDesktop()
            if (!desktop.isSupported(Desktop.Action.APP_OPEN_URI)) return
            desktop.setOpenURIHandler { event ->
                val uri = event.uri.toString()
                if (uri.startsWith(PREFIX)) pending.value = uri
            }
        }.onFailure {
            AmberLogger.d("UriLaunch", "Could not install the macOS URI handler: ${it.message}")
        }
    }

    /** Hands [uri] to the running instance over the unix socket. */
    fun forwardToRunningInstance(uri: String): Boolean = runCatching {
        SocketChannel.open(StandardProtocolFamily.UNIX).use { channel ->
            channel.connect(UnixDomainSocketAddress.of(socketPath()))
            channel.write(ByteBuffer.wrap(uri.toByteArray(Charsets.UTF_8)))
            true
        }
    }.getOrDefault(false)

    /**
     * Accept loop for URIs forwarded by second launches. Removes a stale
     * socket file first (safe: the caller holds the single-instance lock).
     */
    fun startIpcServer() {
        if (!isLinux) return
        Thread {
            runCatching {
                val path = socketPath()
                Files.deleteIfExists(path)
                ServerSocketChannel.open(StandardProtocolFamily.UNIX).use { server ->
                    server.bind(UnixDomainSocketAddress.of(path))
                    while (true) {
                        val client = server.accept() ?: continue
                        readUri(client)
                        runCatching { client.close() }
                    }
                }
            }
        }.apply {
            isDaemon = true
            name = "amber-uri-ipc"
        }.start()
    }

    private fun readUri(client: SocketChannel) {
        val buffer = ByteBuffer.allocate(64 * 1024)
        while (runCatching { client.read(buffer) }.getOrDefault(-1) > 0) {
            if (buffer.position() >= buffer.capacity()) break
        }
        val uri = String(buffer.array(), 0, buffer.position(), Charsets.UTF_8).trim()
        if (uri.startsWith(PREFIX)) pending.value = uri
    }

    /**
     * Registers this binary as the `nostrconnect://` handler for the current
     * user (no root): a private desktop entry plus the xdg default. Best-
     * effort and idempotent — re-run on every launch so the path stays fresh.
     *
     * For a packaged image the handler is the image binary. For a dev run
     * (`./gradlew :desktop:run`, bare `java` on the command line) there is no
     * stable binary, so a small wrapper script is generated instead: it
     * always runs the current code of the project the JVM was started from.
     *
     * The xdg default is global, so a debug run would take links away from a
     * release install; it only registers when asked to
     * (`./gradlew :desktop:run -PdesktopUriHandler`).
     */
    fun registerSchemeHandler() {
        if (!isLinux) return
        if (BuildVariant.isDebug && !System.getProperty("amber.uriHandler").toBoolean()) return
        runCatching {
            val exe = handlerExecutable() ?: return
            val appsDir = xdgAppsDir().apply { mkdirs() }
            val entry = File(appsDir, HANDLER_DESKTOP_FILE)
            entry.writeText(desktopEntry(exe))
            ProcessBuilder("xdg-mime", "default", HANDLER_DESKTOP_FILE, "x-scheme-handler/nostrconnect")
                .start()
                .waitFor()
            runCatching {
                ProcessBuilder("update-desktop-database", appsDir.absolutePath).start().waitFor()
            }
            AmberLogger.d("UriLaunch", "Registered nostrconnect:// handler: $entry -> $exe")
        }.onFailure {
            AmberLogger.d("UriLaunch", "Scheme registration failed: ${it.message}")
        }
    }

    /**
     * The stable launch target for the handler: the packaged binary, or a
     * generated `~/.local/bin/amber-desktop-dev` wrapper that re-runs gradle
     * in the project the dev JVM was started from.
     *
     * The wrapper cannot pass the URI to gradle as an argument (it would be
     * parsed as a task name), so it drops it into the forward file first; the
     * second JVM picks it up via [readForwardedUri] and hands it to the
     * running instance over the socket.
     */
    internal fun handlerExecutable(): String? {
        val commandLine = currentCommandLine() ?: return null
        if (File(commandLine).name != "java") return commandLine

        val projectDir = System.getProperty("user.dir") ?: return null
        val gradlew = File(projectDir, "gradlew")
        if (!gradlew.exists()) return null
        val binDir = File(File(System.getProperty("user.home"), ".local"), "bin").apply { mkdirs() }
        val wrapper = File(binDir, "amber-desktop-dev")
        val forwardFile = Path.of(xdgRuntimeDir(), FORWARD_FILE_NAME)
        wrapper.writeText(
            "#!/bin/sh\n" +
                "# Generated by Amber; runs the desktop app from the dev project.\n" +
                "# gradle cannot take the URI as an argument, so drop it into the\n" +
                "# forward file for the second JVM to pick up.\n" +
                "[ -n \"\$1\" ] && printf '%s' \"\$1\" > " + shellQuote(forwardFile.toString()) + "\n" +
                "cd " + shellQuote(projectDir) + " && exec " + shellQuote(gradlew.absolutePath) + " --quiet :desktop:run\n",
        )
        runCatching { wrapper.setExecutable(true) }
        return wrapper.absolutePath
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    internal fun xdgAppsDir(): File {
        val dataHome = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }
            ?: File(File(System.getProperty("user.home"), ".local"), "share").absolutePath
        return File(dataHome, "applications")
    }

    /** The desktop entry pointing at [commandLine] (single binary, no args). */
    internal fun desktopEntry(exePath: String): String = """
        [Desktop Entry]
        Type=Application
        Name=${BuildVariant.appName}
        NoDisplay=true
        Exec=${quoteForDesktopEntry(exePath)} %u
        MimeType=x-scheme-handler/nostrconnect;
    """.trimIndent() + "\n"

    /** Desktop-entry Exec quoting: double quotes with backslash escapes. */
    internal fun quoteForDesktopEntry(value: String): String = if (value.none { it in " \t\"'\\" }) value else "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    /** The AppImage file rather than its per-launch mount; see AutoStart.currentExecutable. */
    private fun currentCommandLine(): String? = System.getenv("APPIMAGE")?.takeIf { it.isNotBlank() } ?: runCatching {
        String(Files.readAllBytes(Path.of("/proc/self/cmdline")), Charsets.UTF_8)
            .split('\u0000')
            .firstOrNull { it.isNotBlank() }
    }.getOrNull()

    private fun socketPath(): Path = Path.of(xdgRuntimeDir(), SOCKET_NAME)
}
