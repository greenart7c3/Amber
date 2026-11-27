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

    private val _status = MutableStateFlow<TorStatus>(TorStatus.Stopped)
    val status: StateFlow<TorStatus> = _status.asStateFlow()

    @Suppress("UNUSED_PARAMETER")
    fun init(context: Context) = Unit

    @Suppress("UNUSED_PARAMETER")
    fun start(context: Context, scope: CoroutineScope) = Unit

    @Suppress("UNUSED_PARAMETER")
    fun restart(context: Context, scope: CoroutineScope) = Unit

    fun stop() = Unit

    fun showRetrying() = Unit

    fun showFailed() = Unit

    fun cancelNotification() = Unit
}
