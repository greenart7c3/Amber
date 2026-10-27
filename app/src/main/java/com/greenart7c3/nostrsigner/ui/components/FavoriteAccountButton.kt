package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R

/**
 * Orders accounts with the [favorites] first, keeping the saved order within each group.
 * Display-only: callers that select by position must map back to the original index.
 */
fun <T> List<T>.favoritesFirst(favorites: Set<String>, npub: (T) -> String): List<T> = sortedBy { npub(it) !in favorites }

/**
 * Star toggle that marks [npub] as a favorite account. It only changes the saved preference,
 * never the active or selected account.
 */
@Composable
fun FavoriteAccountButton(
    npub: String,
    isFavorite: Boolean,
) {
    val context = LocalContext.current
    IconToggleButton(
        checked = isFavorite,
        onCheckedChange = { LocalPreferences.setFavoriteAccount(context, npub, it) },
    ) {
        Icon(
            imageVector = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
            contentDescription = stringResource(R.string.favorite_account),
            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
