package org.astianx.midori.one.chat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jivesoftware.smackx.vcardtemp.packet.VCard
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatContactsProfileTest {
    @Test fun contactLinksDecodeOnlyAddressesWithoutExecutingActions() {
        assertEquals("alice@example.org", parseChatContactLink("xmpp:alice%40example.org"))
        assertEquals("alice+tag@example.org", parseChatContactLink("xmpp:alice+tag@example.org?message"))
        assertEquals("alice@example.org", parseChatContactLink("midori://contact?address=alice%40example.org"))
        assertEquals("alice@example.org", parseChatContactLink(chatContactLink("alice@example.org")))
        listOf("https://example.org", "xmpp:alice@example.org?message;body=send", "xmpp://evil/alice@example.org",
            "xmpp:alice@example.org/resource", "xmpp:alice%40example.org%0A", "midori://contact?address=alice@example.org&action=send",
            "midori://contact/path?address=alice@example.org", "xmpp:alice@example.org#action", "javascript:alert(1)").forEach {
            assertTrue("Must reject a link with actions/resources: $it", runCatching { parseChatContactLink(it) }.isFailure)
        }
    }

    @Test fun vcardPublicationPreservesOtherClientsFieldsAndWithdrawalClearsOnlyProfile() {
        val card = VCard().apply { setField("TITLE", "Unrelated field"); emailHome = "private@example.org" }
        SmackProfiles.encode(PublishedChatProfile("María", "Disponible"), card)
        assertEquals(PublishedChatProfile("María", "Disponible"), SmackProfiles.decode(card))
        assertEquals("Unrelated field", card.getField("TITLE"))
        assertEquals("private@example.org", card.emailHome)
        SmackProfiles.encode(PublishedChatProfile(), card)
        assertEquals(PublishedChatProfile(), SmackProfiles.decode(card))
        assertEquals("private@example.org", card.emailHome)
    }

    @Test fun profileRejectsOversizedAvatarAndSanitizesRemoteText() {
        assertTrue(runCatching { PublishedChatProfile(photoBase64 = "a".repeat(PublishedChatProfile.MAX_AVATAR_ENCODED + 1), photoMime = "image/jpeg").validate() }.isFailure)
        assertTrue(runCatching { PublishedChatProfile(photoBase64 = "https://example.org/avatar", photoMime = "image/jpeg").validate() }.isFailure)
        val value = SmackProfiles.decode(VCard().apply { setField("FN", "A\n" + "x".repeat(100)); setField("DESC", "\t" + "z".repeat(500)) })
        assertEquals(60, value.name.length)
        assertEquals(160, value.bio.length)
        assertFalse(value.name.contains('\n'))
    }

    @Test fun contactProposalAndLocalEditsNeverPublishAndPhoneStaysOutOfPublicProfile() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val records = MemoryRecords()
            val transport = ProfileTransport()
            val vm = ChatViewModel(records, { transport }, io = dispatcher)
            vm.connect("alice@example.org", "test", "example.org", "5222"); advanceUntilIdle()
            vm.prepareContact("xmpp:bob@example.org")
            assertEquals(0, transport.adds)
            assertTrue(vm.state.value.contacts.isEmpty())
            vm.addContactDetails("bob@example.org", "Bob local", "+55 (11) 1234-5678"); advanceUntilIdle()
            vm.saveProfile("Alice", "Mi perfil"); advanceUntilIdle()
            assertEquals(0, transport.published.size)
            assertEquals("+55 (11) 1234-5678", vm.state.value.conversationOptions["bob@example.org"]?.phone)
            vm.publishProfile(); advanceUntilIdle()
            assertEquals(listOf(PublishedChatProfile("Alice", "Mi perfil")), transport.published)
            val restored = ChatViewModel(records, { ProfileTransport() }, io = dispatcher); advanceUntilIdle()
            assertEquals("Bob local", restored.state.value.conversationOptions["bob@example.org"]?.alias)
            assertEquals("+55 (11) 1234-5678", restored.state.value.conversationOptions["bob@example.org"]?.phone)
            assertEquals(PublishedChatProfile("Alice", "Mi perfil"), restored.state.value.publishedProfile)
            vm.withdrawProfile(); advanceUntilIdle()
            assertNull(vm.state.value.publishedProfile)
            assertEquals("Alice", vm.state.value.profile.name)
        } finally { Dispatchers.resetMain() }
    }

    private class MemoryRecords : ChatRecords {
        val data = mutableMapOf<String, String>()
        override fun get(key: String) = data[key]
        override fun put(key: String, value: String?) { if (value == null) data.remove(key) else data[key] = value }
        override fun keys(prefix: String) = data.keys.filter { it.startsWith(prefix) }.toSet()
    }
    private class ProfileTransport : ChatTransport {
        var adds = 0
        val published = mutableListOf<PublishedChatProfile>()
        override fun connect(account: ChatAccount, events: ChatEvents) { events.connection(ChatConnection.ONLINE) }
        override fun disconnect() {}
        override fun send(peer: String, body: String, id: String) {}
        override fun devices(peer: String) = emptyList<ChatDevice>()
        override fun trust(device: ChatDevice, trusted: Boolean) {}
        override fun addContact(peer: String) { adds++ }
        override fun answerSubscription(peer: String, approve: Boolean) {}
        override fun block(peer: String, blocked: Boolean) {}
        override fun markRead(peer: String, id: String) {}
        override fun setActive(active: Boolean) {}
        override fun publishProfile(value: PublishedChatProfile) { published += value }
        override fun loadProfile(peer: String) = PublishedChatProfile("Remote", "Bio")
    }
}
