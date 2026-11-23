package com.greenart7c3.nostrsigner.service

import com.greenart7c3.nostrsigner.models.Account
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Builds a minimal [Account] with a fresh key; the signer itself is never exercised.
 * A real signer, not a mock: each mockk() generates proxy classes, and hundreds of
 * them blow the test heap.
 */
fun newTestAccount(scope: CoroutineScope): Account = Account(
    signer = NostrSignerInternal(KeyPair()),
    name = MutableStateFlow(""),
    picture = MutableStateFlow(""),
    signPolicy = 0,
    didBackup = false,
    scope = scope,
    keyAccessCheck = {},
)
