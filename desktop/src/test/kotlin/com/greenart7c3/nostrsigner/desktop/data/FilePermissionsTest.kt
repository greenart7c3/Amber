package com.greenart7c3.nostrsigner.desktop.data

import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FilePermissionsTest {
    private val isPosix = FileSystems.getDefault().supportedFileAttributeViews().contains("posix")

    @Test
    fun restrictsFileToOwnerReadWriteOnly() {
        if (!isPosix) return
        val file = Files.createTempFile("amber-bunker-test", ".db").toFile().apply { deleteOnExit() }

        restrictToOwnerOnly(file)

        val permissions = Files.getPosixFilePermissions(file.toPath())
        assertEquals(setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), permissions)
    }

    @Test
    fun restrictsDirectoryToOwnerReadWriteExecuteOnly() {
        if (!isPosix) return
        val dir = Files.createTempDirectory("amber-bunker-test-dir").toFile().apply { deleteOnExit() }

        restrictToOwnerOnly(dir, isDirectory = true)

        val permissions = Files.getPosixFilePermissions(dir.toPath())
        assertEquals(
            setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE),
            permissions,
        )
    }

    @Test
    fun restrictedFileIsNotReadableByGroupOrOthers() {
        if (!isPosix) return
        val file = Files.createTempFile("amber-bunker-test", ".key").toFile().apply { deleteOnExit() }

        restrictToOwnerOnly(file)

        assertTrue(file.canRead())
        assertTrue(file.canWrite())
        val permissions = Files.getPosixFilePermissions(file.toPath())
        assertTrue(PosixFilePermission.GROUP_READ !in permissions)
        assertTrue(PosixFilePermission.OTHERS_READ !in permissions)
    }
}
