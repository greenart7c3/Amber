package com.greenart7c3.nostrsigner.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.greenart7c3.nostrsigner.AccountStorage
import com.greenart7c3.nostrsigner.service.AccountExportService
import com.vitorpamplona.crypto.random.RandomInstance
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip06KeyDerivation.Bip39Mnemonics
import com.vitorpamplona.quartz.nip06KeyDerivation.Nip06
import com.vitorpamplona.quartz.nip19Bech32.bech32.bechToBytes
import com.vitorpamplona.quartz.nip49PrivKeyEnc.Nip49
import com.vitorpamplona.quartz.utils.Hex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The key pair of a typed key ([key]: ncryptsec with [password], nsec, a recovery phrase
 * at [accountIndex], or hex), or null with the reason it was refused.
 */
fun parseLoginKey(key: String, password: String, accountIndex: Long = 0): Pair<KeyPair?, String> = try {
    val keyPair = when {
        key.startsWith("ncryptsec") -> KeyPair(Hex.decode(Nip49().decrypt(key, password)))
        key.startsWith("nsec") -> KeyPair(privKey = key.bechToBytes())
        key.contains(" ") && Nip06().isValidMnemonic(key) -> KeyPair(privKey = Nip06().privateKeyFromMnemonic(key, accountIndex))
        else -> KeyPair(Hex.decode(key))
    }
    Pair(keyPair, "")
} catch (e: Exception) {
    Pair(null, e.message ?: "Unknown error")
}

/** A pasted or scanned key without the invisible and control characters that come along with it. */
internal fun normalizeKeyInput(text: String): String = text.filter { it.code in 33..126 || it.code == 32 }.lowercase().trim()

/** The "add a key" page: checks the typed key or recovery phrase and logs in with it. */
class AddKeyViewModel(
    // Logging in finishes even if the page goes away.
    private val appScope: CoroutineScope,
) : ViewModel() {
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow("")
    val error: StateFlow<String> = _error.asStateFlow()

    // The checked key, kept only until the account is created from it.
    private var keyPair: KeyPair? = null

    fun showError(message: String) {
        _error.value = message
    }

    fun submitKey(input: String, password: String, onValid: () -> Unit) = check(normalizeKeyInput(input), password, 0, onValid)

    fun submitMnemonic(words: List<String>, accountIndex: Int, onValid: () -> Unit) = check(words.joinToString(" "), "", accountIndex.toLong(), onValid)

    private fun check(key: String, password: String, accountIndex: Long, onValid: () -> Unit) {
        _isLoading.value = true
        viewModelScope.launch(Dispatchers.Default) {
            val (parsed, reason) = parseLoginKey(key, password, accountIndex)
            _isLoading.value = false
            if (parsed == null) {
                _error.value = reason
            } else {
                keyPair = parsed
                withContext(Dispatchers.Main) { onValid() }
            }
        }
    }

    fun login(setup: ConnectionSetup, onLogin: suspend (KeyPair, ConnectionSetup) -> Unit, onFinish: () -> Unit) {
        val checked = keyPair ?: return
        _isLoading.value = true
        appScope.launch {
            onLogin(checked, setup)
            keyPair = null
            _isLoading.value = false
            withContext(Dispatchers.Main) { onFinish() }
        }
    }

    companion object {
        fun factory(appScope: CoroutineScope): ViewModelProvider.Factory = viewModelFactory {
            initializer { AddKeyViewModel(appScope) }
        }
    }
}

/** A freshly generated account: its recovery phrase and the key pair derived from it. */
class NewKey(val seedWords: Set<String>, val keyPair: KeyPair)

/** Twelve distinct recovery words (a phrase with a repeated word is drawn again) and their key. */
internal fun generateNewKey(): NewKey {
    var seedWords = setOf<String>()
    while (seedWords.size < 12) {
        seedWords = Bip39Mnemonics.toMnemonics(RandomInstance.bytes(16)).toSet()
    }
    return NewKey(seedWords, KeyPair(privKey = Nip06().privateKeyFromMnemonic(seedWords.joinToString(" "))))
}

/** The "generate a new key" page: draws the new account's key and creates the account. */
class SignUpViewModel(
    // Creating the account finishes even if the page goes away.
    private val appScope: CoroutineScope,
) : ViewModel() {
    // Null while the key is being drawn, and again once the account holds it.
    private val _newKey = MutableStateFlow<NewKey?>(null)
    val newKey: StateFlow<NewKey?> = _newKey.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.Default) { _newKey.value = generateNewKey() }
    }

    fun create(setup: ConnectionSetup, name: String, onCreate: suspend (ConnectionSetup, Set<String>, String) -> Unit, onFinish: () -> Unit) {
        val key = _newKey.value ?: return
        _newKey.value = null
        appScope.launch {
            onCreate(setup, key.seedWords, name)
            withContext(Dispatchers.Main) { onFinish() }
        }
    }

    companion object {
        fun factory(appScope: CoroutineScope): ViewModelProvider.Factory = viewModelFactory {
            initializer { SignUpViewModel(appScope) }
        }
    }
}

/** The first page: restoring accounts from an exported backup file. */
class AccountImportViewModel(
    private val appContext: Context,
    private val appScope: CoroutineScope,
) : ViewModel() {
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _progress = MutableStateFlow("")
    val progress: StateFlow<String> = _progress.asStateFlow()

    // The backup lines that could not be restored; the error dialog shows while non-empty.
    private val _failedLines = MutableStateFlow<List<Int>>(emptyList())
    val failedLines: StateFlow<List<Int>> = _failedLines.asStateFlow()

    // Set when some accounts were restored but not all: they are opened once the error is dismissed.
    private var finishAfterError: (() -> Unit)? = null

    /** Restores the backup at [uri]; [onImported] gets whether Amber had accounts before. */
    fun import(uri: Uri, password: String, onImported: (hadAccounts: Boolean) -> Unit) {
        appScope.launch {
            val hadAccounts = AccountStorage.all(appContext).isNotEmpty()
            AccountExportService.importAccounts(
                context = appContext,
                scope = appScope,
                uri = uri,
                password = password,
                onLoading = { _isLoading.value = it },
                onText = { _progress.value = it },
                onFinish = { importedCount, failedLines -> onImportFinished(importedCount, failedLines) { onImported(hadAccounts) } },
            )
        }
    }

    internal fun onImportFinished(importedCount: Int, failedLines: List<Int>, finish: () -> Unit) {
        if (failedLines.isEmpty()) {
            finish()
        } else {
            // When nothing was imported, stay here so the user can retry instead of
            // landing on a "your account is ready" screen.
            finishAfterError = finish.takeIf { importedCount > 0 }
            _failedLines.value = failedLines
        }
    }

    fun dismissImportError() {
        _failedLines.value = emptyList()
        finishAfterError?.invoke()
        finishAfterError = null
    }

    companion object {
        fun factory(appContext: Context, appScope: CoroutineScope): ViewModelProvider.Factory = viewModelFactory {
            initializer { AccountImportViewModel(appContext, appScope) }
        }
    }
}
