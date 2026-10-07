package org.astianx.midori.one.chat

import org.astianx.midori.one.R
import org.astianx.midori.one.UiText
import org.json.JSONObject

/** Group routing is independent of join state. A room never falls back to a direct-message engine. */
internal interface GroupTransport {
    fun knows(room: String): Boolean
    fun restore()
    fun create(name: String, members: Set<String>): ChatGroup
    fun join(room: String): ChatGroup
    fun info(room: String): ChatGroup
    fun recipients(room: String): Set<String>
    fun send(room: String, body: String, id: String)
    fun invite(room: String, peer: String)
    fun remove(room: String, peer: String)
    fun delete(room: String)
    fun leave(room: String)
}

/** Local protocol pin persists after leaving/deleting a room and across reconnects.
 * No MLS wire protocol is advertised until an actual engine and delivery service are accepted.
 */
internal class GroupProtocolRegistry(private val records: ChatRecords, private val account: String) {
    companion object { const val LEGACY = "omemo-legacy-v1" }
    private val prefix = "group-protocol/$account/"
    private val autojoinPrefix = "group-autojoin/$account/"
    private val legacyRooms: Set<String> = records.get("history/$account")?.let { history ->
        val groups = JSONObject(history).optJSONArray("groups")
        if (groups == null) emptySet() else (0 until groups.length()).map { groups.getJSONObject(it).getString("address") }.toSet()
    } ?: emptySet()
    fun knows(room: String) = room in legacyRooms || records.get(prefix + room) != null
    fun requireLegacy(room: String) {
        val saved = records.get(prefix + room)
        if (saved != null && saved != LEGACY) throw ChatException(UiText(R.string.chat_group_protocol_unsupported))
    }
    fun pinLegacy(room: String) {
        requireLegacy(room)
        if (records.get(prefix + room) == null) records.put(prefix + room, LEGACY)
    }
    fun allowsAutojoin(room: String) = records.get(autojoinPrefix + room) != "disabled"
    fun setAutojoin(room: String, enabled: Boolean) {
        records.put(autojoinPrefix + room, if (enabled) null else "disabled")
    }
}
