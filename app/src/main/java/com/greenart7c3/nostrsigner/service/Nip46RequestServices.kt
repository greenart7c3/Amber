package com.greenart7c3.nostrsigner.service

import com.greenart7c3.nostrsigner.common.UNVERIFIED_REPLY_ATTEMPTS
import com.greenart7c3.nostrsigner.data.AccountRepository
import com.greenart7c3.nostrsigner.data.SettingsStore
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.AmberBunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import kotlinx.coroutines.CoroutineScope

internal class Nip46RequestServices(
    val settingsStore: SettingsStore,
    val accountRepository: AccountRepository,
    val scope: CoroutineScope,
    val bunkerResponder: BunkerResponder,
    val log: (text: String, url: String, npub: String?) -> Unit,
) {
    suspend fun refuse(acc: Account, request: AmberBunkerRequest, error: String, attempts: Int = UNVERIFIED_REPLY_ATTEMPTS) {
        bunkerResponder.sendBunkerResponse(
            acc,
            request,
            BunkerResponse(request.request.id, "", error),
            request.relays.ifEmpty { settingsStore.settings.defaultRelays },
            onLoading = {},
            onDone = {},
            attempts = attempts,
        )
    }

    suspend fun reply(a: Admitted, result: String?, error: String? = null, onDone: (Boolean) -> Unit = { }) {
        bunkerResponder.sendBunkerResponse(
            account = a.acc,
            bunkerRequest = a.request,
            bunkerResponse = BunkerResponse(a.id, result, error),
            relays = a.relays,
            onLoading = { },
            onDone = onDone,
        )
    }
}
