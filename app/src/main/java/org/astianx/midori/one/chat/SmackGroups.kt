package org.astianx.midori.one.chat

import java.util.Date
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.xml.namespace.QName
import org.astianx.midori.one.R
import org.astianx.midori.one.UiText
import org.jivesoftware.smack.XMPPConnection
import org.jivesoftware.smack.packet.Message
import org.jivesoftware.smack.packet.Stanza
import org.jivesoftware.smack.packet.StanzaBuilder
import org.jivesoftware.smackx.bookmarks.BookmarkManager
import org.jivesoftware.smackx.delay.DelayInformationManager
import org.jivesoftware.smackx.hints.element.StoreHint
import org.jivesoftware.smackx.mam.MamManager
import org.jivesoftware.smackx.muc.MultiUserChat
import org.jivesoftware.smackx.muc.MultiUserChatManager
import org.jivesoftware.smackx.muc.packet.MUCUser
import org.jivesoftware.smackx.omemo.OmemoManager
import org.jivesoftware.smackx.omemo.OmemoMessage
import org.jivesoftware.smackx.omemo.element.OmemoElement
import org.jivesoftware.smackx.omemo.listener.OmemoMucMessageListener
import org.jxmpp.jid.impl.JidCreate
import org.jxmpp.jid.parts.Resourcepart

/** Members-only, non-anonymous rooms. A missing offline member is never silently omitted. */
internal class SmackGroups(private val connection: XMPPConnection, private val omemo: OmemoManager,
    private val account: String, private val records: ChatRecords, private val events: ChatEvents,
    private val store: EncryptedOmemoStore) : GroupTransport {
    private val protocols = GroupProtocolRegistry(records, account)
    private val manager = MultiUserChatManager.getInstanceFor(connection)
    private val rooms = ConcurrentHashMap<String, MultiUserChat>()
    private val states = ConcurrentHashMap<String, ChatGroup>()
    private val watched = ConcurrentHashMap.newKeySet<String>()
    private val nickname = Resourcepart.from(account.substringBefore('@'))

    init {
        manager.addInvitationListener { _, room, inviter, _, _, _, _ ->
            events.invited(GroupInvitation(room.room.toString(), inviter.asBareJid().toString()))
        }
        omemo.addOmemoMucMessageListener(object : OmemoMucMessageListener {
            override fun onOmemoMucMessageReceived(muc: MultiUserChat, stanza: Stanza, decrypted: OmemoMessage.Received) {
                if (stanza is Message && decrypted.body != null && rooms.containsKey(muc.room.toString())) {
                    receive(muc, stanza, decrypted.body, decrypted.senderDevice.jid.toString(), false)
                }
            }
        })
    }
    override fun knows(room: String) = rooms.containsKey(room) || protocols.knows(room)
    override fun restore() {
        BookmarkManager.getBookmarkManager(connection).bookmarkedConferences.filter { it.isAutoJoin && protocols.allowsAutojoin(it.jid.toString()) }.forEach {
            runCatching { join(it.jid.toString()) }.onFailure { events.failure(UiText(R.string.chat_group_restore_error)) }
        }
    }
    override fun create(name: String, members: Set<String>): ChatGroup {
        require(name.isNotBlank() && name.length <= 80)
        if (!GroupPolicy.canCreate(members, account)) throw ChatException(UiText(R.string.chat_group_capacity, listOf(GroupPolicy.MAX_ENCRYPTED_MEMBERS)))
        val service = manager.mucServiceDomains.firstOrNull() ?: throw ChatException(UiText(R.string.chat_groups_unavailable))
        val muc = manager.getMultiUserChat(JidCreate.entityBareFrom("mw-${UUID.randomUUID()}@$service"))
        muc.create(nickname)
        try {
            val form = muc.configurationForm.fillableForm
            form.setAnswer("muc#roomconfig_roomname", name)
            form.setAnswer("muc#roomconfig_membersonly", true)
            form.setAnswer("muc#roomconfig_whois", "anyone")
            form.setAnswer("muc#roomconfig_publicroom", false)
            form.setAnswer("muc#roomconfig_persistentroom", true)
            if (form.dataForm.hasField("muc#roomconfig_allowinvites")) form.setAnswer("muc#roomconfig_allowinvites", false)
            // All members must be able to enumerate offline recipients, not just moderators.
            if (form.dataForm.hasField("muc#roomconfig_getmemberlist"))
                form.setAnswer("muc#roomconfig_getmemberlist", listOf("moderator", "participant", "visitor"))
            muc.sendConfigurationForm(form)
            requireSecure(muc)
            protocols.pinLegacy(muc.room.toString())
            val invitees = members.map { JidCreate.entityBareFrom(it) }
            muc.grantMembership(invitees)
            watch(muc)
            rooms[muc.room.toString()] = muc
            remember(muc, name)
            val created = info(muc.room.toString())
            invitees.forEach { muc.invite(it, "") }
            return created
        } catch (e: Exception) {
            runCatching { muc.destroy(null, null) }
            throw e
        }
    }
    override fun join(room: String): ChatGroup {
        val muc = manager.getMultiUserChat(JidCreate.entityBareFrom(chatAddress(room)))
        protocols.requireLegacy(room)
        requireSecure(muc)
        protocols.pinLegacy(room)
        watch(muc)
        rooms[room] = muc // Decryption listener must know the room before its history arrives.
        try {
            if (!muc.isJoined) muc.join(muc.getEnterConfigurationBuilder(nickname).requestNoHistory().build())
            val state = info(room)
            remember(muc, state.name)
            protocols.setAutojoin(room, true)
            sync(muc)
            return state
        } catch (e: Exception) { rooms.remove(room); runCatching { muc.leave() }; throw e }
    }
    private fun requireSecure(muc: MultiUserChat) {
        if (!omemo.multiUserChatSupportsOmemo(muc)) throw ChatException(UiText(R.string.chat_group_unsafe))
    }
    override fun info(room: String): ChatGroup {
        protocols.requireLegacy(room)
        val muc = rooms[room]?.takeIf { it.isJoined } ?: throw ChatException(UiText(R.string.chat_group_not_joined))
        requireSecure(muc)
        val owners = muc.owners.map { it.jid.asBareJid().toString() }.toSet()
        val members = (owners + muc.admins.map { it.jid.asBareJid().toString() } + muc.members.map { it.jid.asBareJid().toString() }).toSet()
        if (account !in members) throw ChatException(UiText(R.string.chat_group_unsafe))
        if (members.size > GroupPolicy.MAX_ENCRYPTED_MEMBERS) throw ChatException(UiText(R.string.chat_group_capacity, listOf(GroupPolicy.MAX_ENCRYPTED_MEMBERS)))
        return ChatGroup(room, manager.getRoomInfo(muc.room).name, members, account in owners, muc.isJoined)
            .also { states[room] = it; events.group(it) }
    }
    override fun recipients(room: String): Set<String> = info(room).members
    override fun send(room: String, body: String, id: String) {
        val muc = rooms[room]?.takeIf { it.isJoined } ?: throw ChatException(UiText(R.string.chat_group_not_joined))
        protocols.requireLegacy(room)
        val before = recipientSnapshot(room)
        val peers = (before.members - account).map { JidCreate.bareFrom(it) }.toSet()
        // Smack serializes encrypt/decrypt on this manager. Persist every recipient's ratchet
        // together before releasing that lock or allowing the ciphertext onto the connection.
        val encrypted = synchronized(omemo) { records.durableBatch { omemo.encrypt(peers, body) } }
        GroupSendGuard.verifyComplete(before, encrypted.intendedDevices.map { GroupDevice(it.jid.toString(), it.deviceId) }.toSet(), encrypted.isMissingRecipients)
        GroupSendGuard.verifyUnchanged(before, recipientSnapshot(room))
        val builder = StanzaBuilder.buildMessage(id).to(muc.room).ofType(Message.Type.groupchat).addExtension(encrypted.element)
        StoreHint.set(builder)
        val message = builder.build()
        check(message.body == null && message.hasExtension("encrypted", SmackMessenger.OMEMO_NAMESPACE))
        connection.sendStanza(message)
    }
    private fun recipientSnapshot(room: String): GroupRecipients {
        val members = recipients(room)
        val published = GroupDeviceDirectory.fetch(connection, members)
        val devices = published.flatMap { (member, ids) ->
            ids.filterNot { member == account && it == omemo.ownDevice.deviceId }.map { GroupDevice(member, it) }
        }.toSet()
        return GroupRecipients(members, devices).also { snapshot ->
            GroupSendGuard.validate(snapshot, account)
            // Merge serially through the existing OMEMO store. Encryption/trust stays in Smack.
            synchronized(omemo) {
                published.forEach { (member, ids) -> store.mergePublishedDevices(omemo.ownDevice, JidCreate.bareFrom(member), ids) }
            }
        }
    }
    private fun receive(muc: MultiUserChat, stanza: Message, body: String, sender: String, historical: Boolean, stamp: Long? = null) {
        val id = stanza.stanzaId ?: return
        events.message(ChatPayload.decode(ChatMessage(id, muc.room.toString(), body, sender == account,
            stamp ?: DelayInformationManager.getDelayTimestamp(stanza)?.time ?: System.currentTimeMillis(),
            sender = sender)), historical)
    }
    private fun sync(muc: MultiUserChat) {
        val mam = MamManager.getInstanceFor(muc)
        if (!mam.isSupported) return
        val key = "group-archive/$account/${muc.room}"
        val since = records.get(key)?.toLongOrNull()
        val args = MamManager.MamQueryArgs.builder().setResultPageSizeTo(100)
        if (since == null) args.queryLastPage() else args.limitResultsSince(Date((since - 60_000).coerceAtLeast(0)))
        val query = mam.queryArchive(args.build())
        var latest = since ?: 0L
        do {
            query.page.forwarded.forEach { forwarded ->
                val stanza = forwarded.forwardedStanza
                val stamp = forwarded.delayInformation?.stamp?.time ?: System.currentTimeMillis()
                val sender = MUCUser.from(stanza)?.item?.jid?.asBareJid() ?: return@forEach
                val encrypted = stanza.getExtension(QName(SmackMessenger.OMEMO_NAMESPACE, "encrypted")) as? OmemoElement
                if (encrypted != null) {
                    val decrypted = runCatching { omemo.decrypt(sender, encrypted) }.getOrNull()
                    if (decrypted?.body != null) receive(muc, stanza, decrypted.body, sender.toString(), true, stamp)
                }
                latest = maxOf(latest, stamp)
            }
            if (query.isComplete || since == null) break
            query.pageNext(100)
        } while (true)
        if (latest > 0) records.put(key, latest.toString())
    }
    override fun invite(room: String, peer: String) {
        val current = info(room)
        if (!current.owner) throw ChatException(UiText(R.string.chat_group_admin_required))
        if (peer in current.members) return
        if (!GroupPolicy.canInvite(current.members.size)) throw ChatException(UiText(R.string.chat_group_capacity, listOf(GroupPolicy.MAX_ENCRYPTED_MEMBERS)))
        val muc = rooms.getValue(room)
        muc.grantMembership(JidCreate.entityBareFrom(peer))
        // Verify server-side state before emitting an invitation; concurrent admins may have added members.
        try { info(room) } catch (e: Exception) {
            runCatching { muc.revokeMembership(JidCreate.entityBareFrom(peer)) }
            throw e
        }
        muc.invite(JidCreate.entityBareFrom(peer), "")
        info(room)
    }
    override fun remove(room: String, peer: String) {
        val current = info(room)
        if (!current.owner || peer == account) throw ChatException(UiText(R.string.chat_group_admin_required))
        rooms.getValue(room).revokeMembership(JidCreate.entityBareFrom(peer))
        info(room)
    }
    override fun delete(room: String) {
        val current = info(room)
        if (!current.owner) throw ChatException(UiText(R.string.chat_group_admin_required))
        val muc = rooms.getValue(room)
        protocols.setAutojoin(room, false)
        muc.destroy(null, null)
        BookmarkManager.getBookmarkManager(connection).removeBookmarkedConference(muc.room)
        rooms.remove(room)
        events.group(current.copy(joined = false, members = emptySet()))
    }
    override fun leave(room: String) {
        val current = info(room)
        if (current.owner) throw ChatException(UiText(R.string.chat_group_owner_leave))
        val muc = rooms.getValue(room)
        // XEP-0045 leaving presence does not revoke a member's server affiliation.
        // Only an administrator can revoke it. Remove autojoin so the user stays out.
        protocols.setAutojoin(room, false)
        muc.leave()
        BookmarkManager.getBookmarkManager(connection).removeBookmarkedConference(muc.room)
        rooms.remove(room)
        events.group(current.copy(joined = false))
    }
    private fun watch(muc: MultiUserChat) {
        val room = muc.room.toString()
        if (!watched.add(room)) return
        muc.addUserStatusListener(object : org.jivesoftware.smackx.muc.UserStatusListener {
            override fun removed(user: MUCUser?, presence: org.jivesoftware.smack.packet.Presence?) {
                rooms.remove(room)
                // This callback runs on Smack's stanza processing path. Network requests here can
                // deadlock the leave acknowledgement. The local tombstone blocks stale bookmarks.
                protocols.setAutojoin(room, false)
                states[room]?.let { events.group(it.copy(joined = false)) }
            }
        })
    }
    private fun remember(muc: MultiUserChat, name: String) {
        BookmarkManager.getBookmarkManager(connection).addBookmarkedConference(name, muc.room, true, nickname, null)
    }
}
