package com.greenart7c3.nostrsigner.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.database.HistoryDao
import com.greenart7c3.nostrsigner.models.Account
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val AcceptedColor = Color(0xFF1D8802)
private val RejectedColor = Color(0xFFFF6B00)

private data class WindowStats(
    @StringRes val labelRes: Int,
    val accepted: Long,
    val rejected: Long,
) {
    val total: Long get() = accepted + rejected
}

@Composable
fun ActivityStatsCard(
    account: Account,
    modifier: Modifier = Modifier,
) {
    val dao = remember(account.npub) {
        Amber.instance.getHistoryDatabase(account.npub).dao()
    }
    var expanded by rememberSaveable(account.npub) { mutableStateOf(false) }
    var stats by remember(account.npub) { mutableStateOf<List<WindowStats>?>(null) }

    LaunchedEffect(account.npub, expanded) {
        if (expanded && stats == null) {
            stats = withContext(Dispatchers.IO) { loadStats(dao) }
        }
    }

    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "chevron",
    )

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.statistics),
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.Bold,
            )
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.rotate(chevronRotation),
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Legend()
                Spacer(Modifier.height(8.dp))
                val current = stats
                if (current == null) {
                    Text(
                        text = "…",
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                } else {
                    val maxTotal = current.maxOf { it.total }
                    current.forEach { window ->
                        ActivityStatsBar(window, maxTotal)
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Legend() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LegendSwatch(AcceptedColor)
        Spacer(Modifier.width(4.dp))
        Text(stringResource(R.string.filter_accepted), fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(12.dp))
        LegendSwatch(RejectedColor)
        Spacer(Modifier.width(4.dp))
        Text(stringResource(R.string.filter_rejected), fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun LegendSwatch(color: Color) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(color),
    )
}

@Composable
private fun ActivityStatsBar(stats: WindowStats, maxTotal: Long) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(stats.labelRes),
            modifier = Modifier.weight(0.32f),
        )
        Box(
            modifier = Modifier
                .weight(0.55f)
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surface),
        ) {
            if (maxTotal > 0 && stats.total > 0) {
                val filledFraction = stats.total.toFloat() / maxTotal.toFloat()
                Row(
                    modifier = Modifier
                        .fillMaxWidth(filledFraction)
                        .height(12.dp),
                ) {
                    if (stats.accepted > 0) {
                        Box(
                            modifier = Modifier
                                .weight(stats.accepted.toFloat())
                                .fillMaxWidth()
                                .background(AcceptedColor),
                        )
                    }
                    if (stats.rejected > 0) {
                        Box(
                            modifier = Modifier
                                .weight(stats.rejected.toFloat())
                                .fillMaxWidth()
                                .background(RejectedColor),
                        )
                    }
                }
            }
        }
        Text(
            text = stats.total.toString(),
            modifier = Modifier
                .weight(0.13f)
                .padding(start = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            fontWeight = FontWeight.Medium,
        )
    }
    if (stats.total > 0) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, top = 2.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Text(
                text = "${stats.accepted}",
                color = AcceptedColor,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = " · ",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${stats.rejected}",
                color = RejectedColor,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private suspend fun loadStats(dao: HistoryDao): List<WindowStats> {
    val now = TimeUtils.now()
    val day = 24L * 60 * 60
    val windows = listOf(
        R.string.last_24_hours to day,
        R.string.last_7_days to 7 * day,
        R.string.last_30_days to 30 * day,
    )
    return windows.map { (labelRes, windowSeconds) ->
        val since = now - windowSeconds
        WindowStats(
            labelRes = labelRes,
            accepted = dao.countAcceptedSince(since),
            rejected = dao.countRejectedSince(since),
        )
    }
}
