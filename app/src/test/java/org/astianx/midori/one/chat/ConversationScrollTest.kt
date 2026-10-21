/* Copyright 2026 Astian. Licensed under the Apache License, Version 2.0. */
package org.astianx.midori.one.chat

import org.junit.Assert.*
import org.junit.Test

class ConversationScrollTest {
    private fun message(id: Int, outgoing: Boolean = false) = ChatMessage(id.toString(), "bob@example.org", "message $id",
        outgoing, id.toLong(), sender = if (outgoing) "alice@example.org" else "bob@example.org")

    @Test fun anIncomingBurstStillFollowsThePreviouslyNewestVisibleMessage() {
        val previousNewest = message(3)
        val newer = listOf(message(6), message(5), message(4), previousNewest, message(2), message(1))
        // The stable anchor now has index 3; an index < 2 heuristic incorrectly leaves the new messages hidden.
        assertTrue(shouldFollowConversationUpdate(newer, previousNewest.mediaKey, previousNewest.mediaKey))
        assertTrue(shouldFollowConversationUpdate(newer, previousNewest.mediaKey, message(2).mediaKey))
    }

    @Test fun incomingMessagesDoNotStealTheReadersPositionInOlderHistory() {
        val history = (20 downTo 1).map { message(it) }
        val withIncoming = listOf(message(23), message(22), message(21)) + history
        assertFalse(shouldFollowConversationUpdate(withIncoming, history.first().mediaKey, message(12).mediaKey))
    }

    @Test fun sendingWhileReadingOldMessagesBringsTheSentMessageAndImmediateReplyIntoView() {
        val history = (20 downTo 1).map { message(it) }
        val sentAndReply = listOf(message(22), message(21, outgoing = true)) + history
        assertTrue(shouldFollowConversationUpdate(sentAndReply, history.first().mediaKey, message(4).mediaKey))
    }

    @Test fun openingOrClearingAConversationDoesNotReuseAnotherConversationsAnchor() {
        assertTrue(shouldFollowConversationUpdate(listOf(message(1)), null, null))
        assertFalse(shouldFollowConversationUpdate(emptyList(), message(1).mediaKey, message(1).mediaKey))
    }
}
