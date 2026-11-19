package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.desktop.core.AccountManager
import com.greenart7c3.nostrsigner.desktop.core.AmberDesktop
import com.greenart7c3.nostrsigner.desktop.core.AppPermissionRecord
import com.greenart7c3.nostrsigner.desktop.core.AppRecord
import com.greenart7c3.nostrsigner.desktop.core.AppWithPermissions
import com.greenart7c3.nostrsigner.desktop.core.RememberType
import com.greenart7c3.nostrsigner.desktop.core.connectionToReplace
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A nostrconnect:// URI names the client key itself. Approving one whose key
 * is already saved must replace that connection (the card warns about it, like
 * Android's "Replace connection" dialog), not silently merge into it and keep
 * its grants and relays.
 */
class NostrConnectReplaceTest {
    @Test
    fun approvingASameKeyNostrConnectReplacesTheSavedConnection() = runBlocking {
        val account = AccountManager.addAccount(KeyPair(), name = "nc-replace", signPolicy = 1)
        val store = AmberDesktop.store(account.npub)
        val clientHex = KeyPair().pubKey.toHexKey()
        store.upsert(
            AppWithPermissions(
                AppRecord(key = clientHex, name = "old-app", relays = listOf("wss://old.invalid/"), pubKey = account.hexKey, isConnected = true, signPolicy = 2),
                mutableListOf(AppPermissionRecord("SIGN_EVENT", 1, true, RememberType.ALWAYS.screenCode, Long.MAX_VALUE / 1000, 0)),
            ),
        )

        val error = AmberDesktop.engine.addNostrConnect("nostrconnect://$clientHex?relay=wss%3A%2F%2Fnew.invalid&name=new-app", account)
        assertNull(error)
        val card = withTimeout(20.seconds) {
            while (AmberDesktop.engine.pending.value.none { it.localKey == clientHex }) delay(50.milliseconds)
            AmberDesktop.engine.pending.value.first { it.localKey == clientHex }
        }

        // The card can name the connection it will replace...
        assertEquals("old-app", connectionToReplace(card, account.npub)?.app?.name)
        // ...but a bunker:// reconnect under the same key is not a replacement.
        assertNull(connectionToReplace(card.copy(isNostrConnectUri = false), account.npub))

        withTimeoutOrNull(20.seconds) { AmberDesktop.engine.approve(card, RememberType.NEVER, signPolicy = 1).join() }

        val app = store.getByKey(clientHex)
        assertNotNull(app)
        app!!
        assertEquals("new-app", app.app.name)
        assertEquals(listOf("wss://new.invalid/"), app.app.relays)
        assertEquals(1, app.app.signPolicy)
        assertFalse(app.permissions.any { it.type == "SIGN_EVENT" && it.kind == 1 })
    }

    @Test
    fun aNostrConnectNameIsQueuedSanitized() = runBlocking {
        val account = AccountManager.addAccount(KeyPair(), name = "nc-name", signPolicy = 1)
        val clientHex = KeyPair().pubKey.toHexKey()

        // A line break and a right-to-left override in the client-chosen name.
        assertNull(AmberDesktop.engine.addNostrConnect("nostrconnect://$clientHex?relay=wss%3A%2F%2Fnew.invalid&name=Amber%0AApprove%20now%E2%80%AEevil", account))
        val card = withTimeout(20.seconds) {
            while (AmberDesktop.engine.pending.value.none { it.localKey == clientHex }) delay(50.milliseconds)
            AmberDesktop.engine.pending.value.first { it.localKey == clientHex }
        }

        assertEquals("Amber Approve nowevil", card.appName)
    }

    @Test
    fun aNostrConnectKeyMustBeLowercaseHex() = runBlocking {
        val account = AccountManager.addAccount(KeyPair(), name = "nc-key", signPolicy = 1)
        val valid = KeyPair().pubKey.toHexKey()
        listOf(
            valid.uppercase(),
            valid.dropLast(1) + "g",
            valid.dropLast(1),
            "../" + valid.drop(3),
        ).forEach { key ->
            assertEquals(key, "Invalid public key in URI", AmberDesktop.engine.addNostrConnect("nostrconnect://$key?relay=wss%3A%2F%2Fnew.invalid", account))
        }
        assertNull(AmberDesktop.engine.addNostrConnect("nostrconnect://$valid?relay=wss%3A%2F%2Fnew.invalid", account))
    }

    // The shared parser: relays, the secret byte for byte, metadata, and only grantable permissions.
    @Test
    fun aNostrConnectUriIsQueuedAsItsClientAskedFor() = runBlocking {
        val account = AccountManager.addAccount(KeyPair(), name = "nc-parse", signPolicy = 1)
        val clientHex = KeyPair().pubKey.toHexKey()
        val metadata = java.net.URLEncoder.encode("""{"url":"https://example.org"}""", "utf-8")
        val uri = "nostrconnect://$clientHex?relay=wss%3A%2F%2Fa.invalid&relay=wss%3A%2F%2Fb.invalid&name=My+App" +
            "&secret=XQUdha4rjh_gjAgHN8X5yg==&metadata=$metadata&perms=sign_event%3A1,nip%3A9999,nip%3A44"
        assertNull(AmberDesktop.engine.addNostrConnect(uri, account))
        val card = withTimeout(20.seconds) {
            while (AmberDesktop.engine.pending.value.none { it.localKey == clientHex }) delay(50.milliseconds)
            AmberDesktop.engine.pending.value.first { it.localKey == clientHex }
        }

        assertEquals("My App", card.appName)
        assertEquals("https://example.org", card.appUrl)
        assertEquals(listOf("wss://a.invalid/", "wss://b.invalid/"), card.relays.map { it.url })
        assertEquals("XQUdha4rjh_gjAgHN8X5yg==", card.nostrConnectSecret)
        assertEquals(listOf("sign_event" to 1, "nip" to 44), card.requestedPermissions.map { it.type to it.kind })
    }
}
