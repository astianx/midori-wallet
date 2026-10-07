package org.astianx.midori.one.chat

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal object ChatFileCrypto {
    const val MAX_BYTES = 20L * 1024 * 1024
    data class Encrypted(val bytes: ByteArray, val key: String, val iv: String, val sha256: String)
    fun encrypt(plain: ByteArray): Encrypted {
        require(plain.size.toLong() in 1..MAX_BYTES)
        val key = ByteArray(32).also(SecureRandom()::nextBytes)
        val iv = ByteArray(12).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return Encrypted(cipher.doFinal(plain), hex(key), hex(iv), hash(plain)).also { key.fill(0) }
    }
    fun decrypt(bytes: ByteArray, attachment: ChatAttachment): ByteArray {
        require(attachment.size in 1..MAX_BYTES && bytes.size.toLong() == attachment.size + 16)
        val key = unhex(attachment.key)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, unhex(attachment.iv)))
        val plain = try { cipher.doFinal(bytes) } finally { key.fill(0) }
        if (hash(plain) != attachment.sha256) { plain.fill(0); error("File integrity mismatch") }
        return plain
    }
    fun hash(bytes: ByteArray) = hex(MessageDigest.getInstance("SHA-256").digest(bytes))
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
    private fun unhex(text: String) = text.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
