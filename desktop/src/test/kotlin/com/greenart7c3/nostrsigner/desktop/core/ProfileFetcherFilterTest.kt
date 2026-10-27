package com.greenart7c3.nostrsigner.desktop.core

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.utils.TimeUtils
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F-19: a profile relay may answer a REQ with any event it likes. Only
 * events whose author and kind match the requested filter — and whose
 * created_at is not far in the future — may reach the account record.
 */
class ProfileFetcherFilterTest {
    private val victim = NostrSignerInternal(KeyPair())
    private val victimHex = victim.keyPair.pubKey.toHexKey()
    private val attacker = NostrSignerInternal(KeyPair())

    private fun profileFilter() = Filter(kinds = listOf(0), authors = listOf(victimHex), limit = 1)

    private fun event(signer: NostrSignerInternal, kind: Int, createdAt: Long): Event = signer.signerSync.sign(createdAt, kind, emptyArray(), "{}")

    @Test
    fun `the requested author and kind are accepted`() {
        assertTrue(ProfileFetcher.matchesFilter(event(victim, 0, TimeUtils.now()), profileFilter()))
    }

    @Test
    fun `an event signed by another key is rejected`() {
        assertFalse(ProfileFetcher.matchesFilter(event(attacker, 0, TimeUtils.now()), profileFilter()))
    }

    @Test
    fun `an event of another kind is rejected`() {
        assertFalse(ProfileFetcher.matchesFilter(event(victim, 10002, TimeUtils.now()), profileFilter()))
    }

    @Test
    fun `a far future event is rejected before it can pin the relay watermark`() {
        assertFalse(ProfileFetcher.matchesFilter(event(victim, 0, TimeUtils.now() + 6 * 3600), profileFilter()))
    }

    @Test
    fun `a slightly future event within the clock skew window is accepted`() {
        assertTrue(ProfileFetcher.matchesFilter(event(victim, 0, TimeUtils.now() + 60), profileFilter()))
    }

    @Test
    fun `a filter without constraints still rejects far future events`() {
        val unconstrained = Filter(kinds = emptyList(), authors = emptyList())

        assertTrue(ProfileFetcher.matchesFilter(event(attacker, 3, TimeUtils.now()), unconstrained))
        assertFalse(ProfileFetcher.matchesFilter(event(attacker, 3, TimeUtils.now() + 6 * 3600), unconstrained))
    }
}
