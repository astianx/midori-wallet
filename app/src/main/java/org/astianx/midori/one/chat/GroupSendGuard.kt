package org.astianx.midori.one.chat

import org.astianx.midori.one.R
import org.astianx.midori.one.UiText

internal data class GroupDevice(val address: String, val id: Int)
internal data class GroupRecipients(val members: Set<String>, val devices: Set<GroupDevice>)

/** Checks complete affiliation/device snapshots, never a visible page or online-only roster. */
internal object GroupSendGuard {
    fun validate(snapshot: GroupRecipients, self: String) {
        if (self !in snapshot.members) throw ChatException(UiText(R.string.chat_group_unsafe))
        if (snapshot.members.size > GroupPolicy.MAX_ENCRYPTED_MEMBERS)
            throw ChatException(UiText(R.string.chat_group_capacity, listOf(GroupPolicy.MAX_ENCRYPTED_MEMBERS)))
        val peers = snapshot.members - self
        if (peers.isEmpty()) throw ChatException(UiText(R.string.chat_group_no_members))
        if (snapshot.devices.size > GroupPolicy.MAX_ENCRYPTED_DEVICES)
            throw ChatException(UiText(R.string.chat_group_device_capacity, listOf(GroupPolicy.MAX_ENCRYPTED_DEVICES)))
        if (snapshot.devices.any { it.id <= 0 || it.address !in snapshot.members } ||
            peers.any { peer -> snapshot.devices.none { it.address == peer } })
            throw ChatException(UiText(R.string.chat_no_secure_device))
    }
    fun verifyComplete(expected: GroupRecipients, intended: Set<GroupDevice>, missing: Boolean) {
        if (missing || expected.devices != intended) throw ChatException(UiText(R.string.chat_encryption_error))
    }
    fun verifyUnchanged(before: GroupRecipients, after: GroupRecipients) {
        if (before != after) throw ChatException(UiText(R.string.chat_group_changed))
    }
}
