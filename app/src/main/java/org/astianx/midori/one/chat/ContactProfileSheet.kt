/* Copyright 2026 Astian. Licensed under the Apache License, Version 2.0. */
package org.astianx.midori.one.chat

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import org.astianx.midori.one.R

@Composable
internal fun PublishedProfileAvatar(profile: PublishedChatProfile?, name: String, modifier: Modifier = Modifier) {
    val appearance = LocalChatAppearance.current
    val bitmap = remember(profile?.photoBase64) { profile?.let(::decodeProfilePhoto)?.asImageBitmap() }
    Box(modifier.size(appearance.avatarSize.coerceIn(40, 64).dp)
        .clip(RoundedCornerShape(appearance.avatarStyle.radius.dp))) {
        ChatAvatar(name, Modifier.fillMaxSize())
        bitmap?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContactProfileSheet(chat: ChatViewModel, peer: String, onDismiss: () -> Unit) {
    val state by chat.state.collectAsState()
    val profile = state.contactProfiles[peer]
    val options = state.conversationOptions[peer] ?: ConversationOptions()
    val name = options.alias.ifBlank { profile?.name?.takeIf(String::isNotBlank) ?: state.contacts.find { it.address == peer }?.name ?: peer.substringBefore('@') }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var showQr by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var localName by remember(peer) { mutableStateOf(options.alias) }
    var localPhone by remember(peer) { mutableStateOf(options.phone) }
    LaunchedEffect(peer) { if (state.connection == ChatConnection.ONLINE) chat.loadContactProfile(peer) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PublishedProfileAvatar(profile, name, Modifier.size(80.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.profile_contact_title), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onDismiss, Modifier.testTag("chat-contact-profile-close")) {
                    Icon(Icons.Outlined.Close, stringResource(R.string.close))
                }
            }
            if (state.profileLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            ChatError(state.error, chat::clearError)
            if (profile?.name?.isNotBlank() == true && options.alias.isNotBlank() && options.alias != profile.name) {
                Text(stringResource(R.string.profile_published_name, profile.name), style = MaterialTheme.typography.bodyMedium)
            }
            Text(profile?.bio?.takeIf(String::isNotBlank) ?: stringResource(R.string.profile_no_bio),
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SelectionContainer { Text(peer, style = MaterialTheme.typography.bodyMedium) }
            if (options.phone.isNotBlank()) SelectionContainer { Text(options.phone, style = MaterialTheme.typography.bodyMedium) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton({ clipboard.setText(AnnotatedString(chatContactLink(peer))) }) { Icon(Icons.Outlined.ContentCopy, stringResource(R.string.profile_copy_link)) }
                IconButton({ showQr = true }) { Icon(Icons.Outlined.QrCode, stringResource(R.string.profile_show_qr)) }
                IconButton({
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"; putExtra(Intent.EXTRA_TEXT, chatContactLink(peer))
                    }, null))
                }) { Icon(Icons.Outlined.Share, stringResource(R.string.profile_share_link)) }
                IconButton({ chat.loadContactProfile(peer) }, enabled = !state.profileLoading && state.connection == ChatConnection.ONLINE) {
                    Icon(Icons.Outlined.Refresh, stringResource(R.string.profile_refresh))
                }
            }
            HorizontalDivider()
            TextButton({ editing = !editing }) { Text(stringResource(R.string.contacts_edit_local)) }
            if (editing) {
                OutlinedTextField(localName, { localName = it.take(60).filterNot(Char::isISOControl) }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.contacts_local_name)) }, singleLine = true)
                OutlinedTextField(localPhone, { value -> localPhone = value.take(40).filter { it.isDigit() || it in "+ ()-." } }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.contacts_local_phone)) }, singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone))
                Text(stringResource(R.string.contacts_phone_local_hint), style = MaterialTheme.typography.bodySmall)
                Button({ chat.saveContactDetails(peer, localName, localPhone); editing = false }, Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.style_save))
                }
            }
            Text(stringResource(R.string.profile_remote_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
        }
    }
    if (showQr) ProfileQrDialog(peer, name) { showQr = false }
}

@Composable
internal fun ProfileQrDialog(address: String, name: String, onDismiss: () -> Unit) {
    val link = remember(address) { chatContactLink(address) }
    val bitmap = remember(link) {
        val matrix = com.google.zxing.qrcode.QRCodeWriter().encode(link, com.google.zxing.BarcodeFormat.QR_CODE, 640, 640,
            mapOf(com.google.zxing.EncodeHintType.MARGIN to 4, com.google.zxing.EncodeHintType.CHARACTER_SET to "UTF-8"))
        val pixels = IntArray(640 * 640) { index -> if (matrix[index % 640, index / 640]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
        android.graphics.Bitmap.createBitmap(pixels, 640, 640, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(name) }, text = {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(bitmap, stringResource(R.string.profile_contact_qr), Modifier.fillMaxWidth().aspectRatio(1f))
            SelectionContainer { Text(address, style = MaterialTheme.typography.bodyMedium) }
            Text(stringResource(R.string.profile_qr_hint), style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onDismiss) { Text(stringResource(R.string.close)) } })
}
