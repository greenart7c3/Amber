package com.greenart7c3.nostrsigner.desktop.data

import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

private val isPosix = FileSystems.getDefault().supportedFileAttributeViews().contains("posix")
private val ownerOnlyDirPermissions = PosixFilePermissions.fromString("rwx------")
private val ownerOnlyFilePermissions = PosixFilePermissions.fromString("rw-------")

/**
 * Restricts [file] so only its owner (the user running Amber Bunker) can read/write it,
 * denying access to other local accounts. Locking down [AppDataDir.directory] itself is
 * what actually matters on POSIX systems — a non-owner can't traverse into a `700`
 * directory regardless of the permissions on files inside it — but every sensitive file
 * is also restricted individually as defense in depth (and because Windows has no
 * equivalent directory-traversal protection).
 */
fun restrictToOwnerOnly(file: File, isDirectory: Boolean = false) {
    if (isPosix) {
        Files.setPosixFilePermissions(file.toPath(), if (isDirectory) ownerOnlyDirPermissions else ownerOnlyFilePermissions)
    } else {
        file.setReadable(false, false)
        file.setWritable(false, false)
        file.setExecutable(false, false)
        file.setReadable(true, true)
        file.setWritable(true, true)
        if (isDirectory) file.setExecutable(true, true)
    }
}
