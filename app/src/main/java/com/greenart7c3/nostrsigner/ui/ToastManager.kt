package com.greenart7c3.nostrsigner.ui

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

@Immutable
open class ToastMsg

@Immutable
class StringToastMsg(val title: String, val msg: String) : ToastMsg()

@Immutable
class ConfirmationToastMsg(val title: String, val msg: String, val onOk: () -> Unit) : ToastMsg()

@Immutable
class AcceptRejectToastMsg(
    val title: String,
    val msg: String,
    val onAccept: () -> Unit,
    val onReject: () -> Unit,
) : ToastMsg()

@Immutable
class ResourceToastMsg(
    val titleResId: Int,
    val resourceId: Int,
    val params: Array<out String>? = null,
) : ToastMsg()

object ToastManager {
    // DROP_OLDEST means tryEmit always succeeds, so callers no longer need to
    // spawn a coroutine on the application scope just to push a message.
    val toasts = MutableSharedFlow<ToastMsg?>(0, 3, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    fun clearToasts() {
        toasts.tryEmit(null)
    }

    fun toast(
        title: String,
        message: String,
    ) {
        toasts.tryEmit(StringToastMsg(title, message))
    }

    fun toast(
        title: String,
        message: String,
        onOk: () -> Unit,
    ) {
        toasts.tryEmit(ConfirmationToastMsg(title, message, onOk))
    }

    fun toast(
        title: String,
        message: String,
        onAccept: () -> Unit,
        onReject: () -> Unit,
    ) {
        toasts.tryEmit(AcceptRejectToastMsg(title, message, onAccept, onReject))
    }
}
