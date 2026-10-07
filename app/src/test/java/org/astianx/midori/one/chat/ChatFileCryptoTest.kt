package org.astianx.midori.one.chat

import org.junit.Assert.*
import org.junit.Test

class ChatFileCryptoTest {
    private fun file(encrypted: ChatFileCrypto.Encrypted, length: Int) = ChatAttachment("https://example.org/upload/random.bin",
        encrypted.key, encrypted.iv, "report.pdf", "application/pdf", length.toLong(), encrypted.sha256)
    @Test fun roundTripHidesPlaintextAndUsesDifferentKeysForEachFile() {
        val plain = "private document for a group".toByteArray()
        val first = ChatFileCrypto.encrypt(plain); val second = ChatFileCrypto.encrypt(plain)
        assertNotEquals(first.key, second.key); assertNotEquals(first.iv, second.iv)
        assertFalse(String(first.bytes).contains("private document"))
        assertArrayEquals(plain, ChatFileCrypto.decrypt(first.bytes, file(first, plain.size)))
    }
    @Test fun tamperingWrongKeyAndTruncationAreRejected() {
        val plain = ByteArray(1234) { it.toByte() }; val encrypted = ChatFileCrypto.encrypt(plain)
        val attachment = file(encrypted, plain.size)
        val changed = encrypted.bytes.clone().apply { this[100] = (this[100].toInt() xor 1).toByte() }
        assertThrows(Exception::class.java) { ChatFileCrypto.decrypt(changed, attachment) }
        assertThrows(Exception::class.java) { ChatFileCrypto.decrypt(encrypted.bytes, attachment.copy(key = "0".repeat(64))) }
        assertThrows(Exception::class.java) { ChatFileCrypto.decrypt(encrypted.bytes.copyOf(20), attachment) }
        assertThrows(Exception::class.java) { ChatFileCrypto.decrypt(encrypted.bytes, attachment.copy(sha256 = "0".repeat(64))) }
    }
    @Test fun rejectsEmptyAndOversizedFiles() {
        assertThrows(IllegalArgumentException::class.java) { ChatFileCrypto.encrypt(byteArrayOf()) }
        assertThrows(IllegalArgumentException::class.java) { ChatFileCrypto.encrypt(ByteArray((ChatFileCrypto.MAX_BYTES + 1).toInt())) }
    }
    @Test fun contentEnvelopePreservesMetadataAndDoesNotInterpretOrdinaryText() {
        val plain = "private".toByteArray(); val encrypted = ChatFileCrypto.encrypt(plain)
        val original = ChatMessage("id", "bob@example.org", "caption", true, 1, attachment = file(encrypted, plain.size))
        val encoded = ChatPayload.encode(original)
        assertEquals(original, ChatPayload.decode(original.copy(body = encoded, attachment = null)))
        val text = original.copy(body = "geo:1,2 and https://example.org", attachment = null)
        assertEquals(text, ChatPayload.decode(text))
    }
    @Test fun rejectsUnsafeMetadataAndCoordinates() {
        val encrypted = ChatFileCrypto.encrypt("private".toByteArray()); val attachment = file(encrypted, 7)
        listOf("http://example.org/file", "https://user:pass@example.org/file", "file:///etc/hosts").forEach { url ->
            assertThrows(Exception::class.java) { ChatPayload.readAttachment(ChatPayload.attachmentJson(attachment.copy(url = url))) }
        }
        assertEquals("report.pdf", ChatPayload.safeName("../../report.pdf"))
        listOf("geo:NaN,1", "geo:91,0", "geo:0,181", "geo:1,2?q=secret").forEach {
            assertThrows(Exception::class.java) { ChatPayload.validateLocation(it) }
        }
        ChatPayload.validateLocation("geo:4.6097,-74.0817")
    }
}
