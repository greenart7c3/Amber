package com.greenart7c3.nostrsigner.service

import android.content.Intent
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import com.greenart7c3.nostrsigner.NostrSigner
import com.greenart7c3.nostrsigner.database.LogEntity
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.BunkerRequest
import com.greenart7c3.nostrsigner.models.CompressionType
import com.greenart7c3.nostrsigner.models.EncryptionType
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.Permission
import com.greenart7c3.nostrsigner.models.ReturnType
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.models.containsNip
import com.vitorpamplona.ammolite.relays.COMMON_FEED_TYPES
import com.vitorpamplona.ammolite.relays.RelaySetupInfo
import com.vitorpamplona.quartz.encoders.toNpub
import java.util.UUID
import kotlinx.coroutines.launch

object NostrConnectUtils {
    private fun metaDataFromJson(json: String): BunkerMetadata {
        return BunkerMetadata.mapper.readValue(json, BunkerMetadata::class.java)
    }

    fun getIntentFromNostrConnect(
        intent: Intent,
        route: String?,
        account: Account,
        onReady: (IntentData?) -> Unit,
    ) {
        try {
            val data = intent.dataString.toString().replace("nostrconnect://", "")
            val split = data.split("?")
            val relays: MutableList<RelaySetupInfo> = mutableListOf()
            var name = ""
            val pubKey = split.first()
            val parsedData = IntentUtils.decodeData(split.drop(1).joinToString { it })
            val splitParsedData = parsedData.split("&")
            val permissions = mutableListOf<Permission>()
            var nostrConnectSecret = ""
            splitParsedData.forEach {
                val internalSplit = it.split("=")
                val paramName = internalSplit.first()
                val json = internalSplit.mapIndexedNotNull { index, s ->
                    if (index == 0) null else s
                }.joinToString { data -> data }
                if (paramName == "relay") {
                    var relayUrl = json
                    if (relayUrl.endsWith("/")) relayUrl = relayUrl.dropLast(1)
                    relays.add(
                        RelaySetupInfo(relayUrl, read = true, write = true, feedTypes = COMMON_FEED_TYPES),
                    )
                }
                if (paramName == "name") {
                    name = json
                }

                if (paramName == "secret") {
                    nostrConnectSecret = json
                }

                if (paramName == "perms") {
                    if (json.isNotEmpty()) {
                        val splitPerms = json.split(",")
                        splitPerms.forEach { perm ->
                            val split2 = perm.split(":")
                            val permissionType = split2.first()
                            val kind =
                                try {
                                    split2[1].toInt()
                                } catch (_: Exception) {
                                    null
                                }

                            permissions.add(
                                Permission(
                                    permissionType,
                                    kind,
                                ),
                            )
                        }
                    }
                }

                if (paramName == "metadata") {
                    val bunkerMetada = metaDataFromJson(json)
                    name = bunkerMetada.name
                    if (bunkerMetada.perms.isNotEmpty()) {
                        val splitPerms = bunkerMetada.perms.split(",")
                        splitPerms.forEach { perm ->
                            val split2 = perm.split(":")
                            val permissionType = split2.first()
                            val kind =
                                try {
                                    split2[1].toInt()
                                } catch (_: Exception) {
                                    null
                                }

                            permissions.add(
                                Permission(
                                    permissionType,
                                    kind,
                                ),
                            )
                        }
                    }
                }
            }

            permissions.removeIf { it.kind == null && (it.type == "sign_event" || it.type == "nip") }
            permissions.removeIf { it.type == "nip" && (it.kind == null || !it.kind.containsNip()) }

            onReady(
                IntentData(
                    "ack",
                    name,
                    SignerType.CONNECT,
                    pubKey,
                    "",
                    null,
                    CompressionType.NONE,
                    ReturnType.EVENT,
                    permissions,
                    "",
                    mutableStateOf(true),
                    mutableStateOf(false),
                    BunkerRequest(
                        UUID.randomUUID().toString().substring(0, 4),
                        "connect",
                        arrayOf(pubKey),
                        pubKey,
                        relays.ifEmpty { NostrSigner.getInstance().getSavedRelays().toList() },
                        "",
                        account.signer.keyPair.pubKey.toNpub(),
                        EncryptionType.NIP04,
                        nostrConnectSecret,
                        intent.getBooleanExtra("closeApplication", true),
                    ),
                    route,
                    null,
                    null,
                ),
            )
        } catch (e: Exception) {
            Log.e("nostrconnect", e.message, e)
            NostrSigner.getInstance().applicationIOScope.launch {
                NostrSigner.getInstance().getDatabase(account.signer.keyPair.pubKey.toNpub()).applicationDao().insertLog(
                    LogEntity(
                        0,
                        "nostrconnect",
                        "nostrconnect",
                        e.message ?: "",
                        System.currentTimeMillis(),
                    ),
                )
            }
            onReady(null)
        }
    }
}
