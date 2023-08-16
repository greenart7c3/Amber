package com.greenart7c3.nostrsigner.service

import android.content.Intent
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.service.model.Event
import java.net.URLDecoder

object IntentUtils {
    fun getIntentData(intent: Intent): IntentData? {
        if (intent.data != null) {
            val data = URLDecoder.decode(intent.data?.toString()?.replace("+", "%2b") ?: "", "utf-8").replace("nostrsigner:", "")
            val type = when (intent.extras?.getString("type")) {
                "sign_event" -> SignerType.SIGN_EVENT
                "nip04_encrypt" -> SignerType.NIP04_ENCRYPT
                "nip04_decrypt" -> SignerType.NIP04_DECRYPT
                else -> SignerType.SIGN_EVENT
            }
            val split = data.split(";")
            var name = ""
            if (split.isNotEmpty()) {
                if (split.last().lowercase().contains("name=")) {
                    name = split.last().replace("name=", "")
                }
            }

            return IntentData(data, name, type)
        }
        return null
    }
    fun getIntent(data: String, keyPair: KeyPair): Event {
        var tempData = data
        val split = data.split(";")
        if (split.isNotEmpty()) {
            if (split.last().lowercase().contains("name=")) {
                val newList = split.toList().dropLast(1)
                tempData = newList.joinToString("")
            }
        }
        var event = Event.fromJson(tempData)
        if (event.pubKey.isEmpty()) {
            event = Event.setPubKeyIfEmpty(event, keyPair)
        }
        return event
    }
}
