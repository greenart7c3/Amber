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
    val systemIcon = packageName
        ?.takeIf { it.isNotBlank() }
        ?.let { rememberAppDisplayInfo(it).icon }

    val avatarModifier = modifier
        .size(size)
        .clip(CircleShape)

    when {
        !iconUrl.isNullOrBlank() && !BuildFlavorChecker.isOfflineFlavor() -> {
            AsyncImage(
                model = iconUrl,
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
