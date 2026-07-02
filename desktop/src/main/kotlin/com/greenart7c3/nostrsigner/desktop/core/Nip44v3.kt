package com.greenart7c3.nostrsigner.desktop.core

import com.vitorpamplona.quartz.nip44Encryption.crypto.ChaCha20
import com.vitorpamplona.quartz.nip44Encryption.crypto.Hkdf
import com.vitorpamplona.quartz.utils.RandomInstance
import com.vitorpamplona.quartz.utils.Secp256k1Instance
import com.vitorpamplona.quartz.utils.mac.MacInstance
import kotlin.io.encoding.Base64

/**
 * NIP-44 v3 cipher.
 *
 * Implements the asymmetric encryption scheme defined in the
 * `nostr-land/nip44v3` draft: ECDH(secp256k1) → HKDF-SHA256 keyed with
 * `"nip44-v3\x00" || nonce`, ChaCha20 with an all-zeroes 96-bit nonce,
 * HMAC-SHA256 over `nonce || kind || scope_len || scope || ciphertext`,
 * and a context (`kind` + `scope`) authenticated alongside the
 * ciphertext to prevent cross-context replay.
 */
object Nip44v3 {
    const val VERSION: Byte = 0x03
    private const val NONCE_SIZE = 32
    private const val MAC_SIZE = 32
    private const val MIN_DECODED_SIZE = 77 // 1 + 32 + 32 + 4 + 4 + 0 + 4
    private const val MIN_PADDING = 32
    private const val PAD_CHUNK_THRESHOLD = 32768
    private const val PAD_SUBDIVS_SMALL = 4
    private const val PAD_SUBDIVS_LARGE = 8

    private val saltPrefix = "nip44-v3\u0000".encodeToByteArray()
    private val infoEncryptionKey = "encryption_key".encodeToByteArray()
    private val infoMacKey = "mac_key".encodeToByteArray()
    private val zeroChaChaNonce = ByteArray(12)

    private val hkdf = Hkdf()
    private val chaCha = ChaCha20()

    class Nip44v3Exception(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

    fun encrypt(
        plaintext: ByteArray,
        privKey: ByteArray,
        pubKey: ByteArray,
        kind: Int,
        scope: String,
    ): String = encryptWithNonce(plaintext, privKey, pubKey, kind, scope, RandomInstance.bytes(NONCE_SIZE))

    /**
     * Test/library-internal overload that accepts a caller-supplied nonce. The
     * NIP-44 v3 spec is explicit that production code must not let callers
     * choose the nonce — use [encrypt] for that.
     */
    fun encryptWithNonce(
        plaintext: ByteArray,
        privKey: ByteArray,
        pubKey: ByteArray,
        kind: Int,
        scope: String,
        nonce: ByteArray,
    ): String {
        require(nonce.size == NONCE_SIZE) { "nonce must be $NONCE_SIZE bytes, got ${nonce.size}" }
        require(kind >= 0) { "kind must be non-negative, got $kind" }

        val scopeBytes = scope.encodeToByteArray()
        val (encryptionKey, macKey) = deriveKeys(privKey, pubKey, nonce)

        val padded = pad(plaintext)
        val cipherBytes = chaCha.encrypt(padded, zeroChaChaNonce, encryptionKey)

        val mac = computeMac(macKey, nonce, kind, scopeBytes, cipherBytes)

        val payload = ByteArray(1 + NONCE_SIZE + MAC_SIZE + 4 + 4 + scopeBytes.size + cipherBytes.size)
        var off = 0
        payload[off++] = VERSION
        nonce.copyInto(payload, off)
        off += NONCE_SIZE
        mac.copyInto(payload, off)
        off += MAC_SIZE
        writeU32BE(payload, off, kind)
        off += 4
        writeU32BE(payload, off, scopeBytes.size)
        off += 4
        scopeBytes.copyInto(payload, off)
        off += scopeBytes.size
        cipherBytes.copyInto(payload, off)

        return Base64.encode(payload)
    }

    fun decrypt(
        payload: String,
        privKey: ByteArray,
        pubKey: ByteArray,
        expectedKind: Int,
        expectedScope: String,
    ): ByteArray {
        if (payload.isEmpty()) throw Nip44v3Exception("empty payload")
        if (payload[0] == '#') throw Nip44v3Exception("unsupported future version")

        val decoded = try {
            Base64.decode(payload)
        } catch (e: IllegalArgumentException) {
            throw Nip44v3Exception("invalid base64", e)
        }

        if (decoded.size < MIN_DECODED_SIZE) {
            throw Nip44v3Exception("ciphertext too short: ${decoded.size} < $MIN_DECODED_SIZE")
        }
        if (decoded[0] != VERSION) {
            throw Nip44v3Exception("unsupported version: ${decoded[0].toInt() and 0xff}")
        }

        val nonce = decoded.copyOfRange(1, 1 + NONCE_SIZE)
        val mac = decoded.copyOfRange(1 + NONCE_SIZE, 1 + NONCE_SIZE + MAC_SIZE)
        val kind = readU32BE(decoded, 1 + NONCE_SIZE + MAC_SIZE)
        val scopeLen = readU32BE(decoded, 1 + NONCE_SIZE + MAC_SIZE + 4)

        val scopeOff = 1 + NONCE_SIZE + MAC_SIZE + 4 + 4
        if (scopeLen < 0 || scopeLen > decoded.size - scopeOff) {
            throw Nip44v3Exception("scope length out-of-bounds: $scopeLen")
        }
        val scope = decoded.copyOfRange(scopeOff, scopeOff + scopeLen)
        val cipherBytes = decoded.copyOfRange(scopeOff + scopeLen, decoded.size)
        if (cipherBytes.size < 4) {
            throw Nip44v3Exception("ciphertext too short")
        }

        if (kind != expectedKind) {
            throw Nip44v3Exception("context mismatch (kind): got $kind expected $expectedKind")
        }
        val expectedScopeBytes = expectedScope.encodeToByteArray()
        if (!scope.contentEquals(expectedScopeBytes)) {
            throw Nip44v3Exception("context mismatch (scope)")
        }

        val (encryptionKey, macKey) = deriveKeys(privKey, pubKey, nonce)

        val expectedMac = computeMac(macKey, nonce, kind, scope, cipherBytes)
        if (!constantTimeEq(mac, expectedMac)) {
            throw Nip44v3Exception("invalid MAC")
        }

        val padded = chaCha.decrypt(cipherBytes, zeroChaChaNonce, encryptionKey)
        return unpad(padded)
    }

    fun deriveKeys(privKey: ByteArray, pubKey: ByteArray, nonce: ByteArray): Pair<ByteArray, ByteArray> {
        val sharedSecret = Secp256k1Instance.pubKeyTweakMulCompact(pubKey, privKey)
        return deriveKeysFromSharedSecret(sharedSecret, nonce)
    }

    fun deriveKeysFromSharedSecret(sharedSecret: ByteArray, nonce: ByteArray): Pair<ByteArray, ByteArray> {
        val prk = extract(sharedSecret, nonce)
        val encryptionKey = hkdf.expand(prk, infoEncryptionKey, 32)
        val macKey = hkdf.expand(prk, infoMacKey, 32)
        return encryptionKey to macKey
    }

    /** Exposed for test vectors that check the intermediate `prk`. */
    fun extract(sharedSecret: ByteArray, nonce: ByteArray): ByteArray {
        val salt = ByteArray(saltPrefix.size + nonce.size)
        saltPrefix.copyInto(salt, 0)
        nonce.copyInto(salt, saltPrefix.size)
        return hkdf.extract(sharedSecret, salt)
    }

    fun pad(plaintext: ByteArray): ByteArray {
        val prefixedLen = 4 + plaintext.size
        val targetSize = targetSize(prefixedLen)
        val out = ByteArray(targetSize)
        writeU32BE(out, 0, plaintext.size)
        plaintext.copyInto(out, 4)
        return out
    }

    fun unpad(padded: ByteArray): ByteArray {
        if (padded.size < 4) throw Nip44v3Exception("padded buffer too short")
        val plaintextLen = readU32BE(padded, 0)
        if (plaintextLen < 0) throw Nip44v3Exception("invalid plaintext length: $plaintextLen")
        if (plaintextLen.toLong() + 4L > padded.size.toLong()) {
            throw Nip44v3Exception("invalid padding: declared $plaintextLen, available ${padded.size - 4}")
        }
        // The NIP-44 v3 spec deliberately does NOT mandate a canonical padding
        // length: "implementations must not do any other checks on the padding
        // length". The standard padding algorithm is only a SHOULD, so a peer
        // may legitimately send more (or fewer) padding bytes than we would
        // produce. Validating against our own target size would reject those
        // otherwise-valid messages, so we only require that the padding region
        // is all zeroes.
        // Constant-time zero check over the padding region.
        var diff = 0
        for (i in 4 + plaintextLen until padded.size) {
            diff = diff or padded[i].toInt()
        }
        if (diff != 0) throw Nip44v3Exception("invalid padding: non-zero trailing bytes")
        return padded.copyOfRange(4, 4 + plaintextLen)
    }

    fun targetSize(len: Int): Int {
        val t = targetSizeLong(len.toLong())
        if (t > Int.MAX_VALUE) throw Nip44v3Exception("padded length exceeds Int.MAX_VALUE: $t")
        return t.toInt()
    }

    private fun targetSizeLong(len: Long): Long {
        require(len >= 0) { "negative length: $len" }
        if (len == 0L) return MIN_PADDING.toLong()
        // next_power = 2 ** ceil(log2(len))
        val nextPower = if (len == 1L) 1L else 1L shl ceilLog2Long(len)
        val subdivs = if (nextPower >= PAD_CHUNK_THRESHOLD) PAD_SUBDIVS_LARGE.toLong() else PAD_SUBDIVS_SMALL.toLong()
        val chunk = maxOf(MIN_PADDING.toLong(), nextPower / subdivs)
        return chunk * ((len + chunk - 1) / chunk)
    }

    private fun ceilLog2Long(n: Long): Int {
        // For n >= 2; matches ceil(log2(n)).
        if (n <= 1) return 0
        var v = n - 1
        var bits = 0
        while (v > 0) {
            v = v ushr 1
            bits++
        }
        return bits
    }

    private fun computeMac(macKey: ByteArray, nonce: ByteArray, kind: Int, scope: ByteArray, ciphertext: ByteArray): ByteArray {
        val mac = MacInstance("HmacSHA256", macKey)
        mac.update(nonce)
        val u32 = ByteArray(4)
        writeU32BE(u32, 0, kind)
        mac.update(u32)
        writeU32BE(u32, 0, scope.size)
        mac.update(u32)
        if (scope.isNotEmpty()) mac.update(scope)
        if (ciphertext.isNotEmpty()) mac.update(ciphertext)
        return mac.doFinal()
    }

    private fun writeU32BE(out: ByteArray, offset: Int, value: Int) {
        out[offset] = (value ushr 24).toByte()
        out[offset + 1] = (value ushr 16).toByte()
        out[offset + 2] = (value ushr 8).toByte()
        out[offset + 3] = value.toByte()
    }

    private fun readU32BE(src: ByteArray, offset: Int): Int {
        val v = ((src[offset].toLong() and 0xff) shl 24) or
            ((src[offset + 1].toLong() and 0xff) shl 16) or
            ((src[offset + 2].toLong() and 0xff) shl 8) or
            (src[offset + 3].toLong() and 0xff)
        if (v > Int.MAX_VALUE) throw Nip44v3Exception("u32 value exceeds Int.MAX_VALUE: $v")
        return v.toInt()
    }

    private fun constantTimeEq(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }
}
