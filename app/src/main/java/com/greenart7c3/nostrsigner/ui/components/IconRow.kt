package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IconRow(
    title: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 15.dp, horizontal = 25.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                null,
                modifier = Modifier.size(22.dp),
                tint = tint,
            )
            Text(
                modifier = Modifier.padding(start = 16.dp),
                text = title,
                fontSize = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IconRow(
    title: String,
    icon: Int,
    tint: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 15.dp, horizontal = 25.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(icon),
                null,
                modifier = Modifier.size(22.dp),
                tint = tint,
            )
            Text(
                modifier = Modifier.padding(start = 16.dp),
                text = title,
                fontSize = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
