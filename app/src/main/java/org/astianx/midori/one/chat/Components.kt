package org.astianx.midori.one.chat

import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.astianx.midori.one.R
import org.astianx.midori.one.UiText

@Composable
internal fun ChatAvatar(name: String, modifier: Modifier = Modifier) {
    val style = LocalChatAppearance.current
    Box(modifier.size(style.avatarSize.dp).clip(RoundedCornerShape(style.avatarStyle.radius.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center) {
        Text(name.trim().take(1).uppercase(java.util.Locale.getDefault()), fontSize = 22.sp,
            fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun ConversationRow(contact: ChatContact, last: ChatMessage?, unread: Int, draft: String = "",
    options: ConversationOptions = ConversationOptions(), onLongClick: () -> Unit = {}, onClick: () -> Unit) {
    val menuLabel = stringResource(R.string.chat_options)
    val style = LocalChatAppearance.current
    val name = options.alias.ifBlank { contact.name }
    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp)
        .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = menuLabel)
        .padding(horizontal = 16.dp, vertical = if (style.compact) 8.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        ChatAvatar(name)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 16.sp, fontWeight = if (unread > 0) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(if (!style.showPreviews) contact.address else if (draft.isNotBlank()) stringResource(R.string.chat_draft, draft) else last?.displayBody() ?: contact.address, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                fontSize = 14.sp, color = if (draft.isNotBlank() && style.showPreviews) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            last?.let { Text(chatTime(it.timestamp), fontSize = 12.sp,
                color = if (unread > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (options.pinned) Icon(Icons.Outlined.PushPin, stringResource(R.string.chat_pinned), Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                if (options.muted) Icon(Icons.Outlined.NotificationsOff, stringResource(R.string.chat_muted), Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (unread > 0) {
                Spacer(Modifier.height(5.dp))
                androidx.compose.material3.Badge(containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary) { Text(if (unread > 99) "99+" else unread.toString()) }
            }
        }
    }
}

@Composable
internal fun ChatMessage.displayBody(): String = when (protection) {
    MessageProtection.ENCRYPTED -> if (body.startsWith("/me ")) {
        val actor = if (outgoing) stringResource(R.string.chat_you) else peer.substringBefore('@')
        "$actor ${body.removePrefix("/me ")}"
    } else body.ifBlank { attachment?.name ?: sharedContact ?: location?.let { stringResource(R.string.chat_location) } ?: "" }
    MessageProtection.UNREADABLE -> stringResource(R.string.chat_unreadable)
    MessageProtection.REJECTED_PLAINTEXT -> stringResource(R.string.chat_plaintext_rejected)
}

internal fun chatTime(timestamp: Long): String = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
    .format(java.util.Date(timestamp))

@Composable
internal fun MessageBubble(message: ChatMessage, audio: ChatAudioController, onImage: (String) -> Unit, onDownload: () -> Unit = {}, localPath: String? = null,
    onContact: (String) -> Unit = {}, joinsPrevious: Boolean = false, joinsNext: Boolean = false, onRetry: () -> Unit) {
    val style = LocalChatAppearance.current
    val radius = style.bubbles.radius.dp
    val outgoing = message.outgoing
    val background = if (outgoing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val foreground = if (outgoing) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val shape = RoundedCornerShape(
        topStart = if (!outgoing && joinsPrevious) 6.dp else radius,
        topEnd = if (outgoing && joinsPrevious) 6.dp else radius,
        bottomStart = if (!outgoing) 6.dp else radius,
        bottomEnd = if (outgoing) 6.dp else radius)
    Row(Modifier.fillMaxWidth().padding(start = if (outgoing) 48.dp else 12.dp,
        end = if (outgoing) 12.dp else 48.dp, top = if (joinsPrevious) 2.dp else 8.dp, bottom = 2.dp),
        horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start) {
        if (style.showMessageAvatars && !outgoing) {
            if (!joinsPrevious) ChatAvatar(message.sender.ifBlank { message.peer }, Modifier.size(28.dp)) else Spacer(Modifier.width(28.dp))
            Spacer(Modifier.width(6.dp))
        }
        Column(Modifier.weight(1f, fill = false).widthIn(max = 420.dp).clip(shape).background(background).padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (!joinsPrevious && !outgoing && message.sender.isNotBlank() && message.sender != message.peer) Text(message.sender.substringBefore('@'),
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            message.attachment?.let { ChatFileContent(it, localPath, audio, onImage, onDownload) }
            message.sharedContact?.let { address ->
                Row(Modifier.heightIn(min = 48.dp).clickable { onContact(address) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.PersonOutline, null)
                    Spacer(Modifier.width(8.dp)); Text(address, fontSize = 14.sp) }
            }
            message.location?.let { geo ->
                val context = androidx.compose.ui.platform.LocalContext.current
                TextButton(onClick = { runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(geo))) } }) {
                    Icon(Icons.Outlined.LocationOn, null); Text(stringResource(R.string.chat_view_location))
                }
                Text(geo.removePrefix("geo:"), fontSize = 12.sp)
            }
            if (message.body.isNotBlank() || message.protection != MessageProtection.ENCRYPTED) androidx.compose.foundation.text.selection.SelectionContainer {
                ChatLinkedText(message.displayBody(), foreground)
            }
            if (!joinsNext || outgoing) {
            Spacer(Modifier.height(4.dp))
            Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                Text(chatTime(message.timestamp), fontSize = 11.sp, color = foreground.copy(alpha = 0.75f))
                if (outgoing) {
                    Spacer(Modifier.width(6.dp))
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
                    }, stringResource(label), Modifier.size(15.dp),
                        tint = when (message.delivery) {
                            MessageDelivery.FAILED -> MaterialTheme.colorScheme.error
                            MessageDelivery.READ -> MaterialTheme.colorScheme.primary
                            else -> foreground.copy(alpha = .65f)
                        })
                }
            }
            }
            if (message.delivery == MessageDelivery.FAILED && outgoing) TextButton(onClick = onRetry) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}

@Composable
internal fun ChatError(error: UiText?, onDismiss: () -> Unit) {
    if (error == null) return
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).background(MaterialTheme.colorScheme.errorContainer).padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(error.asString(), modifier = Modifier.weight(1f).padding(vertical = 10.dp), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer)
        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, stringResource(R.string.close),
            tint = MaterialTheme.colorScheme.onErrorContainer) }
    }
}

@Composable
internal fun ChatComposer(
    draft: String, onDraft: (String) -> Unit, enabled: Boolean, expanded: Boolean,
    onExpand: () -> Unit, onEmoji: () -> Unit, onVoice: () -> Unit, onSend: () -> Unit,
    onTyping: () -> Unit
) {
    val style = LocalChatAppearance.current
    val messageLabel = stringResource(R.string.chat_type_message)
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom) {
        Row(Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant), verticalAlignment = Alignment.Bottom) {
            IconButton(onClick = onExpand, enabled = enabled, modifier = Modifier.size(48.dp)) {
                Icon(if (expanded) Icons.Outlined.Close else Icons.Outlined.AttachFile,
                    stringResource(if (expanded) R.string.close else R.string.chat_attach), Modifier.size(24.dp),
                    tint = if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            BasicTextField(value = draft, onValueChange = onDraft,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = if (style.sendOnEnter) androidx.compose.ui.text.input.ImeAction.Send else androidx.compose.ui.text.input.ImeAction.Default),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = { if (enabled && draft.isNotBlank()) onSend() }),
                modifier = Modifier.weight(1f).semantics { contentDescription = messageLabel }.onPreviewKeyEvent {
                    if (style.sendOnEnter && it.key == Key.Enter && !it.isShiftPressed) {
                        if (it.type == KeyEventType.KeyUp && enabled && draft.isNotBlank()) onSend()
                        true
                    } else false
                }.onFocusChanged { if (it.isFocused) onTyping() }.padding(vertical = 13.dp), maxLines = 5,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { inner ->
                    Box { if (draft.isEmpty()) Text(messageLabel, style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        inner() }
                })
            IconButton(onClick = onEmoji, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Outlined.SentimentSatisfiedAlt, stringResource(R.string.chat_emoji),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(8.dp))
        androidx.compose.material3.FilledIconButton(onClick = if (draft.isNotBlank()) onSend else onVoice, enabled = enabled,
            modifier = Modifier.size(48.dp)) {
            Icon(if (draft.isNotBlank()) Icons.AutoMirrored.Filled.Send else Icons.Outlined.MicNone,
                stringResource(if (draft.isNotBlank()) R.string.send else R.string.chat_record), Modifier.size(24.dp),
                tint = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun ChatAttachmentTray(onPhoto: () -> Unit, onCamera: () -> Unit, onFile: () -> Unit, onContact: () -> Unit, onLocation: () -> Unit) {
    val actions = listOf(Triple(Icons.Outlined.Image, R.string.chat_photos, onPhoto),
            Triple(Icons.Outlined.PhotoCamera, R.string.chat_camera, onCamera),
            Triple(Icons.Outlined.FolderOpen, R.string.chat_files, onFile),
            Triple(Icons.Outlined.PeopleOutline, R.string.chat_contact, onContact),
            Triple(Icons.Outlined.LocationOn, R.string.chat_location, onLocation))
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 12.dp, vertical = 8.dp)) {
        actions.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { (icon, label, action) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)
                        .clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = action).padding(vertical = 12.dp)) {
                        Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, maxLines = 2,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
internal fun ChatEmojiTray(onSelect: (String) -> Unit) {
    val emoji = listOf("😀", "😊", "😂", "🥰", "😎", "🤔", "😢", "🎉", "👍", "❤️", "🙏", "👋", "🔥", "✨", "✅", "👏")
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(12.dp)) {
        emoji.chunked(6).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { value -> TextButton(onClick = { onSelect(value) }, modifier = Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text(value, fontSize = LocalChatAppearance.current.emojiSize.sp) } }
            }
        }
    }
}

@Composable
internal fun ChatFileContent(file: ChatAttachment, path: String?, audio: ChatAudioController,
    onImage: (String) -> Unit, onDownload: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val image by androidx.compose.runtime.produceState<Bitmap?>(null, path) {
        value = if (path != null && file.mime.startsWith("image/")) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { ChatPhotoEditor.decode(context, android.net.Uri.fromFile(java.io.File(path)), 1000) }.getOrNull()
        } else null
    }
    if (path != null && file.mime.startsWith("audio/")) {
        ChatAudioPlayer(path, audio)
        return
    }
    image?.let { bitmap -> Image(bitmap.asImageBitmap(), stringResource(R.string.chat_view_image),
        Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(8.dp)).clickable { onImage(requireNotNull(path)) }, contentScale = ContentScale.Crop) }
    if (image == null) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
        Icon(if (file.mime.startsWith("audio/")) Icons.Outlined.GraphicEq else Icons.Outlined.InsertDriveFile, null, Modifier.size(28.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(file.name, fontSize = 14.sp, maxLines = 2, fontWeight = FontWeight.Medium)
            Text(android.text.format.Formatter.formatShortFileSize(context, file.size), fontSize = 11.sp)
        }
    }
    if (path == null) TextButton(onClick = onDownload) { Text(stringResource(R.string.chat_download)) }
    else if (file.mime.startsWith("image/")) TextButton(onClick = { onImage(path) }) { Text(stringResource(R.string.chat_view_image)) }
    else TextButton(onClick = { if (!ChatPlatform.open(context, path, file.mime))
        android.widget.Toast.makeText(context, R.string.chat_no_viewer, android.widget.Toast.LENGTH_SHORT).show() }) {
        Text(stringResource(R.string.chat_open_file))
    }
}

@Composable
internal fun rememberChatAudioController(peer: String): ChatAudioController {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    val controller = remember(peer) { ChatAudioController(context.applicationContext) }
    androidx.compose.runtime.DisposableEffect(controller, lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE || event == androidx.lifecycle.Lifecycle.Event.ON_STOP) controller.pause()
        }
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) { controller.pause() }
        }
        context.registerReceiver(receiver, android.content.IntentFilter(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY), android.content.Context.RECEIVER_NOT_EXPORTED)
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); context.unregisterReceiver(receiver); controller.release() }
    }
    androidx.compose.runtime.LaunchedEffect(controller.playing) {
        while (controller.playing) { controller.updatePosition(); kotlinx.coroutines.delay(200) }
    }
    return controller
}

@Composable
internal fun ChatAudioPlayer(path: String, controller: ChatAudioController) {
    val preview by androidx.compose.runtime.produceState(AudioPreview(0), path) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { ChatWaveform.read(path) }
    }
    val active = controller.path == path
    val playing = active && controller.playing
    val duration = if (active && controller.duration > 0) controller.duration else preview.duration
    val position = if (active) controller.position else 0
    val progress = if (duration > 0) position.toFloat() / duration else 0f
    val seekLabel = stringResource(R.string.chat_audio_seek)
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .35f)
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.FilledIconButton(onClick = { controller.toggle(path) }, Modifier.size(48.dp)) {
            if (active && controller.loading) androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Icon(if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                stringResource(if (playing) R.string.chat_pause else R.string.chat_play))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Box(Modifier.fillMaxWidth().height(44.dp)) {
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize().padding(vertical = 8.dp)) {
                    if (preview.peaks.isEmpty()) drawLine(track, androidx.compose.ui.geometry.Offset(0f, size.height / 2),
                        androidx.compose.ui.geometry.Offset(size.width, size.height / 2), 3.dp.toPx())
                    else preview.peaks.forEachIndexed { index, peak ->
                        val x = (index + .5f) * size.width / preview.peaks.size
                        val height = maxOf(2.dp.toPx(), peak * size.height)
                        drawLine(if (index.toFloat() / preview.peaks.size <= progress && progress > 0) accent else track,
                            androidx.compose.ui.geometry.Offset(x, (size.height - height) / 2),
                            androidx.compose.ui.geometry.Offset(x, (size.height + height) / 2), 2.dp.toPx(), androidx.compose.ui.graphics.StrokeCap.Round)
                    }
                }
                // Transparent track keeps native slider touch/keyboard/TalkBack seeking over the real waveform.
                androidx.compose.material3.Slider(progress, { controller.seek(it) }, enabled = active && controller.duration > 0,
                    modifier = Modifier.fillMaxSize().semantics { contentDescription = seekLabel },
                    colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = accent,
                        activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent,
                        disabledActiveTrackColor = Color.Transparent, disabledInactiveTrackColor = Color.Transparent,
                        disabledThumbColor = Color.Transparent))
            }
            Text("${audioTime(position)} / ${audioTime(duration)}", fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (active && controller.failed) Text(stringResource(R.string.chat_file_unavailable), fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
}

@Composable
private fun ChatLinkedText(value: String, color: Color) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(value, linkColor) { androidx.compose.ui.text.buildAnnotatedString {
        append(value)
        Regex("https?://[^\\s<>]+").findAll(value).forEach { match ->
            val url = match.value.trimEnd('.', ',', ')', ';', '!')
            addStringAnnotation("url", url, match.range.first, match.range.first + url.length)
            addStyle(androidx.compose.ui.text.SpanStyle(color = linkColor,
                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline), match.range.first, match.range.first + url.length)
        }
    } }
    androidx.compose.foundation.text.ClickableText(annotated,
        style = MaterialTheme.typography.bodyLarge.copy(color = color, fontSize = LocalChatAppearance.current.fontSize.sp, lineHeight = (LocalChatAppearance.current.fontSize + 6).sp), onClick = { offset ->
            annotated.getStringAnnotations("url", offset, offset).firstOrNull()?.let { link ->
                runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(link.item))) }
            }
        })
}

@Composable
internal fun ChatSelectionPreview(selection: MediaSelection, audio: ChatAudioController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val image by androidx.compose.runtime.produceState<Bitmap?>(null, selection.uri) {
        if (selection.mime.startsWith("image/")) value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { ChatPlatform.preview(context, android.net.Uri.parse(selection.uri)) }.getOrNull()
        }
    }
    image?.let { Image(it.asImageBitmap(), stringResource(R.string.chat_file_preview),
        Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Fit) }
    if (selection.mime.startsWith("audio/") && android.net.Uri.parse(selection.uri).scheme == "file")
        android.net.Uri.parse(selection.uri).path?.let { ChatAudioPlayer(it, audio) }
}

internal enum class PhotoTool { PREVIEW, CROP, DRAW, TEXT }

@Composable
internal fun PhotoEditCanvas(bitmap: Bitmap, edits: PhotoEdits, tool: PhotoTool, color: Int,
    cropSelection: PhotoRect, onCrop: (PhotoRect) -> Unit, onStroke: (PhotoMark.Stroke) -> Unit,
    onTextPosition: (PhotoPoint) -> Unit, modifier: Modifier = Modifier) {
    var stroke by remember { mutableStateOf<List<PhotoPoint>>(emptyList()) }
    val visibleCrop = if (tool == PhotoTool.CROP) PhotoRect() else edits.crop
    val rendered = edits.copy(crop = visibleCrop, marks = if (stroke.isEmpty()) edits.marks else edits.marks + PhotoMark.Stroke(stroke, color))
    val currentStroke by androidx.compose.runtime.rememberUpdatedState(onStroke)
    val currentCrop by androidx.compose.runtime.rememberUpdatedState(onCrop)
    val currentText by androidx.compose.runtime.rememberUpdatedState(onTextPosition)
    val description = stringResource(R.string.chat_file_preview)
    androidx.compose.foundation.Canvas(modifier.aspectRatio(bitmap.width * visibleCrop.width / (bitmap.height * visibleCrop.height))
        .semantics { contentDescription = description }
        .pointerInput(tool, visibleCrop, color) {
            var origin = PhotoPoint(0f, 0f)
            detectDragGestures(
                onDragStart = { p ->
                    origin = visibleCrop.point(p.x / size.width, p.y / size.height)
                    if (tool == PhotoTool.DRAW) stroke = listOf(origin)
                }, onDragEnd = {
                    if (stroke.isNotEmpty()) currentStroke(PhotoMark.Stroke(stroke, color))
                    stroke = emptyList()
                }, onDragCancel = { stroke = emptyList() }, onDrag = { change, _ ->
                    if (tool in setOf(PhotoTool.CROP, PhotoTool.DRAW)) {
                        change.consume()
                        val point = visibleCrop.point(change.position.x / size.width, change.position.y / size.height)
                        if (tool == PhotoTool.CROP) PhotoRect.between(origin, point)?.let(currentCrop)
                        if (tool == PhotoTool.DRAW && stroke.size < 2048) stroke = stroke + point
                    }
                })
        }.pointerInput(tool, visibleCrop) {
            detectTapGestures { p ->
                if (tool == PhotoTool.TEXT) currentText(visibleCrop.point(p.x / size.width, p.y / size.height))
            }
        }) {
        drawIntoCanvas { ChatPhotoEditor.draw(it.nativeCanvas, bitmap, rendered, size.width, size.height) }
        if (tool == PhotoTool.CROP) {
            val left = cropSelection.left * size.width; val top = cropSelection.top * size.height
            val right = cropSelection.right * size.width; val bottom = cropSelection.bottom * size.height
            drawRect(Color.Black.copy(alpha = .55f), size = androidx.compose.ui.geometry.Size(size.width, top))
            drawRect(Color.Black.copy(alpha = .55f), androidx.compose.ui.geometry.Offset(0f, bottom), androidx.compose.ui.geometry.Size(size.width, size.height - bottom))
            drawRect(Color.Black.copy(alpha = .55f), androidx.compose.ui.geometry.Offset(0f, top), androidx.compose.ui.geometry.Size(left, bottom - top))
            drawRect(Color.Black.copy(alpha = .55f), androidx.compose.ui.geometry.Offset(right, top), androidx.compose.ui.geometry.Size(size.width - right, bottom - top))
            drawRect(Color.White, androidx.compose.ui.geometry.Offset(left, top), androidx.compose.ui.geometry.Size(right - left, bottom - top),
                style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
            for (fraction in listOf(1f / 3, 2f / 3)) {
                drawLine(Color.White.copy(alpha = .5f), androidx.compose.ui.geometry.Offset(left + (right - left) * fraction, top), androidx.compose.ui.geometry.Offset(left + (right - left) * fraction, bottom))
                drawLine(Color.White.copy(alpha = .5f), androidx.compose.ui.geometry.Offset(left, top + (bottom - top) * fraction), androidx.compose.ui.geometry.Offset(right, top + (bottom - top) * fraction))
            }
        }
    }
}

@Composable
internal fun ChatContextAction(icon: ImageVector, @StringRes label: Int, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(24.dp)); Spacer(Modifier.width(20.dp))
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun MessagingFeature(icon: ImageVector, @StringRes title: Int, @StringRes description: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 2.dp).size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(description), fontSize = 13.sp, lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
