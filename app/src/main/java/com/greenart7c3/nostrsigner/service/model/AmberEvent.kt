package com.greenart7c3.nostrsigner.service.model

import android.util.Log
import androidx.compose.runtime.Immutable
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.annotations.SerializedName
import com.greenart7c3.nostrsigner.models.TimeUtils
import com.greenart7c3.nostrsigner.service.KeyPair
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.events.EmojiUrl
import fr.acinq.secp256k1.Hex
import java.lang.reflect.Type
import java.math.BigDecimal
import java.util.*

@Immutable
open class AmberEvent(
    val id: HexKey,
    @SerializedName("pubkey") val pubKey: HexKey,
    @SerializedName("created_at") val createdAt: Long,
    val kind: Int,
    val tags: List<List<String>>,
    val content: String,
    val sig: HexKey
) : AmberEventInterface {
    override fun id(): HexKey = id

    override fun pubKey(): HexKey = pubKey

    override fun createdAt(): Long = createdAt

    override fun kind(): Int = kind
    override fun description(): String {
        return "event"
    }

    override fun tags(): List<List<String>> = tags

    override fun content(): String = content

    override fun sig(): HexKey = sig

    override fun toJson(): String = gson.toJson(this)

    fun hasAnyTaggedUser() = tags.any { it.size > 1 && it[0] == "p" }

    override fun taggedUsers() = tags.filter { it.size > 1 && it[0] == "p" }.map { it[1] }
    override fun taggedEvents() = tags.filter { it.size > 1 && it[0] == "e" }.map { it[1] }

    override fun taggedUrls() = tags.filter { it.size > 1 && it[0] == "r" }.map { it[1] }

    override fun taggedEmojis() = tags.filter { it.size > 2 && it[0] == "emoji" }.map { EmojiUrl(it[1], it[2]) }

    override fun isSensitive() = tags.any {
        (it.size > 0 && it[0].equals("content-warning", true)) ||
            (it.size > 1 && it[0] == "t" && it[1].equals("nsfw", true)) ||
            (it.size > 1 && it[0] == "t" && it[1].equals("nude", true))
    }

    override fun zapraiserAmount() = tags.firstOrNull() {
        (it.size > 1 && it[0] == "zapraiser")
    }?.get(1)?.toLongOrNull()

    override fun zapAddress() = tags.firstOrNull { it.size > 1 && it[0] == "zap" }?.get(1)

    override fun taggedAddresses() = tags.filter { it.size > 1 && it[0] == "a" }.mapNotNull {
        val aTagValue = it[1]
        val relay = it.getOrNull(2)

        ATag.parse(aTagValue, relay)
    }

    override fun hashtags() = tags.filter { it.size > 1 && it[0] == "t" }.map { it[1] }
    override fun geohashes() = tags.filter { it.size > 1 && it[0] == "g" }.map { it[1] }

    override fun matchTag1With(text: String) = tags.any { it.size > 1 && it[1].contains(text, true) }

    override fun isTaggedUser(idHex: String) = tags.any { it.size > 1 && it[0] == "p" && it[1] == idHex }

    override fun isTaggedEvent(idHex: String) = tags.any { it.size > 1 && it[0] == "e" && it[1] == idHex }

    override fun isTaggedAddressableNote(idHex: String) = tags.any { it.size > 1 && it[0] == "a" && it[1] == idHex }

    override fun isTaggedAddressableNotes(idHexes: Set<String>) = tags.any { it.size > 1 && it[0] == "a" && it[1] in idHexes }

    override fun isTaggedHash(hashtag: String) = tags.any { it.size > 1 && it[0] == "t" && it[1].equals(hashtag, true) }

    override fun isTaggedGeoHash(hashtag: String) = tags.any { it.size > 1 && it[0] == "g" && it[1].startsWith(hashtag, true) }
    override fun isTaggedHashes(hashtags: Set<String>) = tags.any { it.size > 1 && it[0] == "t" && it[1].lowercase() in hashtags }
    override fun isTaggedGeoHashes(hashtags: Set<String>) = tags.any { it.size > 1 && it[0] == "g" && it[1].lowercase() in hashtags }
    override fun firstIsTaggedHashes(hashtags: Set<String>) = tags.firstOrNull { it.size > 1 && it[0] == "t" && it[1].lowercase() in hashtags }?.getOrNull(1)

    override fun firstIsTaggedAddressableNote(addressableNotes: Set<String>) = tags.firstOrNull { it.size > 1 && it[0] == "a" && it[1] in addressableNotes }?.getOrNull(1)

    override fun isTaggedAddressableKind(kind: Int): Boolean {
        val kindStr = kind.toString()
        return tags.any { it.size > 1 && it[0] == "a" && it[1].startsWith(kindStr) }
    }

    override fun getTagOfAddressableKind(kind: Int): ATag? {
        val kindStr = kind.toString()
        val aTag = tags
            .firstOrNull { it.size > 1 && it[0] == "a" && it[1].startsWith(kindStr) }
            ?.getOrNull(1)
            ?: return null

        return ATag.parse(aTag, null)
    }

    override fun getPoWRank(): Int {
        var rank = 0
        for (i in 0..id.length) {
            if (id[i] == '0') {
                rank += 4
            } else if (id[i] in '4'..'7') {
                rank += 1
                break
            } else if (id[i] in '2'..'3') {
                rank += 2
                break
            } else if (id[i] == '1') {
                rank += 3
                break
            } else {
                break
            }
        }
        return rank
    }

    private fun isHex2(c: Char): Boolean {
        return when (c) {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f', 'A', 'B', 'C', 'D', 'E', 'F', ' ' -> true
            else -> false
        }
    }

    private fun recipientPubKey() = tags.firstOrNull { it.size > 1 && it[0] == "p" }?.get(1)

    fun verifiedRecipientPubKey(): HexKey? {
        val recipient = recipientPubKey()
        return if (isHex(recipient)) {
            recipient
        } else {
            null
        }
    }

    fun isHex(hex: String?): Boolean {
        if (hex == null) return false
        var isHex = true
        for (c in hex.toCharArray()) {
            if (!isHex2(c)) {
                isHex = false
                break
            }
        }
        return isHex
    }

    fun talkingWith(oneSideHex: String): HexKey {
        return if (pubKey == oneSideHex) verifiedRecipientPubKey() ?: pubKey else pubKey
    }

    override fun getGeoHash(): String? {
        return tags.firstOrNull { it.size > 1 && it[0] == "g" }?.get(1)?.ifBlank { null }
    }

    override fun getReward(): BigDecimal? {
        return try {
            tags.firstOrNull { it.size > 1 && it[0] == "reward" }?.get(1)?.let { BigDecimal(it) }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Checks if the ID is correct and then if the pubKey's secret key signed the event.
     */
    override fun checkSignature() {
        if (!id.contentEquals(generateId())) {
            throw Exception(
                """|Unexpected ID.
                   |  Event: ${toJson()}
                   |  Actual ID: $id
                   |  Generated: ${generateId()}
                """.trimIndent()
            )
        }
        if (!CryptoUtils.verifySignature(Hex.decode(sig), Hex.decode(id), Hex.decode(pubKey))) {
            throw Exception("""Bad signature!""")
        }
    }

    override fun hasValidSignature(): Boolean {
        return try {
            id.contentEquals(generateId()) && CryptoUtils.verifySignature(Hex.decode(sig), Hex.decode(id), Hex.decode(pubKey))
        } catch (e: Exception) {
            Log.e("Event", "Fail checking if event $id has a valid signature", e)
            false
        }
    }

    private fun generateId(): String {
        val rawEvent = listOf(0, pubKey, createdAt, kind, tags, content)

        // GSON decided to hardcode these replacements.
        // They break Nostr's hash check.
        // These lines revert their code.
        // https://github.com/google/gson/issues/2295
        val rawEventJson = gson.toJson(rawEvent)
            .replace("\\u2028", "\u2028")
            .replace("\\u2029", "\u2029")

        return CryptoUtils.sha256(rawEventJson.toByteArray()).toHexKey()
    }

    private class EventDeserializer : JsonDeserializer<AmberEvent> {
        override fun deserialize(
            json: JsonElement,
            typeOfT: Type?,
            context: JsonDeserializationContext?
        ): AmberEvent {
            val jsonObject = json.asJsonObject
            return AmberEvent(
                id = jsonObject.get("id")?.asString ?: "",
                pubKey = jsonObject.get("pubkey")?.asString ?: "",
                createdAt = jsonObject.get("created_at")?.asLong ?: TimeUtils.now(),
                kind = jsonObject.get("kind").asInt,
                tags = jsonObject.get("tags")?.asJsonArray?.map {
                    it.asJsonArray.mapNotNull { s -> if (s.isJsonNull) null else s.asString }
                } ?: emptyList(),
                content = jsonObject.get("content").asString,
                sig = jsonObject.get("sig")?.asString ?: ""
            )
        }
    }

    private class EventSerializer : JsonSerializer<AmberEvent> {
        override fun serialize(
            src: AmberEvent,
            typeOfSrc: Type?,
            context: JsonSerializationContext?
        ): JsonElement {
            return JsonObject().apply {
                addProperty("id", src.id)
                addProperty("pubkey", src.pubKey)
                addProperty("created_at", src.createdAt)
                addProperty("kind", src.kind)
                add(
                    "tags",
                    JsonArray().also { jsonTags ->
                        src.tags.forEach { tag ->
                            jsonTags.add(
                                JsonArray().also { jsonTagElement ->
                                    tag.forEach { tagElement ->
                                        jsonTagElement.add(tagElement)
                                    }
                                }
                            )
                        }
                    }
                )
                addProperty("content", src.content)
                addProperty("sig", src.sig)
            }
        }
    }

    private class ByteArrayDeserializer : JsonDeserializer<ByteArray> {
        override fun deserialize(
            json: JsonElement,
            typeOfT: Type?,
            context: JsonDeserializationContext?
        ): ByteArray = Hex.decode(json.asString)
    }

    abstract class Response(
        @SerializedName("result_type")
        val resultType: String
    )

// PayInvoice Call

    class PayInvoiceSuccessResponse(val result: PayInvoiceResultParams? = null) :
        Response("pay_invoice") {
        class PayInvoiceResultParams(val preimage: String)
    }

    class PayInvoiceErrorResponse(val error: PayInvoiceErrorParams? = null) :
        Response("pay_invoice") {
        class PayInvoiceErrorParams(val code: ErrorType?, val message: String?)

        enum class ErrorType {
            @SerializedName(value = "rate_limited", alternate = ["RATE_LIMITED"])
            RATE_LIMITED, // The client is sending commands too fast. It should retry in a few seconds.
            @SerializedName(value = "not_implemented", alternate = ["NOT_IMPLEMENTED"])
            NOT_IMPLEMENTED, // The command is not known or is intentionally not implemented.
            @SerializedName(value = "insufficient_balance", alternate = ["INSUFFICIENT_BALANCE"])
            INSUFFICIENT_BALANCE, // The wallet does not have enough funds to cover a fee reserve or the payment amount.
            @SerializedName(value = "quota_exceeded", alternate = ["QUOTA_EXCEEDED"])
            QUOTA_EXCEEDED, // The wallet has exceeded its spending quota.
            @SerializedName(value = "restricted", alternate = ["RESTRICTED"])
            RESTRICTED, // This public key is not allowed to do this operation.
            @SerializedName(value = "unauthorized", alternate = ["UNAUTHORIZED"])
            UNAUTHORIZED, // This public key has no wallet connected.
            @SerializedName(value = "internal", alternate = ["INTERNAL"])
            INTERNAL, // An internal error.
            @SerializedName(value = "other", alternate = ["OTHER"])
            OTHER // Other error.
        }
    }

    class ResponseDeserializer :
        JsonDeserializer<Response?> {
        @Throws(JsonParseException::class)
        override fun deserialize(
            json: JsonElement,
            typeOfT: Type,
            context: JsonDeserializationContext
        ): Response? {
            val jsonObject = json.asJsonObject
            val resultType = jsonObject.get("result_type")?.asString

            if (resultType == "pay_invoice") {
                val result = jsonObject.get("result")?.asJsonObject
                val error = jsonObject.get("error")?.asJsonObject
                if (result != null) {
                    return context.deserialize<PayInvoiceSuccessResponse>(jsonObject, PayInvoiceSuccessResponse::class.java)
                }
                if (error != null) {
                    return context.deserialize<PayInvoiceErrorResponse>(jsonObject, PayInvoiceErrorResponse::class.java)
                }
            } else {
                // tries to guess
                if (jsonObject.get("result")?.asJsonObject?.get("preimage") != null) {
                    return context.deserialize<PayInvoiceSuccessResponse>(jsonObject, PayInvoiceSuccessResponse::class.java)
                }
                if (jsonObject.get("error")?.asJsonObject?.get("code") != null) {
                    return context.deserialize<PayInvoiceErrorResponse>(jsonObject, PayInvoiceErrorResponse::class.java)
                }
            }
            return null
        }

        companion object {
        }
    }

    abstract class Request(var method: String? = null)

    class PayInvoiceParams(var invoice: String? = null)

    class PayInvoiceMethod(var params: PayInvoiceParams? = null) : Request("pay_invoice") {

        companion object {
            fun create(bolt11: String): PayInvoiceMethod {
                return PayInvoiceMethod(PayInvoiceParams(bolt11))
            }
        }
    }

    class RequestDeserializer :
        JsonDeserializer<Request?> {
        @Throws(JsonParseException::class)
        override fun deserialize(
            json: JsonElement,
            typeOfT: Type,
            context: JsonDeserializationContext
        ): Request? {
            val jsonObject = json.asJsonObject
            val method = jsonObject.get("method")?.asString

            if (method == "pay_invoice") {
                return context.deserialize<PayInvoiceMethod>(jsonObject, PayInvoiceMethod::class.java)
            }
            return null
        }

        companion object {
        }
    }

    private class ByteArraySerializer : JsonSerializer<ByteArray> {
        override fun serialize(
            src: ByteArray,
            typeOfSrc: Type?,
            context: JsonSerializationContext?
        ) = JsonPrimitive(src.toHexKey())
    }

    companion object {
        val gson: Gson = GsonBuilder()
            .disableHtmlEscaping()
            .registerTypeAdapter(AmberEvent::class.java, EventSerializer())
            .registerTypeAdapter(AmberEvent::class.java, EventDeserializer())
            .registerTypeAdapter(ByteArray::class.java, ByteArraySerializer())
            .registerTypeAdapter(ByteArray::class.java, ByteArrayDeserializer())
            .registerTypeAdapter(Response::class.java, ResponseDeserializer())
            .registerTypeAdapter(Request::class.java, RequestDeserializer())
            .create()

        fun fromJson(json: String): AmberEvent = gson.fromJson(json, AmberEvent::class.java).getRefinedEvent()

        fun AmberEvent.getRefinedEvent(): AmberEvent = when (kind) {
            else -> this
        }

        fun generateId(pubKey: HexKey, createdAt: Long, kind: Int, tags: List<List<String>>, content: String): ByteArray {
            val rawEvent = listOf(
                0,
                pubKey,
                createdAt,
                kind,
                tags,
                content
            )

            // GSON decided to hardcode these replacements.
            // They break Nostr's hash check.
            // These lines revert their code.
            // https://github.com/google/gson/issues/2295
            val rawEventJson = gson.toJson(rawEvent)
                .replace("\\u2028", "\u2028")
                .replace("\\u2029", "\u2029")

            return CryptoUtils.sha256(rawEventJson.toByteArray())
        }

        fun setPubKeyIfEmpty(event: AmberEvent, keyPair: KeyPair): AmberEvent {
            if (event.pubKey.isEmpty()) {
                val pubKey = keyPair.pubKey.toHexKey()
                val id = generateId(pubKey, event.createdAt, event.kind, event.tags, event.content)
                val signature = CryptoUtils.sign(id, keyPair.privKey).toHexKey()
                return AmberEvent(id.toHexKey(), pubKey, event.createdAt, event.kind, event.tags, event.content, signature)
            }

            return event
        }

        fun create(privateKey: ByteArray, kind: Int, tags: List<List<String>> = emptyList(), content: String = "", createdAt: Long = TimeUtils.now()): AmberEvent {
            val pubKey = CryptoUtils.pubkeyCreate(privateKey).toHexKey()
            val id = generateId(pubKey, createdAt, kind, tags, content)
            val sig = CryptoUtils.sign(id, privateKey).toHexKey()
            return AmberEvent(id.toHexKey(), pubKey, createdAt, kind, tags, content, sig)
        }
    }
}

@Immutable
interface AddressableEvent {
    fun dTag(): String
    fun address(): ATag
}
