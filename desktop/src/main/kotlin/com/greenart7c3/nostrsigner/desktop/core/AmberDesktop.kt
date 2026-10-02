package com.greenart7c3.nostrsigner.desktop.core

import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.RelayAuthenticator
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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

    // The client is picked per dial, so Tor routing (and the local-relay
    // bypass) applies to every socket opened after a settings change.
    private val socketBuilder = object : WebsocketBuilder {
        override fun build(url: NormalizedRelayUrl, out: WebSocketListener) = BasicOkHttpWebSocket(url, RelayHttpClients::clientFor, out)
    }

    val client: NostrClient by lazy { NostrClient(socketBuilder, applicationIOScope) }

    /** A fresh, throwaway relay client (e.g. to probe a relay before adding it). */
    fun newClient(): NostrClient = NostrClient(socketBuilder, applicationIOScope)

    // Authenticates with relays that request NIP-42 AUTH.
    @Suppress("unused")
    private val authCoordinator by lazy {
        RelayAuthenticator(client, applicationIOScope) { _, event, _ ->
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

    /** The already-loaded account for [npub], if any (never decrypts a key). */
    fun loadedAccount(npub: String): DesktopAccount? = accountCache[npub]

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

    /**
     * When the client was disconnected on purpose (network lost, passphrase
     * lock): relay listeners skip their reconnect scheduling for a short
     * window after this timestamp, mirroring `Amber.intentionalDisconnectTime`.
     */
    @Volatile
    var intentionalDisconnectTime = 0L
        private set

    /** Mirrors `Amber.disconnectIntentionally`. */
    fun disconnectIntentionally() {
        intentionalDisconnectTime = System.currentTimeMillis()
        client.disconnect()
    }

    /**
     * Applies the Tor settings: starts or stops built-in Tor and redials every
     * relay so open sockets move to the new route (an open socket never
     * re-checks its proxy on its own).
     */
    fun applyTorSettings() {
        if (settings.torMode == TorMode.BUILTIN) {
            TorManager.start(applicationIOScope)
        } else {
            TorManager.stop(applicationIOScope)
        }
        redialRelays()
    }

    /** Drops every relay socket and reconnects them through the current route. */
    fun redialRelays() {
        if (PassphraseLock.isLocked()) return
        RelayHealthTracker.reset()
        disconnectIntentionally()
        applicationIOScope.launch {
            engine.updateFilter()
            client.connect()
        }
    }

    @Volatile
    private var torObserverStarted = false

    /**
     * Starts built-in Tor at launch when enabled, and redials relays whenever
     * it (re)gains its SOCKS listener — mirrors `Amber.startTorRecoveryObserver`.
     * Until then relays dial the fail-closed placeholder port and never leak.
     */
    fun startTor() {
        if (torObserverStarted) return
        torObserverStarted = true
        applicationIOScope.launch {
            var wasRunning = TorManager.isRunning.value
            TorManager.isRunning.collect { running ->
                if (running && !wasRunning && settings.torMode == TorMode.BUILTIN) {
                    AmberLogger.d(TAG, "Built-in Tor is up; refreshing relay connections")
                    redialRelays()
                }
                wasRunning = running
            }
        }
        if (settings.torMode == TorMode.BUILTIN) {
            TorManager.start(applicationIOScope)
        }
        Runtime.getRuntime().addShutdownHook(Thread { runCatching { TorManager.shutdown() } })
    }

    /** Mirrors `Amber.reconnect`: connect retries failed relays directly. */
    fun reconnect() {
        if (PassphraseLock.isLocked()) return
        val wasActive = client.isActive()
        // Always call connect() so that failed relays (socket == null) are
        // retried directly, bypassing the relay client's internal backoff
        // delay. For relays already connected, connect() is a safe no-op.
        client.connect()
        client.reconnect(wasActive)
    }
}
