package com.greenart7c3.nostrsigner.desktop.core

import java.io.File
import java.io.OutputStream
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions

/**
 * Resolves the per-user application data directory following each
 * platform's conventions:
 * - Windows: %APPDATA%\Amber
 * - macOS: ~/Library/Application Support/Amber
 * - Linux: $XDG_DATA_HOME/amber (or ~/.local/share/amber)
 *
 * Debug runs use "Amber Debug" / "amber-debug" instead (see [BuildVariant]).
 */
object AppDirs {
    val dataDir: File by lazy {
        val os = System.getProperty("os.name").lowercase()
        val home = System.getProperty("user.home")
        val dir = when {
            os.contains("win") -> File(System.getenv("APPDATA") ?: "$home\\AppData\\Roaming", BuildVariant.appName)
            os.contains("mac") -> File(home, "Library/Application Support/${BuildVariant.appName}")
            else -> File(System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() } ?: "$home/.local/share", "amber${BuildVariant.suffix}")
        }
        dir.mkdirs()
        restrictToOwner(dir)
        dir
    }

    fun accountDir(npub: String): File = File(dataDir, npub).apply {
        mkdirs()
        // mkdirs() honors the umask (typically 0755); the account's apps,
        // permissions and history live here, so keep it owner-only. Also
        // tightens directories created by older versions.
        restrictToOwner(this)
    }

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

    /**
     * Opens [file] for writing, creating it with owner-only permissions
     * BEFORE any content is written, so secrets never exist on disk even
     * briefly readable by other users. POSIX only; on Windows the user
     * profile ACLs already scope access, so the plain write is kept.
     */
    fun privateOutput(file: File): OutputStream {
        if (System.getProperty("os.name").lowercase().contains("win")) {
            return file.outputStream()
        }
        val ownerOnly = PosixFilePermissions.asFileAttribute(
            setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
        )
        try {
            Files.createFile(file.toPath(), ownerOnly)
        } catch (_: FileAlreadyExistsException) {
            // Overwrite in place; the existing file was created under this policy.
        }
        return Files.newOutputStream(
            file.toPath(),
            StandardOpenOption.WRITE,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
        )
    }
}
