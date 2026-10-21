/* Copyright 2026 Astian. Licensed under the Apache License, Version 2.0. */
package org.astianx.midori.one.chat

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.astianx.midori.one.R
import org.astianx.midori.one.UiText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatAttachmentReviewSheet(selection: MediaSelection, draft: String, busy: Boolean,
    progress: Int?, error: UiText?, audio: ChatAudioController, onDismiss: () -> Unit,
    onSend: (String) -> Unit, onClearError: () -> Unit) {
    var caption by remember(selection.uri) { mutableStateOf(draft) }
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true,
            confirmValueChange = { !busy || it != SheetValue.Hidden })) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.82f).imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.chat_attachment_review), style = MaterialTheme.typography.titleLarge)
            ChatSelectionPreview(selection, audio)
            Text(selection.name, style = MaterialTheme.typography.titleMedium)
            Text(if (selection.size >= 0) android.text.format.Formatter.formatShortFileSize(context, selection.size) else selection.mime,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(caption, { caption = it.take(8_000) }, enabled = !busy,
                modifier = Modifier.fillMaxWidth(), maxLines = 4,
                label = { Text(stringResource(R.string.chat_attachment_caption)) })
            progress?.let { LinearProgressIndicator(it / 100f, Modifier.fillMaxWidth()) }
            ChatError(error, onClearError)
            Button(onClick = { onSend(caption) }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Icon(Icons.AutoMirrored.Filled.Send, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.send))
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
internal fun ChatVideoNoteReview(selection: MediaSelection, busy: Boolean, audio: ChatAudioController,
    onDiscard: () -> Unit, onSend: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        ChatVideoContent(Uri.parse(selection.uri), true, selection.durationMs) { audio.pause() }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDiscard, enabled = !busy, modifier = Modifier.size(48.dp).testTag("chat-video-note-discard")) {
                Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.chat_video_cancel))
            }
            Text(stringResource(R.string.chat_video_note), modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium)
            FilledIconButton(onClick = onSend, enabled = !busy, modifier = Modifier.size(48.dp).testTag("chat-video-note-send")) {
                Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.send))
            }
        }
    }
}
