package org.astianx.midori.one.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.astianx.midori.one.*
import org.astianx.midori.one.R

@Composable
internal fun ChatAppearanceScreen(chat: ChatViewModel, peer: String? = null, back: () -> Unit) {
    val state by chat.state.collectAsState()
    val style = state.appearanceFor(peer)
    val save: (ChatAppearance) -> Unit = { chat.setAppearance(it, peer) }
    val photos = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { chat.importWallpaper(it, peer) }
    }
    var alias by rememberSaveable(peer) { mutableStateOf(state.conversationOptions[peer]?.alias.orEmpty()) }
    StylePage(if (peer == null) R.string.style_chats else R.string.style_conversation, back) {
        ChatError(state.error, chat::clearError)
        Text(stringResource(if (peer == null) R.string.style_global_hint else R.string.style_override_hint), style = MaterialTheme.typography.bodySmall)
        CompositionLocalProvider(LocalChatAppearance provides style) {
            ChatStylePreview()
        }
        if (peer != null) {
            OutlinedTextField(alias, { alias = it.take(60).filterNot(Char::isISOControl) }, label = { Text(stringResource(R.string.style_alias)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            TextButton({ chat.setConversationAlias(peer, alias) }, enabled = alias.trim() != state.conversationOptions[peer]?.alias.orEmpty()) { Text(stringResource(R.string.style_save)) }
        }
        StyleChoices(R.string.chat_wallpaper_title, ChatWallpaperStyle.entries.filter { it != ChatWallpaperStyle.PHOTO || style.photo != null }, style.wallpaper,
            { stringResource(it.label) }) { save(style.copy(wallpaper = it)) }
        OutlinedButton({ photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.style_choose_photo)) }
        Text(stringResource(R.string.style_photo_hint), style = MaterialTheme.typography.bodySmall)
        if (style.wallpaper != ChatWallpaperStyle.PLAIN) {
            StyleSlider(R.string.style_opacity, style.wallpaperOpacity, 5..60) { save(style.copy(wallpaperOpacity = it)) }
        }
        StyleChoices(R.string.style_bubbles, BubbleStyle.entries, style.bubbles, { stringResource(it.label) }) { save(style.copy(bubbles = it)) }
        StyleSlider(R.string.style_message_size, style.fontSize, 13..24) { save(style.copy(fontSize = it)) }
        StyleChoices(R.string.style_avatars, AvatarStyle.entries, style.avatarStyle, { stringResource(it.label) }) { save(style.copy(avatarStyle = it)) }
        StyleSlider(R.string.style_avatar_size, style.avatarSize, 40..64) { save(style.copy(avatarSize = it)) }
        StyleToggle(R.string.style_group_bubbles, style.groupBubbles) { save(style.copy(groupBubbles = it)) }
        StyleToggle(R.string.style_message_avatars, style.showMessageAvatars) { save(style.copy(showMessageAvatars = it)) }
        StyleSlider(R.string.style_emoji_size, style.emojiSize, 20..36) { save(style.copy(emojiSize = it)) }
        StyleToggle(R.string.style_enter_send, style.sendOnEnter) { save(style.copy(sendOnEnter = it)) }
        Text(stringResource(R.string.style_enter_hint), style = MaterialTheme.typography.bodySmall)
        if (peer == null) {
            HorizontalDivider()
            Text(stringResource(R.string.chats), style = MaterialTheme.typography.titleMedium)
            StyleToggle(R.string.style_compact, style.compact) { save(style.copy(compact = it)) }
            StyleToggle(R.string.style_previews, style.showPreviews) { save(style.copy(showPreviews = it)) }
            StyleToggle(R.string.style_presence, style.showPresence) { save(style.copy(showPresence = it)) }
            Text(stringResource(R.string.style_presence_hint), style = MaterialTheme.typography.bodySmall)
        }
        TextButton({ if (peer == null) save(ChatAppearance()) else chat.resetConversationAppearance(peer) }) {
            Text(stringResource(if (peer == null) R.string.style_reset else R.string.style_inherit))
        }
    }
}

@Composable
internal fun ChatStylePreview() {
    val style = LocalChatAppearance.current
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        ChatWallpaper(Modifier.matchParentSize())
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.style_preview), style = MaterialTheme.typography.labelSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChatAvatar("M", Modifier.size(36.dp))
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
            }
            Text(stringResource(R.string.style_message_in), Modifier.clip(RoundedCornerShape(style.bubbles.radius.dp))
                .background(MaterialTheme.colorScheme.surface).padding(horizontal = 12.dp, vertical = 8.dp), fontSize = style.fontSize.sp, lineHeight = (style.fontSize + 6).sp)
            Text(stringResource(R.string.style_message_out), Modifier.align(Alignment.End).clip(RoundedCornerShape(style.bubbles.radius.dp))
                .background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 12.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontSize = style.fontSize.sp, lineHeight = (style.fontSize + 6).sp)
        }
    }
}
