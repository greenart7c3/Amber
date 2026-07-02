package com.greenart7c3.nostrsigner.desktop.core

import java.io.File

/**
 * Resolves the per-user application data directory following each
 * platform's conventions:
 * - Windows: %APPDATA%\Amber
 * - macOS: ~/Library/Application Support/Amber
 * - Linux: $XDG_DATA_HOME/amber (or ~/.local/share/amber)
 */
object AppDirs {
    val dataDir: File by lazy {
        val os = System.getProperty("os.name").lowercase()
        val home = System.getProperty("user.home")
        val dir = when {
            os.contains("win") -> File(System.getenv("APPDATA") ?: "$home\\AppData\\Roaming", "Amber")
            os.contains("mac") -> File(home, "Library/Application Support/Amber")
            else -> File(System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() } ?: "$home/.local/share", "amber")
        }
        dir.mkdirs()
        restrictToOwner(dir)
        dir
    }

    fun accountDir(npub: String): File = File(dataDir, npub).apply { mkdirs() }

    /**
     * Best-effort restriction of a file/directory to the current user.
     * POSIX-only; on Windows the user profile ACLs already scope access.
     */
    fun restrictToOwner(file: File) {
        file.setReadable(false, false)
        file.setWritable(false, false)
        file.setExecutable(false, false)
        file.setReadable(true, true)
        file.setWritable(true, true)
        if (file.isDirectory) {
            file.setExecutable(true, true)
        }
    }
}
