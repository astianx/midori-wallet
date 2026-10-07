package org.astianx.midori.one.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
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
    val photos = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(chat::setProfilePhoto) }
    StylePage(R.string.profile, { navigate(Screen.More) }) {
        ChatError(state.error, chat::clearError)
        if (state.account.isBlank()) {
            Text(stringResource(R.string.profile_start), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.profile_handle_hint), style = MaterialTheme.typography.bodyLarge)
            Button({ navigate(Screen.Messaging) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.profile_account)) }
        } else {
            Box(Modifier.align(Alignment.CenterHorizontally).size(96.dp).clip(MaterialTheme.shapes.large)) {
                ChatAvatar(state.profile.name.ifBlank { state.account }, Modifier.fillMaxSize())
                LocalArtwork(state.profile.photo, Modifier.fillMaxSize())
            }
            TextButton({ photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.align(Alignment.CenterHorizontally)) { Text(stringResource(R.string.style_choose_photo)) }
            if (state.profile.photo != null) TextButton({ chat.removeProfilePhoto() }, Modifier.align(Alignment.CenterHorizontally)) { Text(stringResource(R.string.profile_remove_photo)) }
            Text("@${state.account.substringBefore('@')}", style = MaterialTheme.typography.headlineMedium)
            SelectionContainer { Text(state.account, style = MaterialTheme.typography.bodyLarge) }
            Text(stringResource(R.string.profile_handle_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton({ clipboard.setText(AnnotatedString(state.account)); copied = true }, Modifier.fillMaxWidth()) {
                Text(stringResource(if (copied) R.string.profile_copied else R.string.profile_copy_address))
            }
            OutlinedTextField(name, { name = it.take(60).filterNot(Char::isISOControl) }, label = { Text(stringResource(R.string.profile_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(bio, { bio = it.take(160).filterNot(Char::isISOControl) }, label = { Text(stringResource(R.string.profile_bio)) }, modifier = Modifier.fillMaxWidth(), maxLines = 4)
            Text(stringResource(R.string.profile_local_hint), style = MaterialTheme.typography.bodySmall)
            Button({ chat.saveProfile(name, bio) }, Modifier.fillMaxWidth(), enabled = name.trim() != state.profile.name || bio.trim() != state.profile.bio) { Text(stringResource(R.string.style_save)) }
            TextButton({ navigate(Screen.Messaging) }) { Text(stringResource(R.string.chat_account_title)) }
        }
    }
}
