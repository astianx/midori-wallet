package org.astianx.midori.one.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.Share
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.astianx.midori.one.*
import org.astianx.midori.one.R

@Composable
internal fun ProfileScreen(chat: ChatViewModel, navigate: (Screen) -> Unit) {
    val state by chat.state.collectAsState()
    var name by rememberSaveable(state.account, state.profile.name) { mutableStateOf(state.profile.name) }
    var bio by rememberSaveable(state.account, state.profile.bio) { mutableStateOf(state.profile.bio) }
    var copied by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showQr by remember { mutableStateOf(false) }
    var confirmPublish by remember { mutableStateOf(false) }
    var confirmWithdraw by remember { mutableStateOf(false) }
    LaunchedEffect(state.account, state.connection) {
        if (state.account.isNotBlank() && state.connection == ChatConnection.ONLINE) chat.refreshPublishedProfile()
    }
    val photos = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(chat::setProfilePhoto) }
    StylePage(R.string.profile, { navigate(Screen.More) }) {
        ChatError(state.error, chat::clearError)
        if (state.account.isBlank()) {
            Text(stringResource(R.string.profile_start), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.profile_handle_hint), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button({ navigate(Screen.Messaging) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.profile_account)) }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(72.dp).clip(CircleShape)) {
                    ChatAvatar(state.profile.name.ifBlank { state.account }, Modifier.fillMaxSize())
                    LocalArtwork(state.profile.photo, Modifier.fillMaxSize())
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(state.profile.name.ifBlank { state.account.substringBefore('@') },
                        style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("@${state.account.substringBefore('@')}", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            TextButton({ photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text(stringResource(R.string.style_choose_photo)) }
            if (state.profile.photo != null) TextButton({ chat.removeProfilePhoto() }) { Text(stringResource(R.string.profile_remove_photo)) }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SelectionContainer { Text(state.account, style = MaterialTheme.typography.bodyLarge) }
            Text(stringResource(R.string.profile_handle_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton({ clipboard.setText(AnnotatedString(state.account)); copied = true }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (copied) R.string.profile_copied else R.string.profile_copy_address))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ showQr = true }, Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.QrCode, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.profile_show_qr))
                }
                OutlinedButton({ context.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, chatContactLink(state.account))
                }, null)) }, Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.Share, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.profile_share_link))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(stringResource(R.string.profile_edit_hint), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(name, { name = it.take(60).filterNot(Char::isISOControl) }, label = { Text(stringResource(R.string.profile_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(bio, { bio = it.take(160).filterNot(Char::isISOControl) }, label = { Text(stringResource(R.string.profile_bio)) }, modifier = Modifier.fillMaxWidth(), maxLines = 4)
            Button({ chat.saveProfile(name, bio) }, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = name.trim() != state.profile.name || bio.trim() != state.profile.bio) { Text(stringResource(R.string.style_save)) }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(stringResource(if (state.publishedProfile == null) R.string.profile_not_published else R.string.profile_published),
                style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.profile_visibility_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.profileLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            Button({ confirmPublish = true }, Modifier.fillMaxWidth().heightIn(min = 48.dp),
                enabled = !state.profileLoading && state.connection == ChatConnection.ONLINE) { Text(stringResource(R.string.profile_publish)) }
            if (state.publishedProfile != null) TextButton({ confirmWithdraw = true }, enabled = !state.profileLoading) {
                Text(stringResource(R.string.profile_withdraw))
            }
            TextButton({ navigate(Screen.Messaging) }) { Text(stringResource(R.string.chat_account_title)) }
        }
    }
    if (showQr && state.account.isNotBlank()) ProfileQrDialog(state.account, name.ifBlank { state.account.substringBefore('@') }) { showQr = false }
    if (confirmPublish) AlertDialog(onDismissRequest = { confirmPublish = false }, title = { Text(stringResource(R.string.profile_publish)) },
        text = { Text(stringResource(R.string.profile_publish_consent)) }, confirmButton = { TextButton({
            confirmPublish = false
            scope.launch {
                chat.saveProfile(name, bio).join()
                if (chat.state.value.error == null) chat.publishProfile()
            }
        }) { Text(stringResource(R.string.profile_publish)) } }, dismissButton = { TextButton({ confirmPublish = false }) { Text(stringResource(R.string.cancel)) } })
    if (confirmWithdraw) AlertDialog(onDismissRequest = { confirmWithdraw = false }, title = { Text(stringResource(R.string.profile_withdraw)) },
        text = { Text(stringResource(R.string.profile_withdraw_hint)) }, confirmButton = { TextButton({ confirmWithdraw = false; chat.withdrawProfile() }) {
            Text(stringResource(R.string.profile_withdraw))
        } }, dismissButton = { TextButton({ confirmWithdraw = false }) { Text(stringResource(R.string.cancel)) } })
}
