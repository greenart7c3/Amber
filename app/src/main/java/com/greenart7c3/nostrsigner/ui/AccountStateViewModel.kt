package com.greenart7c3.nostrsigner.ui

import android.util.Log
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.BunkerProxy
import com.greenart7c3.nostrsigner.models.TorMode
import com.greenart7c3.nostrsigner.service.BunkerProxyClient
import com.greenart7c3.nostrsigner.service.TorManager
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip06KeyDerivation.Nip06
import com.vitorpamplona.quartz.nip19Bech32.bech32.bechToBytes
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip49PrivKeyEnc.Nip49
import com.vitorpamplona.quartz.utils.Hex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Stable
class AccountStateViewModel(npub: String?) : ViewModel() {
    private val _accountContent = MutableStateFlow<AccountState>(AccountState.LoggedOff)
    val accountContent = _accountContent.asStateFlow()
    private var observerJob: Job? = null

    init {
        tryLoginExistingAccount(null, npub)
    }

    private fun tryLoginExistingAccount(
        route: String?,
        npub: String?,
        forceLogout: Boolean = false,
    ) {
        var currentUser = npub ?: LocalPreferences.currentAccount(Amber.instance)
        val allAccounts = LocalPreferences.allSavedAccounts(Amber.instance)
        if (currentUser != null && !LocalPreferences.containsAccount(Amber.instance, currentUser) && allAccounts.any { it.npub == currentUser }) {
            currentUser = LocalPreferences.currentAccount(Amber.instance)
        }
        if (forceLogout) {
            currentUser = null
        }
        LocalPreferences.loadFromEncryptedStorageSync(Amber.instance, currentUser)?.let {
            startUI(it, route)
        }
        if (currentUser == null) {
            _accountContent.update { AccountState.LoggedOff }
        }
    }

    private fun prepareLogoutOrSwitch() {
        observerJob?.cancel()
        _accountContent.update { AccountState.LoggedOff }
    }

    fun logOff(npub: String) {
        prepareLogoutOrSwitch()
        val shouldLogout = LocalPreferences.updatePrefsForLogout(npub, Amber.instance)
        tryLoginExistingAccount(null, null, forceLogout = shouldLogout)
    }

    fun switchUser(
        npub: String,
        route: String?,
    ) {
        prepareLogoutOrSwitch()
        LocalPreferences.switchToAccount(Amber.instance, npub)
        tryLoginExistingAccount(route, npub)
    }

    fun isValidKey(key: String, password: String): Pair<KeyPair?, String> {
        try {
            val signer =
                if (key.startsWith("ncryptsec")) {
                    val newKey = Nip49().decrypt(key, password)
                    NostrSignerInternal(KeyPair(Hex.decode(newKey)))
                } else if (key.startsWith("nsec")) {
                    NostrSignerInternal(KeyPair(privKey = key.bechToBytes()))
                } else if (key.contains(" ") && Nip06().isValidMnemonic(key)) {
                    val keyPair = KeyPair(privKey = Nip06().privateKeyFromMnemonic(key))
                    NostrSignerInternal(keyPair)
                } else {
                    NostrSignerInternal(KeyPair(Hex.decode(key)))
                }
            return Pair(signer.keyPair, "")
        } catch (e: Exception) {
            return Pair(null, e.message ?: "Unknown error")
        }
    }

    /**
     * Logs in using a remote NIP-46 bunker URI (`bunker://...`).
     *
     * Amber generates a local ephemeral key pair to use as the NIP-46 client,
     * performs the `connect` + `get_public_key` handshake with the remote bunker,
     * and creates a proxy [Account] whose public key is the one reported by the
     * remote bunker.  All subsequent signing/encryption requests for this account
     * will be transparently forwarded to the remote bunker.
     *
     * @return A pair of (success, errorMessage). On success the second element is empty.
     */
    suspend fun loginWithBunker(bunkerUri: String): Pair<Boolean, String> {
        val proxy = BunkerProxy.parse(bunkerUri)
            ?: return Pair(false, "Invalid bunker URI — expected bunker://...")

        if (proxy.relays.isEmpty()) {
            return Pair(false, "Bunker URI contains no relay URLs")
        }

        // Generate a fresh ephemeral key pair to use as the NIP-46 client identity
        val clientKeyPair = KeyPair()
        val clientSigner = NostrSignerInternal(clientKeyPair)

        val userPubKey = BunkerProxyClient.connect(clientSigner, proxy)
            ?: return Pair(false, "Failed to connect to remote bunker — check the URI and that the bunker is online")

        val userPubKeyBytes = try {
            userPubKey.hexToByteArray()
        } catch (_: Exception) {
            return Pair(false, "Remote bunker returned an invalid public key: $userPubKey")
        }

        val account = Account(
            signer = clientSigner,
            hexKey = userPubKey,
            npub = userPubKeyBytes.toNpub(),
            name = MutableStateFlow(""),
            picture = MutableStateFlow(""),
            signPolicy = 1,
            didBackup = true,
            bunkerProxy = proxy,
        )

        if (LocalPreferences.allSavedAccounts(Amber.instance).isEmpty()) {
            LocalPreferences.saveSettingsToEncryptedStorage(Amber.instance.settings)
        }
        LocalPreferences.updatePrefsForLogin(
            context = Amber.instance,
            account = account,
            pubKey = userPubKey,
            privKey = clientKeyPair.privKey!!.toHexKey(),
            seedWords = null,
        )

        startUI(account, null)

        Amber.instance.applicationIOScope.launch {
            Amber.instance.notificationSubscription.updateFilter()
        }

        return Pair(true, "")
    }

    suspend fun startUI(
        keyPair: KeyPair,
        route: String?,
        torMode: TorMode,
        proxyPort: Int,
        signPolicy: Int,
    ) {
        val account = Account(
            hexKey = keyPair.pubKey.toHexKey(),
            npub = keyPair.pubKey.toNpub(),
            name = MutableStateFlow(""),
            picture = MutableStateFlow(""),
            signPolicy = signPolicy,
            didBackup = true,
            signer = NostrSignerInternal(keyPair),
        )

        if (LocalPreferences.allSavedAccounts(Amber.instance).isEmpty()) {
            Amber.instance.settings = Amber.instance.settings.copy(
                torMode = torMode,
                proxyPort = proxyPort,
            )
            LocalPreferences.saveSettingsToEncryptedStorage(
                Amber.instance.settings,
            )
            if (torMode == TorMode.BUILTIN) {
                TorManager.start(Amber.instance, Amber.instance.applicationIOScope)
            }
        }
        LocalPreferences.updatePrefsForLogin(Amber.instance, account, keyPair.pubKey.toHexKey(), keyPair.privKey!!.toHexKey(), null)
        startUI(account, route)
    }

    suspend fun newKey(
        torMode: TorMode,
        proxyPort: Int,
        signPolicy: Int,
        seedWords: Set<String>,
        name: String,
    ) {
        val key = seedWords.joinToString(separator = " ") { it }
        val keyPair = KeyPair(privKey = Nip06().privateKeyFromMnemonic(key))
        val hexKey = keyPair.pubKey.toHexKey()
        val npub = keyPair.pubKey.toNpub()
        val privKey = keyPair.privKey!!.toHexKey()
        val account = Account(
            hexKey = hexKey,
            npub = npub,
            name = MutableStateFlow(name),
            picture = MutableStateFlow(""),
            signPolicy = signPolicy,
            didBackup = false,
            signer = NostrSignerInternal(keyPair),
        )
        if (LocalPreferences.allSavedAccounts(Amber.instance).isEmpty()) {
            Amber.instance.settings = Amber.instance.settings.copy(
                torMode = torMode,
                proxyPort = proxyPort,
            )
            LocalPreferences.saveSettingsToEncryptedStorage(
                Amber.instance.settings,
            )
            if (torMode == TorMode.BUILTIN) {
                TorManager.start(Amber.instance, Amber.instance.applicationIOScope)
            }
            Amber.instance.applicationIOScope.launch {
                Amber.instance.reconnect()
            }
        }
        LocalPreferences.updatePrefsForLogin(Amber.instance, account, hexKey, privKey, key)
        startUI(account, null)
    }

    fun startUI(
        account: Account,
        route: String?,
    ) {
        _accountContent.update { AccountState.LoggedIn(account, route) }

        observerJob?.cancel()
        observerJob = Amber.instance.applicationIOScope.launch(Dispatchers.IO) {
            account.saveable.collect {
                Log.d(Amber.TAG, "Account saved")
                LocalPreferences.saveToEncryptedStorage(Amber.instance, it.account, null, null, null)
            }
        }
    }
}
