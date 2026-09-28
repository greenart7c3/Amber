package com.greenart7c3.nostrsigner

import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.ProxyAccountMetadata
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proxy accounts must always run with signPolicy 2 ("always accept"): proxy
 * requests bypass per-app permissions and are forwarded to the remote bunker.
 * The clamp in [Account.init] also self-heals accounts that were persisted with
 * the older default of 1 before this rule existed.
 */
class AccountTest {
    private fun buildAccount(signPolicy: Int, proxy: ProxyAccountMetadata?): Account = Account(
        signer = NostrSignerInternal(KeyPair(pubKey = "11".repeat(32).hexToByteArray())),
        hexKey = "22".repeat(32),
        npub = "npub1test",
        name = MutableStateFlow("test"),
        picture = MutableStateFlow(""),
        signPolicy = signPolicy,
        didBackup = true,
        scope = CoroutineScope(Dispatchers.Unconfined),
        proxy = proxy,
    )

    @Test
    fun `proxy account forces signPolicy 2 regardless of stored value`() {
        val account = buildAccount(
            signPolicy = 1,
            proxy = ProxyAccountMetadata(
                remotePubkey = "ab".repeat(32),
                relays = emptyList(),
                bunkerName = "bunker",
                nostrConnectSecret = "",
            ),
        )

        assertTrue(account.isProxy)
        assertEquals(2, account.signPolicy)
    }

    @Test
    fun `regular account keeps its signPolicy`() {
        val account = buildAccount(signPolicy = 1, proxy = null)

        assertEquals(1, account.signPolicy)
    }
}
