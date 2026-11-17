package com.greenart7c3.nostrsigner.service

import com.greenart7c3.nostrsigner.models.Account
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import io.mockk.mockk
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow

private val accountIds = AtomicInteger(0)

// One shared relaxed mock for all test accounts: each mockk() generates proxy
// classes, and hundreds of them blow the test heap. The signer is never exercised.
private val sharedSigner = mockk<NostrSignerInternal>(relaxed = true)

/** Builds a minimal [Account] with a unique key; the signer itself is never exercised. */
fun newTestAccount(scope: CoroutineScope): Account {
    val id = accountIds.incrementAndGet()
    return Account(
        signer = sharedSigner,
        hexKey = "%064x".format(id),
        npub = "npub$id",
        name = MutableStateFlow(""),
        picture = MutableStateFlow(""),
        signPolicy = 0,
        didBackup = false,
        scope = scope,
        keyAccessCheck = {},
    )
}
