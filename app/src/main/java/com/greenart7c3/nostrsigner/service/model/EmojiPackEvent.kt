package com.greenart7c3.nostrsigner.service.model

import androidx.compose.runtime.Immutable
import com.greenart7c3.nostrsigner.models.HexKey
import com.greenart7c3.nostrsigner.models.TimeUtils
import com.greenart7c3.nostrsigner.models.toHexKey
import com.greenart7c3.nostrsigner.service.CryptoUtils

@Immutable
class EmojiPackEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : GeneralListEvent(id, pubKey, createdAt, kind, tags, content, sig) {

    companion object {
        const val kind = 30030

        fun create(
            name: String = "",
            privateKey: ByteArray,
            createdAt: Long = TimeUtils.now()
        ): EmojiPackEvent {
            val content = ""
            val pubKey = CryptoUtils.pubkeyCreate(privateKey)

            val tags = mutableListOf<List<String>>()
            tags.add(listOf("d", name))

            val id = generateId(pubKey.toHexKey(), createdAt, kind, tags, content)
            val sig = CryptoUtils.sign(id, privateKey)
            return EmojiPackEvent(id.toHexKey(), pubKey.toHexKey(), createdAt, tags, content, sig.toHexKey())
        }
    }
}

@Immutable
data class EmojiUrl(val code: String, val url: String) {
    fun encode(): String {
        return ":$code:$url"
    }

    companion object {
        fun decode(encodedEmojiSetup: String): EmojiUrl? {
            val emojiParts = encodedEmojiSetup.split(":", limit = 3)
            return if (emojiParts.size > 2) {
                EmojiUrl(emojiParts[1], emojiParts[2])
            } else {
                null
            }
        }
    }
}
