package org.astianx.midori.one.chat

import org.astianx.midori.one.R

internal enum class ChatListFilter(val label: Int) {
    ALL(R.string.chat_filter_all), UNREAD(R.string.chat_filter_unread), GROUPS(R.string.chat_filter_groups), DIRECT(R.string.style_direct_chats), PINNED(R.string.chat_pinned)
}

/** Derive filters from the stored messages; never synthesize unread counts or contacts. */
internal fun ChatUiState.visibleConversations(query: String, filter: ChatListFilter): List<ChatContact> {
    val unread = messages.filter { !it.read && !it.outgoing }.mapTo(mutableSetOf()) { it.peer }
    val latest = messages.groupBy { it.peer }.mapValues { (_, values) -> values.maxOf { it.timestamp } }
    return contacts.filter { contact ->
        conversationOptions[contact.address]?.hidden != true &&
            "${contact.name} ${contact.address} ${conversationOptions[contact.address]?.alias.orEmpty()} ${contactProfiles[contact.address]?.name.orEmpty()}".contains(query.trim(), ignoreCase = true) &&
            when (filter) {
                ChatListFilter.ALL -> true
                ChatListFilter.UNREAD -> contact.address in unread
                ChatListFilter.GROUPS -> contact.group
                ChatListFilter.DIRECT -> !contact.group
                ChatListFilter.PINNED -> conversationOptions[contact.address]?.pinned == true
            }
    }.sortedWith(compareByDescending<ChatContact> { conversationOptions[it.address]?.pinned == true }
        .thenByDescending { latest[it.address] ?: 0L }.thenBy { it.name })
}
