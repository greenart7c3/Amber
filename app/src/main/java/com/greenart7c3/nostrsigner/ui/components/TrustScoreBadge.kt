package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greenart7c3.nostrsigner.R

enum class TrustLevel(val color: Color) {
    EXCELLENT(Color(0xFF4CAF50)), // Green - 80-100
    GOOD(Color(0xFF8BC34A)), // Light green - 60-79
    FAIR(Color(0xFFFF9800)), // Orange - 40-59
    POOR(Color(0xFFF44336)), // Red - <40
    UNKNOWN(Color.Gray), // Gray - null score
}

fun getTrustLevel(score: Int?): TrustLevel = when {
    score == null -> TrustLevel.UNKNOWN
    score >= 80 -> TrustLevel.EXCELLENT
    score >= 60 -> TrustLevel.GOOD
    score >= 40 -> TrustLevel.FAIR
    else -> TrustLevel.POOR
}

@Composable
fun getTrustLevelLabel(level: TrustLevel): String = when (level) {
    TrustLevel.EXCELLENT -> stringResource(R.string.trust_excellent)
    TrustLevel.GOOD -> stringResource(R.string.trust_good)
    TrustLevel.FAIR -> stringResource(R.string.trust_fair)
    TrustLevel.POOR -> stringResource(R.string.trust_poor)
    TrustLevel.UNKNOWN -> stringResource(R.string.trust_unknown)
}

/**
 * A badge displaying the trust score with color-coding.
 * Shows the numeric score and optionally a label.
 */
@Composable
fun TrustScoreBadge(
    score: Int?,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    showLabel: Boolean = false,
) {
    val trustLevel = getTrustLevel(score)

    if (isLoading) {
        CircularProgressIndicator(
            modifier = modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        return
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .background(
                    color = trustLevel.color,
                    shape = RoundedCornerShape(4.dp),
                )
                .padding(horizontal = 6.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = score?.toString() ?: "?",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        if (showLabel) {
            Text(
                text = getTrustLevelLabel(trustLevel),
                modifier = Modifier.padding(start = 4.dp),
                fontSize = 12.sp,
                color = trustLevel.color,
            )
        }
    }
}

/**
 * A compact chip-style trust score indicator.
 */
@Composable
fun TrustScoreChip(
    score: Int?,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
) {
    TrustScoreBadge(
        score = score,
        modifier = modifier,
        isLoading = isLoading,
        showLabel = false,
    )
}
