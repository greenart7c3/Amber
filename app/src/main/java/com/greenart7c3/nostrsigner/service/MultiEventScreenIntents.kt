package com.greenart7c3.nostrsigner.service

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.greenart7c3.nostrsigner.models.AmberBunkerRequest
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.ui.RememberType

object MultiEventScreenIntents {
    var intents = listOf<IntentData>()
    var bunkerRequests = listOf<AmberBunkerRequest>()
    var appName = ""
    val checkedStates = mutableStateMapOf<String, Boolean>()
    var rememberType by mutableStateOf(RememberType.NEVER)
}
