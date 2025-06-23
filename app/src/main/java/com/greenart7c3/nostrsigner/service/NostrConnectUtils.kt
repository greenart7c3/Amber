package com.greenart7c3.nostrsigner.service

import android.content.Intent
import android.util.Log
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.database.LogEntity
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.AmberBunkerRequest
import com.greenart7c3.nostrsigner.models.BunkerMetadata
import com.greenart7c3.nostrsigner.models.EncryptionType
import com.greenart7c3.nostrsigner.models.Permission
import com.greenart7c3.nostrsigner.models.containsNip
import com.vitorpamplona.ammolite.relays.COMMON_FEED_TYPES
import com.vitorpamplona.ammolite.relays.RelaySetupInfo
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect
import kotlinx.coroutines.launch

object NostrConnectUtils {
    private fun metaDataFromJson(json: String): BunkerMetadata {
        return BunkerMetadata.mapper.readValue(json, BunkerMetadata::class.java)
    }

    fun getIntentFromNostrConnect(
        intent: Intent,
        account: Account,
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

            BunkerRequestUtils.addRequest(
                AmberBunkerRequest(
                    BunkerRequestConnect(
                        remoteKey = pubKey,
                        secret = "",
                        permissions = if (permissions.isNotEmpty()) {
                            permissions.joinToString { if (it.kind != null) "${it.type}:${it.kind}" else it.type }
                        } else {
                            null
                        },
                    ),
                    localKey = pubKey,
                    relays = relays.ifEmpty { Amber.instance.getSavedRelays().toList() },
                    currentAccount = account.hexKey,
                    encryptionType = EncryptionType.NIP04,
                    nostrConnectSecret = nostrConnectSecret,
                    closeApplication = intent.getBooleanExtra("closeApplication", true),
                    name = name,
                    signedEvent = null,
                    encryptDecryptResponse = null,
                ),
            )
        } catch (e: Exception) {
            Log.e(Amber.TAG, e.message, e)
            Amber.instance.applicationIOScope.launch {
                Amber.instance.getDatabase(account.npub).applicationDao().insertLog(
                    LogEntity(
                        0,
                        "nostrconnect",
                        "nostrconnect",
                        e.message ?: "",
                        System.currentTimeMillis(),
                    ),
                )
            }
        }
    }
}
