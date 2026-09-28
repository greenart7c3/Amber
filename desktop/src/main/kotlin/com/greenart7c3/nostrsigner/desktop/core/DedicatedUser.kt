package com.greenart7c3.nostrsigner.desktop.core

import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * On Linux, Amber runs under a dedicated OS user instead of the login user:
 * the process that holds your keys is then walled off from the rest of the
 * desktop session by the OS itself (memory, files, and data directory).
 *
 * On first open (and whenever the setup went stale) Amber asks for the sudo
 * password in-app and, as root:
 *  1. creates the dedicated user with its own home directory,
 *  2. moves the invoking user's Amber data into that home (once),
 *  3. installs a root-owned launcher that runs the Amber binary as the
 *     dedicated user with only the session socket locations as arguments,
 *  4. installs a narrow sudoers rule (this user -> launcher, NOPASSWD) so
 *     later launches switch to the dedicated user without asking again,
 *  5. re-executes Amber under the dedicated user.
 *
 * The launcher never accepts arbitrary arguments or environment: it execs
 * exactly the Amber binary it was generated for, so the sudoers rule cannot
 * be reused to run anything else as the dedicated user.
 *
 * Escapes: `AMBER_DISABLE_DEDICATED_USER=1` skips the whole flow, and
 * `AMBER_USER=name` picks a different OS user name. Bare `java` launches
 * (`./gradlew :desktop:run`) are skipped — use the packaged image.
 */
object DedicatedUser {
    private const val ENV_DISABLE = "AMBER_DISABLE_DEDICATED_USER"
    private const val ENV_USER = "AMBER_USER"

    /** `$` for building shell scripts inside Kotlin templates. */
    private const val D = "$"

    private fun wrapperPath(name: String) = "/usr/local/bin/amber-runas-$name"
    private fun sudoersPath(name: String) = "/etc/sudoers.d/amber-runas-$name"

    val isLinux: Boolean = System.getProperty("os.name").lowercase().let {
        it.contains("linux") || it.contains("nix") || it.contains("nux")
    }

    /** Name of the OS user that runs Amber. */
    val userName: String get() = System.getenv(ENV_USER) ?: "amber"

    sealed interface State {
        /** Already running as the dedicated user, or the feature does not apply. */
        data object Active : State

        /** Needs (re-)setup; [stale] is true when only the launcher is outdated. */
        data class SetupNeeded(val stale: Boolean) : State

        /** A working launcher exists: switch without asking for a password. */
        data class Ready(val command: List<String>) : State
    }

    /** Inspects this process and returns what to do before starting the app. */
    fun detect(): State {
        if (!isLinux || System.getenv(ENV_DISABLE) == "1") return State.Active
        if (currentUserName() == userName) return State.Active
        val exe = currentExecutable() ?: return State.Active
        if (File(exe).name == "java") return State.Active // dev run under Gradle

        val wrapper = File(wrapperPath(userName))
        val userOk = userExists(userName)
        val launcherOk = try {
            wrapper.isFile && wrapper.canRead() && wrapper.readText().contains(exe)
        } catch (_: Exception) {
            false
        }
        return when {
            userOk && launcherOk -> State.Ready(relaunchCommand(wrapper.absolutePath))
            else -> State.SetupNeeded(stale = userOk)
        }
    }

    /**
     * Runs the sudo setup with the given password (fed to `sudo -S`) and
     * returns true when the dedicated user, launcher, and sudoers rule are
     * all in place.
     */
    fun setup(password: CharArray): Boolean {
        val session = sessionArgs()
        return try {
            val script = rootSetupScript(
                name = userName,
                currentUserName = currentUserName(),
                exePath = currentExecutable() ?: return false,
                wrapperPath = wrapperPath(userName),
                sudoersPath = sudoersPath(userName),
                dataDir = AppDirs.dataDir.absolutePath,
                amberDataDir = amberDataDir(userName),
                xdgRuntimeDir = session[0],
                waylandDisplay = session[1],
                x11Socket = x11SocketPath(session[2], session[0]),
            )
            val process = ProcessBuilder("sudo", "-k", "-S", "-p", "", "bash", "-s")
                .redirectErrorStream(true)
                .start()
            process.outputStream.use { out ->
                out.write(String(password).toByteArray(Charsets.UTF_8))
                out.write('\n'.code)
                out.write(script.toByteArray(Charsets.UTF_8))
            }
            val output = process.inputStream.readBytes().toString(Charsets.UTF_8)
            val ok = process.waitFor() == 0
            if (!ok) AmberLogger.d("DedicatedUser", "Setup failed: ${output.take(500)}")
            ok
        } catch (e: Exception) {
            AmberLogger.d("DedicatedUser", "Setup failed: ${e.message}")
            false
        } finally {
            password.fill('\u0000')
        }
    }

    /**
     * Spawns [command] (the launcher as the dedicated user) and reports
     * whether the child looks alive — the caller then exits this process.
     */
    fun relaunch(command: List<String>): Boolean = try {
        val child = ProcessBuilder(command).start()
        Thread.sleep(400)
        child.isAlive
    } catch (e: Exception) {
        AmberLogger.d("DedicatedUser", "Relaunch failed: ${e.message}")
        false
    }

    /** The command to switch to the dedicated user for the current session. */
    fun relaunchCommand(wrapperPath: String): List<String> = listOf("sudo", "-n", "-u", userName, wrapperPath) + sessionArgs()

    // ----- pure generators (unit-tested) -----

    fun isValidUserName(name: String): Boolean = Regex("[a-z_][a-z0-9_-]{0,31}").matches(name)

    fun wrapperScript(exePath: String, home: String, name: String): String = """
        #!/bin/sh
        # Generated by Amber; do not edit.
        # Runs the Amber desktop app as its dedicated user. Only the session
        # socket locations are passed as arguments — never arbitrary code or env.
        set -e
        XDG_RUNTIME_DIR='${D}1'
        WAYLAND_DISPLAY='${D}2'
        DISPLAY='${D}3'
        DBUS_SESSION_BUS_ADDRESS='${D}4'
        HOME='$home'
        USER='$name'
        LOGNAME='$name'
        export XDG_RUNTIME_DIR WAYLAND_DISPLAY DISPLAY DBUS_SESSION_BUS_ADDRESS HOME USER LOGNAME
        exec '$exePath'
    """.trimIndent() + "\n"

    fun sudoersRule(invoker: String, name: String, wrapperPath: String): String = "$invoker ALL=($name) NOPASSWD: $wrapperPath\n"

    /**
     * The root script piped to `sudo bash -s`. Every interpolated value is a
     * system path or a validated user name; the launcher and sudoers payloads
     * are written via quoted heredocs so nothing inside them is expanded.
     */
    fun rootSetupScript(
        name: String,
        currentUserName: String,
        exePath: String,
        wrapperPath: String,
        sudoersPath: String,
        dataDir: String,
        amberDataDir: String,
        xdgRuntimeDir: String,
        waylandDisplay: String,
        x11Socket: String?,
    ): String {
        require(isValidUserName(name)) { "Invalid user name: $name" }
        val sudoersTmp = "$sudoersPath.tmp"
        val launcher = wrapperScript(exePath = exePath, home = homeOf(name), name = name)
        val rule = sudoersRule(currentUserName, name, wrapperPath)
        // Payloads are inserted AFTER trimIndent: interpolating them into the
        // template would reset the common indentation to zero (their lines are
        // flush-left), leaving the heredoc terminators indented and the
        // heredocs swallowing the rest of the script.
        return """
            set -e
            if ! id -u '$name' >/dev/null 2>&1; then
                nologin_bin="$D(command -v nologin || echo /bin/false)"
                useradd --create-home --shell "${D}nologin_bin" '$name'
            fi
            if [ -d '$dataDir' ] && [ ! -e '$amberDataDir' ]; then
                mkdir -p "$D(dirname '$amberDataDir')"
                cp -a '$dataDir' '$amberDataDir'
                # 'user:' sets the owner and defaults the group to the user's
                # login group (avoids nested quoting around a command
                # substitution here).
                chown -R "$name:" '$amberDataDir'
            fi
            cat > '$wrapperPath' <<'WRAPPER_EOF'
            AMBER_WRAPPER_PAYLOAD
            WRAPPER_EOF
            chown root:root '$wrapperPath'
            chmod 0755 '$wrapperPath'
            cat > '$sudoersTmp' <<'SUDOERS_EOF'
            AMBER_SUDOERS_PAYLOAD
            SUDOERS_EOF
            chown root:root '$sudoersTmp'
            chmod 0440 '$sudoersTmp'
            if command -v visudo >/dev/null 2>&1; then
                visudo -cf '$sudoersTmp' >/dev/null
            fi
            mv '$sudoersTmp' '$sudoersPath'
            # Let the dedicated user reach this session's sockets: X11/XWayland,
            # Wayland, and D-Bus (tray + notifications).
            if command -v setfacl >/dev/null 2>&1; then
                setfacl -m 'u:$name:rx' '$xdgRuntimeDir' || true
                [ -S '$xdgRuntimeDir/$waylandDisplay' ] && setfacl -m 'u:$name:rw' '$xdgRuntimeDir/$waylandDisplay' || true
                [ -n '${x11Socket ?: ""}' ] && [ -S '$x11Socket' ] && setfacl -m 'u:$name:rw' '$x11Socket' || true
                [ -S '$xdgRuntimeDir/bus' ] && setfacl -m 'u:$name:rw' '$xdgRuntimeDir/bus' || true
            fi
        """.trimIndent()
            .replace("AMBER_WRAPPER_PAYLOAD\n", launcher + "\n")
            .replace("AMBER_SUDOERS_PAYLOAD\n", rule) + "\n"
    }

    // ----- environment helpers -----

    internal fun amberDataDir(name: String): String = Path.of(homeOf(name), ".local", "share", "amber").toString()

    internal fun x11SocketPath(display: String?, xdgRuntimeDir: String): String? {
        if (display.isNullOrBlank() || !display.startsWith(":")) return null
        val number = display.drop(1).substringBefore('.')
        return "$xdgRuntimeDir/.X11-unix/X$number"
    }

    private fun currentUserName(): String = System.getProperty("user.name")

    private fun currentExecutable(): String? = try {
        Files.readSymbolicLink(Path.of("/proc/self/exe")).toString()
    } catch (_: Exception) {
        null
    }

    private fun userExists(name: String): Boolean = try {
        ProcessBuilder("getent", "passwd", name).start().waitFor() == 0
    } catch (_: Exception) {
        false
    }

    private fun homeOf(name: String): String = "/home/$name"

    internal fun sessionArgs(): List<String> = listOf(
        System.getenv("XDG_RUNTIME_DIR") ?: "/run/user/${uid()}",
        System.getenv("WAYLAND_DISPLAY") ?: "",
        System.getenv("DISPLAY") ?: "",
        System.getenv("DBUS_SESSION_BUS_ADDRESS") ?: "",
    )

    private fun uid(): String = ProcessHandle.current().let { _ ->
        try {
            val lines = java.nio.file.Files.readAllLines(Path.of("/proc/self/status"))
            lines.firstOrNull { it.startsWith("Uid:") }?.split(Regex("\\s+"))?.get(1) ?: "1000"
        } catch (_: Exception) {
            "1000"
        }
    }
}
