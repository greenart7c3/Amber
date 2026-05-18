package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
    var rememberType by remember {
        mutableStateOf(RememberType.NEVER)
    }

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
) {
    var rememberType by remember {
        mutableStateOf(RememberType.NEVER)
    }

    Column(modifier) {
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

    SummaryCard(decoded)
    Spacer(Modifier.size(8.dp))
    InputsCard(decoded)
    Spacer(Modifier.size(8.dp))
    OutputsCard(decoded)
    Spacer(Modifier.size(8.dp))
    RawPsbtCard(psbtHex)
}

@Composable
private fun SummaryCard(decoded: DecodedPsbt) {
    val totalOut = decoded.outputs.sumOf { it.valueSats }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp)) {
            Text(
                stringResource(
                    R.string.psbt_summary,
                    decoded.controlledInputCount,
                    decoded.inputs.size,
                ),
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.size(4.dp))
            LabeledRow(stringResource(R.string.psbt_total_out), formatSats(totalOut))
            decoded.feeSats?.let {
                LabeledRow(stringResource(R.string.psbt_fee), formatSats(it))
            }
            LabeledRow("Version", decoded.version.toString())
            if (decoded.lockTime != 0L) {
                LabeledRow("Locktime", decoded.lockTime.toString())
            }
        }
    }
}

@Composable
private fun InputsCard(decoded: DecodedPsbt) {
    var expanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${stringResource(R.string.psbt_inputs)} (${decoded.inputs.size})",
                    fontWeight = FontWeight.Bold,
                )
                ToggleText(expanded) { expanded = !expanded }
            }
            if (expanded) {
                decoded.inputs.forEachIndexed { i, input ->
                    if (i > 0) Spacer(Modifier.size(8.dp))
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("#$i", fontWeight = FontWeight.SemiBold)
                            if (input.controlled) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Text(
                                        stringResource(R.string.psbt_input_signs_label),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                        LabeledRow(
                            stringResource(R.string.psbt_outpoint),
                            "${input.prevTxid}:${input.prevVout}",
                        )
                        LabeledRow(
                            "Value",
                            input.valueSats?.let { formatSats(it) }
                                ?: stringResource(R.string.psbt_unknown_value),
                        )
                        input.address?.let {
                            LabeledRow(stringResource(R.string.psbt_address), it)
                        } ?: input.scriptPubKeyHex?.let {
                            LabeledRow(stringResource(R.string.psbt_script), it)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OutputsCard(decoded: DecodedPsbt) {
    var expanded by remember { mutableStateOf(true) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${stringResource(R.string.psbt_outputs)} (${decoded.outputs.size})",
                    fontWeight = FontWeight.Bold,
                )
                ToggleText(expanded) { expanded = !expanded }
            }
            if (expanded) {
                decoded.outputs.forEachIndexed { i, output ->
                    if (i > 0) Spacer(Modifier.size(8.dp))
                    Column {
                        Text("#$i", fontWeight = FontWeight.SemiBold)
                        LabeledRow("Value", formatSats(output.valueSats))
                        output.address?.let {
                            LabeledRow(stringResource(R.string.psbt_address), it)
                        } ?: LabeledRow(stringResource(R.string.psbt_script), output.scriptPubKeyHex)
                    }
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
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.psbt_raw), fontWeight = FontWeight.Bold)
                ToggleText(expanded) { expanded = !expanded }
            }
            if (expanded) {
                Text(
                    psbtHex,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun LabeledRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(value, fontSize = 14.sp)
    }
}

@Composable
private fun ToggleText(expanded: Boolean, onClick: () -> Unit) {
    Text(
        text = if (expanded) "Hide" else "Show",
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.primary,
    )
}

private fun formatSats(sats: Long): String = "$sats sats"
