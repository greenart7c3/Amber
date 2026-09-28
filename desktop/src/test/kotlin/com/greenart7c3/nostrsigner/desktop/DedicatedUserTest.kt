package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.desktop.core.DedicatedUser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the pure generators behind the dedicated-user setup: the generated
 * launcher must exec exactly one fixed binary (no argument pass-through, so
 * the sudoers rule cannot be reused to run anything else), and the root
 * script must create the user, migrate data, and validate the sudoers file.
 */
class DedicatedUserTest {
    private val dollar = "$"

    @Test
    fun userNameValidation() {
        assertTrue(DedicatedUser.isValidUserName("amber"))
        assertTrue(DedicatedUser.isValidUserName("am-ber_1"))
        assertFalse(DedicatedUser.isValidUserName(""))
        assertFalse(DedicatedUser.isValidUserName("Amber"))
        assertFalse(DedicatedUser.isValidUserName("9amber"))
        assertFalse(DedicatedUser.isValidUserName("amber;rm -rf /"))
        assertFalse(DedicatedUser.isValidUserName("a".repeat(33)))
    }

    @Test
    fun launcherExecsExactlyOneFixedBinary() {
        val script = DedicatedUser.wrapperScript(
            exePath = "/opt/Amber/bin/Amber",
            home = "/home/amber",
            name = "amber",
        )
        assertTrue(script.startsWith("#!/bin/sh"))
        assertTrue(script.contains("exec '/opt/Amber/bin/Amber'\n"))
        assertTrue(script.contains("HOME='/home/amber'"))
        assertTrue(script.contains("USER='amber'"))
        // Session socket locations arrive as positional arguments...
        assertTrue(script.contains("XDG_RUNTIME_DIR='" + dollar + "1'"))
        assertTrue(script.contains("DBUS_SESSION_BUS_ADDRESS='" + dollar + "4'"))
        // ...and nothing else is forwarded: no argument or env pass-through.
        assertFalse(script.contains("\"" + dollar + "@\""))
        assertFalse(script.contains("JDK_JAVA_OPTIONS"))
    }

    @Test
    fun sudoersRuleIsNarrow() {
        val rule = DedicatedUser.sudoersRule(
            invoker = "admin",
            name = "amber",
            wrapperPath = "/usr/local/bin/amber-runas-amber",
        )
        assertEquals("admin ALL=(amber) NOPASSWD: /usr/local/bin/amber-runas-amber\n", rule)
    }

    @Test
    fun rootScriptCreatesUserMigratesDataAndValidates() {
        val script = DedicatedUser.rootSetupScript(
            name = "amber",
            currentUserName = "admin",
            exePath = "/opt/Amber/bin/Amber",
            wrapperPath = "/usr/local/bin/amber-runas-amber",
            sudoersPath = "/etc/sudoers.d/amber-runas-amber",
            dataDir = "/home/admin/.local/share/amber",
            amberDataDir = "/home/amber/.local/share/amber",
            xdgRuntimeDir = "/run/user/1000",
            waylandDisplay = "wayland-1",
            x11Socket = "/run/user/1000/.X11-unix/X0",
        )
        // User creation is guarded and idempotent.
        assertTrue(script.contains("if ! id -u 'amber' >/dev/null 2>&1; then"))
        assertTrue(script.contains("useradd --create-home"))
        // Existing data of the invoking user is migrated once, with ownership.
        assertTrue(script.contains("if [ -d '/home/admin/.local/share/amber' ] && [ ! -e '/home/amber/.local/share/amber' ]; then"))
        assertTrue(script.contains("cp -a '/home/admin/.local/share/amber' '/home/amber/.local/share/amber'"))
        assertTrue(script.contains("chown -R \"amber:\" '/home/amber/.local/share/amber'"))
        // Launcher + sudoers payloads go in via quoted heredocs (no expansion).
        assertTrue(script.contains("cat > '/usr/local/bin/amber-runas-amber' <<'WRAPPER_EOF'"))
        assertTrue(script.contains("<<'SUDOERS_EOF'"))
        assertTrue(script.contains("admin ALL=(amber) NOPASSWD: /usr/local/bin/amber-runas-amber\n"))
        // The sudoers file is syntax-checked before it is installed.
        assertTrue(script.contains("visudo -cf"))
        assertTrue(script.contains("chmod 0440"))
        // Heredoc terminators must be flush-left: an indented terminator makes
        // the heredoc swallow the rest of the script.
        val lines = script.lines()
        assertTrue(lines.contains("WRAPPER_EOF"))
        assertTrue(lines.contains("SUDOERS_EOF"))
        // The launcher is embedded verbatim inside the first heredoc.
        assertTrue(lines.contains("exec '/opt/Amber/bin/Amber'"))
        assertTrue(lines.indexOf("exec '/opt/Amber/bin/Amber'") < lines.indexOf("WRAPPER_EOF"))
        // Session sockets (Wayland, X11, D-Bus) are granted via ACLs.
        assertTrue(script.contains("setfacl -m 'u:amber:rw' '/run/user/1000/wayland-1'"))
        assertTrue(script.contains("setfacl -m 'u:amber:rw' '/run/user/1000/.X11-unix/X0'"))
        assertTrue(script.contains("setfacl -m 'u:amber:rw' '/run/user/1000/bus'"))
    }

    @Test
    fun rootScriptRejectsInvalidUserName() {
        assertThrows(IllegalArgumentException::class.java) {
            DedicatedUser.rootSetupScript(
                name = "amber; rm -rf /",
                currentUserName = "admin",
                exePath = "/opt/Amber/bin/Amber",
                wrapperPath = "/usr/local/bin/amber-runas-amber",
                sudoersPath = "/etc/sudoers.d/amber-runas-amber",
                dataDir = "/home/admin/.local/share/amber",
                amberDataDir = "/home/amber/.local/share/amber",
                xdgRuntimeDir = "/run/user/1000",
                waylandDisplay = "wayland-1",
                x11Socket = null,
            )
        }
    }

    @Test
    fun x11SocketPathDerivation() {
        assertEquals("/run/user/1000/.X11-unix/X0", DedicatedUser.x11SocketPath(":0", "/run/user/1000"))
        assertEquals("/run/user/1000/.X11-unix/X1", DedicatedUser.x11SocketPath(":1.0", "/run/user/1000"))
        assertNull(DedicatedUser.x11SocketPath(null, "/run/user/1000"))
        assertNull(DedicatedUser.x11SocketPath("", "/run/user/1000"))
        assertNull(DedicatedUser.x11SocketPath("wayland-1", "/run/user/1000"))
    }

    @Test
    fun amberDataDirLivesInTheDedicatedHome() {
        assertEquals("/home/amber/.local/share/amber", DedicatedUser.amberDataDir("amber"))
    }

    @Test
    fun relaunchCommandSwitchesToTheDedicatedUser() {
        val command = DedicatedUser.relaunchCommand("/usr/local/bin/amber-runas-amber")
        assertEquals("sudo", command[0])
        assertEquals("-n", command[1])
        assertEquals("-u", command[2])
        assertEquals(DedicatedUser.userName, command[3])
        assertEquals("/usr/local/bin/amber-runas-amber", command[4])
        // Followed by the four session socket arguments.
        assertEquals(9, command.size)
    }
}
