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
    fun unitQuotesExecStartWithSpaces() {
        val unit = AutoStart.unitContent("/opt/My Apps/Amber/bin/Amber")
        assertTrue(unit.contains("ExecStart=\"/opt/My Apps/Amber/bin/Amber\""))
    }

    @Test
    fun windowsRunCommandQuotesPath() {
        // The Run value is parsed as a command line: an unquoted
        // "C:\Program Files\..." would try to launch "C:\Program".
        assertEquals("\"C:\\Program Files\\Amber\\Amber.exe\"", AutoStart.windowsRunCommand("C:\\Program Files\\Amber\\Amber.exe"))
    }
}
