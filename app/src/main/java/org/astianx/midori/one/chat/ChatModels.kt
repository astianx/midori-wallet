package org.astianx.midori.one.chat

import org.astianx.midori.one.UiText
import org.astianx.midori.one.R
import org.jxmpp.jid.impl.JidCreate

enum class ChatConnection { DISCONNECTED, CONNECTING, SECURING, ONLINE, ERROR }
enum class MessageDelivery { SENDING, SENT, DELIVERED, READ, FAILED }
enum class MessageProtection { ENCRYPTED, UNREADABLE, REJECTED_PLAINTEXT }

data class ChatAccount(val address: String, val password: String, val host: String, val port: Int = 5222) {
    override fun toString() = "ChatAccount(address=$address, password=<redacted>, host=$host, port=$port)"
}
data class ChatContact(val address: String, val name: String = address.substringBefore('@'), val online: Boolean = false,
    val group: Boolean = false)
data class ChatGroup(val address: String, val name: String, val members: Set<String>, val owner: Boolean = false,
    val joined: Boolean = true)
data class GroupInvitation(val room: String, val inviter: String)
data class ChatAttachment(val url: String, val key: String, val iv: String, val name: String, val mime: String,
    val size: Long, val sha256: String, val videoNote: Boolean = false, val durationMs: Long? = null) {
    override fun toString() = "ChatAttachment(name=$name, size=$size, key=<redacted>)"
}
data class UploadSlot(val putUrl: String, val getUrl: String, val headers: Map<String, String> = emptyMap())
data class ChatMessage(
    val id: String,
    val peer: String,
    val body: String,
    val outgoing: Boolean,
    val timestamp: Long,
    val delivery: MessageDelivery = MessageDelivery.SENT,
    val protection: MessageProtection = MessageProtection.ENCRYPTED,
    val read: Boolean = false,
    val markable: Boolean = false,
    val sender: String = "",
    val attachment: ChatAttachment? = null,
    val sharedContact: String? = null,
    val location: String? = null
)
data class ChatDevice(val address: String, val id: Int, val fingerprint: String, val trusted: Boolean)
data class ConversationOptions(val pinned: Boolean = false, val muted: Boolean = false,
    val hidden: Boolean = false, val deletedBefore: Long = 0, val deletedIds: Set<String> = emptySet(), val appearance: ChatAppearance? = null, val alias: String = "", val phone: String = "")
data class ChatUiState(
    val restoringSession: Boolean = false,
    val connection: ChatConnection = ChatConnection.DISCONNECTED,
    val backgroundConnection: Boolean = true,
    val connectionServiceRunning: Boolean = false,
    val wallpaper: Boolean = false,
    val appearance: ChatAppearance = ChatAppearance(),
    val profile: ChatProfile = ChatProfile(),
    val publishedProfile: PublishedChatProfile? = null,
    val contactProfiles: Map<String, PublishedChatProfile> = emptyMap(),
    val profileLoading: Boolean = false,
    val pendingContactAddress: String? = null,
    val account: String = "",
    val contacts: List<ChatContact> = emptyList(),
    val messages: List<ChatMessage> = emptyList(),
    val selectedPeer: String? = null,
    val devices: List<ChatDevice> = emptyList(),
    val ownFingerprint: String = "",
    val blocked: Set<String> = emptySet(),
    val requests: Set<String> = emptySet(),
    val readReceipts: Boolean = true,
    val error: UiText? = null,
    val busy: Boolean = false,
    val groups: List<ChatGroup> = emptyList(),
    val invitations: List<GroupInvitation> = emptyList(),
    val drafts: Map<String, String> = emptyMap(),
    val conversationOptions: Map<String, ConversationOptions> = emptyMap(),
    val mediaProgress: Int? = null,
    val pendingMedia: MediaSelection? = null,
    val mediaFiles: Map<String, String> = emptyMap()
) {
    val unreadCount: Int get() = messages.count { !it.outgoing && !it.read }
}

/** Reject resources and domains without a local part; normalize with the protocol's JID parser. */
internal fun chatAddress(input: String): String {
    require(!input.contains('/'))
    return JidCreate.entityBareFrom(input.trim()).toString()
}

/** Updates are peer-bound and monotonic: delayed receipts cannot move READ back to DELIVERED. */
internal fun ChatMessage.withReceipt(from: String, messageId: String, status: MessageDelivery): ChatMessage =
    if (outgoing && peer == from && id == messageId && delivery != MessageDelivery.FAILED &&
        status in setOf(MessageDelivery.SENT, MessageDelivery.DELIVERED, MessageDelivery.READ) &&
        status.ordinal > delivery.ordinal) copy(delivery = status) else this

internal interface ChatTransport {
    fun connect(account: ChatAccount, events: ChatEvents)
    fun disconnect()
    fun send(peer: String, body: String, id: String)
    fun devices(peer: String): List<ChatDevice>
    fun trust(device: ChatDevice, trusted: Boolean)
    fun addContact(peer: String)
    fun answerSubscription(peer: String, approve: Boolean)
    fun block(peer: String, blocked: Boolean)
    fun markRead(peer: String, id: String)
    fun setActive(active: Boolean)
    fun loadProfile(peer: String): PublishedChatProfile = throw UnsupportedOperationException()
    fun publishProfile(value: PublishedChatProfile): Unit = throw UnsupportedOperationException()
    fun requestUpload(size: Long): UploadSlot = throw UnsupportedOperationException()
    fun validateMediaUrl(url: String): Unit = throw UnsupportedOperationException()
    fun createGroup(name: String, members: Set<String>): ChatGroup = throw UnsupportedOperationException()
    fun joinGroup(room: String): ChatGroup = throw UnsupportedOperationException()
    fun groupInfo(room: String): ChatGroup = throw UnsupportedOperationException()
    fun inviteToGroup(room: String, peer: String): Unit = throw UnsupportedOperationException()
    fun removeGroupMember(room: String, peer: String): Unit = throw UnsupportedOperationException()
    fun deleteGroup(room: String): Unit = throw UnsupportedOperationException()
    fun leaveGroup(room: String): Unit = throw UnsupportedOperationException()
}

internal interface ChatEvents {
    fun connection(status: ChatConnection)
    fun connectionFailed(error: Exception) { connection(ChatConnection.ERROR) }
    fun contacts(contacts: List<ChatContact>)
    fun message(message: ChatMessage, historical: Boolean)
    fun receipt(peer: String, id: String, status: MessageDelivery)
    fun fingerprint(value: String)
    fun blocked(addresses: Set<String>)
    fun subscriptionRequested(peer: String) {}
    fun group(group: ChatGroup) {}
    fun invited(invitation: GroupInvitation) {}
    fun failure(error: UiText)
}

internal class ChatException(val text: UiText) : Exception()

internal val ChatMessage.mediaKey: String get() = "$peer\u0000$sender\u0000$outgoing\u0000$id"

internal fun ChatConnection.label() = when (this) {
    ChatConnection.DISCONNECTED, ChatConnection.ERROR -> R.string.chat_disconnected
    ChatConnection.CONNECTING -> R.string.chat_connecting
    ChatConnection.SECURING -> R.string.chat_securing
    ChatConnection.ONLINE -> R.string.chat_connected
}
