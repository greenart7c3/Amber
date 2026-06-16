package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.greenart7c3.nostrsigner.BuildFlavorChecker

/**
 * Centered avatar for a NIP-46 remote client, loaded from the `image` field of
 * its client metadata (nostr-protocol/nips#2381) or the persisted application
 * icon. Renders nothing when the URL is blank or on the offline flavor, which
 * has no network image loading.
 */
@Composable
fun RemoteAppIcon(
    imageUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    if (imageUrl.isNullOrBlank() || BuildFlavorChecker.isOfflineFlavor()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            modifier = Modifier
                .size(size)
                .clip(CircleShape),
        )
    }
}
