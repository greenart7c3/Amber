package com.greenart7c3.nostrsigner.service.nip44v3

import com.fasterxml.jackson.databind.JsonNode
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import java.security.MessageDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class Nip44v3Test {
    private fun loadVectors(): JsonNode {
        val stream = Nip44v3Test::class.java.classLoader!!.getResourceAsStream("nip44v3-vectors.json")
        assertNotNull("nip44v3-vectors.json not found on test classpath", stream)
        return JacksonMapper.mapper.readTree(stream)
    }

    private fun hex(s: String): ByteArray {
        val out = ByteArray(s.length / 2)
        for (i in out.indices) {
            out[i] = ((Character.digit(s[i * 2], 16) shl 4) or Character.digit(s[i * 2 + 1], 16)).toByte()
        }
        return out
    }

    private fun toHex(b: ByteArray): String {
        val sb = StringBuilder(b.size * 2)
        for (x in b) sb.append(String.format("%02x", x.toInt() and 0xff))
        return sb.toString()
    }

    private fun pubKeyFor(privKeyHex: String): ByteArray = KeyPair(privKey = hex(privKeyHex)).pubKey

    @Test
    fun `padded length vectors match`() {
        val arr = loadVectors().get("padded_length")
        for (entry in arr) {
            val len = entry.get(0).intValue()
            val expected = entry.get(1).intValue()
            // The spec's target_size is over length(prefixed_plaintext); the
            // table is parameterized on that prefixed length directly.
            assertEquals("targetSize($len)", expected, Nip44v3.targetSize(len))
        }
    }

    @Test
    fun `encrypt and decrypt vectors round-trip in both directions`() {
        val vectors = loadVectors().get("encrypt_decrypt")
        for ((idx, v) in vectors.withIndex()) {
            val priv1 = hex(v.get("secret1").textValue())
            val priv2 = hex(v.get("secret2").textValue())
            val pub1 = pubKeyFor(v.get("secret1").textValue())
            val pub2 = pubKeyFor(v.get("secret2").textValue())
            val nonce = hex(v.get("nonce").textValue())
            val kind = v.get("kind").intValue()
            val scope = String(hex(v.get("scope_hex").textValue()), Charsets.UTF_8)
            val plaintext = hex(v.get("plaintext_hex").textValue())
            val expectedCt = v.get("ciphertext").textValue()
            val expectedPrk = v.get("prk").textValue()
            val expectedEnc = v.get("encryption_key").textValue()
            val expectedMac = v.get("mac_key").textValue()

            // Key derivation matches PRK / encryption_key / mac_key from spec
            val prk1 = Nip44v3.extract(
                com.vitorpamplona.quartz.utils.Secp256k1Instance.pubKeyTweakMulCompact(pub2, priv1),
                nonce,
            )
            assertEquals("vector $idx: prk", expectedPrk, toHex(prk1))
            val (enc, mac) = Nip44v3.deriveKeys(priv1, pub2, nonce)
            assertEquals("vector $idx: encryption_key", expectedEnc, toHex(enc))
            assertEquals("vector $idx: mac_key", expectedMac, toHex(mac))

            // Encrypt from secret1 → must match the spec ciphertext byte for byte.
            val ct1 = Nip44v3.encryptWithNonce(plaintext, priv1, pub2, kind, scope, nonce)
            assertEquals("vector $idx: ciphertext from secret1", expectedCt, ct1)

            // Encrypt from secret2 with the same nonce — same ECDH product, same ciphertext.
            val ct2 = Nip44v3.encryptWithNonce(plaintext, priv2, pub1, kind, scope, nonce)
            assertEquals("vector $idx: ciphertext from secret2", expectedCt, ct2)

            // Decryption recovers plaintext from each side
            val dec1 = Nip44v3.decrypt(expectedCt, priv1, pub2, kind, scope)
            assertArrayEquals("vector $idx: decrypt from secret1", plaintext, dec1)
            val dec2 = Nip44v3.decrypt(expectedCt, priv2, pub1, kind, scope)
            assertArrayEquals("vector $idx: decrypt from secret2", plaintext, dec2)
        }
    }

    @Test
    fun `decrypt-only vectors with non-standard padding decrypt correctly`() {
        // The spec allows ciphertexts whose padding length differs from the one
        // our encryptor would produce (the standard padding algorithm is a
        // SHOULD, not a MUST). We must still decrypt them, validating only that
        // the padding bytes are zero. These are decrypt-only because re-encrypting
        // would yield the canonical padding length and a different ciphertext.
        val vectors = loadVectors().get("decrypt_only")
        assertNotNull("decrypt_only section missing", vectors)
        for ((idx, v) in vectors.withIndex()) {
            val priv1 = hex(v.get("secret1").textValue())
            val priv2 = hex(v.get("secret2").textValue())
            val pub1 = pubKeyFor(v.get("secret1").textValue())
            val pub2 = pubKeyFor(v.get("secret2").textValue())
            val kind = v.get("kind").intValue()
            val scope = String(hex(v.get("scope_hex").textValue()), Charsets.UTF_8)
            val plaintext = hex(v.get("plaintext_hex").textValue())
            val ciphertext = v.get("ciphertext").textValue()
            val note = v.get("note")?.textValue() ?: ""

            val dec1 = Nip44v3.decrypt(ciphertext, priv1, pub2, kind, scope)
            assertArrayEquals("vector $idx ($note): decrypt from secret1", plaintext, dec1)
            val dec2 = Nip44v3.decrypt(ciphertext, priv2, pub1, kind, scope)
            assertArrayEquals("vector $idx ($note): decrypt from secret2", plaintext, dec2)
        }
    }

    @Test
    fun `long encrypt and decrypt vectors match sha256 of ciphertext`() {
        val vectors = loadVectors().get("long_encrypt_decrypt")
        val sha = MessageDigest.getInstance("SHA-256")
        for ((idx, v) in vectors.withIndex()) {
            val priv1 = hex(v.get("secret1").textValue())
            val priv2 = hex(v.get("secret2").textValue())
            val pub1 = pubKeyFor(v.get("secret1").textValue())
            val pub2 = pubKeyFor(v.get("secret2").textValue())
            val nonce = hex(v.get("nonce").textValue())
            val kind = v.get("kind").intValue()
            val scope = String(hex(v.get("scope_hex").textValue()), Charsets.UTF_8)
            val pattern = hex(v.get("pattern_hex").textValue())
            val repeat = v.get("repeat").intValue()
            val expectedHash = v.get("ciphertext_sha256").textValue()

            val plaintext = ByteArray(pattern.size * repeat)
            for (i in 0 until repeat) pattern.copyInto(plaintext, i * pattern.size)

            val ct = Nip44v3.encryptWithNonce(plaintext, priv1, pub2, kind, scope, nonce)
            sha.reset()
            val hash = toHex(sha.digest(ct.toByteArray(Charsets.US_ASCII)))
            assertEquals("vector $idx: ciphertext sha256", expectedHash, hash)

            // Round-trip with the other side, just to exercise the symmetric path
            val recovered = Nip44v3.decrypt(ct, priv2, pub1, kind, scope)
            assertArrayEquals("vector $idx: long decrypt", plaintext, recovered)
        }
    }

    @Test
    fun `invalid decryption vectors fail`() {
        val vectors = loadVectors().get("invalid_decryption")
        for ((idx, v) in vectors.withIndex()) {
            val priv = hex(v.get("secret").textValue())
            val pub = hex(v.get("public").textValue())
            val kind = v.get("kind").intValue()
            val scope = String(hex(v.get("scope_hex").textValue()), Charsets.UTF_8)
            val ct = v.get("ciphertext").textValue()
            val why = v.get("why").textValue()

            assertThrows("vector $idx ($why) should fail", Nip44v3.Nip44v3Exception::class.java) {
                Nip44v3.decrypt(ct, priv, pub, kind, scope)
            }
        }
    }

    @Test
    fun `round-trip on various plaintext sizes`() {
        val priv = hex("0000000000000000000000000000000000000000000000000000000000000001")
        val peer = hex("0000000000000000000000000000000000000000000000000000000000000002")
        val peerPub = pubKeyFor("0000000000000000000000000000000000000000000000000000000000000002")
        val sizes = listOf(0, 1, 31, 32, 33, 100, 1024, 65535, 65536, 100_000)
        for (size in sizes) {
            val plaintext = ByteArray(size) { (it and 0xff).toByte() }
            val ct = Nip44v3.encrypt(plaintext, priv, peerPub, kind = 4, scope = "dm")
            val ownPub = pubKeyFor("0000000000000000000000000000000000000000000000000000000000000001")
            val recovered = Nip44v3.decrypt(ct, peer, ownPub, expectedKind = 4, expectedScope = "dm")
            assertArrayEquals("size=$size", plaintext, recovered)
        }
    }

    @Test
    fun `context rebinding is rejected`() {
        val priv = hex("0000000000000000000000000000000000000000000000000000000000000001")
        val peer = hex("0000000000000000000000000000000000000000000000000000000000000002")
        val peerPub = pubKeyFor("0000000000000000000000000000000000000000000000000000000000000002")
        val ownPub = pubKeyFor("0000000000000000000000000000000000000000000000000000000000000001")

        val ct = Nip44v3.encrypt("hello".toByteArray(), priv, peerPub, kind = 4, scope = "a")
        assertThrows(Nip44v3.Nip44v3Exception::class.java) {
            Nip44v3.decrypt(ct, peer, ownPub, expectedKind = 4, expectedScope = "b")
        }
        assertThrows(Nip44v3.Nip44v3Exception::class.java) {
            Nip44v3.decrypt(ct, peer, ownPub, expectedKind = 5, expectedScope = "a")
        }
    }

    @Test
    fun `version byte rejection`() {
        val priv = hex("0000000000000000000000000000000000000000000000000000000000000001")
        val peerPub = pubKeyFor("0000000000000000000000000000000000000000000000000000000000000002")

        val e1 = assertThrows(Nip44v3.Nip44v3Exception::class.java) {
            Nip44v3.decrypt("#anything", priv, peerPub, 0, "")
        }
        assertTrue(e1.message!!.contains("unsupported future version"))

        val e2 = assertThrows(Nip44v3.Nip44v3Exception::class.java) {
            Nip44v3.decrypt("", priv, peerPub, 0, "")
        }
        assertTrue(e2.message!!.contains("empty payload"))
    }

    @Test
    fun `pad and unpad are inverse for short and long inputs`() {
        for (size in listOf(0, 1, 32, 33, 1024, 65535)) {
            val plain = ByteArray(size) { (it and 0xff).toByte() }
            val padded = Nip44v3.pad(plain)
            assertEquals(Nip44v3.targetSize(4 + size), padded.size)
            val recovered = Nip44v3.unpad(padded)
            assertArrayEquals(plain, recovered)
        }
    }

    @Test
    fun `unpad rejects non-zero padding`() {
        val plain = byteArrayOf(1, 2, 3)
        val padded = Nip44v3.pad(plain)
        padded[padded.size - 1] = 0x42 // tamper trailing pad byte
        assertThrows(Nip44v3.Nip44v3Exception::class.java) { Nip44v3.unpad(padded) }
    }

    @Test
    fun `constant-time MAC tamper detection`() {
        val priv = hex("0000000000000000000000000000000000000000000000000000000000000001")
        val peer = hex("0000000000000000000000000000000000000000000000000000000000000002")
        val peerPub = pubKeyFor("0000000000000000000000000000000000000000000000000000000000000002")
        val ownPub = pubKeyFor("0000000000000000000000000000000000000000000000000000000000000001")

        val ct = Nip44v3.encrypt("hello".toByteArray(), priv, peerPub, kind = 1, scope = "")
        val decoded = kotlin.io.encoding.Base64.decode(ct)
        // Flip a bit in the MAC region (bytes 33..65)
        decoded[40] = (decoded[40].toInt() xor 0x01).toByte()
        val tampered = kotlin.io.encoding.Base64.encode(decoded)
        val ex = assertThrows(Nip44v3.Nip44v3Exception::class.java) {
            Nip44v3.decrypt(tampered, peer, ownPub, 1, "")
        }
        assertTrue(ex.message!!.contains("invalid MAC"))
    }

    @Test
    fun `kind above 65535 is supported`() {
        // The signer libs MUST support kinds above 65535 per implementing.md.
        val priv = hex("0000000000000000000000000000000000000000000000000000000000000001")
        val peer = hex("0000000000000000000000000000000000000000000000000000000000000002")
        val peerPub = pubKeyFor("0000000000000000000000000000000000000000000000000000000000000002")
        val ownPub = pubKeyFor("0000000000000000000000000000000000000000000000000000000000000001")
        val ct = Nip44v3.encrypt("ok".toByteArray(), priv, peerPub, kind = 1_000_000, scope = "")
        val plain = Nip44v3.decrypt(ct, peer, ownPub, expectedKind = 1_000_000, expectedScope = "")
        assertArrayEquals("ok".toByteArray(), plain)
        assertFalse(plain.isEmpty())
    }
}
