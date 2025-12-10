package com.greenart7c3.nostrsigner.ui.actions

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
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.TimeUtils
import com.greenart7c3.nostrsigner.models.supportedKindNumbers
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
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    val filteredActivities = if (searchQuery.isEmpty()) Amber.instance.getHistoryDatabase(account.npub).dao().getAllHistory(key).collectAsStateWithLifecycle(emptyList()) else Amber.instance.getHistoryDatabase(account.npub).dao().searchAllHistory(key, searchQuery.toLowerCase(Locale.current)).collectAsStateWithLifecycle(emptyList())
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
                if (filteredActivities.value.isEmpty()) {
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

            items(filteredActivities.value) { activity ->
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
                            Text(
                                modifier = Modifier.padding(top = 16.dp),
                                text = activity.translatedPermission,
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
