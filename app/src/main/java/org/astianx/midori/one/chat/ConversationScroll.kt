/* Copyright 2026 Astian. Licensed under the Apache License, Version 2.0. */
package org.astianx.midori.one.chat

/** Reverse-layout indices move when new messages arrive; the anchored message's identity does not. */
internal fun shouldFollowConversationUpdate(
    messagesNewestFirst: List<ChatMessage>, previousNewestKey: String?, firstVisibleKey: String?
): Boolean {
    if (messagesNewestFirst.isEmpty()) return false
    if (previousNewestKey == null || firstVisibleKey == null) return true
    val previousNewestIndex = messagesNewestFirst.indexOfFirst { it.mediaKey == previousNewestKey }
    if (previousNewestIndex <= 0) return false
    // Sending is an explicit action; bring the resulting message into view even while reading older history.
    if (messagesNewestFirst.take(previousNewestIndex).any { it.outgoing }) return true
    val anchorIndex = messagesNewestFirst.indexOfFirst { it.mediaKey == firstVisibleKey }
    return anchorIndex >= 0 && anchorIndex <= previousNewestIndex + 1
}
