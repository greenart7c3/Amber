package com.greenart7c3.nostrsigner.desktop.core

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import java.io.File

/**
 * Optional start-on-boot. Amber always comes up locked — the passphrase is
 * still required before anything signs.
 *
 * - Windows: a per-user `HKCU\Software\Microsoft\Windows\CurrentVersion\Run`
 *   entry pointing at the installed `Amber.exe` (no admin rights needed; shows
 *   up under Task Manager → Startup apps, where the user can also disable it).
 * - Linux: installs and enables a hardened systemd user unit (Opal-style)
 *   that starts Amber with the desktop session.
 *
 * Hardening mirrors Opal's unit with one deliberate exception: no
 * MemoryDenyWriteExecute, which the JVM cannot survive (the JIT needs
 * writable executable memory). The unit needs write access to: the data dir,
 * the runtime dir (nostrconnect socket) and the applications dir (scheme
 * handler registration).
 *
 * Only packaged runs can be supervised: a dev run's command line is a bare
 * gradle invocation with no stable binary ([isSupported] gates the UI).
 */
object AutoStart {
    private const val UNIT_NAME = "amber.service"
    private const val RUN_KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val RUN_VALUE = "Amber"

    val isLinux: Boolean = System.getProperty("os.name").lowercase().let {
        it.contains("linux") || it.contains("nix") || it.contains("nux")
    }

    val isWindows: Boolean = System.getProperty("os.name").lowercase().contains("win")

    /** Set by the jpackage launcher to the installed binary; absent in dev (gradle) runs. */
    private fun packagedExecutable(): String? = System.getProperty("jpackage.app-path")?.takeIf { it.isNotBlank() }

    private fun currentExecutable(): String? = runCatching {
        String(java.nio.file.Files.readAllBytes(java.nio.file.Path.of("/proc/self/cmdline")), Charsets.UTF_8)
            .split('\u0000')
            .firstOrNull { it.isNotBlank() }
    }.getOrNull()

    /** True when the current launch has a stable binary the OS can start at login. */
    fun isSupported(): Boolean = when {
        isWindows -> packagedExecutable() != null
        isLinux -> currentExecutable()?.let { File(it).name != "java" } ?: false
        else -> false
    }

    private fun unitDir(): File = File(System.getProperty("user.home"), ".config/systemd/user")

    private fun unitFile(): File = File(unitDir(), UNIT_NAME)

    /**
     * Installs (or refreshes) the unit and enables it, then starts the
     * service. Starting while a manual instance is already running is
     * harmless: that instance forwards a raise to it and exits cleanly.
     */
    fun setEnabled(enabled: Boolean) {
        if (!isSupported()) return
        if (isWindows) {
            setWindowsRunEntry(enabled)
            return
        }
        runCatching {
            // systemd requires an absolute ExecStart: /proc/self/cmdline
            // records the path exactly as invoked (it can be relative).
            val exe = currentExecutable()
                ?.let { File(it).canonicalFile.path }
                ?: return
            unitDir().mkdirs()
            unitFile().writeText(unitContent(exe))
            systemctl("daemon-reload")
            if (enabled) {
                systemctl("enable", UNIT_NAME)
                systemctl("start", UNIT_NAME)
            } else {
                // No --now on purpose: disabling autostart must not kill an
                // app the user is currently using.
                systemctl("disable", UNIT_NAME)
            }
        }
    }

    /**
     * Writes (or removes) the Run entry. Like the systemd path, enabling never
     * launches a second instance and disabling never kills the running one.
     */
    private fun setWindowsRunEntry(enabled: Boolean) {
        runCatching {
            if (enabled) {
                val exe = packagedExecutable()?.let { File(it).canonicalFile.path } ?: return
                Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, RUN_KEY, RUN_VALUE, windowsRunCommand(exe))
            } else if (Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, RUN_KEY, RUN_VALUE)) {
                Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, RUN_KEY, RUN_VALUE)
            }
        }.onFailure { AmberLogger.e("AutoStart", "Failed to update the Windows Run entry", it) }
    }

    /** The Run value is a command line: always quote the path (e.g. `C:\Program Files\...`). */
    internal fun windowsRunCommand(exePath: String): String = "\"$exePath\""

    private fun systemctl(vararg args: String): Boolean = runCatching {
        ProcessBuilder("systemctl", "--user", *args).start().waitFor() == 0
    }.getOrDefault(false)

    private fun quote(value: String): String = if (value.none { it == ' ' || it == '\t' }) value else "\"$value\""

    internal fun unitContent(exePath: String): String = """
        [Unit]
        Description=Amber Nostr signer
        PartOf=graphical-session.target
        After=graphical-session.target

        [Service]
        Type=simple
        ExecStart=${quote(exePath)}
        Restart=on-failure
        RestartSec=3
        # JVM exception: no MemoryDenyWriteExecute — the JIT needs W+X memory.
        NoNewPrivileges=yes
        PrivateTmp=yes
        # PrivateTmp hides /tmp, and with it the XWayland socket — bind the
        # real X11 socket dir back in or AWT cannot reach the display.
        BindPaths=-/tmp/.X11-unix
        ProtectSystem=strict
        ProtectHome=read-only
        ReadWritePaths=-%h/.local/share/amber -%h/.local/share/applications %t
        ProtectKernelTunables=yes
        ProtectKernelModules=yes
        ProtectControlGroups=yes
        RestrictRealtime=yes
        RestrictSUIDSGID=yes
        LockPersonality=yes
        SystemCallArchitectures=native
        SystemCallFilter=@system-service
        SystemCallErrorNumber=EPERM
        RestrictAddressFamilies=AF_UNIX AF_INET AF_INET6 AF_NETLINK
        RestrictNamespaces=yes
        ProtectClock=yes
        ProtectHostname=yes
        ProtectKernelLogs=yes
        # Decrypted keys live in this process: never write core dumps.
        LimitCORE=0
        UMask=0077

        [Install]
        WantedBy=graphical-session.target
    """.trimIndent() + "\n"
}
