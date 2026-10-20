package com.greenart7c3.nostrsigner.desktop

import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test

/**
 * The directory the AppIndicator shim links live in must be a fresh,
 * owner-only temp dir per run: a fixed directory under the world-writable
 * /tmp would let a local attacker pre-create it or swap its symlinks, and
 * dorkbox dlopens whatever it finds there into Amber.
 */
class NativeTrayShimDirTest {
    private val created = mutableListOf<File>()

    @After
    fun tearDown() {
        created.forEach { it.deleteRecursively() }
    }

    private fun newShimDir(): File = NativeTray.newShimDir().also { created += it }

    @Test
    fun shimDirIsUniquePerCallAndNeverAdopted() {
        val first = newShimDir()
        val second = newShimDir()

        assertNotEquals("each call must create its own directory", first.absolutePath, second.absolutePath)
        assertTrue(first.isDirectory)
        assertTrue(second.isDirectory)
        // Freshly created, not a pre-existing directory that was adopted.
        assertEquals(0, first.listFiles()?.size)
        assertEquals(0, second.listFiles()?.size)
    }

    @Test
    fun shimDirIsOwnerOnlyOnPosix() {
        Assume.assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"))

        val dir = newShimDir()

        assertEquals(
            "the shim dir must be rwx------",
            setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE),
            Files.getPosixFilePermissions(dir.toPath()),
        )
    }
}
