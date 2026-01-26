package com.greenart7c3.nostrsigner.service

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.anggrayudi.storage.extension.toDocumentFile
import com.anggrayudi.storage.file.openInputStream
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.AccountExportData
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.Nip01
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip49PrivKeyEnc.Nip49
import com.vitorpamplona.quartz.utils.Hex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object AccountExportService {
    suspend fun importAccounts(
        uri: Uri,
        password: String,
        onLoading: (isLoading: Boolean) -> Unit,
        onText: (text: String) -> Unit,
        onFinish: () -> Unit,
    ) {
        onLoading(true)
        try {
            val file = uri.toDocumentFile(Amber.instance) ?: return
            val name = file.name
            if (name?.endsWith(".jsonl") == false) {
                Amber.instance.applicationIOScope.launch(Dispatchers.Main) {
                    Toast.makeText(Amber.instance, "File is not a Amber backup", Toast.LENGTH_LONG).show()
                }
                return
            }

            if (file.isFile) {
                file.openInputStream(Amber.instance).use { oi ->
                    oi?.bufferedReader().use { reader ->
                        reader?.readLines()?.forEachIndexed { index, line ->
                            try {
                                onText(Amber.instance.getString(R.string.importing_account, index + 1))

                                val accountData = AccountExportData.fromJson(line)
                                val privKey = Nip49().decrypt(accountData.nsec, password)
                                val hexKey = Nip01.pubKeyCreate(Hex.decode(privKey))
                                val account = Account(
                                    hexKey = hexKey.toHexKey(),
                                    npub = hexKey.toNpub(),
                                    name = MutableStateFlow(accountData.name),
                                    picture = MutableStateFlow(accountData.picture ?: ""),
                                    signPolicy = accountData.signPolicy,
                                    didBackup = accountData.didBackup,
                                    signer = NostrSignerInternal(KeyPair(privKey.hexToByteArray())),
                                )
                                LocalPreferences.switchToAccount(Amber.instance, accountData.npub)
                                LocalPreferences.updatePrefsForLogin(Amber.instance, account, hexKey.toHexKey(), privKey, accountData.seedWords)
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e

                                Log.e("Amber", "Error importing account", e)
                            }
                        }
                        onFinish()
                    }
                }
            }
        } finally {
            onText("")
            onLoading(false)
        }
    }

    /**
     * Export all accounts as an encrypted JSON file
     *
     * @param context Application context
     * @param password Password for encrypting private keys (NIP-49)
     * @param onProgress Callback for progress updates (current, total)
     * @return Encrypted JSON string containing all account data
     */
    suspend fun exportAllAccountsEncrypted(
        context: Context,
        password: String,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> },
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val accountInfos = LocalPreferences.allSavedAccounts(context)
            val totalAccounts = accountInfos.size

            if (totalAccounts == 0) {
                return@withContext Result.failure(Exception("No accounts to export"))
            }

            var data = ""
            accountInfos.forEachIndexed { index, accountInfo ->
                onProgress(index + 1, totalAccounts)

                val account = LocalPreferences.loadFromEncryptedStorage(context, accountInfo.npub)
                account?.let {
                    val exportData = accountToExportData(it, password)
                    data += "${AccountExportData.toJson(exportData)}\n"
                }
            }

            // Each private key is already encrypted with NIP-49 inside the JSON
            Result.success(data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Convert an Account to exportable data with encrypted private key
     */
    private suspend fun accountToExportData(account: Account, password: String): AccountExportData {
        val seedWords = account.seedWords()
        val encrypedSeedWords = if (seedWords.isNotBlank()) account.nip44Encrypt(seedWords, account.hexKey) else ""
        val encryptedNsec = account.nip49Encrypt(password)

        return AccountExportData(
            npub = account.npub,
            name = account.name.value,
            nsec = encryptedNsec,
            signPolicy = account.signPolicy,
            picture = account.picture.value,
            didBackup = account.didBackup,
            seedWords = encrypedSeedWords,
        )
    }
}
