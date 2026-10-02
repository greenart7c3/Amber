package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.desktop.core.AccountRecord
import com.greenart7c3.nostrsigner.desktop.core.AccountsStore
import com.greenart7c3.nostrsigner.desktop.core.ProfileFetcher
import com.greenart7c3.nostrsigner.desktop.ui.ProfileImages
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
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

    /** Fetches a real, long-lived profile from the indexer relays. */
    @Test
    fun fetchesNameAndPictureFromIndexers() = runBlocking {
        assumeTrue("Set AMBER_E2E=1 to run the relay round-trip test", System.getenv("AMBER_E2E") != null)

        // jack
        val npub = "npub1sg6plzptd64u62a878hep2kev88swjh3tw00gjsfl8f237lmu63q0uf63m"
        AccountsStore.upsert(AccountRecord(npub = npub))
        ProfileFetcher.refresh(npub)

        val record = withTimeoutOrNull(90_000) {
            while (AccountsStore.get(npub)?.lastProfileCheck == 0L) delay(250)
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
