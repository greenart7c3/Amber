package com.greenart7c3.nostrsigner.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.NostrSigner
import com.greenart7c3.nostrsigner.models.Account
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.encoders.bechToBytes
import com.vitorpamplona.quartz.signers.NostrSignerInternal
import fr.acinq.secp256k1.Hex
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
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
    ) {
        var currentUser = npub ?: LocalPreferences.currentAccount(NostrSigner.getInstance())
        if (currentUser != null && !LocalPreferences.containsAccount(NostrSigner.getInstance(), currentUser)) {
            currentUser = LocalPreferences.currentAccount(NostrSigner.getInstance())
        }
        LocalPreferences.loadFromEncryptedStorage(NostrSigner.getInstance(), currentUser)?.let {
            startUI(it, route)
        }
        if (currentUser == null) {
            _accountContent.update { AccountState.LoggedOff }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun prepareLogoutOrSwitch() {
        when (val state = accountContent.value) {
            is AccountState.LoggedIn -> {
                GlobalScope.launch(Dispatchers.Main) {
                    state.account.saveable.removeObserver(saveListener)
                }
            }
            else -> {}
        }

        _accountContent.update { AccountState.LoggedOff }
    }

    fun logOff(npub: String) {
        prepareLogoutOrSwitch()
        LocalPreferences.updatePrefsForLogout(npub, NostrSigner.getInstance())
        tryLoginExistingAccount(null, null)
    }

    fun switchUser(
        npub: String,
        route: String?,
    ) {
        prepareLogoutOrSwitch()
        LocalPreferences.switchToAccount(NostrSigner.getInstance(), npub)
        tryLoginExistingAccount(route, npub)
    }

    fun isValidKey(key: String, password: String): Boolean {
        try {
            val account =
                if (key.startsWith("ncryptsec")) {
                    val newKey =
                        CryptoUtils.decryptNIP49(key, password)
                            ?: throw Exception("Could not decrypt key with provided password")
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
                } else if (key.contains(" ") && CryptoUtils.isValidMnemonic(key)) {
                    val keyPair = KeyPair(privKey = CryptoUtils.privateKeyFromMnemonic(key))
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
        } catch (e: Exception) {
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
                val newKey =
                    CryptoUtils.decryptNIP49(key, password)
                        ?: throw Exception("Could not decrypt key with provided password")
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
            } else if (key.contains(" ") && CryptoUtils.isValidMnemonic(key)) {
                val keyPair = KeyPair(privKey = CryptoUtils.privateKeyFromMnemonic(key))
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

        LocalPreferences.updatePrefsForLogin(NostrSigner.getInstance(), account)
        startUI(account, route)
    }

    fun newKey(
        useProxy: Boolean,
        proxyPort: Int,
        signPolicy: Int,
        seedWords: Set<String>,
    ) {
        val key = seedWords.joinToString(separator = " ") { it }
        val keyPair = KeyPair(privKey = CryptoUtils.privateKeyFromMnemonic(key))
        val account = Account(
            NostrSignerInternal(keyPair),
            name = "",
            useProxy = useProxy,
            proxyPort = proxyPort,
            language = null,
            allowNewConnections = false,
            signPolicy = signPolicy,
            seedWords = seedWords,
        )
        LocalPreferences.updatePrefsForLogin(NostrSigner.getInstance(), account)
        startUI(account, null)
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun startUI(
        account: Account,
        route: String?,
    ) {
        _accountContent.update { AccountState.LoggedIn(account, route) }

        GlobalScope.launch(Dispatchers.Main) {
            account.saveable.observeForever(saveListener)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private val saveListener: (com.greenart7c3.nostrsigner.models.AccountState) -> Unit = {
        GlobalScope.launch(Dispatchers.IO) {
            LocalPreferences.saveToEncryptedStorage(NostrSigner.getInstance(), it.account)
        }
    }
}
