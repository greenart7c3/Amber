package com.greenart7c3.nostrsigner.service

import com.greenart7c3.nostrsigner.common.psbt.PsbtSighashPolicy
import com.greenart7c3.nostrsigner.models.Account
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.BitcoinTransaction
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.OutPoint
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.Psbt
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.PsbtSignatureVerifier
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.TaprootSigHash
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.TxIn
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.TxOut
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.inputTapKeySig
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.setInputSighashType
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.setInputTapInternalKey
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.setInputWitnessUtxo
import com.vitorpamplona.quartz.nipBCOnchainZaps.taproot.TaprootAddress
import com.vitorpamplona.quartz.utils.Hex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PsbtSighashPolicyTest {
    private val userKp = KeyPair(ByteArray(32) { 1 })
    private val otherKp = KeyPair(ByteArray(32) { 2 })

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

    private fun psbt(internalKey: ByteArray, sighashType: Int?): String {
        val tx = BitcoinTransaction(
            version = 2,
            inputs = listOf(TxIn(OutPoint("11".repeat(32), 0))),
            outputs = listOf(TxOut(99_000, spk(userKp))),
            lockTime = 0,
        )
        val psbt = Psbt.fromUnsignedTx(tx)
        psbt.setInputWitnessUtxo(0, TxOut(100_000, spk(userKp)))
        psbt.setInputTapInternalKey(0, internalKey)
        sighashType?.let { psbt.setInputSighashType(0, it) }
        return psbt.toHex()
    }

    @Test
    fun `refuses sighash types that do not commit to all outputs`() {
        for (type in listOf(
            TaprootSigHash.SIGHASH_NONE,
            TaprootSigHash.SIGHASH_SINGLE,
            TaprootSigHash.SIGHASH_ALL or TaprootSigHash.SIGHASH_ANYONECANPAY,
            TaprootSigHash.SIGHASH_NONE or TaprootSigHash.SIGHASH_ANYONECANPAY,
        )) {
            assertThrows("sighash 0x%02x".format(type), IllegalArgumentException::class.java) {
                runBlocking { account.signPsbt(psbt(userKp.pubKey, type)) }
            }
        }
    }

    @Test
    fun `signs default and SIGHASH_ALL inputs`() {
        for (type in listOf(null, TaprootSigHash.SIGHASH_DEFAULT, TaprootSigHash.SIGHASH_ALL)) {
            val signed = Psbt.parse(runBlocking { account.signPsbt(psbt(userKp.pubKey, type)) })
            assertNotNull(signed.inputTapKeySig(0))
            assertTrue(PsbtSignatureVerifier.verifyAllKeyPathInputs(signed))
        }
    }

    @Test
    fun `ignores inputs the account does not sign`() {
        // Another signer's input may use any sighash; Amber leaves it untouched.
        PsbtSighashPolicy.requireCommitsToAllOutputs(psbt(otherKp.pubKey, TaprootSigHash.SIGHASH_NONE), userKp.pubKey)
    }
}
