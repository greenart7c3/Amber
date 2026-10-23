package com.greenart7c3.nostrsigner.service

import com.greenart7c3.nostrsigner.models.Account
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.BitcoinTransaction
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.OutPoint
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.Psbt
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.TxIn
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.TxOut
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.setInputTapInternalKey
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.setInputWitnessUtxo
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.setOutputTapInternalKey
import com.vitorpamplona.quartz.nipBCOnchainZaps.taproot.TaprootAddress
import com.vitorpamplona.quartz.utils.Hex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PsbtDecoderTest {
    private val userKp = KeyPair(ByteArray(32) { 1 })
    private val attackerKp = KeyPair(ByteArray(32) { 2 })
    private val account = Account(
        signer = NostrSignerInternal(userKp),
        hexKey = Hex.encode(userKp.pubKey),
        npub = "npub-test",
        name = MutableStateFlow(""),
        picture = MutableStateFlow(""),
        signPolicy = 0,
        didBackup = true,
        scope = CoroutineScope(Dispatchers.Unconfined),
        keyAccessCheck = {},
    )

    private fun spk(kp: KeyPair) = TaprootAddress.outputKeyToScriptPubKey(TaprootAddress.tweakOutputKey(kp.pubKey))

    private fun psbtPaying(output: TxOut, taggedInternalKey: ByteArray?): String {
        val tx = BitcoinTransaction(
            version = 2,
            inputs = listOf(TxIn(OutPoint("11".repeat(32), 0))),
            outputs = listOf(output),
            lockTime = 0,
        )
        val psbt = Psbt.fromUnsignedTx(tx)
        psbt.setInputWitnessUtxo(0, TxOut(100_000, spk(userKp)))
        psbt.setInputTapInternalKey(0, userKp.pubKey)
        taggedInternalKey?.let { psbt.setOutputTapInternalKey(0, it) }
        return psbt.toHex()
    }

    @Test
    fun `output paying another key is sent even when tagged with the account key`() {
        val decoded = PsbtDecoder.decode(psbtPaying(TxOut(99_000, spk(attackerKp)), userKp.pubKey), account)

        assertFalse(decoded.outputs.single().isChange)
        assertEquals(99_000L, decoded.totalSent)
        assertEquals(0L, decoded.totalChange)
    }

    @Test
    fun `output paying the account's own taproot address is change`() {
        for (tag in listOf(userKp.pubKey, null)) {
            val decoded = PsbtDecoder.decode(psbtPaying(TxOut(99_000, spk(userKp)), tag), account)

            assertTrue(decoded.outputs.single().isChange)
            assertEquals(0L, decoded.totalSent)
            assertEquals(99_000L, decoded.totalChange)
            assertEquals(1_000L, decoded.feeSats)
        }
    }

    @Test
    fun `scriptPubKeyToAddress decodes P2TR as bech32m`() {
        val script = Hex.decode("5120a3eb3f9b18bf1ea7a3d4eddbb33c0d34c4d2e9a45dc12e7e98aa3d345e8b3c4f")
        assertEquals(
            "bc1p504nlxcchu020g75ahdmx0qdxnzd96dythqjul5c4g7ngh5t838s9ug0nc",
            PsbtDecoder.scriptPubKeyToAddress(script),
        )
    }

    @Test
    fun `scriptPubKeyToAddress decodes P2WPKH as bech32`() {
        val script = Hex.decode("0014751e76e8199196d454941c45d1b3a323f1433bd6")
        assertEquals(
            "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4",
            PsbtDecoder.scriptPubKeyToAddress(script),
        )
    }

    @Test
    fun `scriptPubKeyToAddress decodes P2WSH as bech32`() {
        val script = Hex.decode("00200102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20")
        assertEquals(
            "bc1qqypqxpq9qcrsszg2pvxq6rs0zqg3yyc5z5tpwxqergd3c8g7rusqyp0mu0",
            PsbtDecoder.scriptPubKeyToAddress(script),
        )
    }

    @Test
    fun `scriptPubKeyToAddress returns null for P2PKH script`() {
        // OP_DUP OP_HASH160 <20> ... OP_EQUALVERIFY OP_CHECKSIG  -- legacy P2PKH, not supported
        val script = Hex.decode("76a914751e76e8199196d454941c45d1b3a323f1433bd688ac")
        assertNull(PsbtDecoder.scriptPubKeyToAddress(script))
    }

    @Test
    fun `scriptPubKeyToAddress returns null for P2SH script`() {
        val script = Hex.decode("a914751e76e8199196d454941c45d1b3a323f1433bd687")
        assertNull(PsbtDecoder.scriptPubKeyToAddress(script))
    }

    @Test
    fun `scriptPubKeyToAddress returns null for empty script`() {
        assertNull(PsbtDecoder.scriptPubKeyToAddress(ByteArray(0)))
    }

    @Test
    fun `scriptPubKeyToAddress returns null for wrong-length witness program`() {
        // OP_0 with 21 bytes (not 20 or 32) is not a valid v0 witness program
        val script = Hex.decode("0015751e76e8199196d454941c45d1b3a323f1433bd600")
        assertNull(PsbtDecoder.scriptPubKeyToAddress(script))
    }

    @Test
    fun `scriptPubKeyToAddress uses provided hrp for testnet-like prefixes`() {
        val script = Hex.decode("0014751e76e8199196d454941c45d1b3a323f1433bd6")
        val tb = PsbtDecoder.scriptPubKeyToAddress(script, hrp = "tb")
        assertEquals("tb1qw508d6qejxtdg4y5r3zarvary0c5xw7kxpjzsx", tb)
    }
}
