package com.greenart7c3.nostrsigner.ui

import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.SavedAccounts
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.builtin_tor_active
import com.greenart7c3.nostrsigner.commonsui.builtin_tor_description
import com.greenart7c3.nostrsigner.commonsui.builtin_tor_title
import com.greenart7c3.nostrsigner.commonsui.connect_through_your_orbot_setup_markdown2
import com.greenart7c3.nostrsigner.commonsui.connect_via_tor_short
import com.greenart7c3.nostrsigner.commonsui.finish
import com.greenart7c3.nostrsigner.commonsui.orbot_socks_port
import com.greenart7c3.nostrsigner.commonsui.orbot_title
import com.greenart7c3.nostrsigner.commonsui.sign_policy_basic
import com.greenart7c3.nostrsigner.commonsui.sign_policy_basic_explainer
import com.greenart7c3.nostrsigner.commonsui.sign_policy_explainer
import com.greenart7c3.nostrsigner.commonsui.sign_policy_manual
import com.greenart7c3.nostrsigner.commonsui.sign_policy_manual_explainer
import com.greenart7c3.nostrsigner.commonsui.use_builtin_tor
import com.greenart7c3.nostrsigner.commonsui.use_orbot
import com.greenart7c3.nostrsigner.models.TorMode
import com.greenart7c3.nostrsigner.ui.components.AmberButton
import com.greenart7c3.nostrsigner.ui.components.MarkdownText
import com.greenart7c3.nostrsigner.ui.components.TitleExplainer
import org.jetbrains.compose.resources.stringResource

/** What a new account is set up with: how much it signs on its own and how it reaches relays. */
data class ConnectionSetup(val signPolicy: Int, val torMode: TorMode, val proxyPort: Int)

private const val DEFAULT_PROXY_PORT = 9050

/** The SOCKS port to save: the typed one for Orbot (null when it is not a number), else the default. */
internal fun proxyPortFor(torMode: TorMode, typed: String): Int? = if (torMode == TorMode.ORBOT) typed.toIntOrNull() else DEFAULT_PROXY_PORT

/**
 * The last step of adding or creating an account: the sign policy and, for the first
 * account only (Tor is an app-wide setting), how to connect through Tor.
 */
@Composable
fun ConnectionSetupPage(
    contentPadding: PaddingValues,
    horizontalPadding: Dp,
    onFinish: (ConnectionSetup) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var signPolicy by remember { mutableIntStateOf(0) }
    var torMode by remember { mutableStateOf(TorMode.DISABLED) }
    var proxyPort by remember { mutableStateOf(TextFieldValue(DEFAULT_PROXY_PORT.toString())) }
    val scrollState = rememberScrollState()

    Column(
        modifier
            .fillMaxSize()
            .verticalScrollbar(scrollState)
            .verticalScroll(scrollState)
            .padding(contentPadding)
            .padding(horizontal = horizontalPadding)
            .padding(top = horizontalPadding * 1.5f),
    ) {
        Text(text = stringResource(Res.string.sign_policy_explainer))
        SignPolicyOptions(signPolicy, onSelect = { signPolicy = it }, modifier = Modifier.padding(vertical = 10.dp))
        if (SavedAccounts.all(context).isEmpty()) {
            TorSetup(torMode, onTorMode = { torMode = it }, proxyPort, onProxyPort = { proxyPort = it })
        }
        AmberButton(
            modifier = Modifier.padding(vertical = 20.dp),
            onClick = {
                val port = proxyPortFor(torMode, proxyPort.text)
                if (port == null) {
                    Toast.makeText(context, "Invalid port number", Toast.LENGTH_SHORT).show()
                } else {
                    onFinish(ConnectionSetup(signPolicy, torMode, port))
                }
            },
            text = stringResource(Res.string.finish),
        )
    }
}

@Composable
private fun SignPolicyOptions(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val options = listOf(
        TitleExplainer(stringResource(Res.string.sign_policy_basic), stringResource(Res.string.sign_policy_basic_explainer)),
        TitleExplainer(stringResource(Res.string.sign_policy_manual), stringResource(Res.string.sign_policy_manual_explainer)),
    )
    Column(modifier) {
        options.forEachIndexed { index, option ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = if (selected == index) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .selectable(selected = selected == index, onClick = { onSelect(index) })
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected == index, onClick = { onSelect(index) })
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = option.title, modifier = Modifier.padding(start = 16.dp), style = MaterialTheme.typography.titleLarge)
                    option.explainer?.let { Text(text = it, modifier = Modifier.padding(start = 16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun TorSetup(
    torMode: TorMode,
    onTorMode: (TorMode) -> Unit,
    proxyPort: TextFieldValue,
    onProxyPort: (TextFieldValue) -> Unit,
) {
    Column {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(vertical = 20.dp)
                .clickable { onTorMode(if (torMode == TorMode.DISABLED) TorMode.BUILTIN else TorMode.DISABLED) },
        ) {
            Switch(
                modifier = Modifier.scale(0.85f),
                checked = torMode != TorMode.DISABLED,
                onCheckedChange = { onTorMode(if (it) TorMode.BUILTIN else TorMode.DISABLED) },
            )
            Text(modifier = Modifier.weight(1f).padding(start = 8.dp), text = stringResource(Res.string.connect_via_tor_short))
        }

        if (torMode != TorMode.DISABLED) {
            Text(text = stringResource(Res.string.builtin_tor_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = stringResource(Res.string.builtin_tor_description), style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            AmberButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onTorMode(TorMode.BUILTIN) },
                text = stringResource(if (torMode == TorMode.BUILTIN) Res.string.builtin_tor_active else Res.string.use_builtin_tor),
                enabled = torMode != TorMode.BUILTIN,
            )
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))
            OrbotSetup(torMode, onTorMode, proxyPort, onProxyPort)
        }
    }
}

@Composable
private fun OrbotSetup(
    torMode: TorMode,
    onTorMode: (TorMode) -> Unit,
    proxyPort: TextFieldValue,
    onProxyPort: (TextFieldValue) -> Unit,
) {
    Column {
        Text(text = stringResource(Res.string.orbot_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        MarkdownText(markdown = stringResource(Res.string.connect_through_your_orbot_setup_markdown2), modifier = Modifier.padding(vertical = 8.dp))
        OutlinedTextField(
            value = proxyPort,
            onValueChange = onProxyPort,
            label = { Text(text = stringResource(Res.string.orbot_socks_port), color = TextFieldDefaults.colors().unfocusedPlaceholderColor) },
            placeholder = { Text(stringResource(Res.string.orbot_socks_port), color = TextFieldDefaults.colors().unfocusedPlaceholderColor) },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Next,
                keyboardType = KeyboardType.Number,
            ),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )
        AmberButton(
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
            onClick = { onTorMode(TorMode.ORBOT) },
            text = stringResource(Res.string.use_orbot) + if (torMode == TorMode.ORBOT) " ✓" else "",
            enabled = torMode != TorMode.ORBOT,
        )
    }
}
