package com.greenart7c3.nostrsigner

import android.content.SharedPreferences
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

    private class FakePreferences(
        initial: Map<String, Any> = emptyMap(),
    ) : SharedPreferences {
        val values = initial.toMutableMap()

        override fun getAll(): Map<String, *> = values.toMap()

        override fun getString(key: String, defValue: String?): String? = values[key] as? String ?: defValue

        override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? {
            @Suppress("UNCHECKED_CAST")
            return values[key] as? Set<String> ?: defValues
        }

        override fun getInt(key: String, defValue: Int): Int = values[key] as? Int ?: defValue

        override fun getLong(key: String, defValue: Long): Long = values[key] as? Long ?: defValue

        override fun getFloat(key: String, defValue: Float): Float = values[key] as? Float ?: defValue

        override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue

        override fun contains(key: String): Boolean = values.containsKey(key)

        override fun edit(): SharedPreferences.Editor = FakeEditor()

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit

        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit

        private inner class FakeEditor : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private var shouldClear = false

            override fun putString(key: String, value: String?) = put(key, value)

            override fun putStringSet(key: String, values: Set<String>?) = put(key, values)

            override fun putInt(key: String, value: Int) = put(key, value)

            override fun putLong(key: String, value: Long) = put(key, value)

            override fun putFloat(key: String, value: Float) = put(key, value)

            override fun putBoolean(key: String, value: Boolean) = put(key, value)

            override fun remove(key: String) = put(key, null)

            override fun clear(): SharedPreferences.Editor {
                shouldClear = true
                return this
            }

            override fun commit(): Boolean {
                apply()
                return true
            }

            override fun apply() {
                if (shouldClear) values.clear()
                pending.forEach { (key, value) ->
                    if (value == null) values.remove(key) else values[key] = value
                }
                pending.clear()
            }

            private fun put(key: String, value: Any?): SharedPreferences.Editor {
                pending[key] = value
                return this
            }
        }
    }
}
