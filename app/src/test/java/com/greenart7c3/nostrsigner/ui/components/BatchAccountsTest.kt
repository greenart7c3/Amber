package com.greenart7c3.nostrsigner.ui.components

import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.models.CompressionType
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.ReturnType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The batch screen used to show only the active account while confirm signed
 * and recorded each request under its own. Both now resolve accounts the same
 * way, so the header lists every account confirm acts as.
 */
class BatchAccountsTest {
    private fun intent(id: String, account: String) = IntentData(
        data = "",
        name = "",
        type = SignerType.SIGN_EVENT,
        pubKey = "",
        id = id,
        callBackUrl = null,
        compression = CompressionType.NONE,
        returnType = ReturnType.SIGNATURE,
        permissions = null,
        currentAccount = account,
        route = null,
        event = null,
        encryptedData = null,
    )

    @Test
    fun aMixedBatchListsEveryAccountItSignsAs() {
        val batch = listOf(intent("1", "npub1active"), intent("2", "npub1second"), intent("3", "npub1active"))
        assertEquals(listOf("npub1active", "npub1second"), batchAccountNpubs(batch.map { it.currentAccount }, "npub1active"))
    }

    @Test
    fun requestsWithoutAnAccountUseTheActiveOne() {
        val batch = listOf(intent("1", ""), intent("2", "npub1second"))
        assertEquals(listOf("npub1active", "npub1second"), batchAccountNpubs(batch.map { it.currentAccount }, "npub1active"))
        assertEquals("npub1active", batchAccountNpub(intent("3", "").currentAccount, "npub1active"))
    }
}
