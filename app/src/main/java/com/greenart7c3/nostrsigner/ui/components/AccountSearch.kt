package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Whether an account with [npub] and display [name] matches the account search [query]. The
 * query is trimmed; names match case-insensitively anywhere, and npubs match any full or partial
 * piece, so a pasted npub (with or without a `nostr:` prefix) finds its account and unnamed
 * accounts stay findable. A blank query matches everything.
 */
fun accountMatchesSearch(query: String, npub: String, name: String): Boolean {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return true
    if (name.contains(trimmed, ignoreCase = true)) return true
    return npub.contains(trimmed.removePrefix("nostr:"), ignoreCase = true)
}

/** The current display name of each of [accounts], keyed by npub; updates as names load or change. */
@Composable
fun rememberAccountNames(accounts: List<Account>): State<Map<String, String>> {
    val flow = remember(accounts) {
        if (accounts.isEmpty()) {
            flowOf(emptyMap())
        } else {
            combine(accounts.map { acc -> acc.name.map { acc.npub to it } }) { it.toMap() }
        }
    }
    // Seeds the first frame with the names already loaded, so the list doesn't filter on blanks.
    val initial = remember(accounts) { accounts.associate { it.npub to it.name.value } }
    return flow.collectAsStateWithLifecycle(initial)
}

@Composable
fun AccountSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.search_accounts)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear_search))
                }
            }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Search,
        ),
        // The list filters as you type, so Search only needs to get the keyboard out of the way.
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        singleLine = true,
    )
}

@Composable
fun NoAccountsFound(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.no_accounts_found),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}
