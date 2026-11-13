package com.greenart7c3.nostrsigner.service

import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.database.ApplicationEntity
import com.greenart7c3.nostrsigner.database.ApplicationWithPermissions
import com.greenart7c3.nostrsigner.database.HistoryEntity
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.CompressionType
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.ReturnType
import com.greenart7c3.nostrsigner.models.encryptDecryptSignerTypes
import com.greenart7c3.nostrsigner.models.encryptSignerTypes
import com.vitorpamplona.quartz.utils.TimeUtils
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlin.io.encoding.Base64

/** The platform-free part of answering a `nostrsigner:` request: what is stored and what is sent back. */
object IntentResponse {
    /**
     * What the history keeps of a request: always the encrypted form, never the
     * plaintext. An accepted encrypt stores its ciphertext output ([value]); a
     * rejected one was never performed, so it stores nothing. A decrypt stores
     * the ciphertext it was asked to open, and a signature its [event].
     */
    fun historyContent(intentData: IntentData, accepted: Boolean, event: String, value: String): String = when {
        intentData.type in encryptSignerTypes -> if (accepted) value else ""
        accepted && intentData.type == SignerType.SIGN_EVENT -> event
        else -> intentData.data
    }

    fun historyEntry(
        intentData: IntentData,
        key: String,
        kind: Int?,
        accepted: Boolean,
        content: String,
    ): HistoryEntity {
        val isV3 = intentData.type == SignerType.NIP44_V3_ENCRYPT || intentData.type == SignerType.NIP44_V3_DECRYPT
        val hasPeer = intentData.type in encryptDecryptSignerTypes && intentData.type != SignerType.DECRYPT_ZAP_EVENT
        return HistoryEntity(
            0,
            key,
            intentData.type.toString(),
            if (isV3) intentData.nip44v3Kind else kind,
            TimeUtils.now(),
            accepted,
            content = content,
            // The peer key is only needed to decrypt the stored ciphertext on demand.
            encryptionPubKey = if (hasPeer && content.isNotEmpty()) intentData.pubKey else "",
            encryptionScope = if (isV3) intentData.nip44v3Scope else "",
        )
    }

    /** What a web app's callback URL receives (before URL encoding): the signature, the event, or the gzipped event. */
    fun callbackPayload(intentData: IntentData, event: String, value: String): String = when {
        intentData.returnType == ReturnType.SIGNATURE -> value
        intentData.compression == CompressionType.GZIP -> "Signer1" + gzipBase64(event)
        else -> event
    }

    private fun gzipBase64(text: String): String = ByteArrayOutputStream().use { bos ->
        GZIPOutputStream(bos).use { it.write(text.toByteArray()) }
        Base64.encode(bos.toByteArray())
    }

    /** A first-seen app, saved under [key] for [account] when it gets its first answer. */
    fun newApplication(
        key: String,
        name: String,
        account: Account,
        closeApplication: Boolean,
        deleteAfter: Long,
    ) = ApplicationWithPermissions(
        application = ApplicationEntity(
            key = key,
            name = name,
            relays = emptyList(),
            url = "",
            icon = "",
            description = "",
            pubKey = account.hexKey,
            isConnected = true,
            secret = "",
            useSecret = false,
            signPolicy = account.signPolicy,
            closeApplication = closeApplication,
            deleteAfter = deleteAfter,
            lastUsed = TimeUtils.now(),
        ),
        permissions = mutableListOf(),
    )
}
