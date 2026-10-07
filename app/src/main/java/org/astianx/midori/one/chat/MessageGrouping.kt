package org.astianx.midori.one.chat

import java.time.Instant
import java.time.ZoneId

/** Adjacent bubbles share a corner only within a short, same-day run from one sender. */
internal fun ChatMessage.groupsWith(other: ChatMessage?, zone: ZoneId = ZoneId.systemDefault()): Boolean =
    other != null && peer == other.peer && sender == other.sender && outgoing == other.outgoing &&
        protection == MessageProtection.ENCRYPTED && other.protection == protection &&
        delivery != MessageDelivery.FAILED && other.delivery != MessageDelivery.FAILED &&
        kotlin.math.abs(timestamp - other.timestamp) < 180_000 &&
        Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate() == Instant.ofEpochMilli(other.timestamp).atZone(zone).toLocalDate()
