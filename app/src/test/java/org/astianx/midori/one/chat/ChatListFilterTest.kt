package org.astianx.midori.one.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatListFilterTest {
    private val state = ChatUiState(
        contacts = listOf(ChatContact("a@example.org", "Alba"), ChatContact("b@example.org", "Beto"),
            ChatContact("group@example.org", "Equipo", group = true), ChatContact("hidden@example.org", "Hidden")),
        messages = listOf(ChatMessage("1", "a@example.org", "Read", false, 10, read = true),
            ChatMessage("2", "b@example.org", "Unread", false, 20),
            ChatMessage("3", "group@example.org", "Sent", true, 30),
            ChatMessage("4", "hidden@example.org", "Hidden", false, 40)),
        conversationOptions = mapOf("a@example.org" to ConversationOptions(pinned = true),
            "hidden@example.org" to ConversationOptions(hidden = true)))

    @Test fun pinnedPrecedesRecencyAndHiddenNeverAppears() {
        assertEquals(listOf("Alba", "Equipo", "Beto"), state.visibleConversations("", ChatListFilter.ALL).map { it.name })
    }
    @Test fun unreadExcludesReadOutgoingAndHiddenMessages() {
        assertEquals(listOf("Beto"), state.visibleConversations("", ChatListFilter.UNREAD).map { it.name })
    }
    @Test fun searchCombinesWithFilterAndMatchesAddresses() {
        assertEquals(listOf("Equipo"), state.visibleConversations(" GROUP@ ", ChatListFilter.GROUPS).map { it.name })
        assertEquals(emptyList<ChatContact>(), state.visibleConversations("Alba", ChatListFilter.UNREAD))
    }
}
