package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class EventKindStyle(val color: Color, val shortLabel: String)

private val KPost = Color(0xFF6FA8FF)
private val KDM = Color(0xFFC08BFF)
private val KZap = Color(0xFFF5A524)
private val KDelete = Color(0xFFFF6B6B)
private val KReact = Color(0xFF6FD49A)
private val KMeta = Color(0xFFFFC56F)
private val KBunker = Color(0xFF7CD6E8)

fun eventKindStyle(kind: Int): EventKindStyle = when (kind) {
    0 -> EventKindStyle(KMeta, "profile")
    1 -> EventKindStyle(KPost, "post")
    3 -> EventKindStyle(KMeta, "follows")
    4 -> EventKindStyle(KDM, "dm")
    5 -> EventKindStyle(KDelete, "delete")
    6 -> EventKindStyle(KPost, "repost")
    7 -> EventKindStyle(KReact, "reaction")
    1059 -> EventKindStyle(KDM, "dm")
    9734, 9735 -> EventKindStyle(KZap, "zap")
    30023 -> EventKindStyle(KPost, "article")
    else -> EventKindStyle(KBunker, "event")
}

@Composable
fun EventKindChip(kind: Int, modifier: Modifier = Modifier) {
    val style = eventKindStyle(kind)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(100))
            .background(style.color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(style.color),
        )
        Text(
            text = "kind $kind · ${style.shortLabel}".uppercase(),
            color = style.color,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}
