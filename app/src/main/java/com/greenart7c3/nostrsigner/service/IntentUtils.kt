package com.greenart7c3.nostrsigner.service

import android.content.Intent
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.service.model.Event
import java.net.URLDecoder

object IntentUtils {
    fun getIntentData(intent: Intent, keyPair: KeyPair): IntentData? {
        if (intent.data != null) {
            var data = URLDecoder.decode(intent.data?.toString()?.replace("+", "%2b") ?: "", "utf-8").replace("nostrsigner:", "")
            val split = data.split(";")
            var name = ""
            if (split.isNotEmpty()) {
                if (split.last().lowercase().contains("name=")) {
                    name = split.last().replace("name=", "")
                    val newList = split.toList().dropLast(1)
                    data = newList.joinToString("")
                }
            }
            var event = Event.fromJson(data)
            if (event.pubKey.isEmpty()) {
                event = Event.setPubKeyIfEmpty(event, keyPair)
            }
            return IntentData(event, name)
        }
        return null
    }
}
