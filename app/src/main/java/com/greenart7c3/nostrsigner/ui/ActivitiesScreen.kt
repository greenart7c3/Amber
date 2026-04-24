package com.greenart7c3.nostrsigner.ui

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.LoadState
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.database.HistoryEntity
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.TimeUtils
import com.greenart7c3.nostrsigner.models.supportedKindNumbers
import com.greenart7c3.nostrsigner.service.ApplicationNameCache
import com.greenart7c3.nostrsigner.service.model.AmberEvent
import com.greenart7c3.nostrsigner.service.toShortenHex
import com.greenart7c3.nostrsigner.ui.components.ActivityStatsCard
import com.greenart7c3.nostrsigner.ui.components.EventSection
import com.greenart7c3.nostrsigner.ui.components.SimpleSearchBar
import com.greenart7c3.nostrsigner.ui.components.TagsSection
import com.greenart7c3.nostrsigner.ui.components.copyToClipboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class ActivityFilter(val accepted: Boolean?) {
    ALL(null),
    ACCEPTED(true),
    REJECTED(false),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivitiesScreen(
    modifier: Modifier,
    paddingValues: PaddingValues,
    topPadding: Dp,
    account: Account,
) {
    val database = Amber.instance.getHistoryDatabase(account.npub)

    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var filter by rememberSaveable(account.npub) { mutableStateOf(ActivityFilter.ALL) }

    val pager = remember(searchQuery, filter) {
        Pager(
            PagingConfig(
                pageSize = 20,
                enablePlaceholders = false,
            ),
        ) {
            if (searchQuery.isEmpty()) {
                database.dao().getAllHistoryPaging(filter.accepted)
            } else {
                database.dao().searchAllHistoryPaging(searchQuery.lowercase(), filter.accepted)
            }
        }
    }

    val lazyPagingItems = pager.flow.collectAsLazyPagingItems()

    val textFieldState by remember { mutableStateOf(TextFieldState(initialText = searchQuery)) }
    val searchResults = remember(context) { supportedKindNumbers.map { it.toLocalizedString(context, true) } }

    Column(
        modifier = modifier.padding(top = topPadding),
    ) {
        ActivityStatsCard(
            account = account,
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
            item {
                if (lazyPagingItems.itemCount == 0) {
                    Text(
                        stringResource(R.string.no_activities_found),
                        Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        fontSize = 18.sp,
                    )
                }
            }

            items(
                count = lazyPagingItems.itemCount,
                key = lazyPagingItems.itemKey { it.id },
            ) { index ->
                val activity = lazyPagingItems[index]
                if (activity != null) {
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

@Composable
fun ActivityRow(activity: HistoryEntity, account: Account) {
    val clipboard = LocalClipboard.current
    val parsedEvent = remember(activity.content) {
        if (activity.content.isBlank()) {
            null
        } else {
            runCatching { AmberEvent.fromJson(activity.content).toEvent() }.getOrNull()
        }
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(0.9f),
                verticalArrangement = Arrangement.Center,
            ) {
                ApplicationName(
                    key = activity.pkKey,
                    accepted = activity.accepted,
                    account = account,
                )

                Text(
                    text = activity.translatedPermission,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (activity.accepted) Color.Unspecified else Color.Gray,
                )

                if (parsedEvent != null) {
                    if (parsedEvent.content.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        EventSection(
                            padding = 0.dp,
                            label = stringResource(R.string.content),
                            displayValue = parsedEvent.content,
                            onCopy = { copyToClipboard(clipboard, parsedEvent.content) },
                        )
                    }
                    if (parsedEvent.tags.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        TagsSection(
                            horizontalPadding = 0,
                            verticalPadding = 0,
                            label = stringResource(R.string.tags),
                            tags = parsedEvent.tags,
                            onCopy = {
                                copyToClipboard(
                                    clipboard,
                                    parsedEvent.tags.joinToString(separator = ", ") {
                                        "[${it.joinToString(separator = ", ") { tag -> "\"$tag\"" }}]"
                                    },
                                )
                            },
                        )
                    }
                } else if (activity.content.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        modifier = Modifier.padding(top = 2.dp),
                        text = activity.content,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 14.sp,
                        color = if (activity.accepted) Color.Unspecified else Color.Gray,
                    )
                }

                Text(
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                    text = TimeUtils.formatLongToCustomDateTimeWithSeconds(activity.time * 1000),
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.Gray,
                )
            }
            Icon(
                if (activity.accepted) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                tint = if (activity.accepted) Color(0xFF1D8802) else Color(0xFFFF6B00),
                modifier = Modifier.padding(start = 10.dp, top = 4.dp, bottom = 16.dp),
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun ActivityFilterRow(
    selected: ActivityFilter,
    onSelect: (ActivityFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        item {
            FilterChip(
                selected = selected == ActivityFilter.ALL,
                onClick = { onSelect(ActivityFilter.ALL) },
                label = { Text(stringResource(R.string.filter_all)) },
            )
        }
        item {
            FilterChip(
                selected = selected == ActivityFilter.ACCEPTED,
                onClick = { onSelect(ActivityFilter.ACCEPTED) },
                label = { Text(stringResource(R.string.filter_accepted)) },
            )
        }
        item {
            FilterChip(
                selected = selected == ActivityFilter.REJECTED,
                onClick = { onSelect(ActivityFilter.REJECTED) },
                label = { Text(stringResource(R.string.filter_rejected)) },
            )
        }
    }
}

@Composable
fun ApplicationName(
    key: String,
    accepted: Boolean,
    account: Account,
) {
    var name by remember { mutableStateOf("") }

    LaunchedEffect(key) {
        val cacheKey = "${account.npub.toShortenHex()}-$key"
        val cached = ApplicationNameCache.names[cacheKey]
        if (cached != null) {
            name = cached
        } else {
            val appName = withContext(Dispatchers.IO) {
                Amber.instance.getDatabase(account.npub).dao().getAppName(key)
            }
            if (appName != null) {
                name = appName
                ApplicationNameCache.names[cacheKey] = appName
            }
        }
    }

    Text(
        modifier = Modifier.padding(top = 16.dp),
        text = name.ifBlank { key.toShortenHex() },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = if (accepted) Color.Unspecified else Color.Gray,
        fontWeight = FontWeight.Bold,
    )
}
