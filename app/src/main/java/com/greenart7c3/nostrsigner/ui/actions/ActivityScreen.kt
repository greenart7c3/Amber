package com.greenart7c3.nostrsigner.ui.actions

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.compose.collectAsLazyPagingItems
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.supportedKindNumbers
import com.greenart7c3.nostrsigner.ui.ActivityRow
import com.greenart7c3.nostrsigner.ui.components.SimpleSearchBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(
    modifier: Modifier,
    paddingValues: PaddingValues,
    topPadding: Dp,
    account: Account,
    key: String,
) {
    val database = Amber.instance.getHistoryDatabase(account.npub)

    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }

    val pager = remember(searchQuery) {
        Pager(
            PagingConfig(
                pageSize = 20,
                enablePlaceholders = false,
            ),
        ) {
            if (searchQuery.isEmpty()) {
                database.dao().getAllHistoryPaging()
            } else {
                database.dao().searchAllHistoryPaging(searchQuery.lowercase())
            }
        }
    }

    val lazyPagingItems = pager.flow.collectAsLazyPagingItems()

    val textFieldState by remember { mutableStateOf(TextFieldState(initialText = searchQuery)) }

    Column(
        modifier = modifier.padding(top = topPadding),
    ) {
        SimpleSearchBar(
            modifier = Modifier
                .padding(start = paddingValues.calculateLeftPadding(LayoutDirection.Ltr), end = paddingValues.calculateRightPadding(LayoutDirection.Ltr))
                .fillMaxWidth(),
            textFieldState = textFieldState,
            onSearch = {
                searchQuery = it
            },
            searchResults = supportedKindNumbers.map { it.toLocalizedString(context, true) },
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = paddingValues.calculateLeftPadding(LayoutDirection.Ltr),
                end = paddingValues.calculateRightPadding(LayoutDirection.Ltr),
                bottom = paddingValues.calculateBottomPadding(),
            ),
        ) {
            items(lazyPagingItems.itemCount) { index ->
                val activity = lazyPagingItems[index]
                activity?.let {
                    ActivityRow(activity = activity, account = account)
                }
            }

            lazyPagingItems.apply {
                when (loadState.refresh) {
                    is LoadState.Loading -> item {
                        Log.d("ActivitiesScreen", "Loading...")
                        Text("Loading...", Modifier.padding(16.dp))
                    }
                    is LoadState.Error -> item {
                        Log.d("ActivitiesScreen", "Error loading data")
                        Text("Error loading data", Modifier.padding(16.dp))
                    }
                    is LoadState.NotLoading -> { }
                }
            }
        }
    }
}
