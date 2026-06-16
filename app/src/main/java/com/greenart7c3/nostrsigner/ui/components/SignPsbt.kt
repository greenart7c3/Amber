package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.service.DecodedPsbt
import com.greenart7c3.nostrsigner.service.DecodedPsbtOutput
import com.greenart7c3.nostrsigner.ui.RememberType

@Composable
fun SignPsbt(
    modifier: Modifier,
    psbtHex: String,
    decoded: DecodedPsbt,
    shouldRunOnAccept: Boolean?,
    packageName: String?,
    onAccept: (RememberType) -> Unit,
    onReject: (RememberType) -> Unit,
) {
    var rememberType by remember { mutableStateOf(RememberType.NEVER) }

    Column(modifier) {
        LocalAppIcon(packageName)

        val message = stringResource(R.string.sign_psbt)

        Text(
            buildAnnotatedString {
                append(stringResource(R.string.requests_message, message))
            },
            fontSize = 18.sp,
        )
        Spacer(Modifier.size(8.dp))

        PsbtBody(psbtHex = psbtHex, decoded = decoded)

        Spacer(Modifier.weight(1f))

        RememberMyChoice(
            shouldRunOnAccept,
            packageName,
            false,
            onAccept,
            onReject,
        ) {
            rememberType = it
        }

        AcceptRejectButtons(
            onAccept = { onAccept(rememberType) },
            onReject = { onReject(rememberType) },
        )
    }
}

@Composable
fun BunkerSignPsbt(
    modifier: Modifier,
    psbtHex: String,
    decoded: DecodedPsbt,
    shouldRunOnAccept: Boolean?,
    appName: String,
    onAccept: (RememberType) -> Unit,
    onReject: (RememberType) -> Unit,
    iconUrl: String = "",
) {
    var rememberType by remember { mutableStateOf(RememberType.NEVER) }

    Column(modifier) {
        RemoteAppIcon(iconUrl, appName)

        val message = stringResource(R.string.sign_psbt)

        Text(
            buildAnnotatedString {
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(appName)
                }
                append(" requests $message")
            },
            fontSize = 18.sp,
        )
        Spacer(Modifier.size(8.dp))

        PsbtBody(psbtHex = psbtHex, decoded = decoded)

        Spacer(Modifier.weight(1f))

        RememberMyChoice(
            shouldRunOnAccept,
            null,
            true,
            onAccept,
            onReject,
        ) {
            rememberType = it
        }

        AcceptRejectButtons(
            onAccept = { onAccept(rememberType) },
            onReject = { onReject(rememberType) },
        )
    }
}

@Composable
private fun PsbtBody(psbtHex: String, decoded: DecodedPsbt) {
    if (decoded.parseError != null) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(8.dp)) {
                Text(
                    stringResource(R.string.psbt_decode_error, decoded.parseError),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Spacer(Modifier.size(8.dp))
        RawPsbtCard(psbtHex)
        return
    }

    val sending = decoded.outputs.filterNot { it.isChange }
    val change = decoded.outputs.filter { it.isChange }

    if (sending.isNotEmpty()) {
        SectionCard(
            title = stringResource(R.string.psbt_sending),
            totalSats = decoded.totalSent,
            entries = sending,
        )
        Spacer(Modifier.size(8.dp))
    }

    if (change.isNotEmpty()) {
        SectionCard(
            title = stringResource(R.string.psbt_change),
            totalSats = decoded.totalChange,
            entries = change,
        )
        Spacer(Modifier.size(8.dp))
    }

    decoded.feeSats?.let {
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.psbt_fee), fontWeight = FontWeight.Bold)
                Text(formatSats(it))
            }
        }
        Spacer(Modifier.size(8.dp))
    }

    RawPsbtCard(psbtHex)
}

@Composable
private fun SectionCard(title: String, totalSats: Long, entries: List<DecodedPsbtOutput>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(formatSats(totalSats), fontWeight = FontWeight.Bold)
            }
            entries.forEach { output ->
                Spacer(Modifier.size(6.dp))
                Column {
                    Text(
                        output.address ?: output.scriptPubKeyHex,
                        fontSize = 13.sp,
                    )
                    Text(
                        formatSats(output.valueSats),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RawPsbtCard(psbtHex: String) {
    var expanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.psbt_raw), fontWeight = FontWeight.Bold)
                Text(
                    text = if (expanded) "Hide" else "Show",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (expanded) {
                Text(
                    psbtHex,
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 12.sp,
                )
            }
        }
    }
}

private fun formatSats(sats: Long): String = "$sats sats"
