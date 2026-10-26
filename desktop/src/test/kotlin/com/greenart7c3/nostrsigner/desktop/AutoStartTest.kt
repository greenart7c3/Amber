package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.desktop.core.AutoStart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoStartTest {
    @Test
    fun unitIsHardenedButJvmCompatible() {
        val unit = AutoStart.unitContent("/opt/Amber/bin/Amber")
        assertTrue(unit.startsWith("[Unit]"))
        assertTrue(unit.contains("ExecStart=/opt/Amber/bin/Amber"))
        assertTrue(unit.contains("PartOf=graphical-session.target"))
        assertTrue(unit.contains("WantedBy=graphical-session.target"))
        assertTrue(unit.contains("Restart=on-failure"))
        assertTrue(unit.contains("NoNewPrivileges=yes"))
        assertTrue(unit.contains("ProtectSystem=strict"))
        assertTrue(unit.contains("ProtectHome=read-only"))
        assertTrue(unit.contains("SystemCallFilter=@system-service"))
        assertTrue(unit.contains("LimitCORE=0"))
        assertTrue(unit.contains("ReadWritePaths=-%h/.local/share/amber -%h/.local/share/applications %t"))
        assertTrue(unit.contains("BindPaths=-/tmp/.X11-unix"))
        // The JVM's JIT needs writable executable memory: this hardening flag
        // would kill the service instantly and must never be emitted.
        assertFalse(unit.contains("MemoryDenyWriteExecute=yes"))
    }

    @Test
    fun appImageUnitExtractsIntoItsCacheAndKeepsTheSandbox() {
        val unit = AutoStart.unitContent("/home/u/Apps/Amber.AppImage", "/home/u/.cache/amber/appimage")
        assertTrue(unit.contains("ExecStart=/home/u/Apps/Amber.AppImage"))
        assertTrue(unit.contains("Environment=APPIMAGE_EXTRACT_AND_RUN=1 NO_CLEANUP=1 TMPDIR=/home/u/.cache/amber/appimage\n"))
        assertTrue(unit.contains("ReadWritePaths=/home/u/.cache/amber/appimage\n"))
        // The extraction replaces the FUSE mount precisely so none of the
        // hardening has to be relaxed for it.
        val plain = AutoStart.unitContent("/home/u/Apps/Amber.AppImage")
        plain.lines().filter { it.isNotBlank() }.forEach { assertTrue("missing: $it", unit.lines().contains(it)) }
    }

    @Test
    fun plainUnitHasNoAppImageSettings() {
        assertFalse(AutoStart.unitContent("/opt/Amber/bin/Amber").contains("APPIMAGE_EXTRACT_AND_RUN"))
    }

    @Test
    fun unitQuotesExecStartWithSpaces() {
        val unit = AutoStart.unitContent("/opt/My Apps/Amber/bin/Amber")
        assertTrue(unit.contains("ExecStart=\"/opt/My Apps/Amber/bin/Amber\""))
    }

    @Test
    fun unitEscapesSystemdSpecialsInPaths() {
        // systemd(5): inside double quotes \ and " are escaped with a
        // backslash; $ would start variable expansion and % a specifier.
        val unit = AutoStart.unitContent($$"/opt/My \"Apps\"/Am\\ber$1/bin/Amber")
        assertTrue(unit.contains($$"ExecStart=\"/opt/My \\\"Apps\\\"/Am\\\\ber\\$1/bin/Amber\""))
        // Even unquoted values must not leak a specifier.
        val percent = AutoStart.unitContent("/opt/Amber%h/bin/Amber")
        assertTrue(percent.contains("ExecStart=/opt/Amber%%h/bin/Amber"))
    }

    @Test
    fun pathsWithControlCharactersAreRejected() {
        assertFalse(AutoStart.isUnitSafe("/opt/Amber/bin/Amber\nevil"))
        assertFalse(AutoStart.isUnitSafe("/opt/Amber/bin/Amber", "/cache/\u0000"))
        assertTrue(AutoStart.isUnitSafe("/opt/Amber/bin/Amber", "/home/u/.cache/amber/appimage"))
    }

    @Test
    fun windowsRunCommandQuotesPath() {
        // The Run value is parsed as a command line: an unquoted
        // "C:\Program Files\..." would try to launch "C:\Program".
        assertEquals("\"C:\\Program Files\\Amber\\Amber.exe\"", AutoStart.windowsRunCommand("C:\\Program Files\\Amber\\Amber.exe"))
    }

    @Test
    fun launchAgentRunsBundleLauncherAtLogin() {
        val plist = AutoStart.launchAgentContent("/Applications/Amber.app/Contents/MacOS/Amber")
        assertTrue(plist.startsWith("<?xml"))
        assertTrue(plist.contains("<string>com.greenart7c3.nostrsigner</string>"))
        assertTrue(plist.contains("<string>/Applications/Amber.app/Contents/MacOS/Amber</string>"))
        assertTrue(plist.contains("<key>RunAtLoad</key>\n    <true/>"))
        assertTrue(plist.contains("<string>Aqua</string>"))
        // Quitting Amber must stick: launchd must not relaunch it.
        assertFalse(plist.contains("KeepAlive"))
    }

    @Test
    fun launchAgentEscapesXmlInPath() {
        val plist = AutoStart.launchAgentContent("/Users/a&b/Apps/<Amber>.app/Contents/MacOS/Amber")
        assertTrue(plist.contains("<string>/Users/a&amp;b/Apps/&lt;Amber&gt;.app/Contents/MacOS/Amber</string>"))
    }
}
