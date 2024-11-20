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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.database.AppDatabase
import com.greenart7c3.nostrsigner.models.Permission
import com.greenart7c3.nostrsigner.models.TimeUtils

@Composable
fun ActivityScreen(
    modifier: Modifier,
    paddingValues: PaddingValues,
    database: AppDatabase,
    key: String,
) {
    val activities = database.applicationDao().getAllHistory(key).collectAsStateWithLifecycle(emptyList())
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier,
        contentPadding = paddingValues,
    ) {
        item {
            if (activities.value.isEmpty()) {
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

        items(activities.value) { activity ->
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
                        Text(
                            modifier = Modifier.padding(top = 16.dp),
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
