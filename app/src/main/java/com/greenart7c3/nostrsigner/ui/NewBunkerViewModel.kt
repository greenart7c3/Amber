package com.greenart7c3.nostrsigner.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.greenart7c3.nostrsigner.common.generateBunkerPrivKey
import com.greenart7c3.nostrsigner.data.AccountRepository
import com.greenart7c3.nostrsigner.data.RelayClient
import com.greenart7c3.nostrsigner.database.ApplicationEntity
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.service.LocalKeyAccountIndex
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Creates a bunker:// connection (saved under its secret until a client connects
 * with it) and, once created, prepares the URI to hand to the client.
 */
class NewBunkerViewModel(
    private val accountRepository: AccountRepository,
    private val relayClient: RelayClient,
) : ViewModel() {
    fun create(account: Account, name: String, relays: List<NormalizedRelayUrl>, secret: String, deleteAfter: Long, onCreated: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val application = ApplicationEntity(
                key = secret,
                name = name,
                relays = relays,
                url = "",
                icon = "",
                description = "",
                pubKey = account.hexKey,
                isConnected = false,
                secret = secret,
                useSecret = true,
                signPolicy = account.signPolicy,
                closeApplication = true,
                deleteAfter = deleteAfter,
                lastUsed = 0L,
                localKey = generateBunkerPrivKey(),
            )
            accountRepository.daos(account.npub).applicationWriteDao.insertApplication(application)
            withContext(Dispatchers.Main) { onCreated() }
        }
    }

    /**
     * The bunker:// URI of the connection saved under [key], once Amber listens on
     * its key. Throws when it is not saved or its key is not routed to [account] yet.
     */
    suspend fun prepareUri(account: Account, key: String): String = withContext(Dispatchers.IO) {
        val saved = checkNotNull(accountRepository.daos(account.npub).applicationDao.getByKey(key)?.application)
        relayClient.checkForNewRelaysAndUpdateAllFilters(shouldReconnect = true)
        if (saved.localPubKey.isNotEmpty()) {
            check(LocalKeyAccountIndex.lookup(saved.localPubKey)?.npub == account.npub)
        }
        val relays = saved.relays.joinToString(separator = "&") { "relay=${it.url}" }
        val signerPubKey = saved.localPubKey.ifEmpty { account.hexKey }
        "bunker://$signerPubKey?$relays&secret=${saved.secret}"
    }

    companion object {
        fun factory(accountRepository: AccountRepository, relayClient: RelayClient): ViewModelProvider.Factory = viewModelFactory {
            initializer { NewBunkerViewModel(accountRepository, relayClient) }
        }
    }
}
