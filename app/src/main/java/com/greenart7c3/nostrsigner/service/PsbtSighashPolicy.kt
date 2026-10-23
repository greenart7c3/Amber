package com.greenart7c3.nostrsigner.service

import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.Psbt
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.TaprootSigHash
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.inputSighashType
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.inputTapInternalKey
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.inputTapKeySig

/**
 * Refuses to sign a PSBT whose requester-chosen sighash type would let the
 * signature skip the outputs shown in the approval sheet.
 *
 * Quartz's PsbtSigner signs with whatever PSBT_IN_SIGHASH_TYPE the requester
 * set. SIGHASH_NONE, SIGHASH_SINGLE and the ANYONECANPAY variants do not commit
 * to every output, so the requester could rewrite the outputs after approval
 * and the signature would still be valid. Only SIGHASH_DEFAULT and SIGHASH_ALL
 * bind the signature to exactly the transaction the user approved.
 *
 * The inputs checked are the ones PsbtSigner would sign: key-path inputs whose
 * internal key is the account's x-only key and that carry no signature yet.
 */
object PsbtSighashPolicy {
    fun requireCommitsToAllOutputs(psbtHex: String, xOnlyPubKey: ByteArray) {
        val psbt = Psbt.parse(psbtHex)
        for (index in psbt.unsignedTx.inputs.indices) {
            val internalKey = psbt.inputTapInternalKey(index) ?: continue
            if (!internalKey.contentEquals(xOnlyPubKey) || psbt.inputTapKeySig(index) != null) continue
            val sighashType = psbt.inputSighashType(index) ?: continue
            require(sighashType == TaprootSigHash.SIGHASH_DEFAULT || sighashType == TaprootSigHash.SIGHASH_ALL) {
                "Refusing to sign: input $index requests sighash type 0x%02x, which does not commit to all outputs".format(sighashType)
            }
        }
    }
}
