package com.greenart7c3.nostrsigner.ui.actions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.toLowerCase
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.database.AppDatabase
import com.greenart7c3.nostrsigner.database.HistoryEntity
import com.greenart7c3.nostrsigner.models.Permission
import com.greenart7c3.nostrsigner.models.TimeUtils.convertLongToDateTime
import com.greenart7c3.nostrsigner.ui.components.CloseButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun ActivityDialog(
    database: AppDatabase,
    key: String,
    onClose: () -> Unit,
) {
    val activities =
        remember {
            mutableStateListOf<HistoryEntity>()
        }

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            activities.clear()
            activities.addAll(
                database.applicationDao().getAllHistory(key).toMutableStateList(),
            )
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier =
            Modifier
                .fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .fillMaxSize(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CloseButton {
                        onClose()
                    }
                }

                if (activities.isEmpty()) {
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

                LazyColumn(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(),
                ) {
                    items(activities.size) {
                        val activity = activities[it]
                        val permission =
                            Permission(
                                activity.type.toLowerCase(Locale.current),
                                activity.kind,
                            )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                Modifier
                                    .weight(0.9f)
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    if (permission.type == "connect") "Connect" else permission.toString(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    convertLongToDateTime(activity.time * 1000),
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(onClick = { }) {
                                Icon(
                                    if (activity.accepted) Icons.Default.Check else Icons.Default.Close,
                                    contentDescription = if (activity.accepted) stringResource(R.string.accepted) else stringResource(R.string.rejected),
                                    tint = if (activity.accepted) Color.Green else Color.Red,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
