package com.greenart7c3.nostrsigner.ui

import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.service.RemoteBunkerClient
import com.greenart7c3.nostrsigner.ui.components.AmberButton
import com.greenart7c3.nostrsigner.ui.navigation.Route
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.utils.RandomInstance
import java.net.URLDecoder
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun BunkerProxyLoginScreen(
    accountViewModel: AccountStateViewModel,
    navHostControllerWrapper: NavHostControllerWrapper,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val tabs = listOf(R.string.bunker_proxy_tab_paste_uri, R.string.bunker_proxy_tab_generate)
    var tabIndex by remember { mutableStateOf(0) }
    val isLoading = remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.bunker_proxy_title),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 16.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.bunker_proxy_description),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))

        if (BuildFlavorChecker.isOfflineFlavor()) {
            Text(
                text = stringResource(R.string.bunker_proxy_offline_unsupported),
                color = MaterialTheme.colorScheme.error,
            )
            return
        }

        SecondaryTabRow(selectedTabIndex = tabIndex) {
            tabs.forEachIndexed { i, res ->
                Tab(
                    selected = tabIndex == i,
                    onClick = { tabIndex = i },
                    text = { Text(stringResource(res)) },
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        when (tabIndex) {
            0 -> PasteBunkerUriTab(accountViewModel, navHostControllerWrapper, isLoading)
            1 -> GenerateNostrConnectUriTab(accountViewModel, navHostControllerWrapper, isLoading)
        }

        if (isLoading.value) {
            Spacer(Modifier.height(16.dp))
            CenterCircularProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.bunker_proxy_connecting),
            )
        }
    }
}

@Composable
private fun PasteBunkerUriTab(
    accountViewModel: AccountStateViewModel,
    navHostControllerWrapper: NavHostControllerWrapper,
    isLoading: MutableState<Boolean>,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uri by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = uri,
        onValueChange = { uri = it },
        label = { Text(stringResource(R.string.bunker_proxy_uri_label)) },
        placeholder = { Text("bunker://...") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
        singleLine = false,
    )
    errorMessage?.let {
        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
    }
    Spacer(Modifier.height(16.dp))

    AmberButton(
        text = stringResource(R.string.bunker_proxy_connect),
        enabled = !isLoading.value,
        onClick = {
            errorMessage = null
            isLoading.value = true
            scope.launch(Dispatchers.IO) {
                try {
                    val parsed = parseBunkerUri(uri)
                    if (parsed == null) {
                        errorMessage = context.getString(R.string.bunker_proxy_invalid_uri)
                        isLoading.value = false
                        return@launch
                    }

                    val localKeyPair = KeyPair(privKey = RandomInstance.bytes(32))
                    val signer = NostrSignerInternal(localKeyPair)

                    // Register the transient signer + subscribe so we receive the bunker's
                    // connect/get_public_key responses before the account is persisted.
                    Amber.instance.proxyResponseSubscription.registerTransientLogin(localKeyPair.privKey!!, parsed.remotePubkey)
                    Amber.instance.proxyResponseSubscription.subscribeForLocalKey(localKeyPair.pubKey.toHexKey(), parsed.remotePubkey, parsed.relays)

                    val connectResp = RemoteBunkerClient.connect(
                        localSigner = signer,
                        remotePubkey = parsed.remotePubkey,
                        relays = parsed.relays,
                        secret = parsed.secret,
                        permissions = "",
                    )
                    if (connectResp == null || !connectResp.error.isNullOrBlank()) {
                        errorMessage = connectResp?.error ?: context.getString(R.string.bunker_proxy_connect_failed)
                        isLoading.value = false
                        return@launch
                    }

                    val pubKeyResp = RemoteBunkerClient.getPublicKey(signer, parsed.remotePubkey, parsed.relays)
                    val remoteUserPubkey = pubKeyResp?.result
                    if (remoteUserPubkey.isNullOrBlank()) {
                        errorMessage = context.getString(R.string.bunker_proxy_no_pubkey)
                        isLoading.value = false
                        return@launch
                    }

                    accountViewModel.startProxyUI(
                        localKeyPair = localKeyPair,
                        remotePubkeyHex = remoteUserPubkey,
                        relays = parsed.relays,
                        bunkerName = "Bunker proxy",
                        nostrConnectSecret = "",
                    )
                    Amber.instance.applicationIOScope.launch {
                        Amber.instance.notificationSubscription.updateFilter()
                        Amber.instance.profileSubscription.updateFilter()
                        Amber.instance.proxyResponseSubscription.updateFilter()
                    }
                    Amber.instance.applicationIOScope.launch(Dispatchers.Main) {
                        navHostControllerWrapper.navController.navigate(Route.Applications.route) {
                            popUpTo(0)
                        }
                    }
                } catch (e: Exception) {
                    errorMessage = e.message ?: context.getString(R.string.bunker_proxy_connect_failed)
                } finally {
                    isLoading.value = false
                }
            }
        },
    )
}

@Composable
private fun GenerateNostrConnectUriTab(
    accountViewModel: AccountStateViewModel,
    navHostControllerWrapper: NavHostControllerWrapper,
    isLoading: MutableState<Boolean>,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val defaultRelays = remember { Amber.instance.settings.defaultRelays.toList() }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val keyPair = remember { KeyPair(privKey = RandomInstance.bytes(32)) }
    val secret = remember { UUID.randomUUID().toString() }
    val nostrConnectUri = remember(keyPair, secret) {
        val relayParams = defaultRelays.joinToString(separator = "&") { "relay=${it.url}" }
        "nostrconnect://${keyPair.pubKey.toHexKey()}?$relayParams&secret=$secret&name=AmberProxy"
    }

    Text(stringResource(R.string.bunker_proxy_generate_explanation))
    Spacer(Modifier.height(16.dp))
    Text(nostrConnectUri, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(16.dp))
    QrCodeDrawer(nostrConnectUri)
    Spacer(Modifier.height(16.dp))
    AmberButton(
        text = stringResource(R.string.copy_to_clipboard),
        onClick = {
            scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", nostrConnectUri)))
                Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
            }
        },
    )
    Spacer(Modifier.height(16.dp))
    errorMessage?.let {
        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
    }
    AmberButton(
        text = stringResource(R.string.bunker_proxy_wait_for_connect),
        enabled = !isLoading.value,
        onClick = {
            errorMessage = null
            isLoading.value = true
            scope.launch(Dispatchers.IO) {
                try {
                    val signer = NostrSignerInternal(keyPair)
                    val pending = Amber.instance.proxyResponseSubscription.awaitInitialConnect(
                        localKeyPair = keyPair,
                        relays = defaultRelays,
                        secret = secret,
                        timeoutMs = 5 * 60 * 1000L,
                    )
                    if (pending == null) {
                        errorMessage = context.getString(R.string.bunker_proxy_connect_failed)
                        isLoading.value = false
                        return@launch
                    }
                    val pubKeyResp = RemoteBunkerClient.getPublicKey(
                        localSigner = signer,
                        remotePubkey = pending,
                        relays = defaultRelays,
                    )
                    val remoteUserPubkey = pubKeyResp?.result
                    if (remoteUserPubkey.isNullOrBlank()) {
                        errorMessage = context.getString(R.string.bunker_proxy_no_pubkey)
                        isLoading.value = false
                        return@launch
                    }

                    accountViewModel.startProxyUI(
                        localKeyPair = keyPair,
                        remotePubkeyHex = remoteUserPubkey,
                        relays = defaultRelays,
                        bunkerName = "Bunker proxy",
                        nostrConnectSecret = secret,
                    )
                    Amber.instance.applicationIOScope.launch {
                        Amber.instance.notificationSubscription.updateFilter()
                        Amber.instance.profileSubscription.updateFilter()
                        Amber.instance.proxyResponseSubscription.updateFilter()
                    }
                    Amber.instance.applicationIOScope.launch(Dispatchers.Main) {
                        navHostControllerWrapper.navController.navigate(Route.Applications.route) {
                            popUpTo(0)
                        }
                    }
                } catch (e: Exception) {
                    errorMessage = e.message ?: context.getString(R.string.bunker_proxy_connect_failed)
                } finally {
                    isLoading.value = false
                }
            }
        },
    )
}

internal data class ParsedBunkerUri(
    val remotePubkey: String,
    val relays: List<NormalizedRelayUrl>,
    val secret: String,
)

internal fun parseBunkerUri(raw: String): ParsedBunkerUri? {
    val trimmed = raw.trim()
    if (!trimmed.startsWith("bunker://")) return null
    val withoutScheme = trimmed.removePrefix("bunker://")
    val parts = withoutScheme.split("?", limit = 2)
    val remotePubkey = parts[0].trim()
    if (remotePubkey.length != 64) return null
    val relays = mutableListOf<NormalizedRelayUrl>()
    var secret = ""
    if (parts.size == 2) {
        parts[1].split("&").forEach { kv ->
            val eq = kv.indexOf('=')
            if (eq <= 0) return@forEach
            val k = kv.substring(0, eq)
            val v = try {
                URLDecoder.decode(kv.substring(eq + 1), "utf-8")
            } catch (_: Exception) {
                kv.substring(eq + 1)
            }
            when (k) {
                "relay" -> RelayUrlNormalizer.normalizeOrNull(v)?.let { relays.add(it) }
                "secret" -> secret = v
            }
        }
    }
    if (relays.isEmpty()) return null
    return ParsedBunkerUri(remotePubkey, relays, secret)
}
