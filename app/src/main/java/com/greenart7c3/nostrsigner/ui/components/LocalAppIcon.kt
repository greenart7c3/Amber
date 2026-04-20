package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.greenart7c3.nostrsigner.ui.theme.MonoFontFamily

@Composable
fun LocalAppIcon(packageName: String?) {
    packageName?.let {
        val appDisplayInfo = rememberAppDisplayInfo(packageName)
        Column(
            modifier = Modifier.padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (appDisplayInfo.icon != null) {
                Image(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(MaterialTheme.shapes.medium),
                    bitmap = appDisplayInfo.icon.toBitmap().asImageBitmap(),
                    contentDescription = appDisplayInfo.name,
                    contentScale = ContentScale.Crop,
                )
            }

            Text(
                text = appDisplayInfo.name,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )

            Text(
                modifier = Modifier.fillMaxWidth(),
                text = packageName,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
