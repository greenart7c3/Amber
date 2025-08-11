package com.greenart7c3.nostrsigner.relays

import android.util.Log
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.models.Account
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.IRelayClientListener
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayState
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient

class BunkerValidationRelayListener(
    val account: Account,
    val onReceiveEvent: (relay: IRelayClient, subscriptionId: String, event: Event) -> Unit,
) : IRelayClientListener {
    override fun onAuth(relay: IRelayClient, challenge: String) {
        Log.d(Amber.TAG, "Received auth challenge $challenge from relay ${relay.url}")
    }

    override fun onBeforeSend(relay: IRelayClient, event: Event) {
        Log.d(Amber.TAG, "Sending event ${event.id} to relay ${relay.url}")
    }

    override fun onError(relay: IRelayClient, subId: String, error: Error) {
        Log.d(Amber.TAG, "Received error $error from subscription $subId")
    }

    override fun onEvent(relay: IRelayClient, subId: String, event: Event, arrivalTime: Long, afterEOSE: Boolean) {
        Log.d(Amber.TAG, "Received event ${event.toJson()} from subscription $subId afterEOSE: $afterEOSE")
        onReceiveEvent(relay, subId, event)
    }

    override fun onNotify(relay: IRelayClient, description: String) {
        Log.d(Amber.TAG, "Received notify $description from relay ${relay.url.url}")
    }

    override fun onRelayStateChange(relay: IRelayClient, type: RelayState) {
        Log.d(Amber.TAG, "Relay ${relay.url} state changed to $type")
    }

    override fun onSend(relay: IRelayClient, msg: String, success: Boolean) {
        Log.d(Amber.TAG, "Sent message $msg to relay ${relay.url} success: $success")
    }

    override fun onSendResponse(relay: IRelayClient, eventId: String, success: Boolean, message: String) {
        Log.d(Amber.TAG, "Sent response to event $eventId to relay ${relay.url} success: $success message: $message")
    }
}
