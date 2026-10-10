package org.astianx.midori.one.wallet.p2p

import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.asn1.sec.SECNamedCurves
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.params.ECDomainParameters
import org.bouncycastle.crypto.params.ECPrivateKeyParameters
import org.bouncycastle.crypto.signers.ECDSASigner
import org.bouncycastle.crypto.signers.HMacDSAKCalculator
import org.bouncycastle.math.ec.ECAlgorithms
import org.json.JSONObject

private fun BigInteger.fixed32() = toString(16).padStart(64, '0').let(::p2pBytes)

/** Implements the pinned SDK's eth-crypto wire format, not a new encryption protocol.
 * ECIES secp256k1 / SHA-512 KDF / AES-256-CBC / HMAC-SHA256; authenticate before decryption.
 * The SDK README says GCM but its pinned implementation uses CBC + HMAC.
 */
internal class P2pEcies(private val random: SecureRandom = SecureRandom()) : P2pRelayCrypto {
    private val curve = SECNamedCurves.getByName("secp256k1")
    private val domain = ECDomainParameters(curve.curve, curve.g, curve.n, curve.h)
    override fun createSecret(): String {
        while (true) {
            val bytes = ByteArray(32).also(random::nextBytes)
            val scalar = BigInteger(1, bytes)
            if (scalar.signum() > 0 && scalar < curve.n) return bytes.p2pHex().also { bytes.fill(0) }
            bytes.fill(0)
        }
    }
    private fun scalar(secret: String): BigInteger {
        require(secret.matches(Regex("[a-f0-9]{64}")))
        return BigInteger(secret, 16).also { require(it.signum() > 0 && it < curve.n) }
    }
    override fun publicKey(secret: String) = curve.g.multiply(scalar(secret)).normalize().getEncoded(false).drop(1).toByteArray().p2pHex()
    private fun signature(secret: String, message: String): String {
        val d = scalar(secret); val hash = p2pKeccak(message.toByteArray(Charsets.UTF_8))
        val signer = ECDSASigner(HMacDSAKCalculator(SHA256Digest()))
        signer.init(true, ECPrivateKeyParameters(d, domain))
        val pair = signer.generateSignature(hash)
        val r = pair[0]; val s = if (pair[1] > curve.n.shiftRight(1)) curve.n - pair[1] else pair[1]
        val expected = curve.g.multiply(d).normalize()
        val e = BigInteger(1, hash); val inverse = r.modInverse(curve.n)
        val recovery = (0..1).firstOrNull { parity ->
            val point = curve.curve.decodePoint(byteArrayOf((2 + parity).toByte()) + r.fixed32())
            ECAlgorithms.sumOfTwoMultiplies(curve.g, e.negate().multiply(inverse).mod(curve.n),
                point, s.multiply(inverse).mod(curve.n)).normalize() == expected
        } ?: error("Signature recovery failed")
        return "0x" + r.fixed32().p2pHex() + s.fixed32().p2pHex() + (27 + recovery).toString(16)
    }
    private fun hmac(key: ByteArray, bytes: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256")); doFinal(bytes)
    }
    override fun encrypt(secret: String, recipientPublicKey: String, message: String): String {
        require(recipientPublicKey.matches(Regex("[a-fA-F0-9]{128}")) && message.isNotBlank() && message.length <= 512)
        val recipient = curve.curve.decodePoint(p2pBytes("04$recipientPublicKey")).normalize()
        require(!recipient.isInfinity && recipient.isValid)
        val ephemeral = createSecret()
        val ephemeralPoint = curve.g.multiply(scalar(ephemeral)).normalize()
        val shared = recipient.multiply(scalar(ephemeral)).normalize().affineXCoord.toBigInteger().fixed32()
        val keys = MessageDigest.getInstance("SHA-512").digest(shared)
        val iv = ByteArray(16).also(random::nextBytes)
        val plaintext = JSONObject().put("message", message).put("signature", signature(secret, message))
            .toString().toByteArray(Charsets.UTF_8)
        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keys.copyOfRange(0, 32), "AES"), IvParameterSpec(iv))
            val ciphertext = cipher.doFinal(plaintext)
            val mac = hmac(keys.copyOfRange(32, 64), iv + ephemeralPoint.getEncoded(false) + ciphertext)
            (iv + ephemeralPoint.getEncoded(true) + mac + ciphertext).p2pHex()
        } finally { keys.fill(0); shared.fill(0); plaintext.fill(0) }
    }
    override fun decrypt(secret: String, encrypted: String): String {
        require(encrypted.length <= 20000)
        val iv: ByteArray; val pointBytes: ByteArray; val ciphertext: ByteArray; val mac: ByteArray
        val legacy = encrypted.startsWith("{")
        if (legacy) {
            val json = JSONObject(encrypted)
            iv = p2pBytes(json.getString("iv")); pointBytes = p2pBytes(json.getString("ephemPublicKey"))
            ciphertext = p2pBytes(json.getString("ciphertext")); mac = p2pBytes(json.getString("mac"))
        } else {
            val bytes = p2pBytes(encrypted)
            require(bytes.size >= 97)
            iv = bytes.copyOfRange(0, 16); pointBytes = bytes.copyOfRange(16, 49)
            mac = bytes.copyOfRange(49, 81); ciphertext = bytes.copyOfRange(81, bytes.size)
        }
        require(iv.size == 16 && mac.size == 32 && ciphertext.isNotEmpty() && ciphertext.size % 16 == 0 && pointBytes.size in setOf(33, 65))
        val point = curve.curve.decodePoint(pointBytes).normalize()
        require(!point.isInfinity && point.isValid)
        val shared = point.multiply(scalar(secret)).normalize().affineXCoord.toBigInteger().fixed32()
        val keys = MessageDigest.getInstance("SHA-512").digest(shared)
        try {
            val expected = hmac(keys.copyOfRange(32, 64), iv + point.getEncoded(false) + ciphertext)
            require(MessageDigest.isEqual(expected, mac))
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keys.copyOfRange(0, 32), "AES"), IvParameterSpec(iv))
            val plaintext = cipher.doFinal(ciphertext)
            return try {
                val text = plaintext.toString(Charsets.UTF_8)
                if (legacy) text else JSONObject(text).also {
                    require(it.getString("signature").matches(Regex("0x[a-fA-F0-9]{130}")))
                }.getString("message")
            } finally { plaintext.fill(0) }
        } finally { keys.fill(0); shared.fill(0) }
    }
}
