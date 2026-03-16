package com.greenart7c3.nostrsigner.service

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object TorManager {
    private val _socksPort = MutableStateFlow(0)
    val socksPort: StateFlow<Int> = _socksPort.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    @Suppress("UNUSED_PARAMETER")
    fun init(context: Context) {
        // No-op in offline flavor
    }

    @Suppress("UNUSED_PARAMETER")
    fun start(context: Context, scope: CoroutineScope) {
        // No-op in offline flavor
    }

    fun stop() {
        // No-op in offline flavor
    }

    fun showRetrying() {
        // No-op in offline flavor
    }

    fun cancelNotification() {
        // No-op in offline flavor
    }
}
