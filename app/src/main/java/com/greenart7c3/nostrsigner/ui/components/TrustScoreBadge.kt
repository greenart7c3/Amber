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

// Trust-level palette in the warm-amber design language:
// each level uses a soft wash background with the accent color as text,
// matching chip-ok / chip-amber / chip-danger from the Amber canvas.
enum class TrustLevel(val color: Color, val background: Color) {
    EXCELLENT(Color(0xFF15803D), Color(0xFFD1FAE5)), // ok 80-100
    GOOD(Color(0xFF166534), Color(0xFFE6F4EA)), // ok-2 60-79
    FAIR(Color(0xFFB45309), Color(0xFFFEF3C7)), // amber 40-59
    POOR(Color(0xFFB91C1C), Color(0xFFFEE2E2)), // danger <40
    UNKNOWN(Color(0xFF7A7269), Color(0xFFF5EFE3)), // ink-3 / surface-2
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
                    color = trustLevel.background,
                    shape = RoundedCornerShape(999.dp),
                )
                .padding(horizontal = 10.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = score?.toString() ?: "?",
                color = trustLevel.color,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (showLabel) {
            Text(
                text = getTrustLevelLabel(trustLevel),
                modifier = Modifier.padding(start = 6.dp),
                fontSize = 12.sp,
                color = trustLevel.color,
                fontWeight = FontWeight.Medium,
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
