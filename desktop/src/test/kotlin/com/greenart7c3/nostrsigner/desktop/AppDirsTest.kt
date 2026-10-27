package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.desktop.core.AppDirs
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

class AppDirsTest {
    private fun perms(file: File): String = PosixFilePermissions.toString(Files.getPosixFilePermissions(file.toPath()))

    @Test
    fun accountDirIsOwnerOnly() {
        assumeTrue(!System.getProperty("os.name").lowercase().contains("win"))
        val dir = AppDirs.accountDir("npub1appdirstest")
        assertEquals("rwx------", perms(dir))
    }

    @Test
    fun accountDirTightensExistingWorldReadableDir() {
        assumeTrue(!System.getProperty("os.name").lowercase().contains("win"))
        // A directory left behind by an older version with the umask default.
        val dir = File(AppDirs.dataDir, "npub1appdirslegacy").apply { mkdirs() }
        Files.setPosixFilePermissions(dir.toPath(), PosixFilePermissions.fromString("rwxr-xr-x"))
        assertEquals("rwx------", perms(AppDirs.accountDir("npub1appdirslegacy")))
    }

    @Test
    fun privateOutputCreatesOwnerOnlyFileBeforeContent() {
        assumeTrue(!System.getProperty("os.name").lowercase().contains("win"))
        val file = File(AppDirs.dataDir, "private-output-test.tmp").apply { delete() }
        try {
            AppDirs.privateOutput(file).use { it.write("secret".toByteArray()) }
            // The file must already be owner-only at creation time, not only
            // after a post-write restriction pass.
            assertEquals("rw-------", perms(file))
        } finally {
            file.delete()
        }
    }
}
