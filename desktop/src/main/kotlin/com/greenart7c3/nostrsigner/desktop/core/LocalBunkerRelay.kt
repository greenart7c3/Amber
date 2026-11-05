package com.greenart7c3.nostrsigner.desktop.core

import com.greenart7c3.nostrsigner.common.relays.LocalRelayServer

/**
 * The desktop's local bunker relay ([LocalRelayServer], shared with the
 * Android app): `ws://127.0.0.1:7777` (7778 for debug runs). Opt out under
 * Settings -> Desktop ([DesktopSettings.localBunkerServer]); the app itself
 * subscribes to it like any other relay (`BunkerEngine`), and new bunker
 * connections list it beside the default relays (`AmberDesktop.newBunkerRelays`).
 */
object LocalBunkerRelay : LocalRelayServer(
    logger = object : LocalRelayServer.Logger {
        override fun d(message: String) = AmberLogger.d(LocalRelayServer.TAG, message)

        override fun i(message: String) = AmberLogger.i(LocalRelayServer.TAG, message)

        override fun e(message: String, throwable: Throwable?) = AmberLogger.e(LocalRelayServer.TAG, message, throwable)
    },
    approvedSenders = { AmberDesktop.approvedRelaySenders() },
)
