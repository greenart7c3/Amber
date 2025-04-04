package com.greenart7c3.nostrsigner.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.NostrSigner
import com.greenart7c3.nostrsigner.models.Account
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip06KeyDerivation.Nip06
import com.vitorpamplona.quartz.nip19Bech32.bech32.bechToBytes
import com.vitorpamplona.quartz.nip49PrivKeyEnc.Nip49
import fr.acinq.secp256k1.Hex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
open class ToastMsg

@Immutable
class StringToastMsg(val title: String, val msg: String) : ToastMsg()

@Immutable
class ConfirmationToastMsg(val title: String, val msg: String, val onOk: () -> Unit) : ToastMsg()

@Immutable
class AcceptRejectToastMsg(val title: String, val msg: String, val onAccept: () -> Unit, val onReject: () -> Unit) : ToastMsg()

@Immutable
class ResourceToastMsg(
    val titleResId: Int,
    val resourceId: Int,
    val params: Array<out String>? = null,
) : ToastMsg()

@Stable
class AccountStateViewModel(npub: String?) : ViewModel() {
    private val _accountContent = MutableStateFlow<AccountState>(AccountState.LoggedOff)
    val accountContent = _accountContent.asStateFlow()
    val toasts = MutableSharedFlow<ToastMsg?>(0, 3, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
        tryLoginExistingAccount(null, npub)
    }

    fun clearToasts() {
        viewModelScope.launch { toasts.emit(null) }
    }

    fun toast(
        title: String,
        message: String,
    ) {
        viewModelScope.launch { toasts.emit(StringToastMsg(title, message)) }
    }

    fun toast(
        title: String,
        message: String,
        onOk: () -> Unit,
    ) {
        viewModelScope.launch { toasts.emit(ConfirmationToastMsg(title, message, onOk)) }
    }

    fun toast(
        title: String,
        message: String,
        onAccept: () -> Unit,
        onReject: () -> Unit,
    ) {
        viewModelScope.launch { toasts.emit(AcceptRejectToastMsg(title, message, onAccept, onReject)) }
    }

    private fun tryLoginExistingAccount(
        route: String?,
        npub: String?,
        forceLogout: Boolean = false,
    ) {
        var currentUser = npub ?: LocalPreferences.currentAccount(NostrSigner.instance)
        val allAccounts = LocalPreferences.allSavedAccounts(NostrSigner.instance)
        if (currentUser != null && !LocalPreferences.containsAccount(NostrSigner.instance, currentUser) && allAccounts.any { it.npub == currentUser }) {
            currentUser = LocalPreferences.currentAccount(NostrSigner.instance)
        }
        if (forceLogout) {
            currentUser = null
        }
        LocalPreferences.loadFromEncryptedStorage(NostrSigner.instance, currentUser)?.let {
            startUI(it, route)
        }
        if (currentUser == null) {
            _accountContent.update { AccountState.LoggedOff }
        }
    }

    private fun prepareLogoutOrSwitch() {
        when (val state = accountContent.value) {
            is AccountState.LoggedIn -> {
                NostrSigner.instance.applicationIOScope.launch(Dispatchers.Main) {
                    state.account.saveable.removeObserver(saveListener)
                }
            }
            else -> {}
        }

        _accountContent.update { AccountState.LoggedOff }
    }

    fun logOff(npub: String) {
        prepareLogoutOrSwitch()
        val shouldLogout = LocalPreferences.updatePrefsForLogout(npub, NostrSigner.instance)
        tryLoginExistingAccount(null, null, forceLogout = shouldLogout)
    }

    fun switchUser(
        npub: String,
        route: String?,
    ) {
        prepareLogoutOrSwitch()
        LocalPreferences.switchToAccount(NostrSigner.instance, npub)
        tryLoginExistingAccount(route, npub)
    }

    fun isValidKey(key: String, password: String): Boolean {
        try {
            val account =
                if (key.startsWith("ncryptsec")) {
                    val newKey = Nip49().decrypt(key, password)

                    Account(
                        signer = NostrSignerInternal(KeyPair(Hex.decode(newKey))),
                        name = "",
                        useProxy = false,
                        proxyPort = 0,
                        language = null,
                        allowNewConnections = false,
                        signPolicy = 0,
                        seedWords = emptySet(),
                    )
                } else if (key.startsWith("nsec")) {
                    Account(
                        signer = NostrSignerInternal(KeyPair(privKey = key.bechToBytes())),
                        name = "",
                        useProxy = false,
                        proxyPort = 0,
                        language = null,
                        allowNewConnections = false,
                        signPolicy = 0,
                        seedWords = emptySet(),
                    )
                } else if (key.contains(" ") && Nip06().isValidMnemonic(key)) {
                    val keyPair = KeyPair(privKey = Nip06().privateKeyFromMnemonic(key))
                    Account(
                        signer = NostrSignerInternal(keyPair),
                        name = "",
                        useProxy = false,
                        proxyPort = 0,
                        language = null,
                        allowNewConnections = false,
                        signPolicy = 0,
                        seedWords = emptySet(),
                    )
                } else {
                    Account(
                        signer = NostrSignerInternal(KeyPair(Hex.decode(key))),
                        name = "",
                        useProxy = false,
                        proxyPort = 0,
                        language = null,
                        allowNewConnections = false,
                        signPolicy = 0,
                        seedWords = emptySet(),
                    )
                }
            return account.signer.keyPair.privKey != null
        } catch (_: Exception) {
            return false
        }
    }

    fun startUI(
        key: String,
        password: String,
        route: String?,
        useProxy: Boolean,
        proxyPort: Int,
        signPolicy: Int,
    ) {
        val account =
            if (key.startsWith("ncryptsec")) {
                val newKey = Nip49().decrypt(key, password)
                Account(
                    signer = NostrSignerInternal(KeyPair(Hex.decode(newKey))),
                    name = "",
                    useProxy = useProxy,
                    proxyPort = proxyPort,
                    language = null,
                    allowNewConnections = false,
                    signPolicy = signPolicy,
                    seedWords = emptySet(),
                )
            } else if (key.startsWith("nsec")) {
                Account(
                    signer = NostrSignerInternal(KeyPair(privKey = key.bechToBytes())),
                    name = "",
                    useProxy = useProxy,
                    proxyPort = proxyPort,
                    language = null,
                    allowNewConnections = false,
                    signPolicy = signPolicy,
                    seedWords = emptySet(),
                )
            } else if (key.contains(" ") && Nip06().isValidMnemonic(key)) {
                val keyPair = KeyPair(privKey = Nip06().privateKeyFromMnemonic(key))
                Account(
                    signer = NostrSignerInternal(keyPair),
                    name = "",
                    useProxy = useProxy,
                    proxyPort = proxyPort,
                    language = null,
                    allowNewConnections = false,
                    signPolicy = signPolicy,
                    seedWords = key.split(" ").toSet(),
                )
            } else {
                Account(
                    signer = NostrSignerInternal(KeyPair(Hex.decode(key))),
                    name = "",
                    useProxy = useProxy,
                    proxyPort = proxyPort,
                    language = null,
                    allowNewConnections = false,
                    signPolicy = signPolicy,
                    seedWords = emptySet(),
                )
            }

        LocalPreferences.updatePrefsForLogin(NostrSigner.instance, account)
        startUI(account, route)
    }

    fun newKey(
        useProxy: Boolean,
        proxyPort: Int,
        signPolicy: Int,
        seedWords: Set<String>,
        name: String,
    ) {
        val key = seedWords.joinToString(separator = " ") { it }
        val keyPair = KeyPair(privKey = Nip06().privateKeyFromMnemonic(key))
        val account = Account(
            NostrSignerInternal(keyPair),
            name = name,
            useProxy = useProxy,
            proxyPort = proxyPort,
            language = null,
            allowNewConnections = false,
            signPolicy = signPolicy,
            seedWords = seedWords,
        )
        LocalPreferences.updatePrefsForLogin(NostrSigner.instance, account)
        startUI(account, null)
    }

    private fun startUI(
        account: Account,
        route: String?,
    ) {
        _accountContent.update { AccountState.LoggedIn(account, route) }

        NostrSigner.instance.applicationIOScope.launch(Dispatchers.Main) {
            account.saveable.observeForever(saveListener)
        }
    }

    private val saveListener: (com.greenart7c3.nostrsigner.models.AccountState) -> Unit = {
        NostrSigner.instance.applicationIOScope.launch(Dispatchers.Main) {
            LocalPreferences.saveToEncryptedStorage(NostrSigner.instance, it.account)
        }
    }
}
