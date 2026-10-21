package com.greenart7c3.nostrsigner.desktop.core

import java.io.File
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
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

    /**
     * Replaces [file] with [bytes] so that a crash or power loss leaves
     * either the old or the new content, never a torn mix: the payload goes
     * to an owner-only temp file, is flushed to disk, and is then renamed
     * over the target (an atomic replace on POSIX and on Windows, where
     * `MoveFileEx` honors ATOMIC_MOVE). The directory entry is synced too, so
     * the rename itself survives a power loss on Linux.
     */
    fun writeAtomically(file: File, bytes: ByteArray) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.delete()
        openPrivateChannel(tmp).use { channel ->
            val buffer = ByteBuffer.wrap(bytes)
            while (buffer.hasRemaining()) channel.write(buffer)
            channel.force(true)
        }
        try {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            // Filesystem without atomic rename support: best-effort replace.
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        syncDirectory(file.parentFile)
        restrictToOwner(file)
    }

    /**
     * Moves an unreadable file out of the way instead of letting the next
     * write replace it: the original bytes stay on disk for manual recovery.
     * Returns the new location, or null when the move failed.
     */
    fun quarantine(file: File): File? {
        val stamp = System.currentTimeMillis()
        val target = generateSequence(0) { it + 1 }
            .map { n -> File(file.parentFile, "${file.name}.corrupt-$stamp" + if (n == 0) "" else "-$n") }
            .first { !it.exists() }
        return runCatching { Files.move(file.toPath(), target.toPath()) }.map { target }.getOrNull()
    }

    private fun openPrivateChannel(file: File): FileChannel {
        val options = setOf(StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
        if (System.getProperty("os.name").lowercase().contains("win")) {
            return FileChannel.open(file.toPath(), options)
        }
        val ownerOnly = PosixFilePermissions.asFileAttribute(
            setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
        )
        return FileChannel.open(file.toPath(), options, ownerOnly)
    }

    // Windows cannot open a directory as a channel; NTFS journals the rename.
    private fun syncDirectory(dir: File) {
        runCatching { FileChannel.open(dir.toPath(), StandardOpenOption.READ).use { it.force(true) } }
    }
}
