package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.desktop.core.AccountRecord
import com.greenart7c3.nostrsigner.desktop.core.AccountsStore
import com.greenart7c3.nostrsigner.desktop.core.ProfileFetcher
import com.greenart7c3.nostrsigner.desktop.ui.ProfileImages
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test

class ProfileFetcherTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun isolateDataDir() {
            val tmp = File.createTempFile("amber-test", "").apply {
                delete()
                mkdirs()
                deleteOnExit()
            }
            System.setProperty("user.home", tmp.absolutePath)
        }
    }

    @Test
    fun throttleMatchesTheAndroidRules() {
        val now = 10_000_000L
        val never = AccountRecord(npub = "npub1x")
        assertTrue(ProfileFetcher.shouldFetch(never, now))
        // Checked a minute ago: wait for the 15-minute interval.
        assertFalse(ProfileFetcher.shouldFetch(never.copy(lastProfileCheck = now - 60), now))
        assertTrue(ProfileFetcher.shouldFetch(never.copy(lastProfileCheck = now - 16 * 60), now))
        // Metadata updated within the last day: no refetch.
        assertFalse(ProfileFetcher.shouldFetch(never.copy(lastMetadataUpdate = now - 3600, lastProfileCheck = now - 16 * 60), now))
        assertTrue(ProfileFetcher.shouldFetch(never.copy(lastMetadataUpdate = now - 25 * 3600, lastProfileCheck = now - 16 * 60), now))
    }

    @Test
    fun anOlderProfileFromALaterFetchDoesNotOverwriteANewerOne() {
        val signer = NostrSignerInternal(KeyPair())
        val npub = signer.keyPair.pubKey.toNpub()
        fun profile(createdAt: Long, name: String): MetadataEvent = signer.signerSync.sign(createdAt, MetadataEvent.KIND, emptyArray(), """{"name":"$name","picture":"https://example.com/$name.png"}""")
        AccountsStore.upsert(AccountRecord(npub = npub))

        ProfileFetcher.applyMetadata(npub, profile(2_000, "current"))
        // A later fetch only reached a relay serving an older profile.
        ProfileFetcher.applyMetadata(npub, profile(1_000, "stale"))
        val afterStale = AccountsStore.get(npub)!!
        ProfileFetcher.applyMetadata(npub, profile(2_000, "same-time"))
        val afterSameTime = AccountsStore.get(npub)!!
        ProfileFetcher.applyMetadata(npub, profile(3_000, "newer"))
        val afterNewer = AccountsStore.get(npub)!!
        AccountsStore.delete(npub)

        assertEquals("current", afterStale.name)
        assertEquals("https://example.com/current.png", afterStale.picture)
        assertEquals("current", afterSameTime.name)
        assertEquals("newer", afterNewer.name)
        assertEquals(3_000L, afterNewer.metadataCreatedAt)
    }

    /** Fetches a real, long-lived profile from the indexer relays. */
    @Test
    fun fetchesNameAndPictureFromIndexers() = runBlocking {
        assumeTrue("Set AMBER_E2E=1 to run the relay round-trip test", System.getenv("AMBER_E2E") != null)

        // jack
        val npub = "npub1sg6plzptd64u62a878hep2kev88swjh3tw00gjsfl8f237lmu63q0uf63m"
        AccountsStore.upsert(AccountRecord(npub = npub))
        ProfileFetcher.refresh(npub)

        val record = withTimeoutOrNull(90.seconds) {
            while (AccountsStore.get(npub)?.lastProfileCheck == 0L) delay(250.milliseconds)
            AccountsStore.get(npub)
        }
        AccountsStore.delete(npub)
        assertTrue("fetch did not finish", record != null)
        assertTrue("no name: $record", record!!.name.isNotBlank())
        assertTrue("no picture: $record", record.picture.startsWith("http"))
        assertTrue("no relay list: $record", record.userRelays.isNotEmpty())
        assertTrue("picture did not decode", ProfileImages.load(record.picture) != null)
    }
}
