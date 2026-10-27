package com.greenart7c3.nostrsigner.ui.components

import com.greenart7c3.nostrsigner.service.AppIconStore
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A NIP-46 client chooses the icon string stored for its connection, so it
 * must never make Amber load an arbitrary local file as the avatar.
 */
class AppAvatarModelTest {
    private val root = Files.createTempDirectory("amber-files").toFile()
    private val iconDir = File(root, "app_icons").apply { mkdirs() }
    private val storedIcon = File(iconDir, "com.example.app.png").apply { writeText("png") }
    private val isStored: (String) -> Boolean = { AppIconStore.isStoredIcon(iconDir, it) }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun remoteIconsLoadOnlyOverHttpsAndOnlineOnly() {
        assertEquals("https://client.example/icon.png", avatarModel("https://client.example/icon.png", offline = false, isStored))
        assertEquals("HTTPS://client.example/icon.png", avatarModel("HTTPS://client.example/icon.png", offline = false, isStored))
        assertNull(avatarModel("https://client.example/icon.png", offline = true, isStored))
        assertNull("plain http", avatarModel("http://client.example/icon.png", offline = false, isStored))
        assertNull("plain http to the LAN", avatarModel("http://192.168.1.1/icon.png", offline = false, isStored))
    }

    @Test
    fun ownStoredIconLoadsAsAFile() {
        assertEquals(storedIcon, avatarModel(storedIcon.absolutePath, offline = false, isStored))
        assertEquals(storedIcon, avatarModel(storedIcon.absolutePath, offline = true, isStored))
    }

    @Test
    fun clientChosenLocalPathsAreIgnored() {
        val privateFile = File(root, "shared_prefs/prefs.xml").apply {
            parentFile!!.mkdirs()
            writeText("<map/>")
        }
        listOf(
            privateFile.absolutePath,
            "${iconDir.absolutePath}/../shared_prefs/prefs.xml",
            "/sdcard/DCIM/Camera/photo.jpg",
            "file://${storedIcon.absolutePath}",
            "content://com.example.provider/secret",
            "app_icons/com.example.app.png",
            "httpfoo",
        ).forEach { assertNull(it, avatarModel(it, offline = false, isStored)) }
    }

    @Test
    fun storeOnlyAcceptsFilesDirectlyInsideItsDirectory() {
        assertTrue(AppIconStore.isStoredIcon(iconDir, storedIcon.absolutePath))
        assertFalse("nested", AppIconStore.isStoredIcon(iconDir, File(iconDir, "sub/x.png").absolutePath))
        assertFalse("relative", AppIconStore.isStoredIcon(iconDir, "app_icons/com.example.app.png"))
        assertFalse("traversal", AppIconStore.isStoredIcon(iconDir, "${iconDir.absolutePath}/../app_icons_evil/x.png"))
        assertFalse("sibling", AppIconStore.isStoredIcon(iconDir, File(root, "x.png").absolutePath))
    }

    @Test
    fun symlinkOutOfTheStoreIsRejected() {
        val outside = File(root, "secret.db").apply { writeText("secret") }
        val link = File(iconDir, "evil.png")
        Files.createSymbolicLink(link.toPath(), outside.toPath())
        assertFalse(AppIconStore.isStoredIcon(iconDir, link.absolutePath))
    }
}
