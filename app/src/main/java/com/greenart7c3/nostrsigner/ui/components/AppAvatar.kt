package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import coil3.compose.AsyncImage
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.service.AppIconStore
import com.greenart7c3.nostrsigner.ui.theme.AmberPreview
import com.greenart7c3.nostrsigner.ui.theme.ThemePreviews
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

    // A native app's launcher icon is a file Amber wrote to its own icon store;
    // a remote client icon is never stored, only fetched over https. Local files
    // load on every flavor (no network); remote URLs are skipped on the offline
    // flavor. Remote clients choose the icon string, so anything that is neither
    // (another local path, plain http, other schemes) is ignored.
    val context = LocalContext.current
    val model: Any? = remember(iconUrl) {
        avatarModel(iconUrl, BuildFlavorChecker.isOfflineFlavor()) { AppIconStore.isStoredIcon(context, it) }
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

/** The image model for a stored icon string, or null when it must not be loaded. */
internal fun avatarModel(iconUrl: String?, offline: Boolean, isStoredIcon: (String) -> Boolean): Any? = when {
    iconUrl.isNullOrBlank() -> null
    isRemoteIconUrl(iconUrl) -> if (offline) null else iconUrl
    isStoredIcon(iconUrl) -> File(iconUrl)
    else -> null
}

internal fun isRemoteIconUrl(iconUrl: String): Boolean = iconUrl.startsWith("https://", ignoreCase = true)

@ThemePreviews
@Composable
fun AppAvatarPlaceholderPreview() {
    AmberPreview {
        Box(Modifier.padding(8.dp)) {
            AppAvatar(name = "Amethyst")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AppAvatarNoNamePreview() {
    AmberPreview {
        Box(Modifier.padding(8.dp)) {
            AppAvatar()
        }
    }
}
