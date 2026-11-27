package com.greenart7c3.nostrsigner.ui

import com.greenart7c3.nostrsigner.models.TorMode
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip06KeyDerivation.Nip06
import com.vitorpamplona.quartz.nip19Bech32.toNsec
import com.vitorpamplona.quartz.nip49PrivKeyEnc.Nip49
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginViewModelsTest {
    private val keyPair = KeyPair()
    private val privHex = keyPair.privKey!!.toHexKey()
    private val pubHex = keyPair.pubKey.toHexKey()
    private val phrase = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

    @Test
    fun `nsec, hex and ncryptsec keys give the same key pair`() {
        assertEquals(pubHex, parseLoginKey(keyPair.privKey!!.toNsec(), "").first?.pubKey?.toHexKey())
        assertEquals(pubHex, parseLoginKey(privHex, "").first?.pubKey?.toHexKey())
        val ncryptsec = Nip49().encrypt(privHex, "hunter2")
        assertEquals(pubHex, parseLoginKey(ncryptsec, "hunter2").first?.pubKey?.toHexKey())
    }

    @Test
    fun `a wrong password or garbage is refused with a reason`() {
        val wrongPassword = parseLoginKey(Nip49().encrypt(privHex, "hunter2"), "nope")
        assertNull(wrongPassword.first)
        assertTrue(wrongPassword.second.isNotBlank())
        assertNull(parseLoginKey("not a key", "").first)
    }

    @Test
    fun `a recovery phrase derives the key of the chosen account index`() {
        val first = parseLoginKey(phrase, "", 0).first!!.privKey!!.toHexKey()
        val second = parseLoginKey(phrase, "", 1).first!!.privKey!!.toHexKey()
        assertEquals(Nip06().privateKeyFromMnemonic(phrase, 0).toHexKey(), first)
        assertNotEquals(first, second)
    }

    @Test
    fun `pasted keys lose invisible characters, case and surrounding spaces`() {
        assertEquals("nsec1abc", normalizeKeyInput("​  NSEC1abc\n "))
    }

    @Test
    fun `a new key has twelve distinct words and the key they derive`() {
        val newKey = generateNewKey()
        assertEquals(12, newKey.seedWords.size)
        assertEquals(Nip06().privateKeyFromMnemonic(newKey.seedWords.joinToString(" ")).toHexKey(), newKey.keyPair.privKey!!.toHexKey())
    }

    @Test
    fun `only orbot uses the typed port, and it must be a number`() {
        assertEquals(9050, proxyPortFor(TorMode.BUILTIN, "abc"))
        assertEquals(9050, proxyPortFor(TorMode.DISABLED, ""))
        assertEquals(9150, proxyPortFor(TorMode.ORBOT, "9150"))
        assertNull(proxyPortFor(TorMode.ORBOT, "nine"))
    }

    private fun importViewModel() = AccountImportViewModel(mockk(relaxed = true), CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun `a clean import finishes at once`() {
        var finished = false
        importViewModel().onImportFinished(2, emptyList()) { finished = true }
        assertTrue(finished)
    }

    @Test
    fun `a partial import finishes once its error is dismissed`() {
        val viewModel = importViewModel()
        var finished = false
        viewModel.onImportFinished(1, listOf(3)) { finished = true }
        assertFalse(finished)
        assertEquals(listOf(3), viewModel.failedLines.value)

        viewModel.dismissImportError()
        assertTrue(finished)
        assertTrue(viewModel.failedLines.value.isEmpty())
    }

    @Test
    fun `an import that restored nothing never finishes, so the user can retry from the first page`() {
        val viewModel = importViewModel()
        var finished = false
        viewModel.onImportFinished(0, listOf(1, 2)) { finished = true }
        viewModel.dismissImportError()
        assertFalse(finished)
    }
}
