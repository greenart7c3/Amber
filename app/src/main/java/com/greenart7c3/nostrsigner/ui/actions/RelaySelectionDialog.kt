package com.greenart7c3.nostrsigner.ui.actions

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.greenart7c3.nostrsigner.service.relays.Relay
import kotlinx.coroutines.launch

data class RelayList(
    val relay: Relay,
    val isSelected: Boolean
)

val ButtonBorder = RoundedCornerShape(20.dp)
val Size20Modifier = Modifier.size(20.dp)

@Composable
fun CloseButton(onCancel: () -> Unit) {
    Button(
        onClick = {
            onCancel()
        },
        shape = ButtonBorder,
        colors = ButtonDefaults
            .buttonColors(
                backgroundColor = Color.Gray
            )
    ) {
        CloseIcon()
    }
}

@Composable
fun PostButton(modifier: Modifier = Modifier, onPost: () -> Unit = {}, isActive: Boolean) {
    Button(
        modifier = modifier,
        onClick = {
            if (isActive) {
                onPost()
            }
        },
        shape = ButtonBorder,
        colors = ButtonDefaults
            .buttonColors(
                backgroundColor = if (isActive) MaterialTheme.colors.primary else Color.Gray
            ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(text = "Post", color = Color.White)
    }
}

@Composable
fun CloseIcon() {
    Icon(
        Icons.Default.Cancel,
        contentDescription = "Cancel",
        modifier = Size20Modifier,
        tint = Color.White
    )
}

@Composable
fun RelaySelectionDialog(
    list: List<Relay>,
    selectRelays: List<Relay>,
    onClose: () -> Unit,
    onPost: (list: List<Relay>) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var relays by remember {
        mutableStateOf(
            list.map {
                RelayList(
                    it,
                    if (selectRelays.isNotEmpty()) selectRelays.any { relay -> it.url == relay.url } else true
                )
            }
        )
    }

    var selected by remember {
        mutableStateOf(true)
    }

    Dialog(
        onDismissRequest = { onClose() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(start = 10.dp, end = 10.dp, top = 10.dp)

            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CloseButton(
                        onCancel = {
                            onClose()
                        }
                    )

                    PostButton(
                        onPost = {
                            val selectedRelays = relays.filter { it.isSelected }
                            if (selectedRelays.isEmpty()) {
                                scope.launch {
                                    Toast.makeText(context, "Select a relay to continue", Toast.LENGTH_SHORT).show()
                                }
                                return@PostButton
                            }
                            onPost(selectedRelays.map { it.relay })
                            onClose()
                        },
                        isActive = true
                    )
                }

                RelaySwitch(
                    text = "Select/Deselect all",
                    checked = selected,
                    onClick = {
                        selected = !selected
                        relays = relays.mapIndexed { _, item ->
                            item.copy(isSelected = selected)
                        }
                    }
                )

                LazyColumn(
                    contentPadding = PaddingValues(
                        top = 10.dp,
                        bottom = 10.dp
                    )
                ) {
                    itemsIndexed(
                        relays,
                        key = { _, item -> item.relay.url }
                    ) { index, item ->
                        RelaySwitch(
                            text = item.relay.url
                                .removePrefix("ws://")
                                .removePrefix("wss://")
                                .removeSuffix("/"),
                            checked = item.isSelected,
                            onClick = {
                                relays = relays.mapIndexed { j, item ->
                                    if (index == j) {
                                        item.copy(isSelected = !item.isSelected)
                                    } else {
                                        item
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RelaySwitch(text: String, checked: Boolean, onClick: () -> Unit, onLongPress: () -> Unit = { }) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            )
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = text
        )
        Switch(
            checked = checked,
            onCheckedChange = {
                onClick()
            }
        )
    }
}
