package org.astianx.midori.one.chat

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Collections
import java.util.UUID
import javax.xml.namespace.QName
import org.astianx.midori.one.UiText
import org.jivesoftware.smack.android.AndroidSmackInitializer
import org.jivesoftware.smack.filter.StanzaTypeFilter
import org.jivesoftware.smack.packet.Message
import org.jivesoftware.smack.tcp.XMPPTCPConnection
import org.jivesoftware.smackx.omemo.element.OmemoElement
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatMediaGroupIntegrationTest {
    private class Records : ChatRecords {
        private val data = mutableMapOf<String, String>()
        @Synchronized override fun get(key: String) = data[key]
        @Synchronized override fun put(key: String, value: String?) { if (value == null) data.remove(key) else data[key] = value }
        @Synchronized override fun keys(prefix: String) = data.keys.filter { it.startsWith(prefix) }.toSet()
    }
    private class Events : ChatEvents {
        val messages = Collections.synchronizedList(mutableListOf<ChatMessage>())
        val requests = Collections.synchronizedSet(mutableSetOf<String>())
        val invitations = Collections.synchronizedList(mutableListOf<GroupInvitation>())
        val groups = Collections.synchronizedList(mutableListOf<ChatGroup>())
        val errors = Collections.synchronizedList(mutableListOf<UiText>())
        override fun connection(status: ChatConnection) {}
        override fun contacts(contacts: List<ChatContact>) {}
        override fun message(message: ChatMessage, historical: Boolean) { messages.add(message) }
        override fun receipt(peer: String, id: String, status: MessageDelivery) {}
        override fun fingerprint(value: String) {}
        override fun blocked(addresses: Set<String>) {}
        override fun subscriptionRequested(peer: String) { requests.add(peer) }
        override fun group(group: ChatGroup) { groups.add(group) }
        override fun invited(invitation: GroupInvitation) { invitations.add(invitation) }
        override fun failure(error: UiText) { errors.add(error) }
    }
    private fun await(check: () -> Boolean) {
        val end = System.nanoTime() + 35_000_000_000L
        while (!check() && System.nanoTime() < end) Thread.sleep(100)
        assertTrue("Messaging condition timed out", check())
    }
    @Test fun encryptedFilesPrivateGroupsOfflineMembersAndRemoval() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixture = File(context.filesDir, "chat-integration-accounts.json")
        assumeTrue(fixture.exists())
        val config = JSONObject(fixture.readText())
        assumeTrue(config.has("third"))
        AndroidSmackInitializer.initialize(context)
        val records = Records()
        val accounts = listOf("first", "second", "third").map { ChatAccount(config.getString(it), config.getString("password"), "10.0.2.2") }
        val events = List(3) { Events() }
        val wire = Collections.synchronizedList(mutableListOf<Message>())
        val clients = List(3) { index -> SmackMessenger(records, config.getString("pin")) { c ->
            XMPPTCPConnection(c).apply { if (index == 0) addStanzaSendingListener({ if (it is Message) wire.add(it) }, StanzaTypeFilter.MESSAGE) }
        } }
        var room: String? = null
        val paths = mutableListOf<File>()
        val uploads = mutableListOf<String>()
        try {
            clients.indices.forEach { clients[it].connect(accounts[it], events[it]) }
            clients.indices.forEach { i -> clients.indices.filter { it != i }.forEach { j -> clients[i].addContact(accounts[j].address) } }
            clients.indices.forEach { i -> clients.indices.filter { it != i }.forEach { j ->
                await { accounts[j].address in events[i].requests }; clients[i].answerSubscription(accounts[j].address, true)
            } }
            clients.indices.forEach { i -> clients.indices.filter { it != i }.forEach { j -> clients[i].devices(accounts[j].address).forEach { clients[i].trust(it, true) } } }
            val group = clients[0].createGroup("Private integration group", accounts.drop(1).map { it.address }.toSet())
            room = group.address
            assertEquals(3, group.members.size)
            for (i in 1..2) {
                await { events[i].invitations.any { it.room == room } }
                assertEquals(group.members, clients[i].joinGroup(group.address).members)
            }
            // An unapproved recipient must block the complete message, never omit that member.
            val device = clients[0].devices(accounts[2].address).first { it.address == accounts[2].address }
            clients[0].trust(device, false)
            val blockedId = UUID.randomUUID().toString()
            assertThrows(Exception::class.java) { clients[0].send(group.address, "Must not be emitted", blockedId) }
            assertTrue(wire.none { it.stanzaId == blockedId })
            clients[0].trust(device, true)
            val groupId = UUID.randomUUID().toString()
            clients[0].send(group.address, "Private group text", groupId)
            for (i in 1..2) await { events[i].messages.any { it.id == groupId && it.body == "Private group text" && it.sender == accounts[0].address } }
            assertNull(wire.first { it.stanzaId == groupId }.body)
            // Files go through the real upload service and are decrypted only after authentication.
            val media = ChatMedia(context, config.getString("pin"))
            val original = ByteArray(32_777) { (it % 251).toByte() }
            val source = ChatPlatform.captureFile(context, "bin").also { it.writeBytes(original); paths.add(it) }
            val selection = media.describe(android.net.Uri.fromFile(source))
            val file = media.upload(selection, clients[0]) {}.also { uploads.add(it.url) }
            val message = ChatMessage(UUID.randomUUID().toString(), group.address, "Encrypted file", true, 1L, attachment = file)
            clients[0].send(group.address, ChatPayload.encode(message), message.id)
            await { events[1].messages.any { it.id == message.id && it.attachment != null } }
            val received = events[1].messages.first { it.id == message.id }.attachment!!
            val downloaded = media.download(received, clients[1]) {}.also { paths.add(it) }
            assertArrayEquals(original, downloaded.readBytes())
            val preview = media.previewCopy(selection).also { paths.add(it) }
            assertArrayEquals(original, preview.readBytes())
            assertThrows(Exception::class.java) { ChatMedia(context, "0".repeat(64)).download(received, clients[1]) {} }
            assertThrows(ChatException::class.java) { ChatMedia.limitedRead(java.io.ByteArrayInputStream(original), 1_000) }
            val stanza = wire.first { it.stanzaId == message.id }.toXML().toString()
            assertFalse(stanza.contains(file.key)); assertFalse(stanza.contains(file.url)); assertFalse(stanza.contains(file.name))
            assertThrows(ChatException::class.java) { media.download(received.copy(sha256 = "0".repeat(64)), clients[1]) {} }
            assertThrows(ChatException::class.java) { clients[1].validateMediaUrl("http://localhost:5280/admin/") }
            assertThrows(ChatException::class.java) { clients[1].validateMediaUrl("https://127.0.0.1/private") }
            // Contacts and coordinates travel through the same encrypted envelope.
            listOf(message.copy(id = UUID.randomUUID().toString(), body = "", attachment = null, sharedContact = accounts[2].address),
                message.copy(id = UUID.randomUUID().toString(), body = "", attachment = null, location = "geo:4.6097,-74.0817")).forEach { card ->
                clients[0].send(group.address, ChatPayload.encode(card), card.id)
                await { events[1].messages.any { it.id == card.id && it.sharedContact == card.sharedContact && it.location == card.location } }
            }
            // Offline members are part of encryption recipients, then recover through room MAM.
            clients[1].disconnect()
            val offlineId = UUID.randomUUID().toString()
            clients[0].send(group.address, "Offline group message", offlineId)
            clients[1].connect(accounts[1], events[1])
            await { events[1].messages.any { it.id == offlineId && it.body == "Offline group message" } }
            // Revoked members cannot rejoin, and their device key is absent from future messages.
            val removedDevice = clients[0].devices(accounts[2].address).first { it.address == accounts[2].address }.id
            clients[0].removeGroupMember(group.address, accounts[2].address)
            assertFalse(clients[0].groupInfo(group.address).members.contains(accounts[2].address))
            val finalId = UUID.randomUUID().toString()
            clients[0].send(group.address, "After removal", finalId)
            await { events[1].messages.any { it.id == finalId } }
            val encrypted = wire.first { it.stanzaId == finalId }.getExtension(QName(SmackMessenger.OMEMO_NAMESPACE, "encrypted")) as OmemoElement
            assertTrue(encrypted.header.keys.none { it.id == removedDevice })
            assertThrows(Exception::class.java) { clients[2].joinGroup(group.address) }
            assertTrue(events[2].messages.none { it.id == finalId })
            clients[1].leaveGroup(group.address)
            assertFalse(events[1].groups.last().joined)
            val afterLeaving = events[1].groups.size
            clients[1].disconnect(); clients[1].connect(accounts[1], events[1])
            assertEquals(afterLeaving, events[1].groups.size) // No automatic rejoin after leaving.
            clients[0].removeGroupMember(group.address, accounts[1].address)
            assertFalse(clients[0].groupInfo(group.address).members.contains(accounts[1].address))
            clients[0].deleteGroup(group.address)
            room = null
        } finally {
            room?.let { runCatching { clients[0].deleteGroup(it) } }
            clients.forEach { it.disconnect() }
            paths.forEach { it.delete() }
            File(context.filesDir, "chat-integration-uploads.json").writeText(JSONArray(uploads).toString())
        }
    }
}
