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
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect
import java.util.UUID
import kotlinx.coroutines.launch

object NostrConnectUtils {
    private fun metaDataFromJson(json: String): BunkerMetadata = BunkerMetadata.mapper.readValue(json, BunkerMetadata::class.java)

    fun getIntentFromNostrConnect(
        intent: Intent,
        account: Account,
    ) {
        if (intent.extras?.getString("id") == null) {
            intent.putExtra("id", UUID.randomUUID().toString().substring(0, 6))
        }

        try {
            val data = intent.dataString.toString().replace("nostrconnect://", "")
            val split = data.split("?")
            val relays: MutableList<NormalizedRelayUrl> = mutableListOf()
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
                    val relayUrl = RelayUrlNormalizer.normalizeOrNull(json)
                    if (relayUrl != null) {
                        relays.add(relayUrl)
                    }
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
                                    permissionType.trim(),
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
                                    permissionType.trim(),
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
                        id = intent.extras?.getString("id") ?: UUID.randomUUID().toString().substring(0, 6),
                        remoteKey = pubKey,
                        secret = "",
                        permissions = if (permissions.isNotEmpty()) {
                            permissions.joinToString { if (it.kind != null) "${it.type}:${it.kind}" else it.type }
                        } else {
                            null
                        },
                    ),
                    localKey = pubKey,
                    relays = relays.ifEmpty { Amber.instance.getSavedRelays(account).toList() },
                    currentAccount = account.npub,
                    nostrConnectSecret = nostrConnectSecret,
                    closeApplication = intent.getBooleanExtra("closeApplication", true),
                    name = name,
                    signedEvent = null,
                    encryptedData = null,
                    encryptionType = EncryptionType.NIP44,
                    isNostrConnectUri = true,
                ),
            )
        } catch (e: Exception) {
            Log.e(Amber.TAG, e.message, e)
            Amber.instance.applicationIOScope.launch {
                Amber.instance.getLogDatabase(account.npub).dao().insertLog(
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
