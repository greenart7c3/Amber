package com.greenart7c3.nostrsigner.service

import java.io.File
import java.nio.file.Files
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NIP-49 backups and exported logs used to be written to the cache root and
 * never deleted, and the FileProvider served the whole cache directory.
 */
class SharedFilesTest {
    private val cacheDir = Files.createTempDirectory("amber-cache").toFile()

    @After
    fun tearDown() {
        cacheDir.deleteRecursively()
    }

    private fun write(path: String) = File(cacheDir, path).apply {
        parentFile!!.mkdirs()
        writeText("x")
    }

    @Test
    fun aNewShareRemovesTheFilesOfThePreviousOne() {
        val dir = SharedFiles.freshShareDir(cacheDir)
        File(dir, "npub1old.ncryptsec").writeText("ncryptsec1...")

        val again = SharedFiles.freshShareDir(cacheDir)

        assertEquals(dir, again)
        assertTrue(again.isDirectory)
        assertEquals(0, again.listFiles()!!.size)
    }

    @Test
    fun cleanUpRemovesSharedAndLegacyFilesOnly() {
        val shared = write("share/npub1a.ncryptsec")
        val legacyBackup = write("npub1b.ncryptsec")
        val legacyLogs = write("amber-logs.txt")
        val legacyApk = write("amber-update-6.6.9.apk")
        val imageCache = write("coil_image_cache/abc")
        val currentUpdate = write("updates/amber-update-7.0.0.apk")
        val unrelated = write("something-else.txt")

        SharedFiles.cleanUp(cacheDir)

        listOf(shared, legacyBackup, legacyLogs, legacyApk).forEach { assertFalse(it.name, it.exists()) }
        listOf(imageCache, currentUpdate, unrelated).forEach { assertTrue(it.name, it.exists()) }
    }

    @Test
    fun fileProviderServesOnlyTheShareAndUpdateDirectories() {
        val xml = File("src/main/res/xml/file_provider_paths.xml")
        val paths = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml).documentElement.childNodes
        val served = (0 until paths.length).map { paths.item(it) }.filter { it.nodeType == org.w3c.dom.Node.ELEMENT_NODE }
            .map { "${it.nodeName}:${it.attributes.getNamedItem("path").nodeValue}" }

        assertEquals(setOf("cache-path:share/", "cache-path:updates/"), served.toSet())
    }
}
