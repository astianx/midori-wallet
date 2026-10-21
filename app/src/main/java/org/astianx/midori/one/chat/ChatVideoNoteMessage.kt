/* Copyright 2026 Astian. Licensed under the Apache License, Version 2.0. */
package org.astianx.midori.one.chat

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.astianx.midori.one.R
import java.io.File

/** A video note is the message surface itself; it is never wrapped in a rectangular file bubble. */
@Composable
internal fun ChatVideoNoteMessage(message: ChatMessage, audio: ChatAudioController,
    localPath: String?, onDownload: () -> Unit, joinsPrevious: Boolean = false, onRetry: () -> Unit) {
    val attachment = requireNotNull(message.attachment)
    val outgoing = message.outgoing
    val style = LocalChatAppearance.current
    val prefix = "chat-video-note-${message.id}"
    Row(Modifier.fillMaxWidth().padding(start = if (outgoing) 48.dp else 12.dp,
        end = if (outgoing) 12.dp else 48.dp, top = if (joinsPrevious) 4.dp else 10.dp, bottom = 4.dp),
        horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start) {
        if (style.showMessageAvatars && !outgoing) {
            if (!joinsPrevious) ChatAvatar(message.sender.ifBlank { message.peer }, Modifier.size(28.dp))
            else Spacer(Modifier.width(28.dp))
            Spacer(Modifier.width(6.dp))
        }
        Column(Modifier.width(216.dp).testTag("$prefix-message"), horizontalAlignment = Alignment.End) {
            if (!joinsPrevious && !outgoing && message.sender.isNotBlank() && message.sender != message.peer) {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .92f)) {
                    Text(message.sender.substringBefore('@'), style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
                Spacer(Modifier.height(4.dp))
            }
            if (localPath != null) {
                ChatVideoContent(Uri.fromFile(File(localPath)), true, attachment.durationMs, prefix) { audio.pause() }
            } else {
                Box(Modifier.size(216.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                    .testTag(prefix), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FilledIconButton(onClick = onDownload, modifier = Modifier.size(56.dp).testTag("$prefix-download")) {
                            Icon(Icons.Outlined.Download, stringResource(R.string.chat_download), Modifier.size(28.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.chat_video_note), style = MaterialTheme.typography.labelLarge)
                        Text(android.text.format.Formatter.formatShortFileSize(LocalContext.current, attachment.size),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    attachment.durationMs?.takeIf { it in 1..86_400_000L }?.let {
                        Text(audioTime(it.toInt()), style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp).testTag("$prefix-duration"))
                    }
                }
            }
            Spacer(Modifier.height(3.dp))
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .92f)) {
                Row(Modifier.padding(horizontal = 7.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(chatTime(message.timestamp), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (outgoing) {
                        Spacer(Modifier.width(4.dp))
                        val label = when (message.delivery) {
                            MessageDelivery.SENDING -> R.string.chat_sending
                            MessageDelivery.SENT -> R.string.chat_sent
                            MessageDelivery.DELIVERED -> R.string.chat_delivered
                            MessageDelivery.READ -> R.string.chat_read
                            MessageDelivery.FAILED -> R.string.chat_failed
                        }
                        Icon(when (message.delivery) {
                            MessageDelivery.SENDING -> Icons.Outlined.Schedule
                            MessageDelivery.SENT -> Icons.Outlined.Done
                            MessageDelivery.DELIVERED, MessageDelivery.READ -> Icons.Outlined.DoneAll
                            MessageDelivery.FAILED -> Icons.Outlined.ErrorOutline
                        }, stringResource(label), Modifier.size(15.dp), tint = when (message.delivery) {
                            MessageDelivery.READ -> MaterialTheme.colorScheme.primary
                            MessageDelivery.FAILED -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        })
                    }
                }
            }
            // Other compatible clients may attach a caption even though our recorder does not.
            if (message.body.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Surface(shape = RoundedCornerShape(12.dp), color = if (outgoing)
                    MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
                    Text(message.body, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
            if (outgoing && message.delivery == MessageDelivery.FAILED) {
                TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.retry))
                }
            }
        }
    }
}
