package org.astianx.midori.one.chat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.astianx.midori.one.R
import org.astianx.midori.one.UiText
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private class Records : ChatRecords {
        val data = mutableMapOf<String, String>()
        var writeError: Exception? = null
        override fun get(key: String) = data[key]
        override fun put(key: String, value: String?) {
            writeError?.let { throw it }
            if (value == null) data.remove(key) else data[key] = value
        }
        override fun keys(prefix: String) = data.keys.filter { it.startsWith(prefix) }.toSet()
    }
    private class Transport : ChatTransport {
        lateinit var events: ChatEvents
        var sends = 0
        var error: Exception? = null
        override fun connect(account: ChatAccount, events: ChatEvents) { this.events = events; error?.let { throw it }; events.connection(ChatConnection.ONLINE) }
        override fun disconnect() {}
        override fun send(peer: String, body: String, id: String) { sends++; error?.let { throw it } }
        override fun devices(peer: String) = emptyList<ChatDevice>()
        override fun trust(device: ChatDevice, trusted: Boolean) {}
        override fun addContact(peer: String) {}
        override fun answerSubscription(peer: String, approve: Boolean) {}
        override fun block(peer: String, blocked: Boolean) { events.blocked(if (blocked) setOf(peer) else emptySet()) }
        override fun markRead(peer: String, id: String) {}
        override fun setActive(active: Boolean) {}
    }
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun notificationCountsComeFromUnreadStateAcrossABurst() = runTest(dispatcher) {
        val client = Transport()
        val counts = mutableListOf<Int>()
        val vm = ChatViewModel(Records(), { client }, { _, count -> counts += count }, io = dispatcher)
        vm.connect("alice@example.org", "password", "example.org", "5222"); advanceUntilIdle()
        val first = ChatMessage("first", "bob@example.org", "One", false, 1)
        client.events.message(first, false)
        client.events.message(first, false)
        client.events.message(first.copy(id = "second", timestamp = 2), false)
        advanceUntilIdle()
        assertEquals(listOf(1, 2), counts)
    }

    @Test fun encryptionFailureDoesNotRetryWithPlaintext() = runTest(dispatcher) {
        val records = Records(); val client = Transport(); val vm = ChatViewModel(records, { client }, io = dispatcher)
        advanceUntilIdle(); vm.connect("alice@example.org", "test", "example.org", "5222"); advanceUntilIdle()
        vm.open("bob@example.org"); advanceUntilIdle()
        client.error = ChatException(UiText(R.string.chat_encryption_error))
        vm.send("private draft"); advanceUntilIdle()
        assertEquals(1, client.sends)
        assertEquals(MessageDelivery.FAILED, vm.state.value.messages.single().delivery)
        assertEquals("private draft", vm.state.value.messages.single().body)
    }
    @Test fun duplicateArchiveAndLiveMessageHaveOneUnreadAndOneNotification() = runTest(dispatcher) {
        val client = Transport(); var notifications = 0
        val vm = ChatViewModel(Records(), { client }, { _, _ -> notifications++ }, io = dispatcher)
        advanceUntilIdle(); vm.connect("alice@example.org", "test", "example.org", "5222"); advanceUntilIdle()
        val message = ChatMessage("id", "bob@example.org", "hello", false, 1L)
        client.events.message(message, false); client.events.message(message, true); advanceUntilIdle()
        assertEquals(1, vm.state.value.unreadCount); assertEquals(1, notifications)
        vm.open(message.peer); advanceUntilIdle(); assertEquals(0, vm.state.value.unreadCount)
        assertFalse(vm.state.value.readReceipts)
    }
    @Test fun storageFailureDoesNotSendOrLeaveMessageStuckSending() = runTest(dispatcher) {
        val records = Records(); val client = Transport(); val vm = ChatViewModel(records, { client }, io = dispatcher)
        advanceUntilIdle(); vm.connect("alice@example.org", "test", "example.org", "5222"); advanceUntilIdle()
        vm.open("bob@example.org"); advanceUntilIdle()
        records.writeError = java.io.IOException("Disk full")
        vm.send("private draft"); advanceUntilIdle()
        assertEquals(0, client.sends)
        assertEquals(MessageDelivery.FAILED, vm.state.value.messages.single().delivery)
        assertFalse(vm.state.value.busy)
        assertNotNull(vm.state.value.error)
    }
    @Test fun restoreFailureKeepsCredentialsAndHistory() = runTest(dispatcher) {
        val records = Records(); val client = Transport(); val vm = ChatViewModel(records, { client }, io = dispatcher)
        advanceUntilIdle(); vm.connect("alice@example.org", "test", "example.org", "5222"); advanceUntilIdle()
        client.events.message(ChatMessage("id", "bob@example.org", "saved", false, 1L), false); advanceUntilIdle()
        val session = records.get("session")
        val broken = Transport().apply { error = IllegalStateException() }
        val restored = ChatViewModel(records, { broken }, io = dispatcher); advanceUntilIdle()
        assertEquals(session, records.get("session")); assertEquals("saved", restored.state.value.messages.single().body)
        assertEquals(ChatConnection.ERROR, restored.state.value.connection)
    }
    @Test fun delayedOrForeignReceiptsCannotChangeDelivery() {
        val message = ChatMessage("id", "bob@example.org", "private", true, 1L, MessageDelivery.SENT)
        assertEquals(message, message.withReceipt("mallory@example.org", "id", MessageDelivery.READ))
        val read = message.withReceipt(message.peer, message.id, MessageDelivery.READ)
        assertEquals(read, read.withReceipt(message.peer, message.id, MessageDelivery.DELIVERED))
    }
    @Test fun switchingAccountDoesNotExposeOtherHistory() = runTest(dispatcher) {
        val records = Records(); val client = Transport(); val vm = ChatViewModel(records, { client }, io = dispatcher)
        advanceUntilIdle(); vm.connect("alice@example.org", "test", "example.org", "5222"); advanceUntilIdle()
        client.events.message(ChatMessage("id", "bob@example.org", "private", false, 1L), false); advanceUntilIdle()
        vm.logout(); advanceUntilIdle(); vm.connect("other@example.org", "test", "example.org", "5222"); advanceUntilIdle()
        assertTrue(vm.state.value.messages.isEmpty())
        assertTrue(records.get("history/alice@example.org")!!.contains("private"))
    }
    @Test fun blockedAndOfflineRecipientsCannotSend() = runTest(dispatcher) {
        val client = Transport(); val vm = ChatViewModel(Records(), { client }, io = dispatcher)
        advanceUntilIdle(); vm.connect("alice@example.org", "test", "example.org", "5222"); advanceUntilIdle()
        vm.open("bob@example.org"); advanceUntilIdle(); vm.blockSelected(); advanceUntilIdle()
        vm.send("blocked"); advanceUntilIdle(); assertEquals(0, client.sends)
        client.events.connection(ChatConnection.DISCONNECTED); vm.send("offline"); advanceUntilIdle(); assertEquals(0, client.sends)
    }
    @Test fun deviceKeyReplacementDoesNotInheritTrust() {
        val store = EncryptedOmemoStore(Records())
        val device = org.jivesoftware.smackx.omemo.internal.OmemoDevice(org.jxmpp.jid.impl.JidCreate.bareFrom("bob@example.org"), 17)
        val oldKey = org.jivesoftware.smackx.omemo.trust.OmemoFingerprint("a".repeat(64))
        val newKey = org.jivesoftware.smackx.omemo.trust.OmemoFingerprint("b".repeat(64))
        val trust = store.trustFor("alice@example.org")
        trust.setTrust(device, oldKey, org.jivesoftware.smackx.omemo.trust.TrustState.trusted)
        assertEquals(org.jivesoftware.smackx.omemo.trust.TrustState.undecided, trust.getTrust(device, newKey))
    }
    @Test fun trustApprovalIsIsolatedBetweenAccounts() {
        val store = EncryptedOmemoStore(Records())
        val device = org.jivesoftware.smackx.omemo.internal.OmemoDevice(org.jxmpp.jid.impl.JidCreate.bareFrom("bob@example.org"), 17)
        val fingerprint = org.jivesoftware.smackx.omemo.trust.OmemoFingerprint("a".repeat(64))
        store.trustFor("alice@example.org").setTrust(device, fingerprint, org.jivesoftware.smackx.omemo.trust.TrustState.trusted)
        assertEquals(org.jivesoftware.smackx.omemo.trust.TrustState.undecided,
            store.trustFor("other@example.org").getTrust(device, fingerprint))
    }
    @Test fun draftsAndRichMessagesSurviveReconnectWithoutLeakingAcrossAccounts() = runTest(dispatcher) {
        val records = Records(); val client = Transport(); val vm = ChatViewModel(records, { client }, io = dispatcher)
        advanceUntilIdle(); vm.connect("alice@example.org", "test", "example.org", "5222"); advanceUntilIdle()
        vm.open("bob@example.org"); advanceUntilIdle(); vm.updateDraft("Unfinished message"); advanceUntilIdle()
        val attachment = ChatAttachment("https://upload.example.org/a", "a".repeat(64), "b".repeat(24), "photo.jpg", "image/jpeg", 10, "c".repeat(64))
        client.events.message(ChatMessage("file", "bob@example.org", "Caption", false, 1, attachment = attachment), false)
        advanceUntilIdle()
        val restored = ChatViewModel(records, { Transport() }, io = dispatcher); advanceUntilIdle()
        assertEquals("Unfinished message", restored.state.value.drafts["bob@example.org"])
        assertEquals(attachment, restored.state.value.messages.single().attachment)
        restored.logout(); advanceUntilIdle(); restored.connect("other@example.org", "test", "example.org", "5222"); advanceUntilIdle()
        assertTrue(restored.state.value.drafts.isEmpty()); assertTrue(restored.state.value.messages.isEmpty())
    }
    @Test fun groupEchoIsDeduplicatedButDifferentSendersMayReuseAnId() = runTest(dispatcher) {
        val client = Transport(); val vm = ChatViewModel(Records(), { client }, io = dispatcher)
        advanceUntilIdle(); vm.connect("alice@example.org", "test", "example.org", "5222"); advanceUntilIdle()
        val message = ChatMessage("same-id", "room@conference.example.org", "hello", false, 1, sender = "bob@example.org")
        client.events.message(message, false); client.events.message(message, true)
        client.events.message(message.copy(sender = "carol@example.org"), false); advanceUntilIdle()
        assertEquals(2, vm.state.value.messages.size); assertEquals(2, vm.state.value.unreadCount)
        assertNotEquals(message.mediaKey, message.copy(sender = "carol@example.org").mediaKey)
        client.events.group(ChatGroup(message.peer, "Friends", setOf("alice@example.org", "bob@example.org"), false, false))
        advanceUntilIdle(); vm.open(message.peer); advanceUntilIdle(); vm.send("Cannot send while out of group"); advanceUntilIdle()
        assertEquals(0, client.sends)
    }
    @Test fun pinAndMutePersistPerAccountAndMuteSuppressesOnlyNotifications() = runTest(dispatcher) {
        val records = Records(); val client = Transport(); var notifications = 0
        val cleared = mutableListOf<String?>()
        val vm = ChatViewModel(records, { client }, { _, _ -> notifications++ }, { cleared += it }, io = dispatcher)
        advanceUntilIdle(); vm.connect("alice@example.org", "test", "example.org", "5222"); advanceUntilIdle()
        client.events.contacts(listOf(ChatContact("bob@example.org"))); advanceUntilIdle()
        vm.pinConversation("bob@example.org", true); vm.muteConversation("bob@example.org", true); advanceUntilIdle()
        client.events.message(ChatMessage("muted", "bob@example.org", "hello", false, 1), false); advanceUntilIdle()
        assertEquals(0, notifications); assertEquals(1, vm.state.value.unreadCount)
        assertTrue(cleared.contains("bob@example.org"))
        val restored = ChatViewModel(records, { Transport() }, io = dispatcher); advanceUntilIdle()
        assertTrue(restored.state.value.conversationOptions.getValue("bob@example.org").pinned)
        assertTrue(restored.state.value.conversationOptions.getValue("bob@example.org").muted)
        vm.muteConversation("bob@example.org", false); advanceUntilIdle()
        client.events.message(ChatMessage("audible", "bob@example.org", "hello", false, 2), false); advanceUntilIdle()
        assertEquals(1, notifications)
        restored.logout(); advanceUntilIdle(); restored.connect("other@example.org", "test", "example.org", "5222"); advanceUntilIdle()
        assertTrue(restored.state.value.conversationOptions.isEmpty())
    }
    @Test fun localDeletionSurvivesRosterAndArchiveSyncButAcceptsNewMessages() = runTest(dispatcher) {
        val records = Records(); val client = Transport(); val vm = ChatViewModel(records, { client }, io = dispatcher)
        advanceUntilIdle(); vm.connect("alice@example.org", "test", "example.org", "5222"); advanceUntilIdle()
        val message = ChatMessage("old", "room@conference.example.org", "delete this", false, 1, sender = "bob@example.org")
        client.events.group(ChatGroup(message.peer, "Group", setOf("bob@example.org")))
        client.events.message(message, false)
        val future = message.copy(id = "bad-clock", timestamp = System.currentTimeMillis() + 86_400_000)
        client.events.message(future, false); advanceUntilIdle(); vm.open(message.peer); advanceUntilIdle()
        vm.updateDraft("draft"); advanceUntilIdle(); vm.deleteConversation(message.peer); advanceUntilIdle()
        assertTrue(vm.state.value.messages.isEmpty()); assertTrue(vm.state.value.drafts.isEmpty())
        assertTrue(vm.state.value.groups.single().joined)
        val newClient = Transport(); val restored = ChatViewModel(records, { newClient }, io = dispatcher); advanceUntilIdle()
        newClient.events.contacts(listOf(ChatContact(message.peer)))
        newClient.events.message(message, true); newClient.events.message(message, false); newClient.events.message(future, true); advanceUntilIdle()
        assertTrue(restored.state.value.messages.isEmpty())
        val options = restored.state.value.conversationOptions.getValue(message.peer)
        assertTrue(options.hidden)
        newClient.events.message(message.copy(id = "new", timestamp = options.deletedBefore + 1), false); advanceUntilIdle()
        assertFalse(restored.state.value.conversationOptions.getValue(message.peer).hidden)
        assertEquals("new", restored.state.value.messages.single().id)
        restored.open(message.peer); advanceUntilIdle()
        newClient.events.message(message, true); advanceUntilIdle()
        assertEquals(1, restored.state.value.messages.size)
    }
    @Test fun failedDeletionPreservesHistoryAndDraft() = runTest(dispatcher) {
        val records = Records(); val client = Transport(); val vm = ChatViewModel(records, { client }, io = dispatcher)
        advanceUntilIdle(); vm.connect("alice@example.org", "test", "example.org", "5222"); advanceUntilIdle()
        vm.open("bob@example.org"); advanceUntilIdle()
        client.events.message(ChatMessage("old", "bob@example.org", "retain", false, 1), false)
        vm.updateDraft("retain draft"); advanceUntilIdle()
        records.writeError = java.io.IOException()
        vm.deleteConversation("bob@example.org"); advanceUntilIdle()
        assertEquals("retain", vm.state.value.messages.single().body)
        assertEquals("retain draft", vm.state.value.drafts["bob@example.org"])
        assertFalse(vm.state.value.conversationOptions.getValue("bob@example.org").hidden)
    }

}
