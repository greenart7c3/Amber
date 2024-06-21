/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.greenart7c3.nostrsigner.relays

import android.util.Log
import com.greenart7c3.nostrsigner.BuildConfig
import com.greenart7c3.nostrsigner.checkNotInMainThread
import com.greenart7c3.nostrsigner.service.HttpClientManager
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.events.EventInterface
import com.vitorpamplona.quartz.events.RelayAuthEvent
import com.vitorpamplona.quartz.events.bytesUsedInMemory
import com.vitorpamplona.quartz.utils.TimeUtils
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

enum class FeedType {
    FOLLOWS,
    PUBLIC_CHATS,
    PRIVATE_DMS,
    GLOBAL,
    SEARCH,
    WALLET_CONNECT,
}

class Relay(
    val url: String,
    private val read: Boolean = true,
    private val write: Boolean = true,
    private val activeTypes: Set<FeedType> = FeedType.entries.toSet(),
) {
    companion object {
        const val RECONNECTING_IN_SECONDS = 60
    }

    private val httpClient =
        if (url.startsWith("ws://127.0.0.1") || url.startsWith("ws://localhost")) {
            HttpClientManager.getHttpClient(false)
        } else {
            HttpClientManager.getHttpClient()
        }

    private var listeners = setOf<Listener>()
    private var socket: WebSocket? = null
    private var isReady: Boolean = false
    private var usingCompression: Boolean = false

    var eventDownloadCounterInBytes = 0
    private var eventUploadCounterInBytes = 0

    var errorCounter = 0
    private var pingInMs: Long? = null

    private var lastConnectTentative: Long = 0L

    private var afterEOSEPerSubscription = mutableMapOf<String, Boolean>()

    private val authResponse = mutableMapOf<HexKey, Boolean>()

    fun register(listener: Listener) {
        listeners = listeners.plus(listener)
    }

    fun unregister(listener: Listener) {
        listeners = listeners.minus(listener)
    }

    fun isConnected(): Boolean {
        return socket != null
    }

    fun connect() {
        connectAndRun {
            checkNotInMainThread()

            // Sends everything.
            renewFilters()
        }
    }

    private var connectingBlock = AtomicBoolean()

    fun connectAndRun(
        onConnected: (Relay) -> Unit,
    ) {
        Log.d("Relay", "Relay.connect $url hasProxy: ${this.httpClient.proxy != null}")
        // BRB is crashing OkHttp Deflater object :(
        if (url.contains("brb.io")) return

        // If there is a connection, don't wait.
        if (connectingBlock.getAndSet(true)) {
            return
        }

        checkNotInMainThread()

        if (socket != null) return

        lastConnectTentative = TimeUtils.now()

        try {
            val request =
                Request.Builder()
                    .header("User-Agent", "Amber/${BuildConfig.VERSION_NAME}")
                    .url(url.trim())
                    .build()

            socket = httpClient.newWebSocket(request, RelayListener(onConnected))
        } catch (e: Exception) {
            if (e is CancellationException) throw e

            errorCounter++
            markConnectionAsClosed()
            Log.e("Relay", "Relay Invalid $url")
            e.printStackTrace()
        } finally {
            connectingBlock.set(false)
        }
    }

    inner class RelayListener(val onConnected: (Relay) -> Unit) : WebSocketListener() {
        override fun onOpen(
            webSocket: WebSocket,
            response: Response,
        ) {
            checkNotInMainThread()
            Log.d("Relay", "Connect onOpen $url $socket")

            markConnectionAsReady(
                pingInMs = response.receivedResponseAtMillis - response.sentRequestAtMillis,
                usingCompression = response.headers["Sec-WebSocket-Extensions"]?.contains("permessage-deflate") ?: false,
            )

            // Log.w("Relay", "Relay OnOpen, Loading All subscriptions $url")
            onConnected(this@Relay)

            listeners.forEach { it.onRelayStateChange(this@Relay, StateType.CONNECT, null) }
        }

        override fun onMessage(
            webSocket: WebSocket,
            text: String,
        ) {
            checkNotInMainThread()

            eventDownloadCounterInBytes += text.bytesUsedInMemory()

            try {
                processNewRelayMessage(text)
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                e.printStackTrace()
                text.chunked(2000) { chunked ->
                    listeners.forEach { it.onError(this@Relay, "", Error("Problem with $chunked")) }
                }
            }
        }

        override fun onClosing(
            webSocket: WebSocket,
            code: Int,
            reason: String,
        ) {
            checkNotInMainThread()

            Log.w("Relay", "Relay onClosing $url: $reason")

            listeners.forEach {
                it.onRelayStateChange(
                    this@Relay,
                    StateType.DISCONNECTING,
                    null,
                )
            }
        }

        override fun onClosed(
            webSocket: WebSocket,
            code: Int,
            reason: String,
        ) {
            checkNotInMainThread()

            markConnectionAsClosed()

            Log.w("Relay", "Relay onClosed $url: $reason")

            listeners.forEach { it.onRelayStateChange(this@Relay, StateType.DISCONNECT, null) }
        }

        override fun onFailure(
            webSocket: WebSocket,
            t: Throwable,
            response: Response?,
        ) {
            checkNotInMainThread()

            errorCounter++

            socket?.cancel() // 1000, "Normal close"

            // checks if this is an actual failure. Closing the socket generates an onFailure as well.
            if (!(socket == null && (t.message == "Socket is closed" || t.message == "Socket closed"))) {
                Log.w("Relay", "Relay onFailure $url, ${response?.message} $response")
                t.printStackTrace()
                listeners.forEach {
                    it.onError(
                        this@Relay,
                        "",
                        Error("WebSocket Failure. Response: $response. Exception: ${t.message}", t),
                    )
                }
            }
            markConnectionAsClosed()
        }
    }

    fun markConnectionAsReady(
        pingInMs: Long,
        usingCompression: Boolean,
    ) {
        this.resetEOSEStatuses()
        this.isReady = true
        this.pingInMs = pingInMs
        this.usingCompression = usingCompression
    }

    fun markConnectionAsClosed() {
        this.socket = null
        this.isReady = false
        this.usingCompression = false
        this.resetEOSEStatuses()
    }

    fun processNewRelayMessage(newMessage: String) {
        val msgArray = Event.mapper.readTree(newMessage)

        when (val type = msgArray.get(0).asText()) {
            "EVENT" -> {
                val subscriptionId = msgArray.get(1).asText()
                val event = Event.fromJson(msgArray.get(2))

                // Log.w("Relay", "Relay onEVENT ${event.kind} $url, $subscriptionId ${msgArray.get(2)}")
                listeners.forEach {
                    it.onEvent(
                        this@Relay,
                        subscriptionId,
                        event,
                        afterEOSEPerSubscription[subscriptionId] == true,
                    )
                }
            }
            "EOSE" ->
                listeners.forEach {
                    val subscriptionId = msgArray.get(1).asText()

                    afterEOSEPerSubscription[subscriptionId] = true
                    // Log.w("Relay", "Relay onEOSE $url $subscriptionId")
                    it.onRelayStateChange(this@Relay, StateType.EOSE, subscriptionId)
                }
            "NOTICE" ->
                listeners.forEach {
                    val message = msgArray.get(1).asText()
                    Log.w("Relay", "Relay onNotice $url, $message")

                    it.onError(this@Relay, message, Error("Relay sent notice: $message"))
                }
            "OK" ->
                listeners.forEach {
                    val eventId = msgArray[1].asText()
                    val success = msgArray[2].asBoolean()
                    val message = if (msgArray.size() > 2) msgArray[3].asText() else ""

                    if (authResponse.containsKey(eventId)) {
                        val wasAlreadyAuthenticated = authResponse[eventId]
                        authResponse[eventId] = success
                        if (wasAlreadyAuthenticated != true && success) {
                            renewFilters()
                        }
                    }

                    Log.w("Relay", "Relay on OK $url, $eventId, $success, $message")
                    it.onSendResponse(this@Relay, eventId, success, message)
                }
            "AUTH" ->
                listeners.forEach {
                    Log.w("Relay", "Relay onAuth $url, ${msgArray[1].asText()}")
                    it.onAuth(this@Relay, msgArray[1].asText())
                }
            "NOTIFY" ->
                listeners.forEach {
                    // Log.w("Relay", "Relay onNotify $url, ${msg[1].asString}")
                    it.onNotify(this@Relay, msgArray[1].asText())
                }
            "CLOSED" -> listeners.forEach { _ -> Log.w("Relay", "Relay onClosed $url, $newMessage") }
            else ->
                listeners.forEach {
                    Log.w("Relay", "Unsupported message: $newMessage")
                    it.onError(
                        this@Relay,
                        "",
                        Error("Unknown type $type on channel. Msg was $newMessage"),
                    )
                }
        }
    }

    fun isReady(): Boolean {
        return this.isReady
    }

    fun disconnect() {
        Log.d("Relay", "Relay.disconnect $url")
        checkNotInMainThread()

        lastConnectTentative = 0L // this is not an error, so prepare to reconnect as soon as requested.
        socket?.cancel()
        socket = null
        isReady = false
        usingCompression = false
        resetEOSEStatuses()
    }

    private fun resetEOSEStatuses() {
        afterEOSEPerSubscription = LinkedHashMap(afterEOSEPerSubscription.size)
    }

    fun sendFilter(requestId: String) {
        checkNotInMainThread()

        if (read) {
            if (isConnected()) {
                if (isReady) {
                    val filters =
                        Client.getSubscriptionFilters(requestId).filter { filter ->
                            activeTypes.any { it in filter.types }
                        }
                    if (filters.isNotEmpty()) {
                        val request =
                            filters.joinToStringLimited(
                                separator = ",",
                                limit = 20,
                                prefix = """["REQ","$requestId",""",
                                postfix = "]",
                            ) {
                                it.filter.toJson(url)
                            }

                        // Log.d("Relay", "onFilterSent $url $requestId $request")

                        socket?.send(request)
                        eventUploadCounterInBytes += request.bytesUsedInMemory()
                        resetEOSEStatuses()
                    }
                }
            } else {
                // waits 60 seconds to reconnect after disconnected.
                if (TimeUtils.now() > lastConnectTentative + RECONNECTING_IN_SECONDS) {
                    // sends all filters after connection is successful.
                    connect()
                }
            }
        }
    }

    private fun <T> Iterable<T>.joinToStringLimited(
        separator: CharSequence = ", ",
        prefix: CharSequence = "",
        postfix: CharSequence = "",
        limit: Int = -1,
        transform: ((T) -> CharSequence)? = null,
    ): String {
        val buffer = StringBuilder()
        buffer.append(prefix)
        var count = 0
        for (element in this) {
            if (limit < 0 || count <= limit) {
                if (++count > 1) buffer.append(separator)
                when {
                    transform != null -> buffer.append(transform(element))
                    element is CharSequence? -> buffer.append(element)
                    element is Char -> buffer.append(element)
                    else -> buffer.append(element.toString())
                }
            } else {
                break
            }
        }
        buffer.append(postfix)
        return buffer.toString()
    }

    fun connectAndSendFiltersIfDisconnected() {
        checkNotInMainThread()

        if (socket == null) {
            // waits 60 seconds to reconnect after disconnected.
            if (TimeUtils.now() > lastConnectTentative + RECONNECTING_IN_SECONDS) {
                // println("sendfilter Only if Disconnected ${url} ")
                connect()
            }
        }
    }

    private fun renewFilters() {
        // Force update all filters after AUTH.
        Client.allSubscriptions().forEach { sendFilter(requestId = it) }
    }

    fun send(
        signedEvent: EventInterface,
    ) {
        checkNotInMainThread()

        listeners.forEach {
            it.onBeforeSend(this@Relay, signedEvent)
        }

        if (signedEvent is RelayAuthEvent) {
            authResponse[signedEvent.id] = false
            // specific protocol for this event.
            val event = """["AUTH",${signedEvent.toJson()}]"""
            var result = socket?.send(event)
            while (result == false || result == null) {
                Log.d("Relay", "Relay.send failed trying again $url $event")
                result = socket?.send(event)
            }
            listeners.forEach {
                it.onSend(this@Relay, signedEvent, result == true)
            }
            eventUploadCounterInBytes += event.bytesUsedInMemory()
        } else {
            val maxTries = 3
            if (write) {
                val event = """["EVENT",${signedEvent.toJson()}]"""
                if (isConnected()) {
                    if (isReady) {
                        var result = socket?.send(event)
                        var tryCount = 0
                        while (tryCount < maxTries && (result == false || result == null)) {
                            tryCount++
                            Log.d("Relay", "Relay.send failed trying again $url $event")
                            result = socket?.send(event)
                        }
                        listeners.forEach {
                            // Log.w("Relay", "Relay onNotify $url, ${msg[1].asString}")
                            it.onSend(this@Relay, signedEvent, result == true)
                        }
                        eventUploadCounterInBytes += event.bytesUsedInMemory()
                    }
                } else {
                    // sends all filters after connection is successful.
                    connectAndRun {
                        checkNotInMainThread()

                        var result = socket?.send(event)
                        var tryCount = 0
                        while (tryCount < maxTries && (result == false || result == null)) {
                            tryCount++
                            Log.d("Relay", "Relay.send failed trying again $url $event")
                            result = socket?.send(event)
                        }
                        eventUploadCounterInBytes += event.bytesUsedInMemory()

                        // Sends everything.
                        Client.allSubscriptions().forEach { sendFilter(requestId = it) }
                    }
                }
            }
        }
    }

    fun close(subscriptionId: String) {
        checkNotInMainThread()

        val msg = """["CLOSE","$subscriptionId"]"""
        // Log.d("Relay", "Close Subscription $url $msg")
        socket?.send(msg)
    }

    fun isSameRelayConfig(other: Relay): Boolean {
        return url == other.url &&
            write == other.write &&
            read == other.read &&
            activeTypes == other.activeTypes
    }

    enum class StateType {
        // Websocket connected
        CONNECT,

        // Websocket disconnecting
        DISCONNECTING,

        // Websocket disconnected
        DISCONNECT,

        // End Of Stored Events
        EOSE,
    }

    interface Listener {
        /** A new message was received */
        fun onEvent(
            relay: Relay,
            subscriptionId: String,
            event: Event,
            afterEOSE: Boolean,
        )

        fun onError(
            relay: Relay,
            subscriptionId: String,
            error: Error,
        )

        fun onSendResponse(
            relay: Relay,
            eventId: String,
            success: Boolean,
            message: String,
        )

        fun onAuth(
            relay: Relay,
            challenge: String,
        )

        /**
         * Connected to or disconnected from a relay
         *
         * @param type is 0 for disconnect and 1 for connect
         */
        fun onRelayStateChange(
            relay: Relay,
            type: StateType,
            channel: String?,
        )

        /** Relay sent an invoice */
        fun onNotify(
            relay: Relay,
            description: String,
        )

        fun onSend(
            relay: Relay,
            event: EventInterface,
            success: Boolean,
        )

        fun onBeforeSend(
            relay: Relay,
            event: EventInterface,
        )
    }
}
