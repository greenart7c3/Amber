package com.greenart7c3.nostrsigner.service

import android.content.Context
import java.io.File

/**
 * The only cache subdirectories the FileProvider serves (see
 * `res/xml/file_provider_paths.xml`), so no other cached file can be handed
 * out through a content URI.
 *
 * Shared files (ncryptsec backups, exported logs) cannot be deleted right after
 * the share: the chooser returns at once and the receiving app reads the URI
 * later. They are cleared before the next share and on app start instead.
 */
object SharedFiles {
    private const val SHARE_DIR = "share"
    private const val UPDATES_DIR = "updates"

    /** Empties the share directory and returns it, ready for a new share. */
    fun freshShareDir(context: Context): File = freshShareDir(context.cacheDir)

    fun updatesDir(context: Context): File = File(context.cacheDir, UPDATES_DIR).apply { mkdirs() }

    /** Removes shared files left from an earlier session or version. */
    fun cleanUp(context: Context) = cleanUp(context.cacheDir)

    internal fun freshShareDir(cacheDir: File): File = File(cacheDir, SHARE_DIR).apply {
        deleteRecursively()
        mkdirs()
    }

    internal fun cleanUp(cacheDir: File) {
        File(cacheDir, SHARE_DIR).deleteRecursively()
        // Earlier versions wrote these to the cache root, which the provider served whole.
        cacheDir.listFiles()?.filter { file ->
            file.isFile && (file.name.endsWith(".ncryptsec") || file.name == "amber-logs.txt" || (file.name.startsWith("amber-update-") && file.name.endsWith(".apk")))
        }?.forEach { it.delete() }
    }
}
