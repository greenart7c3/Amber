package com.greenart7c3.nostrsigner.ui.theme

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.greenart7c3.nostrsigner.models.Account
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Multipreview annotation that renders a composable in both the light and
 * dark variants of [NostrSignerTheme].
 */
@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class ThemePreviews

/**
 * Shared wrapper for component previews: applies [NostrSignerTheme] and paints
 * the theme background behind the content, so previews look like the real app
 * in both themes.
 *
 * Previews must only compose components that don't reach global state
 * ([com.greenart7c3.nostrsigner.Amber.instance] or an
 * [com.greenart7c3.nostrsigner.models.Account]) during composition — the
 * Application singleton doesn't exist in the preview renderer.
 */
@Composable
fun AmberPreview(content: @Composable () -> Unit) {
    NostrSignerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            content()
        }
    }
}

private const val PREVIEW_PUBKEY = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"

/**
 * A read-only fake [Account] for previews of account-bound components. Built
 * from a public key only, so no private key, native secp256k1 code, or the
 * [com.greenart7c3.nostrsigner.Amber] singleton is touched at render time.
 */
fun previewAccount(name: String = "Preview Account"): Account = Account(
    signer = NostrSignerInternal(KeyPair(pubKey = PREVIEW_PUBKEY.hexToByteArray())),
    hexKey = PREVIEW_PUBKEY,
    npub = PREVIEW_PUBKEY.hexToByteArray().toNpub(),
    name = MutableStateFlow(name),
    picture = MutableStateFlow(""),
    signPolicy = 1,
    didBackup = true,
    scope = CoroutineScope(Dispatchers.Default),
)
