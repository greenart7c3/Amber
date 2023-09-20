package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun EventRow(
    icon: ImageVector,
    title: String,
    content: String
) {
    Divider(
        modifier = Modifier.padding(vertical = 15.dp),
        thickness = Dp.Hairline
    )

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Icon(
            icon,
            null,
            tint = MaterialTheme.colorScheme.onBackground
        )
        Text(
            modifier = Modifier.padding(start = 8.dp),
            text = "$title ",
            fontWeight = FontWeight.Bold
        )
        Text(
            content,
            color = Color.Gray,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
