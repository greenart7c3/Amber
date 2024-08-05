package com.greenart7c3.nostrsigner.models

object Settings {
    var endPoint: String = ""
    var pushServerMessage: Boolean = true
    var defaultRelays: List<String> = listOf("wss://relay.nsec.app")
    var lastBiometricsTime: Long = 0
    var useAuth: Boolean = false

    init {

    }
}
