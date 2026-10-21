/* Copyright 2026 Astian. Licensed under the Apache License, Version 2.0. */
package org.astianx.midori.one.chat

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom
import java.security.Security
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.astianx.midori.one.UiText
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.jivesoftware.smack.filter.StanzaTypeFilter
import org.jivesoftware.smack.java7.Java7SmackInitializer
import org.jivesoftware.smack.packet.Message
import org.jivesoftware.smack.tcp.XMPPTCPConnection
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** A genuine ejabberd/OMEMO peer in a separate JVM, never in the app's global OMEMO store. */
class LiveUiPeerTest {
    @Test fun exchangeWithVisiblePixelApp() {
        val filename = System.getProperty("midori.liveUi.fixture")
        assumeTrue("Explicit live UI fixture is required", filename != null)
        val fixture = File(requireNotNull(filename)).absoluteFile
        val config = JSONObject(fixture.readText())
        val directory = requireNotNull(fixture.parentFile)
        val first = config.getString("first")
        val second = config.getString("second")
        require(first.matches(Regex("midori-ui-a-[a-z0-9]+@localhost")))
        require(second.matches(Regex("midori-ui-b-[a-z0-9]+@localhost")))
        Security.insertProviderAt(BouncyCastleProvider(), 1)
        Java7SmackInitializer().initialize()
        val mediaSince = System.currentTimeMillis()
        val events = Events()
        val incoming = CopyOnWriteArrayList<Message>()
        val outgoing = CopyOnWriteArrayList<Message>()
        val records = PrivatePeerRecords(File(directory, "peer-state.enc"), config.getString("password"))
        val peer = SmackMessenger(records, config.getString("pin")) { builder ->
            XMPPTCPConnection(builder).apply {
                addAsyncStanzaListener({ if (it is Message) incoming.add(it) }, StanzaTypeFilter.MESSAGE)
                addStanzaSendingListener({ if (it is Message) outgoing.add(it) }, StanzaTypeFilter.MESSAGE)
            }
        }
        try {
            peer.connect(ChatAccount(second, config.getString("password"), "127.0.0.1"), events)
            assertTrue(events.fingerprint.isNotBlank())
            if (config.optBoolean("richChat")) {
                peer.publishProfile(PublishedChatProfile("Interlocutor local de Midori", "Cuenta de pruebas creada en ejabberd"))
                assertEquals("Interlocutor local de Midori", peer.loadProfile(second).name)
            }
            publish(File(directory, "peer-ready.json"), JSONObject().put("account", second)
                .put("fingerprint", events.fingerprint).toString())
            await("phone authenticated", 180) { File(directory, "phone-ready.json").exists() }
            val phone = JSONObject(File(directory, "phone-ready.json").readText())
            assertEquals(first, phone.getString("account"))
            peer.addContact(first)
            await("phone contact subscription", 60) { first in events.requests || first in events.online }
            peer.answerSubscription(first, true)
            await("phone presence after reciprocal subscription", 60) { first in events.online }
            val devices = peer.devices(first).filter { it.address == first }
            assertEquals("A new account must have exactly the independently inspected phone device", 1, devices.size)
            assertEquals(phone.getString("fingerprint"), devices.single().fingerprint)
            peer.trust(devices.single(), true)
            publish(File(directory, "peer-trusted"), "verified")
            await("encrypted message from actual composer", 180) {
                events.messages.any { it.peer == first && it.body == config.getString("message") }
            }
            val received = events.messages.last { it.peer == first && it.body == config.getString("message") }
            assertEquals(MessageProtection.ENCRYPTED, received.protection)
            await("incoming encrypted stanza", 10) { incoming.any { it.stanzaId == received.id } }
            val wire = incoming.first { it.stanzaId == received.id }
            assertNull(wire.body)
            assertTrue(wire.hasExtension("encrypted", SmackMessenger.OMEMO_NAMESPACE))
            assertFalse(wire.toXML().toString().contains(config.getString("message")))
            peer.markRead(first, received.id)
            val replyId = UUID.randomUUID().toString()
            peer.send(first, config.getString("reply"), replyId)
            await("outgoing encrypted reply", 10) { outgoing.any { it.stanzaId == replyId } }
            val reply = outgoing.first { it.stanzaId == replyId }
            assertNull(reply.body)
            assertTrue(reply.hasExtension("encrypted", SmackMessenger.OMEMO_NAMESPACE))
            assertFalse(reply.toXML().toString().contains(config.getString("reply")))
            publish(File(directory, "peer-report.json"), JSONObject().put("status", "passed")
                .put("account", second).put("phone", first).put("incomingId", received.id)
                .put("replyId", replyId).put("incomingEncrypted", true).put("replyEncrypted", true)
                .put("isolatedPeerStorage", "AES-256-GCM encrypted host file; no app vault or wallet access").toString(2))
            if (config.optBoolean("richChat")) {
                val checked = org.json.JSONArray()
                val seen = mutableSetOf<String>()
                val deadline = System.nanoTime() + 900_000_000_000L
                while (!File(directory, "finish").exists() && System.nanoTime() < deadline) {
                    events.messages.filter { it.peer == first && !it.outgoing && it.timestamp >= mediaSince &&
                        it.attachment != null && it.id !in seen }.forEach { media ->
                        assertEquals(MessageProtection.ENCRYPTED, media.protection)
                        val file = requireNotNull(media.attachment)
                        val bytes = downloadLaboratoryMedia(file, config.getString("pin"))
                        try {
                            if (file.mime.startsWith("image/")) {
                                // Android unit-test compilation omits java.desktop from its boot classpath;
                                // the opt-in peer runs on the host JDK and decodes the actual received photo.
                                val imageIo = Class.forName("javax.imageio.ImageIO")
                                assertNotNull(imageIo.getMethod("read", java.io.InputStream::class.java).invoke(null, bytes.inputStream()))
                            } else {
                                assertTrue(bytes.size > 12)
                                assertEquals("ftyp", String(bytes, 4, 4, Charsets.US_ASCII))
                            }
                            val prefix = when {
                                file.mime.startsWith("audio/") -> "Audio"
                                file.mime.startsWith("video/") -> "Video"
                                file.mime.startsWith("image/") -> "Imagen"
                                else -> error("Unexpected laboratory media type")
                            }
                            peer.markRead(first, media.id)
                            // Keep the exact acknowledgement meaningful and bound to this message.
                            val ack = if (prefix == "Imagen") "Imagen recibida y descifrada · ${media.id}"
                                else "$prefix recibido y descifrado · ${media.id}"
                            peer.send(first, ack, UUID.randomUUID().toString())
                            checked.put(JSONObject().put("id", media.id).put("mime", file.mime)
                                .put("size", bytes.size).put("videoNote", file.videoNote).put("sha256Verified", true))
                            seen.add(media.id)
                        } finally { bytes.fill(0) }
                    }
                    Thread.sleep(100)
                }
                val report = JSONObject(File(directory, "peer-report.json").readText())
                report.put("media", checked).put("profilePublishedAndRead", true)
                publish(File(directory, "peer-report.json"), report.toString(2))
                assertTrue("Voice, video note and photo must reach and decrypt at the real peer", checked.length() >= 3)
            } else await("UI acceptance finished", 180) { File(directory, "finish").exists() }
        } finally { peer.disconnect() }
    }

    private fun downloadLaboratoryMedia(file: ChatAttachment, pin: String): ByteArray {
        val url = java.net.URL(file.url)
        require(url.protocol == "https" && url.host == "localhost" && url.port == 5443 && url.userInfo == null && url.ref == null)
        val trust = object : javax.net.ssl.X509TrustManager {
            override fun getAcceptedIssuers() = emptyArray<java.security.cert.X509Certificate>()
            override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, type: String) = error("Client certificate not expected")
            override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, type: String) {
                val certificate = chain.first(); certificate.checkValidity()
                require(ChatFileCrypto.hash(certificate.encoded).equals(pin, ignoreCase = true))
            }
        }
        val connection = url.openConnection() as javax.net.ssl.HttpsURLConnection
        connection.sslSocketFactory = javax.net.ssl.SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), SecureRandom()) }.socketFactory
        connection.connectTimeout = 15_000; connection.readTimeout = 30_000
        connection.instanceFollowRedirects = false
        try {
            assertEquals(200, connection.responseCode)
            require(file.size in 1..ChatFileCrypto.MAX_BYTES)
            val encrypted = connection.inputStream.use { it.readNBytes((file.size + 17).toInt()) }
            return ChatFileCrypto.decrypt(encrypted, file)
        } finally { connection.disconnect() }
    }

    private fun await(label: String, seconds: Int, predicate: () -> Boolean) {
        val deadline = System.nanoTime() + seconds * 1_000_000_000L
        while (!predicate() && System.nanoTime() < deadline) Thread.sleep(100)
        assertTrue("Timed out waiting for $label", predicate())
    }

    private fun publish(file: File, text: String) {
        val temporary = Files.createTempFile(requireNotNull(file.parentFile).toPath(), ".live-ui-", ".tmp",
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
        try {
            java.io.FileOutputStream(temporary.toFile()).use { out ->
                out.write(text.toByteArray(Charsets.UTF_8)); out.fd.sync()
            }
            Files.move(temporary, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temporary) }
    }

    private class Events : ChatEvents {
        val messages = CopyOnWriteArrayList<ChatMessage>()
        val requests = ConcurrentHashMap.newKeySet<String>()
        val online = ConcurrentHashMap.newKeySet<String>()
        @Volatile var fingerprint = ""
        override fun connection(status: ChatConnection) = Unit
        override fun contacts(contacts: List<ChatContact>) { online.clear(); online.addAll(contacts.filter { it.online }.map { it.address }) }
        override fun message(message: ChatMessage, historical: Boolean) { messages.add(message) }
        override fun receipt(peer: String, id: String, status: MessageDelivery) = Unit
        override fun fingerprint(value: String) { fingerprint = value }
        override fun blocked(addresses: Set<String>) = Unit
        override fun subscriptionRequested(peer: String) { requests.add(peer) }
        override fun failure(error: UiText) { throw IllegalStateException("Peer messaging failure") }
    }

    /** Laboratory persistence only: no plaintext OMEMO keys, wallets or Android stores. */
    private class PrivatePeerRecords(private val file: File, password: String) : ChatRecords {
        private val random = SecureRandom()
        private val base64 = Base64.getEncoder()
        private val decode = Base64.getDecoder()
        private val existing = if (file.exists()) JSONObject(file.readText()) else null
        private val salt = existing?.getString("salt")?.let(decode::decode) ?: ByteArray(16).also(random::nextBytes)
        private val key = PBEKeySpec(password.toCharArray(), salt, 120_000, 256).let { spec ->
            try { SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES") }
            finally { spec.clearPassword() }
        }
        private val values = mutableMapOf<String, String>()
        init {
            existing?.let { envelope ->
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, decode.decode(envelope.getString("iv"))))
                val json = JSONObject(String(cipher.doFinal(decode.decode(envelope.getString("ciphertext"))), Charsets.UTF_8))
                json.keys().forEach { values[it] = json.getString(it) }
            }
        }
        @Synchronized override fun get(key: String) = values[key]
        @Synchronized override fun keys(prefix: String) = values.keys.filter { it.startsWith(prefix) }.toSet()
        @Synchronized override fun put(key: String, value: String?) {
            val next = values.toMutableMap()
            if (value == null) next.remove(key) else next[key] = value
            val iv = ByteArray(12).also(random::nextBytes)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, this.key, GCMParameterSpec(128, iv))
            val ciphertext = cipher.doFinal(JSONObject(next as Map<*, *>).toString().toByteArray(Charsets.UTF_8))
            val envelope = JSONObject().put("salt", base64.encodeToString(salt)).put("iv", base64.encodeToString(iv))
                .put("ciphertext", base64.encodeToString(ciphertext)).toString()
            val temporary = Files.createTempFile(requireNotNull(file.parentFile).toPath(), ".peer-", ".tmp",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
            try {
                java.io.FileOutputStream(temporary.toFile()).use { out -> out.write(envelope.toByteArray(Charsets.UTF_8)); out.fd.sync() }
                Files.move(temporary, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                values.clear(); values.putAll(next)
            } finally { Files.deleteIfExists(temporary) }
        }
    }
}
