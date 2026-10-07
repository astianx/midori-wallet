package org.astianx.midori.one

import org.junit.Assert.*
import org.junit.Test
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.KeyGenerator

class WalletSecretCipherTest {
    private fun key() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @Test
    fun authenticatedEnvelopeRoundTripsWithFreshRandomIvAndNoPlaintextSecrets() {
        val cipher = WalletSecretCipher()
        val key = key()
        val credentials = WalletCredentials("test-encryption-key", "test-encrypted-seed")
        val first = cipher.encrypt(credentials, key)
        val second = cipher.encrypt(credentials, key)
        assertNotEquals(first, second)
        assertFalse(first.contains(credentials.key))
        assertFalse(first.contains(credentials.seed))
        assertEquals(credentials, cipher.decrypt(first, key))
        assertEquals(credentials, cipher.decrypt(second, key))
    }

    @Test
    fun tamperingAndWrongKeyFailAuthentication() {
        val cipher = WalletSecretCipher()
        val key = key()
        val encrypted = cipher.encrypt(WalletCredentials("test-key", "test-seed"), key)
        val parts = encrypted.split(':').toMutableList()
        val bytes = Base64.getDecoder().decode(parts[2])
        bytes[0] = (bytes[0].toInt() xor 1).toByte()
        parts[2] = Base64.getEncoder().encodeToString(bytes)
        assertThrows(AEADBadTagException::class.java) { cipher.decrypt(parts.joinToString(":"), key) }
        assertThrows(AEADBadTagException::class.java) { cipher.decrypt(encrypted, key()) }
        assertThrows(IllegalArgumentException::class.java) { cipher.decrypt(encrypted.replaceFirst("1:", "2:"), key) }
    }
}
