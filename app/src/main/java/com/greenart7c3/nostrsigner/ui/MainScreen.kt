package com.greenart7c3.nostrsigner.ui

import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.DrawerValue
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.rememberDrawerState
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.TimeUtils
import com.greenart7c3.nostrsigner.models.toHexKey
import com.greenart7c3.nostrsigner.service.CryptoUtils
import com.greenart7c3.nostrsigner.service.getAppCompatActivity
import com.greenart7c3.nostrsigner.service.model.Event
import com.greenart7c3.nostrsigner.service.toShortenHex
import com.greenart7c3.nostrsigner.ui.components.Drawer
import com.greenart7c3.nostrsigner.ui.components.MainAppBar
import com.greenart7c3.nostrsigner.ui.theme.ButtonBorder
import com.greenart7c3.nostrsigner.ui.theme.NostrSignerTheme
import kotlinx.coroutines.launch
import org.json.JSONObject

@OptIn(ExperimentalStdlibApi::class)
@SuppressLint("UnusedMaterialScaffoldPaddingParameter")
@Composable
fun MainScreen(account: Account, accountStateViewModel: AccountStateViewModel, json: IntentData?, packageName: String?) {
    val scaffoldState = rememberScaffoldState(rememberDrawerState(DrawerValue.Closed))
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    Scaffold(
        scaffoldState = scaffoldState,
        drawerContent = {
            Drawer(
                accountStateViewModel = accountStateViewModel,
                account = account,
                scaffoldState = scaffoldState
            )
        },
        topBar = {
            MainAppBar(
                scaffoldState,
                accountStateViewModel,
                account
            )
        }
    ) {
        if (json == null) {
            Column(
                Modifier.fillMaxSize(),
                Arrangement.Center,
                Alignment.CenterHorizontally
            ) {
                Text("No event to sign")
            }
        } else {
            json.let {
                val event = it.data

                EventData(
                    packageName ?: it.name,
                    event,
                    event.toJson(),
                    {
                        if (event.pubKey != account.keyPair.pubKey.toHexKey()) {
                            coroutineScope.launch {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.event_pubkey_is_not_equal_to_current_logged_in_user),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            return@EventData
                        }

                        val id = event.id.hexToByteArray()
                        val sig = CryptoUtils.sign(id, account.keyPair.privKey!!).toHexKey()

                        clipboardManager.setText(AnnotatedString(sig))

                        coroutineScope.launch {
                            val activity = context.getAppCompatActivity()
                            if (packageName != null) {
                                val intent = Intent()
                                intent.putExtra("signature", sig)
                                activity?.setResult(RESULT_OK, intent)
                            } else {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.signature_copied_to_the_clipboard),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            activity?.finish()
                        }
                    },
                    {
                        context.getAppCompatActivity()?.finish()
                    }
                )
            }
        }
    }
}

@Composable
fun EventData(
    appName: String,
    event: Event,
    rawJson: String,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    var showMore by remember {
        mutableStateOf(false)
    }
    var checked by remember {
        mutableStateOf(false)
    }
    val scroll = rememberScrollState()
    val eventDescription = event.description()

    Column(
        Modifier
            .fillMaxSize()
            .padding(8.dp)
            .verticalScroll(scroll)
    ) {
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = appName,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.size(4.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Column(Modifier.padding(6.dp)) {
                Text(
                    "wants you to sign a $eventDescription",
                    fontWeight = FontWeight.Bold
                )

                EventRow(
                    Icons.Default.CalendarMonth,
                    "Kind ",
                    "${event.kind}"
                )

                EventRow(
                    Icons.Default.AccessTime,
                    "Created at ",
                    TimeUtils.format(event.createdAt)
                )

                EventRow(
                    Icons.Default.Person,
                    "Signed by ",
                    event.pubKey.toShortenHex()
                )

                EventRow(
                    Icons.Default.ContentPaste,
                    "Content ",
                    event.content
                )

                Divider(
                    modifier = Modifier.padding(top = 15.dp),
                    thickness = Dp.Hairline
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Button(
                shape = ButtonBorder,
                onClick = {
                    showMore = !showMore
                }
            ) {
                Text(text = if (!showMore) "Show Details" else "Hide Details")
            }
        }
        if (showMore) {
            Text(
                modifier = Modifier.padding(5.dp),
                text = JSONObject(rawJson).toString(2)
            )
        }
        Spacer(modifier = Modifier.weight(1f))
//        Row(
//            horizontalArrangement = Arrangement.SpaceBetween,
//            verticalAlignment = Alignment.CenterVertically,
//            modifier = Modifier
//                .clickable {
//                    checked = !checked
//                }
//        ) {
//            Text(
//                modifier = Modifier.weight(1f),
//                text = "Remember my choice and don't ask again"
//            )
//            Switch(
//                checked = checked,
//                onCheckedChange = {
//                    checked = !checked
//                }
//            )
//        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(10.dp),
            Arrangement.Center
        ) {
            Button(
                shape = ButtonBorder,
                onClick = onReject
            ) {
                Text("Reject")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                shape = ButtonBorder,
                onClick = onAccept
            ) {
                Text("Accept")
            }
        }
    }
}

@Composable
fun EventRow(
    icon: ImageVector,
    title: String,
    content: String
) {
    Divider(
        modifier = Modifier.padding(vertical = 15.dp),
        thickness = Dp.Hairline
    )

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Icon(
            icon,
            null,
            tint = MaterialTheme.colors.onBackground
        )
        Text(
            modifier = Modifier.padding(start = 8.dp),
            text = "$title ",
            fontWeight = FontWeight.Bold
        )
        Text(
            content,
            color = Color.Gray,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@SuppressLint("UnusedMaterialScaffoldPaddingParameter")
@Preview(device = "id:Nexus S")
@Composable
fun Preview() {
    val event = Event("123", "7579076d9aff0a4cfdefa7e2045f2486c7e5d8bc63bfc6b45397233e1bbfcb19", TimeUtils.now(), 1, listOf(), "This is a test 123 123 123 123 123 123", "")
    val data = event.toJson()

    NostrSignerTheme(darkTheme = true) {
        Scaffold {
            EventData(
                "App",
                event,
                data,
                { },
                { }
            )
        }
    }
}
