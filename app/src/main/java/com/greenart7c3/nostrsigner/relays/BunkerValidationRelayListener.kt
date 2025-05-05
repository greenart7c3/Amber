package com.greenart7c3.nostrsigner.relays

import android.util.Log
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.models.Account
import com.vitorpamplona.ammolite.relays.NostrClient
import com.vitorpamplona.ammolite.relays.Relay
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.RelayState

class BunkerValidationRelayListener(
    val account: Account,
    val onReceiveEvent: (relay: Relay, subscriptionId: String, event: Event) -> Unit,
) : NostrClient.Listener {
    override fun onAuth(relay: Relay, challenge: String) {
        Log.d(Amber.TAG, "Received auth challenge $challenge from relay ${relay.url}")
    }

    override fun onBeforeSend(relay: Relay, event: Event) {
        Log.d(Amber.TAG, "Sending event ${event.id} to relay ${relay.url}")
    }

    override fun onError(error: Error, subscriptionId: String, relay: Relay) {
        Log.d(Amber.TAG, "Received error $error from subscription $subscriptionId")
    }

    override fun onEvent(event: Event, subscriptionId: String, relay: Relay, afterEOSE: Boolean) {
        Log.d(Amber.TAG, "Received event ${event.toJson()} from subscription $subscriptionId afterEOSE: $afterEOSE")
        onReceiveEvent(relay, subscriptionId, event)
    }

    override fun onNotify(relay: Relay, description: String) {
        Log.d(Amber.TAG, "Received notify $description from relay ${relay.url}")
    }

    override fun onRelayStateChange(type: RelayState, relay: Relay) {
        Log.d(Amber.TAG, "Relay ${relay.url} state changed to $type")
    }

    override fun onSend(relay: Relay, msg: String, success: Boolean) {
        Log.d(Amber.TAG, "Sent message $msg to relay ${relay.url} success: $success")
    }

    override fun onSendResponse(eventId: String, success: Boolean, message: String, relay: Relay) {
        Log.d(Amber.TAG, "Sent response to event $eventId to relay ${relay.url} success: $success message: $message")
    }
}
