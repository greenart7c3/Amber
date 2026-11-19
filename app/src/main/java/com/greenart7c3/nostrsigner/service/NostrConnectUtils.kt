package com.greenart7c3.nostrsigner.service

import android.content.Intent
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.common.EncryptionType
import com.greenart7c3.nostrsigner.common.NostrConnectUri
import com.greenart7c3.nostrsigner.commonsui.containsNip
import com.greenart7c3.nostrsigner.data.AccountRepository
import com.greenart7c3.nostrsigner.database.LogEntity
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.AmberBunkerRequest
import com.greenart7c3.nostrsigner.models.BunkerClientMetadata
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

object NostrConnectUtils {
    /** Queues the connect a `nostrconnect://` intent asks for (parsed by the shared [NostrConnectUri]). */
    fun getIntentFromNostrConnect(
        intent: Intent,
        account: Account,
        accountRepository: AccountRepository,
        scope: CoroutineScope,
    ) {
        if (intent.extras?.getString("id") == null) {
            intent.putExtra("id", UUID.randomUUID().toString().substring(0, 6))
        }

        try {
            val uri = NostrConnectUri.parse(intent.dataString.toString()) { it.containsNip() }
            val clientMetadata = BunkerClientMetadata(name = uri.name, url = uri.url, image = uri.image)
                .takeUnless { it.isEmpty() }

            BunkerRequestUtils.addRequest(
                AmberBunkerRequest(
                    BunkerRequestConnect(
                        id = intent.extras?.getString("id") ?: UUID.randomUUID().toString().substring(0, 6),
                        remoteKey = uri.clientPubKey,
                        secret = "",
                        permissions = if (uri.permissions.isNotEmpty()) {
                            uri.permissions.joinToString { if (it.kind != null) "${it.type}:${it.kind}" else it.type }
                        } else {
                            null
                        },
                    ),
                    localKey = uri.clientPubKey,
                    relays = uri.relays,
                    currentAccount = account.npub,
                    nostrConnectSecret = uri.secret,
                    closeApplication = intent.getBooleanExtra("closeApplication", true),
                    name = uri.name,
                    signedEvent = null,
                    encryptedData = null,
                    encryptionType = EncryptionType.NIP44,
                    isNostrConnectUri = true,
                    clientMetadata = clientMetadata,
                ),
            )
        } catch (e: Exception) {
            AmberLog.e(Amber.TAG, e.message, e)
            scope.launch {
                accountRepository.getLogDatabase(account.npub).dao().insertLogIfEnabled(
                    LogEntity(
                        0,
                        "nostrconnect",
                        "nostrconnect",
                        e.message ?: "",
                        System.currentTimeMillis(),
                    ),
                )
            }
        }
    }
}
