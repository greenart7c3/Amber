package com.greenart7c3.nostrsigner.desktop.core

import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.RelayAuthenticator
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

/**
 * Desktop counterpart of the Android `Amber` Application singleton: owns the
 * shared coroutine scope, the Nostr relay client, per-account stores, and the
 * NIP-46 engine.
 */
object AmberDesktop {
    const val TAG = "Amber"

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        AmberLogger.e("AmberCoroutine", "Caught exception: ${throwable.message}", throwable)
    }

    val applicationIOScope = CoroutineScope(Dispatchers.IO + SupervisorJob() + exceptionHandler)

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .pingInterval(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val socketBuilder = object : WebsocketBuilder {
        override fun build(url: NormalizedRelayUrl, out: WebSocketListener) = BasicOkHttpWebSocket(url, { httpClient }, out)
    }

    val client: NostrClient by lazy { NostrClient(socketBuilder, applicationIOScope) }

    /** A fresh, throwaway relay client (e.g. to probe a relay before adding it). */
    fun newClient(): NostrClient = NostrClient(socketBuilder, applicationIOScope)

    // Authenticates with relays that request NIP-42 AUTH.
    @Suppress("unused")
    private val authCoordinator by lazy {
        RelayAuthenticator(client, applicationIOScope) { event ->
            accounts().map { it.signer.sign(event) }
        }
    }

    val engine: BunkerEngine by lazy {
        authCoordinator
        BunkerEngine(client, applicationIOScope)
    }

    private val stores = ConcurrentHashMap<String, AccountStore>()
    private val accountCache = ConcurrentHashMap<String, DesktopAccount>()

    fun store(npub: String): AccountStore = stores.computeIfAbsent(npub) { AccountStore(it) }

    suspend fun account(npub: String): DesktopAccount? {
        accountCache[npub]?.let { return it }
        val loaded = AccountManager.loadAccount(npub) ?: return null
        return accountCache.putIfAbsent(npub, loaded) ?: loaded
    }

    suspend fun accounts(): List<DesktopAccount> = AccountsStore.accounts.value.mapNotNull { account(it.npub) }

    fun evictAccount(npub: String) {
        accountCache.remove(npub)
        stores.remove(npub)
    }

    /** Drops every decrypted account key from memory (passphrase lock). */
    fun evictAllAccounts() {
        accountCache.clear()
    }

    /**
     * Rewrites every account's database in the current encryption state.
     * Invoked when the passphrase lock is enabled or removed so the on-disk
     * apps/history/logs are re-encrypted (or decrypted) immediately.
     */
    fun rewriteAllStores() {
        AccountsStore.accounts.value.forEach { store(it.npub).rewriteAll() }
    }

    val settings: DesktopSettings get() = SettingsStore.settings.value

    fun defaultRelays(): List<NormalizedRelayUrl> = settings.normalizedDefaultRelays()

    /** Union of every connection's relays, mirroring `Amber.getSavedRelays`. */
    fun savedRelays(npub: String): Set<NormalizedRelayUrl> = buildSet {
        store(npub).apps.value.forEach { addAll(it.app.normalizedRelays()) }
        if (isEmpty()) addAll(defaultRelays())
    }
}
