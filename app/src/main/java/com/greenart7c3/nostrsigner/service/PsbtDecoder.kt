package com.greenart7c3.nostrsigner.service

import androidx.compose.runtime.Immutable
import com.greenart7c3.nostrsigner.models.Account
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.Psbt
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.inputTapInternalKey
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.inputWitnessUtxo
import com.vitorpamplona.quartz.utils.Hex

@Immutable
data class DecodedPsbtInput(
    val prevTxid: String,
    val prevVout: Long,
    val valueSats: Long?,
    val scriptPubKeyHex: String?,
    val address: String?,
    val controlled: Boolean,
)

@Immutable
data class DecodedPsbtOutput(
    val valueSats: Long,
    val scriptPubKeyHex: String,
    val address: String?,
)

@Immutable
data class DecodedPsbt(
    val version: Long,
    val lockTime: Long,
    val inputs: List<DecodedPsbtInput>,
    val outputs: List<DecodedPsbtOutput>,
    val feeSats: Long?,
    val controlledInputCount: Int,
    val parseError: String?,
)

object PsbtDecoder {
    fun decode(psbtHex: String, account: Account): DecodedPsbt = try {
        val psbt = Psbt.parse(psbtHex)
        val tx = psbt.unsignedTx
        val xOnlyPubKey = account.signer.keyPair.pubKey

        var anyInputMissingValue = false
        var outputTotal = 0L
        var inputTotal = 0L
        var controlledCount = 0

        val inputs = tx.inputs.mapIndexed { index, txIn ->
            val witnessUtxo = psbt.inputWitnessUtxo(index)
            val valueSats = witnessUtxo?.valueSats
            val scriptPubKey = witnessUtxo?.scriptPubKey
            if (valueSats == null) {
                anyInputMissingValue = true
            } else {
                inputTotal += valueSats
            }
            val internalKey = psbt.inputTapInternalKey(index)
            val controlled = internalKey != null && internalKey.contentEquals(xOnlyPubKey)
            if (controlled) controlledCount++

            DecodedPsbtInput(
                prevTxid = txIn.outPoint.txid,
                prevVout = txIn.outPoint.vout,
                valueSats = valueSats,
                scriptPubKeyHex = scriptPubKey?.let { Hex.encode(it) },
                address = scriptPubKey?.let { scriptPubKeyToAddress(it) },
                controlled = controlled,
            )
        }

        val outputs = tx.outputs.map { txOut ->
            outputTotal += txOut.valueSats
            DecodedPsbtOutput(
                valueSats = txOut.valueSats,
                scriptPubKeyHex = Hex.encode(txOut.scriptPubKey),
                address = scriptPubKeyToAddress(txOut.scriptPubKey),
            )
        }

        val fee = if (anyInputMissingValue) null else inputTotal - outputTotal

        DecodedPsbt(
            version = tx.version,
            lockTime = tx.lockTime,
            inputs = inputs,
            outputs = outputs,
            feeSats = fee,
            controlledInputCount = controlledCount,
            parseError = null,
        )
    } catch (e: Exception) {
        DecodedPsbt(
            version = 0,
            lockTime = 0,
            inputs = emptyList(),
            outputs = emptyList(),
            feeSats = null,
            controlledInputCount = 0,
            parseError = e.message ?: e::class.simpleName ?: "parse error",
        )
    }

    // Recognises segwit witness program script shapes and encodes them as bech32/bech32m mainnet addresses.
    // - P2WPKH (BIP-141): OP_0 <20>  -> bech32 with witver=0
    // - P2WSH  (BIP-141): OP_0 <32>  -> bech32 with witver=0
    // - P2TR   (BIP-341): OP_1 <32>  -> bech32m with witver=1
    // Returns null for everything else (legacy P2PKH/P2SH and non-standard scripts).
    fun scriptPubKeyToAddress(script: ByteArray, hrp: String = "bc"): String? {
        if (script.size == 22 && script[0] == 0x00.toByte() && script[1] == 0x14.toByte()) {
            return encodeSegwit(hrp, 0, script.copyOfRange(2, 22))
        }
        if (script.size == 34 && script[0] == 0x00.toByte() && script[1] == 0x20.toByte()) {
            return encodeSegwit(hrp, 0, script.copyOfRange(2, 34))
        }
        if (script.size == 34 && script[0] == 0x51.toByte() && script[1] == 0x20.toByte()) {
            return encodeSegwit(hrp, 1, script.copyOfRange(2, 34))
        }
        return null
    }

    private const val BECH32_CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    private const val BECH32_CONST = 1L
    private const val BECH32M_CONST = 0x2bc830a3L

    private fun encodeSegwit(hrp: String, witver: Int, program: ByteArray): String? {
        val program5 = convertBits(program, 8, 5, true) ?: return null
        val data = IntArray(program5.size + 1)
        data[0] = witver
        for (i in program5.indices) data[i + 1] = program5[i]
        val constant = if (witver == 0) BECH32_CONST else BECH32M_CONST
        val checksum = createChecksum(hrp, data, constant)
        val combined = data + checksum
        val sb = StringBuilder(hrp.length + 1 + combined.size)
        sb.append(hrp).append('1')
        for (v in combined) sb.append(BECH32_CHARSET[v])
        return sb.toString()
    }

    private fun convertBits(data: ByteArray, fromBits: Int, toBits: Int, pad: Boolean): IntArray? {
        var acc = 0
        var bits = 0
        val maxv = (1 shl toBits) - 1
        val result = mutableListOf<Int>()
        for (b in data) {
            val v = b.toInt() and 0xff
            if (v ushr fromBits != 0) return null
            acc = (acc shl fromBits) or v
            bits += fromBits
            while (bits >= toBits) {
                bits -= toBits
                result.add((acc ushr bits) and maxv)
            }
        }
        if (pad) {
            if (bits > 0) result.add((acc shl (toBits - bits)) and maxv)
        } else if (bits >= fromBits || ((acc shl (toBits - bits)) and maxv) != 0) {
            return null
        }
        return result.toIntArray()
    }

    private fun polymod(values: IntArray): Long {
        val gen = longArrayOf(0x3b6a57b2L, 0x26508e6dL, 0x1ea119faL, 0x3d4233ddL, 0x2a1462b3L)
        var chk = 1L
        for (v in values) {
            val top = chk ushr 25
            chk = ((chk and 0x1ffffffL) shl 5) xor v.toLong()
            for (i in 0..4) {
                if (((top ushr i) and 1L) == 1L) chk = chk xor gen[i]
            }
        }
        return chk
    }

    private fun hrpExpand(hrp: String): IntArray {
        val ret = IntArray(hrp.length * 2 + 1)
        for (i in hrp.indices) ret[i] = hrp[i].code ushr 5
        ret[hrp.length] = 0
        for (i in hrp.indices) ret[hrp.length + 1 + i] = hrp[i].code and 31
        return ret
    }

    private fun createChecksum(hrp: String, data: IntArray, constant: Long): IntArray {
        val values = hrpExpand(hrp) + data + IntArray(6)
        val mod = polymod(values) xor constant
        val ret = IntArray(6)
        for (i in 0..5) ret[i] = ((mod ushr (5 * (5 - i))) and 31L).toInt()
        return ret
    }
}
