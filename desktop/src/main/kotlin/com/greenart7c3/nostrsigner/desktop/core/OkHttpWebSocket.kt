/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.greenart7c3.nostrsigner.desktop.core

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket as OkHttpWebSocket
import okhttp3.WebSocketListener as OkHttpWebSocketListener

/**
 * Copy of Quartz's `BasicOkHttpWebSocket` from amethyst main
 * (de7b135d2a411b4ac7348ea4f654a25e28aa592d). Quartz 1.17.0 ships a version
 * without the in-flight dial guard ([dialing]) and without storing the socket
 * in `onOpen`, which double-dials relays and drops the REQs sent from
 * `onOpen`. Mirrors the Android app's `okhttp/OkHttpWebSocket`. Drop it for
 * Quartz's class once a release includes both fixes.
 */
class OkHttpWebSocket(
    val url: NormalizedRelayUrl,
    val httpClient: (NormalizedRelayUrl) -> OkHttpClient,
    val out: WebSocketListener,
) : WebSocket {
    companion object {
        /**
         * Buffered relay messages before the oldest one is dropped. Deep
         * enough to absorb a burst while the consumer catches up (pages of
         * EVENTs across 2000+ relay connections), small enough that a wedged
         * consumer costs memory, not the process.
         */
        private const val MESSAGE_BUFFER_CAPACITY = 1024

        // Exists to avoid exceptions stopping the coroutine
        val exceptionHandler =
            CoroutineExceptionHandler { _, throwable ->
                AmberLogger.e("OkHttpWebSocket", "WebsocketListener Caught exception: ${throwable.message}", throwable)
            }
    }

    @Volatile private var socket: OkHttpWebSocket? = null

    /**
     * Set once, by whichever of `onClosed`, `onFailure` or [disconnect] ends the session first.
     *
     * One adapter is one session: the relay client builds a fresh one per dial, and OkHttp binds
     * exactly one socket to the listener created in [connect], so anything that reaches that
     * listener is from this session by construction. The only question a callback has to ask is
     * whether the session already ended -- which is what keeps the [WebSocket.disconnect] contract:
     * after [disconnect] the failure OkHttp raises for its own `cancel()` on the reader thread, or
     * the `onClosed` its writer thread delivers once a close handshake completes, is dropped rather
     * than reaching a relay client that has already moved on. Claimed with a compare-and-set so a
     * [disconnect] racing a terminal callback still yields exactly one report.
     */
    private val ended = AtomicBoolean(false)

    /**
     * True from the start of [connect] until OkHttp hands back its socket. A dial in flight is not
     * a dead session: reporting it as one let a concurrent reconnect pass tear it down and dial
     * again, and the first dial, never ended, opened as well. Measured with amy under load: two
     * connections to one relay, the REQ sent twice and every message of the page delivered twice.
     */
    @Volatile private var dialing = false

    override fun needsReconnect() = ended.get() || (socket == null && !dialing)

    override fun connect() {
        if (socket != null || dialing || ended.get()) return
        dialing = true

        val request = Request.Builder().url(url.url).build()

        val listener =
            object : OkHttpWebSocketListener() {
                val scope = CoroutineScope(Dispatchers.IO + exceptionHandler)

                // A bounded hand-off: `offer` never blocks, so the OkHttp
                // reader thread stays free to keep draining the socket (the
                // reason this was UNLIMITED — backpressure must park on the
                // app, never on the relay's outbound buffers). But the queue
                // itself is capped: an unbounded buffer under a flooded or
                // wedged consumer grew until the process OOMed. On overflow
                // the oldest buffered message is dropped; see
                // [RelayMessageBuffer].
                val incomingMessages = RelayMessageBuffer(MESSAGE_BUFFER_CAPACITY)
                val job = // Launch a coroutine to process messages from the buffer.
                    scope.launch {
                        while (true) {
                            val message = incomingMessages.receive() ?: break
                            out.onMessage(message)
                        }
                    }

                /** Claims the session's single terminal report. False if it already ended. */
                private fun endSession(): Boolean {
                    if (!ended.compareAndSet(false, true)) return false
                    socket = null
                    incomingMessages.close()
                    job.cancel()
                    scope.cancel()
                    return true
                }

                override fun onOpen(
                    webSocket: OkHttpWebSocket,
                    response: Response,
                ) {
                    if (ended.get()) return
                    // OkHttp dials on its own thread, so this can run before newWebSocket()
                    // returns to connect() and stores the socket. The relay client sends its
                    // REQs from onOpen: without this they went to a null socket and were
                    // dropped, and the relay, never asked, never answered.
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
                    // Never blocks (bounded buffer, drops the oldest on
                    // overflow): the OkHttp reader thread must stay free to
                    // keep draining the socket.
                    incomingMessages.offer(text)
                }

                override fun onClosing(
                    webSocket: OkHttpWebSocket,
                    code: Int,
                    reason: String,
                ) {
                    // The relay sent a CLOSE frame. OkHttp's contract (WebSocketListener KDoc,
                    // RealWebSocket, and its own WebSocketEcho recipe) is that onClosed fires
                    // only once BOTH peers have sent a close, and sending ours is the
                    // application's job. Left unanswered, the socket sits half-closed: no
                    // onClosed, no onFailure, send() still accepted and silently discarded, and
                    // a later cancel() is silent too -- so the relay client kept believing it
                    // was connected, with its REQs live, until OkHttp's 120s ping path finally
                    // failed up to two intervals later. Answering completes the handshake and
                    // OkHttp reports onClosed at once, whether or not the relay still holds the
                    // TCP session open.
                    //
                    // Always 1000 rather than echoing `code`: close() validates the code it is
                    // asked to write and throws on the reserved ones (1005, 1006, 1015), and a
                    // relay may send anything.
                    webSocket.close(1000, null)
                }

                override fun onClosed(
                    webSocket: OkHttpWebSocket,
                    code: Int,
                    reason: String,
                ) {
                    if (!endSession()) return
                    out.onClosed(code, reason)
                }

                override fun onFailure(
                    webSocket: OkHttpWebSocket,
                    t: Throwable,
                    response: Response?,
                ) {
                    if (!endSession()) return
                    out.onFailure(t, response?.code, response?.message)
                }
            }

        try {
            val dialed = httpClient(url).newWebSocket(request, listener)
            socket = dialed
            // A disconnect() that landed while dialing ended the session but had no socket to
            // cancel yet: cancel it now, or it opens and lives on beside the relay client's next one.
            if (ended.get()) {
                socket = null
                dialed.cancel()
            }
        } finally {
            dialing = false
        }
    }

    override fun disconnect() {
        // Claim the session ourselves: OkHttp's cancel() raises no callback when no reader is
        // left to fail (the state a relay-initiated close leaves behind), and when it does the
        // failure arrives later on its own thread. The relay client needs the answer now.
        //
        // Claimed even with no socket yet: a dial still in flight must not open after this
        // (connect() cancels it once OkHttp returns it), and its callbacks must find the session
        // ended. Nothing was open, so there is nothing to report.
        if (!ended.compareAndSet(false, true)) return
        val closing = socket ?: return
        socket = null
        closing.cancel()
        out.onClosed(1000, "client disconnect")
    }

    override fun send(msg: String): Boolean = socket?.send(msg) ?: false

    class Builder(
        val httpClient: (NormalizedRelayUrl) -> OkHttpClient,
    ) : WebsocketBuilder {
        // Called when connecting.
        override fun build(
            url: NormalizedRelayUrl,
            out: WebSocketListener,
        ): WebSocket = OkHttpWebSocket(url, httpClient, out)
    }
}

/**
 * Bounded hand-off between OkHttp's reader thread and the consumer coroutine.
 *
 * `offer` never blocks — the reader thread must stay free to keep draining the
 * socket, and backpressure must park on the app, never on the relay's outbound
 * buffers. The queue itself is capped, though: unbounded, it OOMed the whole
 * app when a relay flooded or the consumer wedged. At capacity the OLDEST
 * buffered message is dropped to make room for the newest one (a fresh EVENT
 * is worth more than a stale one) and the drop is counted and logged
 * ([AmberLogger] has no warn level; error is its always-on channel).
 */
internal class RelayMessageBuffer(capacity: Int) {
    private val channel = Channel<String>(capacity)
    private val droppedCount = AtomicLong()

    /** Never blocks: on a full buffer, drops the oldest message and retries. */
    fun offer(message: String) {
        while (true) {
            val result = channel.trySend(message)
            if (result.isSuccess || result.isClosed) return
            // Full: make room by dropping the oldest buffered message. A
            // closed-and-empty channel can never accept again, so give up.
            if (channel.tryReceive().isClosed) return
            val dropped = droppedCount.incrementAndGet()
            if (dropped == 1L || dropped % DROP_LOG_EVERY == 0L) {
                AmberLogger.e("OkHttpWebSocket", "Slow relay consumer: $dropped buffered messages dropped so far")
            }
        }
    }

    /** Next message, or null once the session ended and the buffer drained. */
    suspend fun receive(): String? = channel.receiveCatching().getOrNull()

    /** Ends the session side: no further offers accepted; receives drain, then return null. */
    fun close() {
        channel.close()
    }

    val dropped: Long get() = droppedCount.get()

    companion object {
        private const val DROP_LOG_EVERY = 1000L
    }
}
