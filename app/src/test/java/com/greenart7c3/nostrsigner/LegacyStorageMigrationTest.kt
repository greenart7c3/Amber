package com.greenart7c3.nostrsigner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The decisions of [LegacyStorageMigration] that can be taken without the
 * AndroidKeyStore: which values are copied where, how the two account lists are
 * merged, and which legacy files may be deleted. Deleting a legacy file whose
 * account did not come back would lose the only copy of that user's key.
 */
class LegacyStorageMigrationTest {
    private val legacyAccount = mapOf(
        "nostr_privkey" to "privkey-hex",
        "seed_words" to "twelve words of a mnemonic",
        "nostr_pubkey" to "pubkey-hex",
        "account_name" to "synthetic",
        "default_sign_policy" to 1,
        "user_relays" to setOf("wss://relay.one", "wss://relay.two"),
    )

    @Test
    fun `the private key and the seed words never reach a plain preference file`() {
        val target = FakePreferences()

        copyPreferences(FakePreferences(legacyAccount), target, skipped = LegacyStorageMigration.SECRET_KEYS)

        assertEquals(legacyAccount.keys - LegacyStorageMigration.SECRET_KEYS, target.values.keys)
        assertEquals("pubkey-hex", target.values["nostr_pubkey"])
        assertEquals(setOf("wss://relay.one", "wss://relay.two"), target.values["user_relays"])
        assertFalse(target.values.containsKey("nostr_privkey"))
        assertFalse(target.values.containsKey("seed_words"))
    }

    @Test
    fun `settings the current install already has are not overwritten`() {
        val current = FakePreferences(mapOf("default_sign_policy" to 2, "privacy_mode" to true))
        val legacy = FakePreferences(mapOf("default_sign_policy" to 1, "start_service_on_boot" to false))

        copyPreferences(legacy, current, onlyMissing = true)

        assertEquals(2, current.values["default_sign_policy"])
        assertEquals(true, current.values["privacy_mode"])
        assertEquals(false, current.values["start_service_on_boot"])
    }

    @Test
    fun `the migrated accounts are appended to the ones created in this build`() {
        val merged = mergeAccountLists(
            current = listOf("npub1new"),
            legacy = listOf("npub1old", "npub1new"),
        )

        assertEquals(listOf("npub1new", "npub1old"), merged)
    }

    @Test
    fun `a second run changes nothing`() {
        val restored = listOf("npub1old")
        val target = FakePreferences(mapOf("account_name" to "kept"))

        copyPreferences(FakePreferences(legacyAccount), target, skipped = LegacyStorageMigration.SECRET_KEYS, onlyMissing = true)
        val afterFirstRun = target.values.toMap()
        copyPreferences(FakePreferences(legacyAccount), target, skipped = LegacyStorageMigration.SECRET_KEYS, onlyMissing = true)

        assertEquals(afterFirstRun, target.values)
        assertEquals(mergeAccountLists(restored, restored), mergeAccountLists(mergeAccountLists(restored, restored), restored))
        assertEquals(listOf("secret_keeper_npub1old.xml", "secret_keeper.xml"), legacyFilesToDelete(restored, restored))
    }

    @Test
    fun `the settings file is kept while one account did not come back`() {
        val accounts = listOf("npub1restored", "npub1failed")

        val files = legacyFilesToDelete(accounts, restored = listOf("npub1restored"))

        assertEquals(listOf("secret_keeper_npub1restored.xml"), files)
        assertFalse(files.contains("secret_keeper.xml"))
    }

    @Test
    fun `every file goes once every account came back`() {
        val accounts = listOf("npub1one", "npub1two")

        val files = legacyFilesToDelete(accounts, restored = accounts)

        assertTrue(files.containsAll(accounts.map { "secret_keeper_$it.xml" }))
        assertTrue(files.contains("secret_keeper.xml"))
    }

    @Test
    fun `values of a type preferences cannot hold are skipped`() {
        val target = FakePreferences()

        copyPreferences(FakePreferences(mapOf("unsupported" to Any(), "name" to "kept")), target)

        assertEquals(mapOf<String, Any>("name" to "kept"), target.values)
    }
}
