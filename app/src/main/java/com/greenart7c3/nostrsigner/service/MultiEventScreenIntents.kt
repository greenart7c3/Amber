package com.greenart7c3.nostrsigner.service

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.greenart7c3.nostrsigner.ui.RememberType

/** The batch screens' choices: which requests are approved (by queue key) and how long to remember the answer. */
object MultiEventScreenIntents {
    val checkedStates = mutableStateMapOf<String, Boolean>()
    var rememberType by mutableStateOf(RememberType.NEVER)
}
