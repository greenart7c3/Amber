package com.greenart7c3.nostrsigner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.toLowerCase
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.database.AppDatabase
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.Permission
import com.greenart7c3.nostrsigner.models.TimeUtils
import com.greenart7c3.nostrsigner.models.supportedKindNumbers
import com.greenart7c3.nostrsigner.service.ApplicationNameCache
import com.greenart7c3.nostrsigner.service.toShortenHex
import com.greenart7c3.nostrsigner.ui.components.SimpleSearchBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivitiesScreen(
    modifier: Modifier,
    paddingValues: PaddingValues,
    topPadding: Dp,
    account: Account,
) {
    val database = Amber.instance.getHistoryDatabase(account.npub)
    val activities = database.dao().getAllHistory().collectAsStateWithLifecycle(emptyList())
    val context = LocalContext.current
    // State for the search query
    var searchQuery by remember { mutableStateOf("") }

    // Filtered activities based on the search query
    val filteredActivities = activities.value.filter { activity ->
        if (searchQuery.isEmpty()) {
            true
        } else {
            val permission = Permission(activity.type.toLowerCase(Locale.current), activity.kind)
            permission.toLocalizedString(context, true).contains(searchQuery, ignoreCase = true)
        }
    }
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
            item {
                if (filteredActivities.isEmpty()) {
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

            items(filteredActivities) { activity ->
                val permission =
                    Permission(
                        activity.type.toLowerCase(Locale.current),
                        activity.kind,
                    )
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
                                database = Amber.instance.getDatabase(account.npub),
                                account = account,
                            )

                            Text(
                                text = if (permission.type == "connect") stringResource(R.string.connect) else permission.toLocalizedString(context),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (activity.accepted) Color.Unspecified else Color.Gray,
                            )

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
                            contentDescription = if (activity.accepted) stringResource(R.string.accepted) else stringResource(R.string.rejected),
                            tint = if (activity.accepted) Color(0xFF1D8802) else Color(0xFFFF6B00),
                            modifier = Modifier.padding(start = 10.dp, top = 4.dp, bottom = 16.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
fun ApplicationName(
    key: String,
    accepted: Boolean,
    database: AppDatabase,
    account: Account,
) {
    var name by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            if (ApplicationNameCache.names["${account.npub.toShortenHex()}-$key"] == null) {
                val app = database.applicationDao().getByKey(key)
                app?.let {
                    name = it.application.name
                    ApplicationNameCache.names["${account.npub.toShortenHex()}-$key"] = it.application.name
                }
            } else {
                ApplicationNameCache.names["${account.npub.toShortenHex()}-$key"]?.let {
                    name = it
                }
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
