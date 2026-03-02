package com.greenart7c3.nostrsigner.ui

import androidx.compose.runtime.Immutable
import com.greenart7c3.nostrsigner.Amber
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

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
    val toasts = MutableSharedFlow<ToastMsg?>(0, 3, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    fun clearToasts() {
        Amber.instance.applicationIOScope.launch { toasts.emit(null) }
    }

    fun toast(
        title: String,
        message: String,
    ) {
        Amber.instance.applicationIOScope.launch { toasts.emit(StringToastMsg(title, message)) }
    }

    fun toast(
        title: String,
        message: String,
        onOk: () -> Unit,
    ) {
        Amber.instance.applicationIOScope.launch { toasts.emit(ConfirmationToastMsg(title, message, onOk)) }
    }

    fun toast(
        title: String,
        message: String,
        onAccept: () -> Unit,
        onReject: () -> Unit,
    ) {
        Amber.instance.applicationIOScope.launch { toasts.emit(AcceptRejectToastMsg(title, message, onAccept, onReject)) }
    }
}
