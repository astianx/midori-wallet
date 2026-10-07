package org.astianx.midori.one.chat

import org.junit.Assert.*
import org.junit.Test

class GroupSecurityTest {
    private val self = "owner@example.org"
    private val peer = "member@example.org"
    private val snapshot = GroupRecipients(setOf(self, peer), setOf(GroupDevice(peer, 1), GroupDevice(peer, 2), GroupDevice(self, 3)))

    @Test fun everyDeviceIncludingOtherOwnDevicesIsRequired() {
        GroupSendGuard.validate(snapshot, self)
        GroupSendGuard.verifyComplete(snapshot, snapshot.devices, false)
        snapshot.devices.forEach { omitted ->
            assertThrows(ChatException::class.java) { GroupSendGuard.verifyComplete(snapshot, snapshot.devices - omitted, false) }
        }
        assertThrows(ChatException::class.java) { GroupSendGuard.verifyComplete(snapshot, snapshot.devices, true) }
        assertThrows(ChatException::class.java) { GroupSendGuard.verifyComplete(snapshot, snapshot.devices + GroupDevice("outsider@example.org", 4), false) }
    }

    @Test fun membershipAndDeviceChangesInvalidateTheWholeMessage() {
        GroupSendGuard.verifyUnchanged(snapshot, snapshot.copy())
        listOf(snapshot.copy(members = snapshot.members - peer),
            snapshot.copy(members = snapshot.members + "offline@example.org"),
            snapshot.copy(devices = snapshot.devices - GroupDevice(peer, 1)),
            snapshot.copy(devices = snapshot.devices + GroupDevice(peer, 4))).forEach { changed ->
            assertThrows(ChatException::class.java) { GroupSendGuard.verifyUnchanged(snapshot, changed) }
        }
    }

    @Test fun missingDevicesInvalidIdentitiesAndRemovedSenderFailClosed() {
        listOf(snapshot.copy(members = snapshot.members - self),
            snapshot.copy(members = snapshot.members + "offline@example.org"),
            snapshot.copy(devices = setOf(GroupDevice(peer, 0))),
            snapshot.copy(devices = snapshot.devices + GroupDevice("outsider@example.org", 4)),
            GroupRecipients(setOf(self), emptySet())).forEach { invalid ->
            assertThrows(ChatException::class.java) { GroupSendGuard.validate(invalid, self) }
        }
    }

    @Test fun capacityIncludesOfflineMembersAndHasAnIndependentDeviceBudget() {
        val members = (1 until GroupPolicy.MAX_ENCRYPTED_MEMBERS).map { "member$it@example.org" }.toSet() + self
        val devices = members.flatMap { member -> (1..5).map { GroupDevice(member, it) } }.toSet()
        val full = GroupRecipients(members, devices)
        GroupSendGuard.validate(full, self)
        assertThrows(ChatException::class.java) { GroupSendGuard.validate(full.copy(members = members + peer), self) }
        assertThrows(ChatException::class.java) { GroupSendGuard.validate(full.copy(devices = devices + GroupDevice(self, 6)), self) }
    }

    @Test fun parallelDeviceDiscoveryNeverReturnsPartialResultsOnFailure() {
        val members = (1..200).map { "member$it@example.org" }.toSet()
        val reads = java.util.concurrent.atomic.AtomicInteger()
        val result = GroupDeviceDirectory.fetch(members) { reads.incrementAndGet(); setOf(1, 2) }
        assertEquals(members, result.keys)
        assertEquals(200, reads.get())
        assertTrue(result.values.all { it == setOf(1, 2) })
        val failure = java.io.IOException("Synthetic discovery failure")
        val thrown = assertThrows(java.io.IOException::class.java) {
            GroupDeviceDirectory.fetch(members) { throw failure }
        }
        assertSame(failure, thrown)
    }

    private class Records : ChatRecords {
        val data = mutableMapOf<String, String>()
        override fun get(key: String) = data[key]
        override fun put(key: String, value: String?) { if (value == null) data.remove(key) else data[key] = value }
        override fun keys(prefix: String) = data.keys.filter { it.startsWith(prefix) }.toSet()
    }

    @Test fun groupClassificationPersistsAcrossReconnectAndIsAccountScoped() {
        val records = Records()
        val room = "group@conference.example.org"
        val first = GroupProtocolRegistry(records, self)
        assertFalse(first.knows(room))
        first.pinLegacy(room)
        assertTrue(GroupProtocolRegistry(records, self).knows(room))
        assertFalse(GroupProtocolRegistry(records, peer).knows(room))
        records.put("group-protocol/$self/$room", "mls-future-v1")
        val future = GroupProtocolRegistry(records, self)
        assertTrue(future.knows(room))
        assertThrows(ChatException::class.java) { future.pinLegacy(room) }
        assertThrows(ChatException::class.java) { future.requireLegacy(room) }
        assertEquals("mls-future-v1", records.get("group-protocol/$self/$room"))
    }

    @Test fun existingHistoryRecognizesLeftRoomsWithoutGuessingFromTheDomain() {
        val records = Records()
        val room = "group@chat.example.org"
        records.put("history/$self", """{"groups":[{"address":"$room","joined":false}]}""")
        assertTrue(GroupProtocolRegistry(records, self).knows(room))
        assertFalse(GroupProtocolRegistry(records, self).knows("person@chat.example.org"))
    }

    @Test fun localLeaveOrRevocationOverridesAStaleServerBookmarkAfterReconnect() {
        val records = Records()
        val room = "group@conference.example.org"
        val registry = GroupProtocolRegistry(records, self)
        registry.pinLegacy(room)
        assertTrue(registry.allowsAutojoin(room))
        registry.setAutojoin(room, false)
        val reconnected = GroupProtocolRegistry(records, self)
        assertTrue(reconnected.knows(room))
        assertFalse(reconnected.allowsAutojoin(room))
        assertTrue(GroupProtocolRegistry(records, peer).allowsAutojoin(room))
        reconnected.setAutojoin(room, true) // An explicit successful join can re-enable it.
        assertTrue(GroupProtocolRegistry(records, self).allowsAutojoin(room))
    }
}
