/* Copyright 2026 Astian. Licensed under the Apache License, Version 2.0. */
package org.astianx.midori.one.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.astianx.midori.one.R
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ConversationRow(
    contact: ChatContact,
    last: ChatMessage?,
    unread: Int,
    draft: String = "",
    options: ConversationOptions = ConversationOptions(),
    profile: PublishedChatProfile? = null,
    onLongClick: () -> Unit = {},
    onClick: () -> Unit
) {
    val appearance = LocalChatAppearance.current
    val name = options.alias.ifBlank { profile?.name?.ifBlank { contact.name } ?: contact.name }
    val showDraft = appearance.showPreviews && draft.isNotBlank()
    val preview = when {
        !appearance.showPreviews -> contact.address
        showDraft -> stringResource(R.string.chat_draft, draft)
        last == null -> contact.address
        else -> last.conversationPreview()
    }
    val previewIcon = if (appearance.showPreviews && !showDraft) last?.conversationPreviewIcon() else null
    val menuLabel = stringResource(R.string.chat_options)
    val secondaryColor = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().testTag("chat-row-${contact.address}")
            .heightIn(min = if (appearance.compact) 68.dp else 76.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = menuLabel)
            .padding(horizontal = 16.dp, vertical = if (appearance.compact) 8.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PublishedProfileAvatar(profile, name,
            Modifier.size(appearance.avatarSize.coerceIn(40, 64).dp).testTag("chat-row-avatar-${contact.address}"))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(name, Modifier.weight(1f).alignByBaseline().testTag("chat-row-name-${contact.address}"),
                    color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                last?.let {
                    Text(conversationDate(it.timestamp),
                        Modifier.padding(start = 12.dp).alignByBaseline().testTag("chat-row-time-${contact.address}"),
                        fontSize = 12.sp, maxLines = 1,
                        color = if (unread > 0 && !options.muted) MaterialTheme.colorScheme.primary else secondaryColor)
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (appearance.showPreviews && !showDraft && last?.outgoing == true && last.protection == MessageProtection.ENCRYPTED) {
                    ConversationDeliveryIcon(last.delivery)
                    Spacer(Modifier.width(4.dp))
                }
                previewIcon?.let { icon ->
                    Icon(icon, null, Modifier.size(16.dp), tint = secondaryColor)
                    Spacer(Modifier.width(4.dp))
                }
                Text(preview, Modifier.weight(1f).testTag("chat-row-preview-${contact.address}"),
                    fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (showDraft) MaterialTheme.colorScheme.primary else secondaryColor)
                if (options.muted || options.pinned || unread > 0) {
                    Spacer(Modifier.width(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (options.muted) Icon(Icons.Outlined.NotificationsOff, stringResource(R.string.chat_muted),
                            Modifier.size(14.dp), tint = secondaryColor)
                        if (options.pinned) Icon(Icons.Outlined.PushPin, stringResource(R.string.chat_pinned),
                            Modifier.size(14.dp), tint = secondaryColor)
                        if (unread > 0) {
                            Box(Modifier.heightIn(min = 20.dp).widthIn(min = 20.dp).clip(CircleShape)
                                .background(if (options.muted) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary)
                                .padding(horizontal = 5.dp, vertical = 1.dp), contentAlignment = Alignment.Center) {
                                Text(if (unread > 99) "99+" else unread.toString(), fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (options.muted) secondaryColor else MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Protection is checked before any metadata so a rejected message cannot leak a preview. */
@Composable
private fun ChatMessage.conversationPreview(): String = when {
    protection != MessageProtection.ENCRYPTED -> displayBody()
    body.isNotBlank() -> displayBody()
    attachment?.videoNote == true -> stringResource(R.string.chat_video_note)
    attachment?.mime?.startsWith("image/") == true -> stringResource(R.string.chat_photo)
    attachment?.mime?.startsWith("video/") == true -> stringResource(R.string.chat_video)
    attachment?.mime?.startsWith("audio/") == true -> stringResource(R.string.chat_audio)
    attachment != null -> stringResource(R.string.chat_document)
    sharedContact != null -> stringResource(R.string.chat_contact)
    location != null -> stringResource(R.string.chat_location)
    else -> ""
}

private fun ChatMessage.conversationPreviewIcon(): ImageVector? = when {
    protection != MessageProtection.ENCRYPTED -> Icons.Outlined.Lock
    attachment?.videoNote == true -> Icons.Outlined.Videocam
    attachment?.mime?.startsWith("image/") == true -> Icons.Outlined.Image
    attachment?.mime?.startsWith("video/") == true -> Icons.Outlined.Videocam
    attachment?.mime?.startsWith("audio/") == true -> Icons.Outlined.MicNone
    attachment != null -> Icons.Outlined.Description
    sharedContact != null -> Icons.Outlined.PersonOutline
    location != null -> Icons.Outlined.LocationOn
    else -> null
}

@Composable
private fun ConversationDeliveryIcon(delivery: MessageDelivery) {
    val label = when (delivery) {
        MessageDelivery.SENDING -> R.string.chat_sending
        MessageDelivery.SENT -> R.string.chat_sent
        MessageDelivery.DELIVERED -> R.string.chat_delivered
        MessageDelivery.READ -> R.string.chat_read
        MessageDelivery.FAILED -> R.string.chat_failed
    }
    Icon(when (delivery) {
        MessageDelivery.SENDING -> Icons.Outlined.Schedule
        MessageDelivery.SENT -> Icons.Outlined.Done
        MessageDelivery.DELIVERED, MessageDelivery.READ -> Icons.Outlined.DoneAll
        MessageDelivery.FAILED -> Icons.Outlined.ErrorOutline
    }, stringResource(label), Modifier.size(16.dp), tint = when (delivery) {
        MessageDelivery.FAILED -> MaterialTheme.colorScheme.error
        MessageDelivery.READ -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    })
}

@Composable
private fun conversationDate(timestamp: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    val date = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return when (date) {
        today -> DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(Date(timestamp))
        today.minusDays(1) -> stringResource(R.string.chat_yesterday)
        else -> DateFormat.getDateInstance(DateFormat.SHORT, locale).format(Date(timestamp))
    }
}
