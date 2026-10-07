package org.astianx.midori.one.chat

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Collections
import java.util.UUID
import org.astianx.midori.one.UiText
import org.jivesoftware.smack.android.AndroidSmackInitializer
import org.jivesoftware.smack.filter.StanzaTypeFilter
import org.jivesoftware.smack.packet.Message
import org.jivesoftware.smack.tcp.XMPPTCPConnection
import org.jivesoftware.smackx.omemo.exceptions.UndecidedOmemoIdentityException
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in: scripts/test-local-messaging.py provisions temporary accounts on the user's local server. */
@RunWith(AndroidJUnit4::class)
class ChatIntegrationTest {
    private class Records : ChatRecords {
        private val data = mutableMapOf<String, String>()
        @Synchronized override fun get(key: String) = data[key]
        @Synchronized override fun put(key: String, value: String?) { if (value == null) data.remove(key) else data[key] = value }
        @Synchronized override fun keys(prefix: String) = data.keys.filter { it.startsWith(prefix) }.toSet()
    }
    private class Events : ChatEvents {
        val messages = Collections.synchronizedList(mutableListOf<ChatMessage>())
        val receipts = Collections.synchronizedList(mutableListOf<String>())
        val read = Collections.synchronizedList(mutableListOf<String>())
        val requests = Collections.synchronizedSet(mutableSetOf<String>())
        var fingerprint = ""
        override fun connection(status: ChatConnection) {}
        override fun contacts(contacts: List<ChatContact>) {}
        override fun message(message: ChatMessage, historical: Boolean) { messages.add(message) }
        override fun receipt(peer: String, id: String, status: MessageDelivery) {
            receipts.add(id)
            if (status == MessageDelivery.READ) read.add(id)
        }
        override fun fingerprint(value: String) { fingerprint = value }
        override fun blocked(addresses: Set<String>) {}
        override fun subscriptionRequested(peer: String) { requests.add(peer) }
        override fun failure(error: UiText) { error("Unexpected messaging failure") }
    }
    private fun await(check: () -> Boolean) {
        val end = System.nanoTime() + 25_000_000_000L
        while (!check() && System.nanoTime() < end) Thread.sleep(100)
        assertTrue("Messaging condition timed out", check())
    }

    @Test fun mandatoryEncryptionRoundTripOfflineArchiveAndIdentityContinuity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configFile = File(context.filesDir, "chat-integration-accounts.json")
        assumeTrue("Local account fixture is required", configFile.exists())
        val json = JSONObject(configFile.readText())
        AndroidSmackInitializer.initialize(context)
        val records = Records()
        val one = ChatAccount(json.getString("first"), json.getString("password"), "10.0.2.2")
        val two = ChatAccount(json.getString("second"), json.getString("password"), "10.0.2.2")
        val events1 = Events()
        val events2 = Events()
        val outgoing = Collections.synchronizedList(mutableListOf<Message>())
        val a = SmackMessenger(records, json.getString("pin")) { config ->
            XMPPTCPConnection(config).apply {
                addStanzaSendingListener({ if (it is Message) outgoing.add(it) }, StanzaTypeFilter.MESSAGE)
            }
        }
        val b = SmackMessenger(records, json.getString("pin"))
        try {
            val wrongCertificate = SmackMessenger(records, "0".repeat(64))
            try { wrongCertificate.connect(one, Events()); fail("Wrong certificate was accepted") }
            catch (e: Exception) {
                assertTrue(generateSequence<Throwable>(e) { it.cause }.any { it is javax.net.ssl.SSLException })
            } finally { wrongCertificate.disconnect() }
            a.connect(one, events1)
            b.connect(two, events2)
            a.addContact(two.address)
            b.addContact(one.address)
            await { events2.requests.contains(one.address) && events1.requests.contains(two.address) }
            b.answerSubscription(one.address, true)
            a.answerSubscription(two.address, true)
            val originalFingerprint = events2.fingerprint
            assertTrue(originalFingerprint.isNotBlank())
            val id = UUID.randomUUID().toString()
            try { a.send(two.address, "must never leave in plaintext", id); fail("Unknown devices were trusted implicitly") }
            catch (_: UndecidedOmemoIdentityException) { }
            assertTrue(outgoing.none { it.stanzaId == id })
            val aDevices = a.devices(two.address)
            val bDevices = b.devices(one.address)
            assertTrue(aDevices.any { it.address == two.address && it.fingerprint == originalFingerprint })
            aDevices.forEach { a.trust(it, true) }
            bDevices.forEach { b.trust(it, true) }
            val privateText = "Private integration message ${UUID.randomUUID()}"
            a.send(two.address, privateText, id)
            await { events2.messages.any { it.id == id && it.body == privateText } }
            await { events1.receipts.contains(id) }
            val wire = outgoing.first { it.stanzaId == id }
            assertNull(wire.body)
            assertTrue(wire.hasExtension("encrypted", SmackMessenger.OMEMO_NAMESPACE))
            assertFalse(wire.toXML().toString().contains(privateText))
            assertEquals(MessageProtection.ENCRYPTED, events2.messages.first { it.id == id }.protection)
            a.devices(two.address).forEach { a.trust(it, false) }
            val rejectedId = UUID.randomUUID().toString()
            try { a.send(two.address, "blocked by trust", rejectedId); fail("Distrusted devices must block sending") }
            catch (_: Exception) { }
            assertTrue(outgoing.none { it.stanzaId == rejectedId })
            a.devices(two.address).forEach { a.trust(it, true) }
            b.disconnect()
            val offlineId = UUID.randomUUID().toString()
            a.send(two.address, "Offline encrypted delivery", offlineId)
            b.connect(two, events2)
            assertEquals(originalFingerprint, events2.fingerprint)
            await { events2.messages.any { it.id == offlineId && it.body == "Offline encrypted delivery" } }
            b.markRead(one.address, id)
            await { events1.read.contains(id) }
            a.block(two.address, true)
            a.block(two.address, false)
            a.setActive(false)
            a.setActive(true)
        } finally {
            a.disconnect()
            b.disconnect()
        }
    }
}
