package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.greenart7c3.nostrsigner.SavedAccounts
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.favorite_account
import org.jetbrains.compose.resources.stringResource

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
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    IconToggleButton(
        checked = isFavorite,
        onCheckedChange = { SavedAccounts.setFavorite(context, npub, it) },
        modifier = modifier,
    ) {
        Icon(
            imageVector = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
            contentDescription = stringResource(Res.string.favorite_account),
            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
