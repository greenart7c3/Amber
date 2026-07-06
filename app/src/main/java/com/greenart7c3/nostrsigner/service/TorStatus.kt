package com.greenart7c3.nostrsigner.service

sealed class TorStatus {
    data object Stopped : TorStatus()

    data class Connecting(val percentage: Int) : TorStatus()

    data object Connected : TorStatus()

    data class Failed(val message: String?) : TorStatus()
}
