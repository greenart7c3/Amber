package com.greenart7c3.nostrsigner.ui.theme

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.greenart7c3.nostrsigner.data.SettingsStore
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.AmberSettings
import com.greenart7c3.nostrsigner.models.ThemeMode
import com.greenart7c3.nostrsigner.ui.LocalSettingsStore
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Multipreview annotation that renders a composable in both the light and
 * dark variants of [NostrSignerTheme].
 */
@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class PreviewsLightDark

/**
 * Shared wrapper for component previews: applies [NostrSignerTheme] and paints
 * the theme background behind the content, so previews look like the real app
 * in both themes.
 *
 * Previews get an in-memory [LocalSettingsStore]; they must not compose components
 * that reach the other app dependencies (databases, relays, the Application) or
 * an [com.greenart7c3.nostrsigner.models.Account]'s keys during composition.
 */
@Composable
fun AmberPreview(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val inMemorySettingsStore = remember {
        object : SettingsStore {
            override var settings = AmberSettings()
            override val themeMode = MutableStateFlow(ThemeMode.SYSTEM)
        }
    }
    CompositionLocalProvider(LocalSettingsStore provides inMemorySettingsStore) {
        NostrSignerTheme {
            Surface(modifier = modifier, color = MaterialTheme.colorScheme.background) {
                content()
            }
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
    name = MutableStateFlow(name),
    picture = MutableStateFlow(""),
    signPolicy = 1,
    didBackup = true,
    scope = CoroutineScope(Dispatchers.Default),
)
