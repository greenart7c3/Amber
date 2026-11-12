package com.greenart7c3.nostrsigner.cli

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket as OkHttpWebSocket
import okhttp3.WebSocketListener as OkHttpListener

/**
 * OkHttp behind Quartz's [WebSocket] interface, the one piece Quartz does not
 * ship (the Android and desktop apps each carry their own copy for the same
 * reason). Everything protocol-level — REQ/EVENT/OK framing, NIP-42, event
 * parsing and verification, reconnects — is [com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient]'s.
 */
class OkHttpWebsocketBuilder(
    private val http: OkHttpClient,
    private val scope: CoroutineScope,
) : WebsocketBuilder {
    override fun build(
        url: NormalizedRelayUrl,
        out: WebSocketListener,
    ): WebSocket = OkHttpRelaySocket(url, http, out, scope)
}

class OkHttpRelaySocket(
    private val url: NormalizedRelayUrl,
    private val http: OkHttpClient,
    private val out: WebSocketListener,
    scope: CoroutineScope,
) : WebSocket {
    /** Set once by whichever of onClosed, onFailure or [disconnect] ends the session first. */
    private val ended = AtomicBoolean(false)

    @Volatile
    private var socket: OkHttpWebSocket? = null

    /** A dial in flight is not a dead session. */
    @Volatile
    private var dialing = false

    // Incoming messages are handed to Quartz one at a time, in order, and off
    // OkHttp's reader thread: a slow consumer must not stall the socket.
    private val incoming = Channel<String>(capacity = 1024)

    init {
        scope.launch {
            for (message in incoming) out.onMessage(message)
        }
    }

    override fun needsReconnect(): Boolean = ended.get() || (socket == null && !dialing)

    override fun connect() {
        if (socket != null || dialing || ended.get()) return
        dialing = true
        try {
            val request = Request.Builder()
                .url(url.url)
                .header("User-Agent", BuildInfo.userAgent)
                .build()
            val dialed = http.newWebSocket(request, listener)
            socket = dialed
            // A disconnect() that landed while dialing has no socket to cancel yet.
            if (ended.get()) {
                socket = null
                dialed.cancel()
            }
        } finally {
            dialing = false
        }
    }

    override fun disconnect() {
        if (!ended.compareAndSet(false, true)) return
        val closing = socket ?: return
        socket = null
        closing.close(1000, null)
        incoming.close()
        out.onClosed(1000, "client disconnect")
    }

    override fun send(msg: String): Boolean = socket?.send(msg) ?: false

    private val listener = object : OkHttpListener() {
        override fun onOpen(
            webSocket: OkHttpWebSocket,
            response: Response,
        ) {
            if (ended.get()) {
                webSocket.close(1000, null)
                return
            }
            socket = webSocket
            out.onOpen(
                (response.receivedResponseAtMillis - response.sentRequestAtMillis).toInt(),
                response.headers["Sec-WebSocket-Extensions"]?.contains("permessage-deflate") ?: false,
            )
        }

        override fun onMessage(
            webSocket: OkHttpWebSocket,
            text: String,
        ) {
            if (ended.get()) return
            incoming.trySend(text)
        }

        override fun onClosing(
            webSocket: OkHttpWebSocket,
            code: Int,
            reason: String,
        ) {
            // OkHttp reports onClosed only once both sides sent a close frame.
            webSocket.close(1000, null)
        }

        override fun onClosed(
            webSocket: OkHttpWebSocket,
            code: Int,
            reason: String,
        ) {
            if (!ended.compareAndSet(false, true)) return
            socket = null
            incoming.close()
            out.onClosed(code, reason)
        }

        override fun onFailure(
            webSocket: OkHttpWebSocket,
            t: Throwable,
            response: Response?,
        ) {
            if (!ended.compareAndSet(false, true)) return
            socket = null
            incoming.close()
            out.onFailure(t, response?.code, response?.message)
        }
    }
}
