package com.greenart7c3.nostrsigner.service

import android.content.Context
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.AccountExportData
import com.greenart7c3.nostrsigner.models.BulkAccountExport
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip19Bech32.toNsec
import com.vitorpamplona.quartz.nip49PrivKeyEnc.Nip49
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AccountExportService {
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

            val exportedAccounts = mutableListOf<AccountExportData>()

            accountInfos.forEachIndexed { index, accountInfo ->
                onProgress(index + 1, totalAccounts)

                val account = LocalPreferences.loadFromEncryptedStorage(context, accountInfo.npub)
                account?.let {
                    val exportData = accountToExportData(it, password)
                    exportedAccounts.add(exportData)
                }
            }

            val bulkExport = BulkAccountExport(
                accountCount = exportedAccounts.size,
                accounts = exportedAccounts,
            )

            val json = BulkAccountExport.toJson(bulkExport)

            // Each private key is already encrypted with NIP-49 inside the JSON
            Result.success(json)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Convert an Account to exportable data with encrypted private key
     */
    private fun accountToExportData(account: Account, password: String): AccountExportData {
        val privKey = account.signer.keyPair.privKey
        val encrypedSeedWords = if (account.seedWords.isNotEmpty()) account.signer.signerSync.nip44Encrypt(account.seedWords.joinToString(" "), account.hexKey) else ""

        // Encrypt private key with password (NIP-49) or use nsec if no password
        val encryptedNsec = if (privKey != null) {
            if (password.isNotBlank()) {
                Nip49().encrypt(privKey.toHexKey(), password)
            } else {
                privKey.toNsec()
            }
        } else {
            null
        }

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
