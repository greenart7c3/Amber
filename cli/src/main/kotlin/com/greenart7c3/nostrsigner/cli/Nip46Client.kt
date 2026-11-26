package com.greenart7c3.nostrsigner.cli

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import com.vitorpamplona.quartz.nip46RemoteSigner.signer.NostrSignerRemote
import java.io.PrintStream
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient

/** How long a publish/wait may take; `amber --timeout` sets it. */
class Deadline(
    seconds: Long,
) {
    private val end = System.nanoTime() + seconds * 1_000_000_000L

    fun remainingMillis(): Long = (end - System.nanoTime()) / 1_000_000L
}

/**
 * The client half of NIP-46, built on Quartz's own implementation: the
 * `NostrClient` both apps use for relays, and [NostrSignerRemote] for the
 * protocol (connect, get_public_key, sign_event, nip04/nip44, ping, logout).
 * This class only adds what a command line tool needs around it: the throwaway
 * client key that is saved per connection, the `nostrconnect://` approval wait
 * that Quartz's address-based signer has no flow for, `--timeout`, and the
 * exit codes.
 */
class Nip46Client(
    clientKey: KeyPair,
    private val relays: List<NormalizedRelayUrl>,
    private val timeoutSeconds: Long,
    private val err: PrintStream,
    private val openUri: (String) -> Unit,
) : AutoCloseable {
    private val connectionKeySigner = NostrSignerInternal(clientKey)

    val clientPubKey: String = clientKey.pubKey.toHexKey()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(LONG_LIVED_SOCKET_NO_READ_TIMEOUT, TimeUnit.MILLISECONDS)
        .pingInterval(IDLE_NAT_KEEPALIVE_PING_SECONDS, TimeUnit.SECONDS)
        .build()

    private val relayClient = NostrClient(OkHttpWebsocketBuilder(http, scope), scope)

    private var remote: NostrSignerRemote? = null

    private data class NostrConnectApproval(
        val signerConnectionKey: String,
        val answeredSecret: String?,
    )

    private val approvals = LinkedBlockingQueue<NostrConnectApproval>()

    init {
        relayClient.connect()
    }

    /**
     * `login`: Quartz parses the `bunker://` URI (relays, secret, the signer's
     * connection key) and runs the NIP-46 handshake. Returns the signer's key,
     * the user's key and the relays the connection uses.
     */
    fun login(bunkerUri: String): Login {
        val signer = runCatching {
            NostrSignerRemote.fromBunkerUri(bunkerUri, connectionKeySigner, relayClient, onAuthUrl = ::authUrl)
        }.getOrElse {
            throw CliError("not a usable bunker:// URI: ${it.message}", Exit.USAGE)
        }
        remote = signer
        signer.openSubscription()
        awaitRelays()
        requestWithinTimeout("connect") { signer.connect() }
        val userPubKey = requestWithinTimeout("get_public_key") { signer.getPublicKey() }
        return Login(signer.remotePubkey, userPubKey, signer.relays.map { it.url })
    }

    /**
     * Waits for Amber to approve a `nostrconnect://` URI: it answers with the
     * URI's secret, and that answer's author is the connection key to talk to
     * from now on.
     *
     * NIP-46 events are ephemeral, so relays do not replay an answer that
     * arrived before the subscription: [onListening] is where the URI may
     * leave the process, and it runs only once a relay has answered the
     * subscription with EOSE and every other relay has too, failed, or had a
     * few seconds more to (see [RelaysListening]), never before.
     */
    fun awaitNostrConnectApproval(
        secret: String,
        onListening: () -> Unit,
    ): String {
        val subId = "amber-cli-" + UUID.randomUUID().toString().take(8)
        val filter = Filter(
            kinds = listOf(NostrConnectEvent.KIND),
            tags = mapOf("p" to listOf(clientPubKey)),
        )
        val listening = RelaysListening(relays.toSet())
        relayClient.subscribe(
            subId,
            relays.associateWith { listOf(filter) },
            object : SubscriptionListener {
                override suspend fun onEvent(
                    event: Event,
                    isLive: Boolean,
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) = readApproval(event)

                override fun onEose(
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) = listening.eose(relay)

                override fun onCannotConnect(
                    relay: NormalizedRelayUrl,
                    message: String,
                    forFilters: List<Filter>?,
                ) = listening.failed(relay)
            },
        )
        try {
            if (!listening.awaitSettledOrGraceAfterFirstListening(timeoutSeconds * 1000)) throw unreachable()
            onListening()
            val deadline = Deadline(timeoutSeconds)
            while (true) {
                val remaining = deadline.remainingMillis()
                if (remaining <= 0) break
                val (signerPubKey, answered) = approvals.poll(remaining, TimeUnit.MILLISECONDS) ?: break
                if (answered == secret) return signerPubKey
            }
            throw timeout("waiting for Amber to approve the connection")
        } finally {
            relayClient.unsubscribe(subId)
        }
    }

    private class RelaysListening(
        private val relays: Set<NormalizedRelayUrl>,
    ) {
        private val lock = ReentrantLock()
        private val changed = lock.newCondition()
        private val listening = mutableSetOf<NormalizedRelayUrl>()
        private val failed = mutableSetOf<NormalizedRelayUrl>()

        fun eose(relay: NormalizedRelayUrl) = lock.withLock {
            failed.remove(relay)
            listening.add(relay)
            changed.signalAll()
        }

        fun failed(relay: NormalizedRelayUrl) = lock.withLock {
            if (relay !in listening) failed.add(relay)
            changed.signalAll()
        }

        fun awaitSettledOrGraceAfterFirstListening(timeoutMillis: Long): Boolean = lock.withLock {
            var deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
            var lateRelayGraceStarted = false
            while (!relays.all { it in listening || it in failed }) {
                if (!lateRelayGraceStarted && listening.isNotEmpty()) {
                    lateRelayGraceStarted = true
                    deadline = minOf(deadline, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(LATE_RELAY_GRACE_MILLIS))
                }
                val remaining = deadline - System.nanoTime()
                if (remaining <= 0) break
                changed.awaitNanos(remaining)
            }
            listening.isNotEmpty()
        }
    }

    private companion object {
        @Suppress("NoComments")
        // A relay behind a firewall never answers and waits out the connect timeout, so it must
        // not hold the URI back. Healthy relays subscribe within a second or two of the first;
        // a human then still takes longer than that to paste the URI and approve it.
        const val LATE_RELAY_GRACE_MILLIS = 3_000L

        const val LONG_LIVED_SOCKET_NO_READ_TIMEOUT = 0L

        const val IDLE_NAT_KEEPALIVE_PING_SECONDS = 30L
    }

    /** Continues a saved connection. */
    fun open(
        signerPubKey: String,
        userPubKey: String,
    ) {
        val signer = NostrSignerRemote(
            signer = connectionKeySigner,
            remotePubkey = signerPubKey,
            relays = relays.toSet(),
            client = relayClient,
            onAuthUrl = ::authUrl,
        )
        if (userPubKey.isNotEmpty()) signer.bindUserPubkey(userPubKey)
        signer.openSubscription()
        remote = signer
    }

    /** The signer's connection key, after [login] or [open]. */
    fun signerPubKey(): String? = remote?.remotePubkey

    /** The connection's relays: the URI's after [login], the given ones for a saved connection. */
    fun relayUrls(): List<String> = (remote?.relays?.map { it.url } ?: relays.map { it.url }).sorted()

    fun awaitRelays() {
        if (relayClient.connectedRelaysFlow().value.isNotEmpty()) return
        val connected = runBlocking {
            withTimeoutOrNull(timeoutSeconds * 1000) {
                relayClient.connectedRelaysFlow().first { it.isNotEmpty() }
            }
        }
        if (connected == null) throw unreachable()
    }

    /** The requests to the paired signer, after [login] or [open]. */
    val requests = SignerRequests()

    /** One NIP-46 request per function, each bounded by `--timeout` and mapped to an exit code. */
    inner class SignerRequests {
        fun getPublicKey(): String = requestWithinTimeout("get_public_key") { requireSigner().getPublicKey() }

        fun signEvent(template: EventTemplate<Event>): Event = requestWithinTimeout("sign_event") { requireSigner().sign(template) }

        fun nip44Encrypt(
            pubKey: String,
            text: String,
        ): String = requestWithinTimeout("nip44_encrypt") { requireSigner().nip44Encrypt(text, pubKey) }

        fun nip44Decrypt(
            pubKey: String,
            ciphertext: String,
        ): String = requestWithinTimeout("nip44_decrypt") { requireSigner().nip44Decrypt(ciphertext, pubKey) }

        fun nip04Encrypt(
            pubKey: String,
            text: String,
        ): String = requestWithinTimeout("nip04_encrypt") { requireSigner().nip04Encrypt(text, pubKey) }

        fun nip04Decrypt(
            pubKey: String,
            ciphertext: String,
        ): String = requestWithinTimeout("nip04_decrypt") { requireSigner().nip04Decrypt(ciphertext, pubKey) }

        /**
         * A ping is, by definition, only answered by a live signer. Quartz's
         * `PingResult` carries the request id rather than the pong, so the answer
         * itself is "Amber replied"; this reports the round trip, not that value.
         */
        fun ping() {
            requestWithinTimeout("ping") { requireSigner().ping() }
        }

        fun switchRelays(): List<String>? = requestWithinTimeout("switch_relays") {
            SwitchRelaysOutcome(requireSigner().switchRelays()?.map { it.url })
        }.relays

        fun logout() {
            requestWithinTimeout("logout") { requireSigner().logout() }
        }
    }

    override fun close() {
        runCatching { remote?.closeSubscription() }
        remote = null
        relayClient.disconnect()
        scope.cancel()
        http.dispatcher.executorService.shutdown()
        http.connectionPool.evictAll()
    }

    /** What pairing a `bunker://` URI produced. */
    data class Login(
        val signerPubKey: String,
        val userPubKey: String,
        val relays: List<String>,
    )

    private class SwitchRelaysOutcome(
        val relays: List<String>?,
    )

    private fun requireSigner(): NostrSignerRemote = remote ?: throw CliError("this connection is not paired with a signer", Exit.NOT_PAIRED)

    private fun readApproval(event: Event) {
        val connectEvent = event as? NostrConnectEvent ?: return
        val message = runCatching { runBlocking { connectEvent.decryptMessage(connectionKeySigner) } }.getOrNull()
        val response = message as? BunkerResponse ?: return
        approvals.offer(NostrConnectApproval(connectEvent.pubKey, response.result))
    }

    private fun <T> requestWithinTimeout(
        what: String,
        block: suspend () -> T,
    ): T {
        val outcome = runBlocking {
            runCatching { withTimeoutOrNull(timeoutSeconds * 1000) { block() } }
        }
        outcome.exceptionOrNull()?.let { throw mapFailure(what, it) }
        return outcome.getOrNull() ?: throw timeout(what)
    }

    private fun mapFailure(
        what: String,
        error: Throwable,
    ): CliError = when (error) {
        is SignerExceptions.ManuallyUnauthorizedException -> CliError("Amber refused $what: ${error.message}", Exit.REJECTED)
        is SignerExceptions.AutomaticallyUnauthorizedException -> CliError("Amber refused $what: ${error.message}", Exit.REJECTED)
        is SignerExceptions.CouldNotPerformException -> CliError("Amber refused $what: ${error.message}", Exit.REJECTED)
        is SignerExceptions.UnsupportedMethodException -> CliError("this signer does not support $what: ${error.message}", Exit.ERROR)
        is SignerExceptions.TimedOutException -> timeout(what)
        else -> CliError("$what failed: ${error.message ?: error.toString()}", Exit.ERROR)
    }

    private fun timeout(what: String): CliError = if (relayClient.connectedRelaysFlow().value.isEmpty()) {
        unreachable()
    } else {
        CliError(
            "$what timed out after ${timeoutSeconds}s. A human may need to approve the request in Amber: " +
                "open Amber, approve the pending request, then run the command again " +
                "(or pair with --perms and let Amber remember the grant).",
            Exit.TIMEOUT,
        )
    }

    private fun unreachable(): CliError {
        val list = relayUrls().joinToString(", ")
        return CliError("could not reach any relay: " + if (list.isEmpty()) "(none configured)" else list, Exit.UNREACHABLE)
    }

    private fun authUrl(url: String) {
        err.println("Amber asks you to authorize this connection at:")
        err.println(url)
        if (url.startsWith("http")) openUri(url)
        err.println("once done, Amber answers the request by itself")
    }
}
