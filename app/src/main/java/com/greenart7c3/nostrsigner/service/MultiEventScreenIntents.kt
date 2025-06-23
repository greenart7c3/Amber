package com.greenart7c3.nostrsigner.service

import com.greenart7c3.nostrsigner.models.AmberBunkerRequest
import com.greenart7c3.nostrsigner.models.IntentData

object MultiEventScreenIntents {
    var intents = listOf<IntentData>()
    var bunkerRequests = listOf<AmberBunkerRequest>()
    var appName = ""
}
