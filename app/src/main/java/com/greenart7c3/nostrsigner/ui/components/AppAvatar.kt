package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import coil3.compose.AsyncImage
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import java.io.File

/**
 * App avatar resolved in priority order:
 *   1. [iconUrl] — a remote NIP-46 client icon (skipped on the offline flavor,
 *      which has no network image loading).
 *   2. [packageName] — a native Android app's launcher icon, via PackageManager.
 *   3. A letter placeholder built from [name] for apps without any icon.
 */
@Composable
fun AppAvatar(
    modifier: Modifier = Modifier,
    iconUrl: String? = null,
    packageName: String? = null,
    name: String = "",
    size: Dp = 48.dp,
) {
    val avatarModifier = modifier
        .size(size)
        .clip(CircleShape)

    // A persisted native-app icon is a local file path; a remote client icon is
    // an http(s) URL. Local files load on every flavor (no network); remote URLs
    // are skipped on the offline flavor.
    val isRemote = iconUrl?.startsWith("http", ignoreCase = true) == true
    val model: Any? = when {
        iconUrl.isNullOrBlank() -> null
        isRemote -> if (BuildFlavorChecker.isOfflineFlavor()) null else iconUrl
        else -> File(iconUrl)
    }

    // Only query PackageManager when there's no stored icon to show — avoids a
    // futile (and log-noisy) lookup for rows that aren't native apps.
    val systemIcon = if (model == null) rememberAppIcon(packageName) else null

    when {
        model != null -> {
            AsyncImage(
                model = model,
                contentDescription = name.ifBlank { null },
                modifier = avatarModifier,
            )
        }

        systemIcon != null -> {
            Image(
                bitmap = systemIcon.toBitmap().asImageBitmap(),
                contentDescription = name.ifBlank { null },
                contentScale = ContentScale.Crop,
                modifier = avatarModifier,
            )
        }

        else -> {
            Box(
                modifier = avatarModifier.background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value / 2).sp,
                )
            }
        }
    }
}
