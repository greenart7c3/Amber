package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.desktop.core.PsbtSighashPolicy
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.BitcoinTransaction
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.OutPoint
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.Psbt
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.TaprootSigHash
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.TxIn
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.TxOut
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.setInputSighashType
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.setInputTapInternalKey
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.setInputWitnessUtxo
import com.vitorpamplona.quartz.nipBCOnchainZaps.taproot.TaprootAddress
import org.junit.Assert.assertThrows
import org.junit.Test

class PsbtSighashPolicyTest {
    private val userKp = KeyPair(ByteArray(32) { 1 })
    private val otherKp = KeyPair(ByteArray(32) { 2 })

    private fun psbt(internalKey: ByteArray, sighashType: Int?): String {
        val spk = TaprootAddress.outputKeyToScriptPubKey(TaprootAddress.tweakOutputKey(userKp.pubKey))
        val tx = BitcoinTransaction(
            version = 2,
            inputs = listOf(TxIn(OutPoint("11".repeat(32), 0))),
            outputs = listOf(TxOut(99_000, spk)),
            lockTime = 0,
        )
        val psbt = Psbt.fromUnsignedTx(tx)
        psbt.setInputWitnessUtxo(0, TxOut(100_000, spk))
        psbt.setInputTapInternalKey(0, internalKey)
        sighashType?.let { psbt.setInputSighashType(0, it) }
        return psbt.toHex()
    }

    @Test
    fun refusesSighashTypesThatDoNotCommitToAllOutputs() {
        for (type in listOf(
            TaprootSigHash.SIGHASH_NONE,
            TaprootSigHash.SIGHASH_SINGLE,
            TaprootSigHash.SIGHASH_ALL or TaprootSigHash.SIGHASH_ANYONECANPAY,
        )) {
            assertThrows(IllegalArgumentException::class.java) {
                PsbtSighashPolicy.requireCommitsToAllOutputs(psbt(userKp.pubKey, type), userKp.pubKey)
            }
        }
    }

    @Test
    fun allowsDefaultAndAllAndOtherSignersInputs() {
        for (type in listOf(null, TaprootSigHash.SIGHASH_DEFAULT, TaprootSigHash.SIGHASH_ALL)) {
            PsbtSighashPolicy.requireCommitsToAllOutputs(psbt(userKp.pubKey, type), userKp.pubKey)
        }
        PsbtSighashPolicy.requireCommitsToAllOutputs(psbt(otherKp.pubKey, TaprootSigHash.SIGHASH_NONE), userKp.pubKey)
    }
}
