package com.greenart7c3.nostrsigner.desktop

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Ayatana -> legacy appindicator symlink shim that lets dorkbox 4.4 (which
 * only searches libappindicator3.so names) load libayatana-appindicator3.
 */
class AppIndicatorShimTest {
    private fun tmp(name: String): File = Files.createTempDirectory(name).toFile().apply { deleteOnExit() }

    @Test
    fun createsGtk3SymlinksPointingAtAyatanaWhenOnlyAyatanaPresent() {
        val lib = tmp("libdir")
        val ayatana = File(lib, "libayatana-appindicator3.so.1")
        ayatana.writeText("fake so")
        val shimDir = tmp("shim").also { it.delete() } // let the code mkdirs

        val result = NativeTray.buildAppIndicatorShim(listOf(lib.absolutePath), shimDir)

        assertEquals(shimDir.absolutePath, result)
        val expected = listOf(
            "libappindicator3.so",
            "libappindicator3.so.1",
            "libappindicator3-1.so",
            "libappindicator-gtk3.so",
            "libappindicator-gtk3-1.so",
        )
        expected.forEach { name ->
            val link = File(shimDir, name)
            assertTrue("$name should exist", link.exists())
            assertTrue("$name should be a symlink", Files.isSymbolicLink(link.toPath()))
            assertEquals(ayatana.toPath().toRealPath(), link.toPath().toRealPath())
        }
    }

    @Test
    fun skipsWhenLegacyLibraryAlreadyPresent() {
        val lib = tmp("libdir")
        File(lib, "libappindicator3.so.1").writeText("real legacy")
        File(lib, "libayatana-appindicator3.so.1").writeText("ayatana")
        assertNull(NativeTray.buildAppIndicatorShim(listOf(lib.absolutePath), tmp("shim")))
    }

    @Test
    fun skipsWhenNoAppindicatorLibraryAtAll() {
        val lib = tmp("libdir")
        assertNull(NativeTray.buildAppIndicatorShim(listOf(lib.absolutePath), tmp("shim")))
    }
}
