package com.greenart7c3.nostrsigner.service

import android.content.Intent
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.service.model.Event
import java.net.URLDecoder

object IntentUtils {
    fun getIntentData(intent: Intent): IntentData? {
        if (intent.data != null) {
            val type = when (intent.extras?.getString("type")) {
                "sign_event" -> SignerType.SIGN_EVENT
                "nip04_encrypt" -> SignerType.NIP04_ENCRYPT
                "nip04_decrypt" -> SignerType.NIP04_DECRYPT
                "nip44_decrypt" -> SignerType.NIP44_DECRYPT
                "nip44_encrypt" -> SignerType.NIP44_ENCRYPT
                "get_public_key" -> SignerType.GET_PUBLIC_KEY
                "decrypt_zap_event" -> SignerType.DECRYPT_ZAP_EVENT
                else -> SignerType.SIGN_EVENT
            }

            val data = try {
                when (type) {
                    SignerType.NIP44_ENCRYPT, SignerType.NIP04_DECRYPT, SignerType.NIP44_DECRYPT, SignerType.NIP04_ENCRYPT -> {
                        intent.data?.toString()?.replace("nostrsigner:", "") ?: ""
                    }
                    else -> URLDecoder.decode(intent.data?.toString()?.replace("+", "%2b") ?: "", "utf-8").replace("nostrsigner:", "")
                }
            } catch (e: Exception) {
                intent.data?.toString()?.replace("nostrsigner:", "") ?: ""
            }

            val split = data.split(";")
            var name = ""
            if (split.isNotEmpty()) {
                if (split.last().lowercase().contains("name=")) {
                    name = split.last().replace("name=", "")
                }
            }
            val pubKey = intent.extras?.getString("pubKey") ?: ""
            val id = intent.extras?.getString("id") ?: ""

            return IntentData(data, name, type, pubKey, id)
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
