package org.astianx.midori.one.chat

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class ConversationPolicyTest {
    @Test fun groupingSeparatesAuthorsDaysFailuresAndLongPauses() {
        val zone = ZoneId.of("UTC")
        val first = ChatMessage("1", "room@example.org", "a", false,
            Instant.parse("2026-10-07T23:59:00Z").toEpochMilli(), sender = "alice@example.org")
        assertTrue(first.groupsWith(first.copy(id = "2", timestamp = first.timestamp + 30_000), zone))
        assertFalse(first.groupsWith(first.copy(sender = "bob@example.org"), zone))
        assertFalse(first.groupsWith(first.copy(timestamp = first.timestamp + 60_000), zone))
        assertFalse(first.groupsWith(first.copy(timestamp = first.timestamp - 180_000), zone))
        assertFalse(first.groupsWith(first.copy(delivery = MessageDelivery.FAILED), zone))
        assertFalse(first.groupsWith(first.copy(protection = MessageProtection.REJECTED_PLAINTEXT), zone))
    }

    @Test fun currentEncryptionBudgetIncludesOwnerAndDoesNotEnableFutureTarget() {
        val members = (1 until GroupPolicy.MAX_ENCRYPTED_MEMBERS).map { "person$it@example.org" }.toSet()
        assertEquals(200, GroupPolicy.MAX_ENCRYPTED_MEMBERS)
        assertTrue(GroupPolicy.canCreate(members, "self@example.org"))
        assertFalse(GroupPolicy.canCreate(members + "overflow@example.org", "self@example.org"))
        assertFalse(GroupPolicy.canCreate(setOf("self@example.org"), "self@example.org"))
        assertTrue(GroupPolicy.canInvite(GroupPolicy.MAX_ENCRYPTED_MEMBERS - 1))
        assertFalse(GroupPolicy.canInvite(GroupPolicy.MAX_ENCRYPTED_MEMBERS))
        assertFalse(GroupPolicy.canInvite(GroupPolicy.LARGE_GROUP_TARGET))
    }
}
