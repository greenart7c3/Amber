package com.greenart7c3.nostrsigner.ui.actions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.error_loading_data
import com.greenart7c3.nostrsigner.commonsui.loading
import com.greenart7c3.nostrsigner.commonsui.localizedBlocking
import com.greenart7c3.nostrsigner.commonsui.supportedKindNumbers
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.ui.ActivityFilter
import com.greenart7c3.nostrsigner.ui.ActivityFilterRow
import com.greenart7c3.nostrsigner.ui.ActivityRow
import com.greenart7c3.nostrsigner.ui.ActivityViewModel
import com.greenart7c3.nostrsigner.ui.LocalAccountRepository
import com.greenart7c3.nostrsigner.ui.components.ActivityStatsCard
import com.greenart7c3.nostrsigner.ui.components.SimpleSearchBar
import org.jetbrains.compose.resources.stringResource

@Composable
fun ActivityScreen(
    paddingValues: PaddingValues,
    topPadding: Dp,
    account: Account,
    key: String,
    modifier: Modifier = Modifier,
    viewModel: ActivityViewModel = viewModel(factory = ActivityViewModel.factory(LocalAccountRepository.current)),
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var filter by rememberSaveable(account.npub, key) { mutableStateOf(ActivityFilter.ALL) }

    val history = remember(searchQuery, filter) { viewModel.history(account.npub, key, searchQuery, filter) }
    val lazyPagingItems = history.collectAsLazyPagingItems()

    val textFieldState by remember { mutableStateOf(TextFieldState(initialText = searchQuery)) }
    val searchResults = remember(context) { supportedKindNumbers.map { it.localizedBlocking(true) } }

    Column(
        modifier = modifier.padding(top = topPadding),
    ) {
        ActivityStatsCard(
            account = account,
            pkKey = key,
            modifier = Modifier
                .padding(
                    start = paddingValues.calculateLeftPadding(LayoutDirection.Ltr),
                    end = paddingValues.calculateRightPadding(LayoutDirection.Ltr),
                    top = 8.dp,
                    bottom = 4.dp,
                )
                .fillMaxWidth(),
        )
        ActivityFilterRow(
            selected = filter,
            onSelect = { filter = it },
            modifier = Modifier
                .padding(
                    start = paddingValues.calculateLeftPadding(LayoutDirection.Ltr),
                    end = paddingValues.calculateRightPadding(LayoutDirection.Ltr),
                    bottom = 4.dp,
                )
                .fillMaxWidth(),
        )
        SimpleSearchBar(
            modifier = Modifier
                .padding(start = paddingValues.calculateLeftPadding(LayoutDirection.Ltr), end = paddingValues.calculateRightPadding(LayoutDirection.Ltr))
                .fillMaxWidth(),
            textFieldState = textFieldState,
            onSearch = {
                searchQuery = it
            },
            searchResults = searchResults,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = paddingValues.calculateLeftPadding(LayoutDirection.Ltr),
                end = paddingValues.calculateRightPadding(LayoutDirection.Ltr),
                bottom = paddingValues.calculateBottomPadding(),
            ),
        ) {
            // No item keys: history rows are inserted continuously and Room pages
            // are independent LIMIT/OFFSET queries, so a row can transiently appear
            // in two loaded pages. Keying by the database id then crashes with
            // "Key X was already used". Positional keys are always unique.
            items(
                count = lazyPagingItems.itemCount,
            ) { index ->
                val activity = lazyPagingItems[index]
                activity?.let {
                    ActivityRow(activity = activity, account = account)
                }
            }

            lazyPagingItems.apply {
                when (loadState.refresh) {
                    is LoadState.Loading -> item {
                        AmberLog.d("ActivitiesScreen", "Loading...")
                        Text(stringResource(Res.string.loading), Modifier.padding(16.dp))
                    }
                    is LoadState.Error -> item {
                        AmberLog.d("ActivitiesScreen", "Error loading data")
                        Text(stringResource(Res.string.error_loading_data), Modifier.padding(16.dp))
                    }
                    is LoadState.NotLoading -> { }
                }
            }
        }
    }
}
