package com.greenart7c3.nostrsigner.desktop.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.common.relayHostAndPort
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.add
import com.greenart7c3.nostrsigner.commonsui.add_relay_to_whitelist
import com.greenart7c3.nostrsigner.commonsui.remove
import com.greenart7c3.nostrsigner.desktop.core.SettingsStore
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ColumnScope.AuthWhitelistSection() {
    val settings by SettingsStore.settings.collectAsState()
    var input by remember { mutableStateOf("") }

    fun addEntry() {
        val host = relayHostAndPort(input)
        if (host.isNotBlank() && host !in settings.authWhitelist) {
            SettingsStore.update { it.copy(authWhitelist = it.authWhitelist + host) }
        }
        input = ""
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text(stringResource(Res.string.add_relay_to_whitelist)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        AmberOutlinedButton(
            text = stringResource(Res.string.add),
            enabled = relayHostAndPort(input).isNotBlank(),
            onClick = { addEntry() },
        )
    }
    settings.authWhitelist.forEach { host ->
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(host, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            AmberTextButton(
                text = stringResource(Res.string.remove),
                onClick = { SettingsStore.update { it.copy(authWhitelist = it.authWhitelist - host) } },
            )
        }
        HorizontalDivider()
    }
}
