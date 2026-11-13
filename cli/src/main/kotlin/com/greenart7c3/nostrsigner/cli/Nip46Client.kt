package com.greenart7c3.nostrsigner.cli

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.DynamicSubscription
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import com.vitorpamplona.quartz.nip46RemoteSigner.signer.NostrSignerRemote
import java.io.PrintStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
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
    /** The saved connection's key: Amber binds the connection to this pubkey. Not an account. */
    private val transport = NostrSignerInternal(clientKey)

    val clientPubKey: String = clientKey.pubKey.toHexKey()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        // Long-lived sockets: no read timeout, and ping so idle NATs keep them.
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private val relayClient = NostrClient(OkHttpWebsocketBuilder(http, scope), scope)

    private var remote: NostrSignerRemote? = null

    /** Approvals of a `nostrconnect://` URI: signer connection key to the secret it answered. */
    private val approvals = LinkedBlockingQueue<Pair<String, String?>>()

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
            NostrSignerRemote.fromBunkerUri(bunkerUri, transport, relayClient, onAuthUrl = ::authUrl)
        }.getOrElse {
            throw CliError("not a usable bunker:// URI: ${it.message}", Exit.USAGE)
        }
        remote = signer
        signer.openSubscription()
        awaitRelays()
        call("connect") { signer.connect() }
        val userPubKey = call("get_public_key") { signer.getPublicKey() }
        return Login(signer.remotePubkey, userPubKey, signer.relays.map { it.url })
    }

    /**
     * Waits for Amber to approve a `nostrconnect://` URI: it answers with the
     * URI's secret, and that answer's author is the connection key to talk to
     * from now on.
     */
    fun awaitNostrConnectApproval(secret: String): String {
        // The subscription is what dials the relays, so it comes first.
        val subscription = DynamicSubscription(
            relayClient,
            {
                relays.associateWith {
                    listOf(
                        Filter(
                            kinds = listOf(NostrConnectEvent.KIND),
                            tags = mapOf("p" to listOf(clientPubKey)),
                        ),
                    )
                }
            },
        ) { event -> readApproval(event) }
        subscription.refresh()
        try {
            awaitRelays()
            val deadline = Deadline(timeoutSeconds)
            while (true) {
                val remaining = deadline.remainingMillis()
                if (remaining <= 0) break
                val (signerPubKey, answered) = approvals.poll(remaining, TimeUnit.MILLISECONDS) ?: break
                if (answered == secret) return signerPubKey
            }
            throw timeout("waiting for Amber to approve the connection")
        } finally {
            subscription.close()
        }
    }

    /** Continues a saved connection. */
    fun open(
        signerPubKey: String,
        userPubKey: String,
    ) {
        val signer = NostrSignerRemote(
            signer = transport,
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

    fun getPublicKey(): String = call("get_public_key") { requireSigner().getPublicKey() }

    fun signEvent(template: EventTemplate<Event>): Event = call("sign_event") { requireSigner().sign(template) }

    fun nip44Encrypt(
        pubKey: String,
        text: String,
    ): String = call("nip44_encrypt") { requireSigner().nip44Encrypt(text, pubKey) }

    fun nip44Decrypt(
        pubKey: String,
        ciphertext: String,
    ): String = call("nip44_decrypt") { requireSigner().nip44Decrypt(ciphertext, pubKey) }

    fun nip04Encrypt(
        pubKey: String,
        text: String,
    ): String = call("nip04_encrypt") { requireSigner().nip04Encrypt(text, pubKey) }

    fun nip04Decrypt(
        pubKey: String,
        ciphertext: String,
    ): String = call("nip04_decrypt") { requireSigner().nip04Decrypt(ciphertext, pubKey) }

    /**
     * A ping is, by definition, only answered by a live signer. Quartz's
     * `PingResult` carries the request id rather than the pong, so the answer
     * itself is "Amber replied"; this reports the round trip, not that value.
     */
    fun ping() {
        call("ping") { requireSigner().ping() }
    }

    fun logout() {
        call("logout") { requireSigner().logout() }
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

    private fun requireSigner(): NostrSignerRemote = remote ?: throw CliError("this connection is not paired with a signer", Exit.NOT_PAIRED)

    private fun readApproval(event: Event) {
        val connectEvent = event as? NostrConnectEvent ?: return
        val message = runCatching { runBlocking { connectEvent.decryptMessage(transport) } }.getOrNull()
        val response = message as? BunkerResponse ?: return
        approvals.offer(connectEvent.pubKey to response.result)
    }

    /** One request, bounded by `--timeout` (a human may have to approve it in Amber). */
    private fun <T> call(
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
