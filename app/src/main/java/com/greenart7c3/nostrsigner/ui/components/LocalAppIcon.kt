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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap

@Composable
fun LocalAppIcon(packageName: String?) {
    packageName?.let {
        val appDisplayInfo = rememberAppDisplayInfo(packageName)
        Column(
            modifier = Modifier.padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (appDisplayInfo.icon != null) {
                Image(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(MaterialTheme.shapes.small),
                    bitmap = appDisplayInfo.icon.toBitmap().asImageBitmap(),
                    contentDescription = appDisplayInfo.name,
                    contentScale = ContentScale.Crop,
                )
            }

            Text(
                text = appDisplayInfo.name,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 18.sp,
                    lineHeight = 24.sp,
                ),
                fontWeight = FontWeight.Bold,
            )

            Text(
                modifier = Modifier
                    .fillMaxWidth(),
                text = packageName,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
