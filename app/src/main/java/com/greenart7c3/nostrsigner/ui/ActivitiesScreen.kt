package com.greenart7c3.nostrsigner.ui

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.content
import com.greenart7c3.nostrsigner.commonsui.error_loading_data
import com.greenart7c3.nostrsigner.commonsui.filter_accepted
import com.greenart7c3.nostrsigner.commonsui.filter_all
import com.greenart7c3.nostrsigner.commonsui.filter_rejected
import com.greenart7c3.nostrsigner.commonsui.loading
import com.greenart7c3.nostrsigner.commonsui.localizedBlocking
import com.greenart7c3.nostrsigner.commonsui.no_activities_found
import com.greenart7c3.nostrsigner.commonsui.supportedKindNumbers
import com.greenart7c3.nostrsigner.commonsui.tags
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.TimeUtils
import com.greenart7c3.nostrsigner.service.ApplicationNameCache
import com.greenart7c3.nostrsigner.service.model.AmberEvent
import com.greenart7c3.nostrsigner.service.toShortenHex
import com.greenart7c3.nostrsigner.ui.components.ActivityStatsCard
import com.greenart7c3.nostrsigner.ui.components.EventSection
import com.greenart7c3.nostrsigner.ui.components.SimpleSearchBar
import com.greenart7c3.nostrsigner.ui.components.TagsSection
import com.greenart7c3.nostrsigner.ui.components.rememberCopyToClipboard
import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

enum class ActivityFilter(val accepted: Boolean?) {
    ALL(null),
    ACCEPTED(true),
    REJECTED(false),
}

@Composable
fun ActivitiesScreen(
    paddingValues: PaddingValues,
    topPadding: Dp,
    account: Account,
    modifier: Modifier = Modifier,
    viewModel: ActivityViewModel = viewModel(factory = ActivityViewModel.factory(LocalAccountRepository.current)),
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var filter by rememberSaveable(account.npub) { mutableStateOf(ActivityFilter.ALL) }

    val history = remember(searchQuery, filter) { viewModel.history(account.npub, pkKey = null, searchQuery, filter) }
    val lazyPagingItems = history.collectAsLazyPagingItems()

    val textFieldState by remember { mutableStateOf(TextFieldState(initialText = searchQuery)) }
    val searchResults = remember(context) { supportedKindNumbers.map { it.localizedBlocking(true) } }
    val start = paddingValues.calculateLeftPadding(LayoutDirection.Ltr)
    val end = paddingValues.calculateRightPadding(LayoutDirection.Ltr)

    Column(
        modifier = modifier.padding(top = topPadding),
    ) {
        ActivityStatsCard(
            account = account,
            modifier = Modifier
                .padding(start = start, end = end, top = 8.dp, bottom = 4.dp)
                .fillMaxWidth(),
        )
        ActivityFilterRow(
            selected = filter,
            onSelect = { filter = it },
            modifier = Modifier
                .padding(start = start, end = end, bottom = 4.dp)
                .fillMaxWidth(),
        )
        SimpleSearchBar(
            modifier = Modifier
                .padding(start = start, end = end)
                .fillMaxWidth(),
            textFieldState = textFieldState,
            onSearch = {
                searchQuery = it
            },
            searchResults = searchResults,
        )
        ActivityList(
            activities = lazyPagingItems,
            account = account,
            contentPadding = PaddingValues(start = start, end = end, bottom = paddingValues.calculateBottomPadding()),
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun ActivityList(
    activities: LazyPagingItems<ActivityEntry>,
    account: Account,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
    ) {
        item {
            if (activities.itemCount == 0) {
                Text(
                    stringResource(Res.string.no_activities_found),
                    Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    fontSize = 18.sp,
                )
            }
        }

        // No item keys: history rows are inserted continuously and Room pages
        // are independent LIMIT/OFFSET queries, so a row can transiently appear
        // in two loaded pages. Keying by the database id then crashes with
        // "Key X was already used". Positional keys are always unique.
        items(
            count = activities.itemCount,
        ) { index ->
            val activity = activities[index]
            if (activity != null) {
                ActivityRow(activity = activity, account = account)
            }
        }

        when (activities.loadState.refresh) {
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

/**
 * Recovers the readable plaintext for a history row on demand. Encrypt/decrypt
 * rows persist only the ciphertext, so the plaintext is derived here using the
 * stored counterparty key (and, for NIP-44 v3, the kind + scope). Falls back to
 * the stored content if the row is not encrypted or decryption fails (e.g. old
 * rows created before ciphertext-at-rest, or a missing counterparty key).
 */
suspend fun decryptHistoryContent(activity: ActivityEntry, account: Account): String {
    if (activity.content.isBlank()) return ""
    return try {
        when (activity.type) {
            SignerType.NIP04_ENCRYPT.name, SignerType.NIP44_ENCRYPT.name,
            SignerType.NIP04_DECRYPT.name, SignerType.NIP44_DECRYPT.name,
            ->
                if (activity.encryptionPubKey.isBlank()) {
                    activity.content
                } else {
                    account.decrypt(activity.content, activity.encryptionPubKey)
                }
            SignerType.NIP44_V3_ENCRYPT.name, SignerType.NIP44_V3_DECRYPT.name -> {
                val kind = activity.kind
                if (activity.encryptionPubKey.isBlank() || kind == null) {
                    activity.content
                } else {
                    account.nip44v3Decrypt(activity.content, activity.encryptionPubKey, kind, activity.encryptionScope).decodeToString()
                }
            }
            SignerType.DECRYPT_ZAP_EVENT.name -> account.decryptZapEvent(activity.content) ?: activity.content
            else -> activity.content
        }
    } catch (_: Exception) {
        activity.content
    }
}

@Composable
fun ActivityRow(activity: ActivityEntry, account: Account, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    Column(modifier) {
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

                ActivityContentPreview(activity, account)

                Text(
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                    text = TimeUtils.formatDateTime(context, activity.time * 1000, withSeconds = true),
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
private fun ActivityContentPreview(activity: ActivityEntry, account: Account) {
    val displayContent by produceState(initialValue = "", activity.id, activity.content) {
        value = withContext(Dispatchers.IO) { decryptHistoryContent(activity, account) }
    }
    val parsedEvent = remember(displayContent) {
        if (displayContent.isBlank()) {
            null
        } else {
            runCatching { AmberEvent.fromJson(displayContent).toEvent() }.getOrNull()
        }
    }

    if (parsedEvent != null) {
        ActivityEventPreview(parsedEvent)
    } else if (displayContent.isNotBlank()) {
        Column {
            Spacer(Modifier.height(4.dp))
            Text(
                modifier = Modifier.padding(top = 2.dp),
                text = displayContent,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                fontSize = 14.sp,
                color = if (activity.accepted) Color.Unspecified else Color.Gray,
            )
        }
    }
}

@Composable
private fun ActivityEventPreview(event: Event) {
    val copyToClipboard = rememberCopyToClipboard()
    Column {
        if (event.content.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            EventSection(
                padding = 0.dp,
                label = stringResource(Res.string.content),
                displayValue = event.content,
                onCopy = { copyToClipboard(event.content) },
            )
        }
        if (event.tags.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            TagsSection(
                horizontalPadding = 0,
                verticalPadding = 0,
                label = stringResource(Res.string.tags),
                tags = event.tags,
                onCopy = {
                    copyToClipboard(
                        event.tags.joinToString(separator = ", ") {
                            "[${it.joinToString(separator = ", ") { tag -> "\"$tag\"" }}]"
                        },
                    )
                },
            )
        }
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
                label = { Text(stringResource(Res.string.filter_all)) },
            )
        }
        item {
            FilterChip(
                selected = selected == ActivityFilter.ACCEPTED,
                onClick = { onSelect(ActivityFilter.ACCEPTED) },
                label = { Text(stringResource(Res.string.filter_accepted)) },
            )
        }
        item {
            FilterChip(
                selected = selected == ActivityFilter.REJECTED,
                onClick = { onSelect(ActivityFilter.REJECTED) },
                label = { Text(stringResource(Res.string.filter_rejected)) },
            )
        }
    }
}

@Composable
fun ApplicationName(
    key: String,
    accepted: Boolean,
    account: Account,
    modifier: Modifier = Modifier,
    viewModel: ActivityViewModel = viewModel(factory = ActivityViewModel.factory(LocalAccountRepository.current)),
) {
    var name by remember { mutableStateOf("") }

    LaunchedEffect(key) {
        val cacheKey = "${account.npub.toShortenHex()}-$key"
        val cached = ApplicationNameCache[cacheKey]
        if (cached != null) {
            name = cached
        } else {
            val appName = viewModel.appName(account.npub, key)
            if (appName != null) {
                name = appName
                ApplicationNameCache[cacheKey] = appName
            }
        }
    }

    Text(
        modifier = modifier.padding(top = 16.dp),
        text = name.ifBlank { key.toShortenHex() },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = if (accepted) Color.Unspecified else Color.Gray,
        fontWeight = FontWeight.Bold,
    )
}
