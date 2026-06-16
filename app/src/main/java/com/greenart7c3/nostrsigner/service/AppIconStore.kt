package com.greenart7c3.nostrsigner.service

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import java.io.File
import java.io.FileOutputStream

/**
 * Persists a native app's launcher icon to internal storage so it can be shown
 * later on non-interactive screens (e.g. the applications list).
 *
 * Android 11+ package-visibility filtering only lets us resolve an installed
 * app's icon while that app is visible to us — typically the moment it sends a
 * `nostrsigner://` request. We capture it then and store the file path on the
 * saved [com.greenart7c3.nostrsigner.database.ApplicationEntity], avoiding the
 * broad QUERY_ALL_PACKAGES permission.
 */
object AppIconStore {
    private const val DIR = "app_icons"

    /**
     * Returns the path to the cached PNG for [packageName], writing it from the
     * PackageManager icon on first use. Returns null if the icon can't be
     * resolved (e.g. the app is not currently visible).
     */
    fun resolveIconPath(context: Context, packageName: String): String? {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val file = File(dir, "$packageName.png")

        if (!file.exists()) {
            val drawable = runCatching {
                context.packageManager.getApplicationIcon(packageName)
            }.getOrNull() ?: return null

            val saved = runCatching {
                FileOutputStream(file).use { out ->
                    drawable.toBitmap().compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }.isSuccess

            if (!saved) return null
        }

        return file.absolutePath
    }
}
