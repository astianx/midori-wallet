package org.astianx.midori.one.chat

import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentHashMap
import javax.xml.namespace.QName
import org.astianx.midori.one.UiText
import org.jivesoftware.smack.filter.StanzaTypeFilter
import org.jivesoftware.smack.packet.Message
import org.jivesoftware.smack.tcp.XMPPTCPConnection
import org.jivesoftware.smackx.omemo.element.OmemoElement
import org.jivesoftware.smackx.omemo.OmemoManager
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*

/** Shared real-client scenario. Platform wrappers supply files, metrics and optional phone probes. */
internal class GroupCapacityScenario(
    private val config: JSONObject,
    private val files: File,
    private val host: String,
    private val log: (String) -> Unit,
    private val memoryKiB: () -> Long,
    private val memoryLabel: String = "process_pss_kib_at_end",
    private val phoneProbe: ((String, ChatAccount, Set<String>, () -> Unit, (String, String) -> Boolean, (String) -> Unit) -> JSONArray)? = null,
) {
    private class Records : ChatRecords {
        private val data = ConcurrentHashMap<String, String>()
        override fun get(key: String) = data[key]
        override fun put(key: String, value: String?) { if (value == null) data.remove(key) else data[key] = value }
        override fun keys(prefix: String) = data.keys.filter { it.startsWith(prefix) }.toSet()
    }
    private class Events : ChatEvents {
        val messages = CopyOnWriteArrayList<ChatMessage>()
        val requests = ConcurrentHashMap.newKeySet<String>()
        val groups = CopyOnWriteArrayList<ChatGroup>()
        override fun connection(status: ChatConnection) {}
        override fun contacts(contacts: List<ChatContact>) {}
        override fun message(message: ChatMessage, historical: Boolean) { messages.add(message) }
        override fun receipt(peer: String, id: String, status: MessageDelivery) {}
        override fun fingerprint(value: String) {}
        override fun blocked(addresses: Set<String>) {}
        override fun subscriptionRequested(peer: String) { requests.add(peer) }
        override fun group(group: ChatGroup) { groups.add(group) }
        override fun failure(error: UiText) {}
    }
    private fun await(label: String, condition: () -> Boolean) {
        val end = System.nanoTime() + 45_000_000_000L
        while (!condition() && System.nanoTime() < end) Thread.sleep(50)
        assertTrue(label, condition())
    }
    private fun Message.encrypted() = getExtension(QName(SmackMessenger.OMEMO_NAMESPACE, "encrypted")) as OmemoElement
    private fun List<Message>.sent(id: String): Message {
        await("outbound encrypted stanza") { any { it.stanzaId == id } }
        return first { it.stanzaId == id }
    }

    fun run() {
        val users = config.getJSONArray("accounts")
        assertEquals(GroupPolicy.MAX_ENCRYPTED_MEMBERS + 1, users.length())
        val accounts = (0 until users.length()).map { ChatAccount(users.getString(it), config.getString("password"), host) }
        assertTrue(accounts.all { it.address.startsWith("midori-group-it-") && it.address.endsWith("@localhost") })
        val records = Records()
        val count = GroupPolicy.MAX_ENCRYPTED_MEMBERS
        val events = List(count) { Events() }
        val wire = List(count) { CopyOnWriteArrayList<Message>() }
        val clients = List(count) { index -> SmackMessenger(records, config.getString("pin")) { c ->
            XMPPTCPConnection(c).apply {
                addStanzaSendingListener({ if (it is Message) wire[index].add(it) }, StanzaTypeFilter.MESSAGE)
            }
        } }
        val checks = JSONArray()
        fun checked(label: String) { checks.put(label); log(label) }
        val durations = mutableListOf<Double>()
        var room: String? = null
        var companion: SmackMessenger? = null
        try {
            clients.indices.forEach {
                clients[it].connect(accounts[it], events[it])
                if ((it + 1) % 25 == 0) log("clients_connected=${it + 1}")
            }
            for (i in 1 until count) {
                clients[0].addContact(accounts[i].address)
                clients[i].addContact(accounts[0].address)
                await("owner subscription") { accounts[i].address in events[0].requests }
                await("member subscription") { accounts[0].address in events[i].requests }
                clients[0].answerSubscription(accounts[i].address, true)
                clients[i].answerSubscription(accounts[0].address, true)
                clients[0].devices(accounts[i].address).forEach { clients[0].trust(it, true) }
                clients[i].devices(accounts[0].address).forEach { clients[i].trust(it, true) }
                if (i % 25 == 0) log("members_trusted=$i")
            }
            assertThrows(ChatException::class.java) { clients[0].createGroup("Over capacity", accounts.drop(1).map { it.address }.toSet()) }
            checked("create_over_capacity_rejected")
            val group = clients[0].createGroup("Capacity acceptance", accounts.subList(1, count).map { it.address }.toSet())
            room = group.address
            // Written immediately so host cleanup can destroy exactly this synthetic room after a crash.
            File(files, "group-capacity-room.txt").writeText(group.address)
            assertEquals(count, group.members.size)
            for (i in 1 until count) {
                assertEquals(group.members, clients[i].joinGroup(group.address).members)
                if (i % 25 == 0) log("members_joined=$i")
            }
            assertThrows(ChatException::class.java) { clients[0].inviteToGroup(group.address, accounts.last().address) }
            clients[0].inviteToGroup(group.address, accounts[1].address) // Existing invitation remains idempotent at capacity.
            assertEquals(group.members, clients[0].groupInfo(group.address).members)
            checked("invite_over_capacity_rejected_existing_member_idempotent")
            assertThrows(ChatException::class.java) { clients[1].removeGroupMember(group.address, accounts[2].address) }
            assertThrows(ChatException::class.java) { clients[0].leaveGroup(group.address) }
            checked("permissions_enforced")
            val device = clients[0].devices(accounts[count - 1].address).first { it.address == accounts[count - 1].address }
            clients[0].trust(device, false)
            val blocked = UUID.randomUUID().toString()
            assertThrows(Exception::class.java) { clients[0].send(group.address, "untrusted", blocked) }
            assertTrue(wire[0].none { it.stanzaId == blocked })
            clients[0].trust(device, true)
            checked("untrusted_member_blocks_entire_send")
            var largestStanza = 0
            repeat(10) { sequence ->
                val id = UUID.randomUUID().toString()
                val body = "Synthetic group acceptance $sequence"
                val start = System.nanoTime()
                clients[0].send(group.address, body, id)
                await("all ${count - 1} recipients decrypt $sequence") { events.drop(1).all { e -> e.messages.any { it.id == id && it.body == body } } }
                durations.add((System.nanoTime() - start) / 1_000_000.0)
                log("all_recipients_decrypted=$sequence delivery_ms=${durations.last()}")
                val stanza = wire[0].sent(id)
                assertEquals(Message.Type.groupchat, stanza.type)
                assertNull(stanza.body)
                assertEquals(count - 1, stanza.encrypted().header.keys.size)
                assertFalse(stanza.toXML().toString().contains(body))
                largestStanza = maxOf(largestStanza, stanza.toXML().toString().toByteArray().size)
            }
            checked("full_capacity_10_messages_all_decrypted_no_plaintext")
            // A second real device on the owner's account must also be explicitly trusted and included.
            val companionEvents = Events()
            val ownerDeviceId = wire[0].first { it.type == Message.Type.groupchat }.encrypted().header.sid
            val companionDeviceId = if (ownerDeviceId == Int.MAX_VALUE) Int.MAX_VALUE - 1 else Int.MAX_VALUE
            val secondDevice = SmackMessenger(records, config.getString("pin")) { c ->
                XMPPTCPConnection(c).apply { OmemoManager.getInstanceFor(this, companionDeviceId) }
            }
            companion = secondDevice
            secondDevice.connect(accounts[0], companionEvents)
            val ownOtherDevice = clients[0].devices(accounts[0].address).single { it.id == companionDeviceId }
            val unknownOwn = UUID.randomUUID().toString()
            assertThrows(Exception::class.java) { clients[0].send(group.address, "unknown own device", unknownOwn) }
            assertTrue(wire[0].none { it.stanzaId == unknownOwn })
            clients[0].trust(ownOtherDevice, true)
            val ownId = UUID.randomUUID().toString()
            clients[0].send(group.address, "message for both own devices", ownId)
            await("companion device decrypts") { companionEvents.messages.any { it.id == ownId && it.body == "message for both own devices" } }
            assertEquals(count, wire[0].sent(ownId).encrypted().header.keys.size)
            secondDevice.disconnect()
            checked("second_own_device_requires_trust_and_decrypts")
            clients[1].disconnect()
            val offline = UUID.randomUUID().toString()
            clients[0].send(group.address, "offline synthetic payload", offline)
            assertEquals(count, wire[0].sent(offline).encrypted().header.keys.size)
            clients[1].connect(accounts[1], events[1])
            await("offline member recovers MAM") { events[1].messages.any { it.id == offline && it.body == "offline synthetic payload" } }
            checked("offline_member_included_and_archive_recovered")
            clients[0].removeGroupMember(group.address, accounts[count - 1].address)
            await("removed member leaves") { events.last().groups.any { it.address == group.address && !it.joined } }
            val revokedSend = UUID.randomUUID().toString()
            assertThrows(ChatException::class.java) { clients.last().send(group.address, "removed sender", revokedSend) }
            assertTrue(wire.last().none { it.stanzaId == revokedSend })
            val afterRemoval = UUID.randomUUID().toString()
            clients[0].send(group.address, "new message after revocation", afterRemoval)
            await("remaining member receives") { events[1].messages.any { it.id == afterRemoval } }
            assertTrue(wire[0].sent(afterRemoval).encrypted().header.keys.none { it.id == device.id })
            assertThrows(Exception::class.java) { clients.last().joinGroup(group.address) }
            assertTrue(events.last().messages.none { it.id == afterRemoval })
            checked("revoked_member_excluded_cannot_send_or_rejoin")
            clients[1].leaveGroup(group.address)
            clients[1].disconnect()
            clients[1].connect(accounts[1], events[1])
            val leftSend = UUID.randomUUID().toString()
            assertThrows(ChatException::class.java) { clients[1].send(group.address, "left sender", leftSend) }
            assertThrows(ChatException::class.java) { clients[1].devices(group.address) }
            assertTrue(wire[1].none { it.stanzaId == leftSend })
            checked("left_room_stays_group_after_reconnect_no_direct_fallback")
            // A valid member without any published OMEMO device must block everyone, including online members.
            clients[0].inviteToGroup(group.address, accounts.last().address)
            val missingDevice = UUID.randomUUID().toString()
            assertThrows(Exception::class.java) { clients[0].send(group.address, "no secure device", missingDevice) }
            assertTrue(wire[0].none { it.stanzaId == missingDevice })
            checked("offline_member_without_device_blocks_entire_send")
            val phones = if (phoneProbe == null) JSONArray() else {
                clients[0].removeGroupMember(group.address, accounts.last().address)
                clients[0].inviteToGroup(group.address, accounts[count - 1].address)
                clients.last().joinGroup(group.address)
                clients[1].joinGroup(group.address)
                val full = clients[0].groupInfo(group.address)
                assertEquals(count, full.members.size)
                phoneProbe.invoke(group.address, accounts[0], full.members,
                    { clients.forEach { client -> client.devices(accounts[0].address).forEach { client.trust(it, true) } } },
                    { id, body -> events.all { e -> e.messages.any { it.id == id && it.body == body } } },
                    { id -> clients[0].send(group.address, "Synthetic Android reply", id) })
            }
            clients[0].deleteGroup(group.address)
            room = null
            val sorted = durations.sorted()
            fun percentile(p: Double) = sorted[(kotlin.math.ceil(p * sorted.size).toInt() - 1).coerceAtLeast(0)]
            // Keep the same 15 s budget, including receipt by the slowest member at full capacity.
            assertTrue("p95 delivery exceeds laboratory 15 s budget", percentile(.95) < 15_000)
            File(files, "group-capacity-report.json").writeText(JSONObject()
                .put("status", "passed").put("members", count).put("clients", count)
                .put("checks", checks).put("android_probes", phones).put("samples", durations.size)
                .put("delivery_p50_ms", percentile(.5)).put("delivery_p95_ms", percentile(.95)).put("delivery_p99_ms", percentile(.99))
                .put("largest_stanza_bytes", largestStanza).put(memoryLabel, memoryKiB())
                .toString(2))
        } finally {
            room?.let { runCatching { clients[0].deleteGroup(it) } }
            companion?.let { runCatching { it.disconnect() } }
            clients.forEach { runCatching { it.disconnect() } }
        }
    }
}
