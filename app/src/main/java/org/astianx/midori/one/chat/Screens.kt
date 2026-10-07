package org.astianx.midori.one.chat

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.astianx.midori.one.AppSearchField
import org.astianx.midori.one.BuildConfig
import org.astianx.midori.one.IntroSlide
import org.astianx.midori.one.MainHeader
import org.astianx.midori.one.OnboardingArtwork
import org.astianx.midori.one.R
import org.astianx.midori.one.Screen
import org.astianx.midori.one.ScreenHeader
import org.astianx.midori.one.UiText

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(chat: ChatViewModel, onNavigate: (Screen) -> Unit, onScan: () -> Unit) {
    val s by chat.state.collectAsState()
    var filter by rememberSaveable { mutableStateOf(ChatListFilter.ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    var newChat by rememberSaveable { mutableStateOf(false) }
    var chooseNew by rememberSaveable { mutableStateOf(false) }
    var newGroup by rememberSaveable { mutableStateOf(false) }
    var contextPeer by rememberSaveable { mutableStateOf<String?>(null) }
    var deletePeer by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        MainHeader(stringResource(R.string.chats),
            onScan = onScan, onProfile = { onNavigate(Screen.Profile) })
        AppSearchField(query, { query = it }, R.string.search_chats)
        Row(Modifier.horizontalScroll(rememberScrollState()).fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChatListFilter.entries.forEach { option ->
                androidx.compose.material3.FilterChip(selected = filter == option, onClick = { filter = option },
                    label = { Text(stringResource(option.label), style = MaterialTheme.typography.labelMedium) },
                    border = null, shape = RoundedCornerShape(12.dp),
                    colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer))
            }
        }
        if (s.account.isNotBlank()) ConnectionStatus(s) { onNavigate(Screen.Messaging) }
        ChatError(s.error, chat::clearError)
        Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
            s.invitations.forEach { invitation ->
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).padding(16.dp)) {
                    Text(stringResource(R.string.chat_group_invitation, invitation.inviter), fontSize = 13.sp)
                    Row {
                        TextButton(onClick = { scope.launch { chat.acceptInvitation(invitation).join(); if (chat.state.value.selectedPeer == invitation.room) onNavigate(Screen.Conversation) } }) { Text(stringResource(R.string.chat_group_join)) }
                        TextButton(onClick = { chat.declineInvitation(invitation) }) { Text(stringResource(R.string.chat_decline)) }
                    }
                }
            }
            s.requests.forEach { peer ->
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text(stringResource(R.string.chat_contact_request), fontSize = 12.sp)
                    Text(peer, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row {
                        TextButton(onClick = { chat.answerSubscription(peer, true) }) { Text(stringResource(R.string.chat_accept)) }
                        TextButton(onClick = { chat.answerSubscription(peer, false) }) { Text(stringResource(R.string.chat_decline)) }
                    }
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val rows = s.visibleConversations(query, filter)
            if (rows.isEmpty()) {
                Column(Modifier.align(Alignment.Center).padding(horizontal = 38.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    OnboardingArtwork(IntroSlide.MESSAGING, Modifier.fillMaxWidth().height(150.dp))
                    Spacer(Modifier.height(24.dp))
                    Text(stringResource(if (query.isBlank() && filter == ChatListFilter.ALL) R.string.chats_empty_title else R.string.no_results),
                        fontSize = 23.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(if (filter == ChatListFilter.ALL && query.isBlank()) R.string.chats_empty_body else R.string.chat_filter_empty), fontSize = 15.sp, lineHeight = 22.sp,
                        textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { if (s.account.isBlank()) onNavigate(Screen.Messaging) else chooseNew = true }) {
                        Text(stringResource(if (s.account.isBlank()) R.string.chat_connect else R.string.new_chat))
                    }
                }
            } else {
                androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 88.dp)) {
                    items(rows.size, key = { rows[it].address }) { index ->
                        val contact = rows[index]
                        ConversationRow(contact, s.messages.lastOrNull { it.peer == contact.address },
                            s.messages.count { it.peer == contact.address && !it.read && !it.outgoing }, s.drafts[contact.address].orEmpty(),
                            s.conversationOptions[contact.address] ?: ConversationOptions(), onLongClick = { contextPeer = contact.address }) {
                            scope.launch { chat.open(contact.address).join(); onNavigate(Screen.Conversation) }
                        }
                    }
                }
            }
            if (s.account.isNotBlank()) androidx.compose.material3.FloatingActionButton(onClick = { chooseNew = true },
                containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)) {
                Icon(Icons.Outlined.AddComment, stringResource(R.string.new_chat))
            }
        }
    }
    contextPeer?.let { address ->
        val options = s.conversationOptions[address] ?: ConversationOptions()
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { contextPeer = null }) {
            Text(s.contacts.find { it.address == address }?.name ?: address, Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            ChatContextAction(Icons.Outlined.PushPin, if (options.pinned) R.string.chat_unpin else R.string.chat_pin) {
                chat.pinConversation(address, !options.pinned); contextPeer = null
            }
            ChatContextAction(Icons.Outlined.NotificationsOff, if (options.muted) R.string.chat_unmute else R.string.chat_mute) {
                chat.muteConversation(address, !options.muted); contextPeer = null
            }
            ChatContextAction(Icons.Outlined.DeleteOutline, R.string.chat_delete_local) { deletePeer = address; contextPeer = null }
            Spacer(Modifier.height(20.dp))
        }
    }
    deletePeer?.let { address -> AlertDialog(onDismissRequest = { deletePeer = null },
        title = { Text(stringResource(R.string.chat_delete_local)) },
        text = { Text(stringResource(R.string.chat_delete_local_body)) },
        confirmButton = { TextButton(onClick = { chat.deleteConversation(address); deletePeer = null }) { Text(stringResource(R.string.chat_delete_local)) } },
        dismissButton = { TextButton(onClick = { deletePeer = null }) { Text(stringResource(R.string.cancel)) } }) }
    if (chooseNew) AlertDialog(onDismissRequest = { chooseNew = false }, title = { Text(stringResource(R.string.new_chat)) },
        text = { Column {
            TextButton(onClick = { chooseNew = false; newChat = true }) { Icon(Icons.Outlined.ChatBubbleOutline, null); Spacer(Modifier.width(12.dp)); Text(stringResource(R.string.new_chat)) }
            TextButton(onClick = { chooseNew = false; newGroup = true }) { Icon(Icons.Outlined.GroupAdd, null); Spacer(Modifier.width(12.dp)); Text(stringResource(R.string.chat_group_new)) }
        } }, confirmButton = { TextButton(onClick = { chooseNew = false }) { Text(stringResource(R.string.close)) } })
    if (newGroup) CreateGroupDialog(chat, onClose = { newGroup = false }, onCreated = { newGroup = false; onNavigate(Screen.Conversation) })
    if (newChat) NewChatDialog(chat, onDismiss = { newChat = false }, onOpen = { onNavigate(Screen.Conversation) })
}

@Composable
private fun NewChatDialog(chat: ChatViewModel, onDismiss: () -> Unit, onOpen: () -> Unit) {
    var address by androidx.compose.runtime.remember { mutableStateOf("") }
    var invalid by androidx.compose.runtime.remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.new_chat)) }, text = {
        Column {
            androidx.compose.material3.OutlinedTextField(value = address, onValueChange = { address = it; invalid = false },
                label = { Text(stringResource(R.string.profile_contact_handle)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), isError = invalid)
            if (invalid) Text(stringResource(R.string.chat_invalid_address), color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(onClick = {
        val peer = runCatching { resolveChatHandle(address, chat.state.value.account) }.getOrNull()
        if (peer == null) invalid = true else scope.launch {
            chat.open(peer).join(); onDismiss(); onOpen()
        }
    }) { Text(stringResource(R.string.chat_start_conversation)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
fun MessagingScreen(chat: ChatViewModel, onNavigate: (Screen) -> Unit, returnScreen: Screen) {
    val backgroundLabel = stringResource(R.string.chat_background_title)
    val s by chat.state.collectAsState()
    val context = LocalContext.current
    val notificationPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
    var address by rememberSaveable { mutableStateOf("") }
    // Credentials stay in memory until the authenticated connection is saved in the encrypted vault.
    var password by androidx.compose.runtime.remember { mutableStateOf("") }
    var host by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("5222") }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var registering by rememberSaveable { mutableStateOf(false) }
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    val busy = s.busy || s.connection in setOf(ChatConnection.CONNECTING, ChatConnection.SECURING)
    val back = { onNavigate(returnScreen) }
    BackHandler(onBack = back)
    LaunchedEffect(s.connection) { if (s.connection == ChatConnection.ONLINE) { password = ""; editing = false } }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.chat_account_title), back)
        ChatError(s.error, chat::clearError)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp)) {
            if (s.account.isBlank() || editing) {
                Icon(Icons.Outlined.ChatBubbleOutline, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
                Spacer(Modifier.height(18.dp))
                Text(stringResource(R.string.chat_account_body), fontSize = 16.sp, lineHeight = 24.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (s.account.isBlank()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.FilterChip(!registering, { registering = false }, label = { Text(stringResource(R.string.chat_connect)) }, enabled = !busy)
                    androidx.compose.material3.FilterChip(registering, { registering = true }, label = { Text(stringResource(R.string.profile_register)) }, enabled = !busy)
                }
                if (registering) Text(stringResource(R.string.profile_registration_hint), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(22.dp))
                androidx.compose.material3.OutlinedTextField(address, { address = it }, singleLine = true,
                    label = { Text(stringResource(R.string.chat_account_address)) }, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), enabled = !busy)
                Spacer(Modifier.height(12.dp))
                androidx.compose.material3.OutlinedTextField(password, { password = it }, singleLine = true,
                    label = { Text(stringResource(R.string.chat_password)) }, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), enabled = !busy)
                TextButton(onClick = { advanced = !advanced }) { Text(stringResource(R.string.chat_advanced)) }
                if (advanced) {
                    androidx.compose.material3.OutlinedTextField(host, { host = it }, singleLine = true,
                        label = { Text(stringResource(R.string.chat_server)) }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedTextField(port, { port = it }, singleLine = true,
                        label = { Text(stringResource(R.string.chat_port)) }, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                if (BuildConfig.DEBUG && BuildConfig.CHAT_DEV_CERT_SHA256.isNotEmpty()) {
                    TextButton(onClick = { host = "10.0.2.2"; advanced = true; if (address.isBlank()) address = "@localhost" }) {
                        Text(stringResource(R.string.chat_local_lab))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = { if (registering) chat.register(address, password, host.ifBlank { address.substringAfter('@') }, port)
                    else chat.connect(address, password, host.ifBlank { address.substringAfter('@') }, port) },
                    enabled = !busy && address.isNotBlank() && password.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(stringResource(if (busy) R.string.profile_working else if (registering) R.string.profile_register else R.string.chat_connect))
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChatAvatar(s.account)
                    Spacer(Modifier.width(16.dp))
                    Column { Text(s.account, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                        Text(stringResource(s.connection.label()), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                if (busy) { Spacer(Modifier.height(20.dp)); androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if (s.connection in setOf(ChatConnection.DISCONNECTED, ChatConnection.ERROR)) {
                    TextButton(onClick = { chat.retry() }) { Text(stringResource(R.string.retry)) }
                    TextButton(onClick = { address = s.account; password = ""; editing = true }) {
                        Text(stringResource(R.string.chat_advanced))
                    }
                }
                Spacer(Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.chat_background_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.chat_background_body), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    androidx.compose.material3.Switch(s.backgroundConnection, { chat.setBackgroundConnection(it) },
                        modifier = Modifier.semantics { contentDescription = backgroundLabel })
                }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(if (s.connectionServiceRunning) R.string.chat_background_active else R.string.chat_background_inactive),
                    color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(24.dp))
                Text(stringResource(R.string.chat_privacy), fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(16.dp))
                MessagingFeature(Icons.Outlined.Lock, R.string.chat_encrypted, R.string.chat_encryption_body)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(stringResource(R.string.chat_read_receipts), fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(R.string.chat_read_receipts_body), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    androidx.compose.material3.Switch(checked = s.readReceipts, onCheckedChange = { chat.setReadReceipts(it) })
                }
                Spacer(Modifier.height(22.dp))
                Text(stringResource(R.string.chat_notifications_body), fontSize = 13.sp, lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = {
                    if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        context.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName))
                    } else notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }) { Text(stringResource(R.string.chat_enable_notifications)) }
                Spacer(Modifier.height(20.dp))
                Text(stringResource(R.string.chat_own_key), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                SelectionContainer { Text(s.ownFingerprint.chunked(8).joinToString(" "), fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Spacer(Modifier.height(24.dp))
                Button(onClick = { onNavigate(Screen.Chats) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.go_to_chats)) }
                TextButton(onClick = { confirmLogout = true }) { Text(stringResource(R.string.chat_disconnect)) }
            }
        }
    }
    if (confirmLogout) AlertDialog(onDismissRequest = { confirmLogout = false },
        title = { Text(stringResource(R.string.chat_disconnect)) }, text = { Text(stringResource(R.string.chat_logout_body)) },
        confirmButton = { TextButton(onClick = { confirmLogout = false; chat.logout() }) { Text(stringResource(R.string.chat_disconnect)) } },
        dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable
fun ConversationScreen(chat: ChatViewModel, onNavigate: (Screen) -> Unit) {
    val s by chat.state.collectAsState()
    val peer = s.selectedPeer ?: return
    val contact = s.contacts.find { it.address == peer } ?: ChatContact(peer)
    val audio = rememberChatAudioController(peer)
    var viewedImage by rememberSaveable(peer) { mutableStateOf<String?>(null) }
    val draft = s.drafts[peer].orEmpty()
    val group = s.groups.find { it.address == peer }
    var options by androidx.compose.runtime.remember { mutableStateOf(false) }
    var attachments by androidx.compose.runtime.remember { mutableStateOf(false) }
    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    val messages = s.messages.filter { it.peer == peer }.asReversed()
    val canSend = s.connection == ChatConnection.ONLINE && !s.busy && peer !in s.blocked && (group == null || group.joined)
    val context = LocalContext.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var emojis by androidx.compose.runtime.remember { mutableStateOf(false) }
    var shareContact by androidx.compose.runtime.remember { mutableStateOf(false) }
    var recordVoice by androidx.compose.runtime.remember { mutableStateOf(false) }
    var shareLocation by androidx.compose.runtime.remember { mutableStateOf(false) }
    var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraUri?.let { chat.selectMedia(Uri.parse(it)) }
    }
    val photos = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { chat.selectMedia(it) }
    }
    val files = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { chat.selectMedia(it) }
    }
    val microphone = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) recordVoice = true else chat.showError(UiText(R.string.chat_permission_denied))
    }
    val locationPermission = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) shareLocation = true else chat.showError(UiText(R.string.chat_permission_denied))
    }
    val closeTray = { attachments = false; emojis = false }
    val openPhotos = { closeTray(); photos.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val back = { chat.closeConversation(); onNavigate(Screen.Chats) }
    BackHandler { if (attachments || emojis) closeTray() else back() }
    LaunchedEffect(messages.firstOrNull()?.id) { if (list.firstVisibleItemIndex < 2) list.animateScrollToItem(0) }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
    ChatWallpaper(Modifier.fillMaxSize())
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            ChatAvatar(contact.name, Modifier.size(42.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).clickable { onNavigate(Screen.ChatSecurity) }.padding(vertical = 4.dp)) {
                Text(s.conversationOptions[peer]?.alias?.ifBlank { contact.name } ?: contact.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (s.connection != ChatConnection.ONLINE) stringResource(s.connection.label()) else if (group != null) stringResource(R.string.chat_group_private, group.members.size) else if (LocalChatAppearance.current.showPresence && contact.online) stringResource(R.string.chat_online) else contact.address,
                    fontSize = 12.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { options = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.chat_options)) }
                androidx.compose.material3.DropdownMenu(expanded = options, onDismissRequest = { options = false }) {
                    androidx.compose.material3.DropdownMenuItem(text = { Text(stringResource(R.string.style_conversation)) }, onClick = { options = false; onNavigate(Screen.ConversationAppearance) })
                    androidx.compose.material3.DropdownMenuItem(text = { Text(stringResource(R.string.chat_devices)) }, onClick = { options = false; onNavigate(Screen.ChatSecurity) })
                    if (group != null) androidx.compose.material3.DropdownMenuItem(text = { Text(stringResource(R.string.chat_group_info)) }, onClick = { options = false; onNavigate(Screen.GroupDetails) })
                    if (group == null) androidx.compose.material3.DropdownMenuItem(text = { Text(stringResource(R.string.chat_add_contact)) }, onClick = { options = false; chat.addContact(peer) })
                    if (group == null) androidx.compose.material3.DropdownMenuItem(text = { Text(stringResource(if (peer in s.blocked) R.string.chat_unblock else R.string.chat_block)) }, onClick = { options = false; chat.blockSelected() })
                }
            }
        }
        ChatError(s.error, chat::clearError)
        TextButton(onClick = { onNavigate(Screen.ChatSecurity) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Icon(Icons.Outlined.Lock, null, Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.chat_encrypted), fontSize = 12.sp)
        }
        if (messages.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(36.dp), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.chat_conversation_empty), fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.chat_conversation_empty_body), textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 22.sp)
                TextButton(onClick = { onNavigate(Screen.ChatSecurity) }) { Text(stringResource(R.string.chat_devices)) }
            }
        } else androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, reverseLayout = true,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)) {
            items(messages.size, key = { "${messages[it].sender}:${messages[it].outgoing}:${messages[it].id}" }) { index ->
                val message = messages[index]
                val day = java.time.Instant.ofEpochMilli(message.timestamp).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                val olderDay = messages.getOrNull(index + 1)?.let { java.time.Instant.ofEpochMilli(it.timestamp).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }
                Column {
                    if (day != olderDay) Text(if (day == java.time.LocalDate.now()) stringResource(R.string.chat_today)
                        else java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(message.timestamp)),
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 12.dp)
                            .clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 12.dp, vertical = 5.dp))
                    MessageBubble(message, audio = audio, onImage = { audio.pause(); viewedImage = it }, onDownload = { chat.downloadMedia(message) }, localPath = s.mediaFiles[message.mediaKey],
                        onContact = { address -> scope.launch { chat.open(address).join() } },
                        joinsPrevious = s.appearanceFor(peer).groupBubbles && message.groupsWith(messages.getOrNull(index + 1)),
                        joinsNext = s.appearanceFor(peer).groupBubbles && message.groupsWith(messages.getOrNull(index - 1))) { chat.retryMessage(message) }
                }
            }
        }
        if (peer in s.blocked) Text(stringResource(R.string.chat_contact_blocked), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
        s.mediaProgress?.let { progress ->
            androidx.compose.material3.LinearProgressIndicator(progress = progress / 100f, modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.chat_media_progress, progress), fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
        ChatComposer(draft, chat::updateDraft, canSend, attachments,
            onExpand = { emojis = false; attachments = !attachments; focus.clearFocus(); keyboard?.hide() },
            onEmoji = { attachments = false; emojis = !emojis; focus.clearFocus(); keyboard?.hide() },
            onPhoto = openPhotos,
            onVoice = { audio.pause(); closeTray(); microphone.launch(android.Manifest.permission.RECORD_AUDIO) },
            onSend = { chat.send(draft) }, onTyping = closeTray)
        androidx.compose.animation.AnimatedVisibility(attachments) {
            ChatAttachmentTray(onCamera = {
                closeTray()
                val uri = ChatPlatform.uri(context, ChatPlatform.captureFile(context, "jpg"))
                cameraUri = uri.toString()
                runCatching { camera.launch(uri) }.onFailure { chat.showError(UiText(R.string.chat_file_unavailable)) }
            }, onFile = { closeTray(); files.launch(arrayOf("*/*")) },
                onContact = { closeTray(); shareContact = true },
                onLocation = { closeTray(); locationPermission.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION)) })
        }
        androidx.compose.animation.AnimatedVisibility(emojis) { ChatEmojiTray { chat.updateDraft(draft + it) } }
    }
    }
    viewedImage?.let { ChatImageViewer(it) { viewedImage = null } }
    s.pendingMedia?.let { selection ->
        if (selection.mime.startsWith("image/")) ChatImageEditor(selection, draft, s.busy, s.mediaProgress, s.error,
            onClearError = chat::clearError, onClose = chat::dismissMedia,
            onSend = { image, edits, caption -> chat.sendSelectedMedia(caption, image, edits) })
        else {
        var caption by androidx.compose.runtime.remember(selection.uri) { mutableStateOf(draft) }
        AlertDialog(onDismissRequest = { if (!s.busy) chat.dismissMedia() }, title = { Text(stringResource(R.string.chat_attachment_review)) },
            text = { Column {
                ChatSelectionPreview(selection, audio)
                Text(selection.name, fontWeight = FontWeight.Medium)
                Text(if (selection.size >= 0) android.text.format.Formatter.formatShortFileSize(context, selection.size) else selection.mime, fontSize = 12.sp)
                Spacer(Modifier.height(16.dp))
                androidx.compose.material3.OutlinedTextField(caption, { caption = it.take(8_000) }, label = { Text(stringResource(R.string.chat_attachment_caption)) }, maxLines = 4)
                s.mediaProgress?.let { Text(stringResource(R.string.chat_media_progress, it), Modifier.padding(top = 12.dp)) }
                ChatError(s.error, chat::clearError)
            } }, confirmButton = { TextButton(enabled = !s.busy, onClick = { chat.sendSelectedMedia(caption) }) { Text(stringResource(R.string.send)) } },
            dismissButton = { TextButton(enabled = !s.busy, onClick = { chat.dismissMedia() }) { Text(stringResource(R.string.cancel)) } })
    }
    }
    if (shareContact) ContactShareDialog(chat) { shareContact = false }
    if (recordVoice) VoiceCaptureDialog(chat, onClose = { recordVoice = false })
    if (shareLocation) LocationShareDialog(chat) { shareLocation = false }
}

@Composable
fun ChatSecurityScreen(chat: ChatViewModel, onNavigate: (Screen) -> Unit) {
    val s by chat.state.collectAsState()
    val back = { onNavigate(if (s.selectedPeer == null) Screen.Messaging else Screen.Conversation) }
    BackHandler(onBack = back)
    LaunchedEffect(Unit) { chat.loadDevices() }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.chat_devices), back)
        ChatError(s.error, chat::clearError)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp)) {
            Text(stringResource(R.string.chat_devices_body), fontSize = 15.sp, lineHeight = 23.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            if (s.busy) CircularProgressIndicator()
            if (!s.busy && s.devices.isEmpty()) Text(stringResource(R.string.chat_no_devices))
            s.devices.forEach { device ->
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp).clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp)) {
                    Text(device.address, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.chat_device_number, device.id), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(14.dp))
                    SelectionContainer { Text(device.fingerprint.chunked(8).joinToString(" "), fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp, lineHeight = 22.sp) }
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(if (device.trusted) R.string.chat_trusted else R.string.chat_untrusted), fontSize = 12.sp)
                    TextButton(onClick = { chat.trust(device, !device.trusted) }) {
                        Text(stringResource(if (device.trusted) R.string.chat_revoke_device else R.string.chat_trust_device))
                    }
                }
            }
            OutlinedButton(onClick = { chat.loadDevices() }, enabled = !s.busy) { Text(stringResource(R.string.retry)) }
        }
    }
}

@Composable
private fun CreateGroupDialog(chat: ChatViewModel, onClose: () -> Unit, onCreated: () -> Unit) {
    val s by chat.state.collectAsState()
    var name by androidx.compose.runtime.remember { mutableStateOf("") }
    var selected by androidx.compose.runtime.remember { mutableStateOf(emptySet<String>()) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = { if (!s.busy) onClose() }, title = { Text(stringResource(R.string.chat_group_new)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.chat_group_create_body), fontSize = 13.sp)
            Text(stringResource(R.string.chat_group_capacity, GroupPolicy.MAX_ENCRYPTED_MEMBERS),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.chat_group_selected, selected.size + 1, GroupPolicy.MAX_ENCRYPTED_MEMBERS),
                modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.OutlinedTextField(name, { name = it.take(80) }, label = { Text(stringResource(R.string.chat_group_name)) })
            s.contacts.filter { !it.group && it.address !in s.blocked }.forEach { contact ->
                Row(Modifier.fillMaxWidth().clickable {
                    selected = if (contact.address in selected) selected - contact.address else if (selected.size < GroupPolicy.MAX_ENCRYPTED_MEMBERS - 1) selected + contact.address else selected
                }, verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(contact.address in selected, null)
                    Text(contact.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            ChatError(s.error, chat::clearError)
        } }, confirmButton = { TextButton(enabled = name.isNotBlank() && GroupPolicy.canCreate(selected, s.account) && !s.busy, onClick = {
            scope.launch {
                val previous = s.selectedPeer
                chat.createGroup(name, selected).join()
                if (chat.state.value.selectedPeer != previous && chat.state.value.error == null) onCreated()
            }
        }) { Text(stringResource(R.string.chat_group_create)) } },
        dismissButton = { TextButton(enabled = !s.busy, onClick = onClose) { Text(stringResource(R.string.cancel)) } })
}

@Composable
fun GroupDetailsScreen(chat: ChatViewModel, onNavigate: (Screen) -> Unit) {
    val s by chat.state.collectAsState()
    val group = s.groups.find { it.address == s.selectedPeer }
    var invite by androidx.compose.runtime.remember { mutableStateOf(false) }
    var removing by androidx.compose.runtime.remember { mutableStateOf<String?>(null) }
    var deleting by androidx.compose.runtime.remember { mutableStateOf(false) }
    var leaving by androidx.compose.runtime.remember { mutableStateOf(false) }
    val back = { onNavigate(Screen.Conversation) }
    BackHandler(onBack = back)
    LaunchedEffect(Unit) { chat.refreshGroup() }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.chat_group_info), back)
        ChatError(s.error, chat::clearError)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
            if (group != null) {
                Text(group.name, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.chat_group_private, group.members.size), Modifier.padding(vertical = 12.dp), fontSize = 13.sp)
                Text(stringResource(R.string.chat_group_security), fontSize = 13.sp, lineHeight = 20.sp)
                TextButton(onClick = { onNavigate(Screen.ChatSecurity) }) { Text(stringResource(R.string.chat_devices)) }
                if (!group.joined && s.account in group.members) OutlinedButton(enabled = !s.busy, onClick = { chat.rejoinGroup() }) {
                    Text(stringResource(R.string.chat_group_join))
                }
                group.members.sorted().forEach { address ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        ChatAvatar(address.substringBefore('@'), Modifier.size(40.dp))
                        Text(address, Modifier.weight(1f).padding(horizontal = 12.dp), maxLines = 2, fontSize = 14.sp)
                        if (group.owner && address != s.account) IconButton(onClick = { removing = address }) {
                            Icon(Icons.Outlined.PersonRemove, stringResource(R.string.chat_group_remove))
                        }
                    }
                }
                if (group.owner && group.joined) {
                    OutlinedButton(enabled = GroupPolicy.canInvite(group.members.size), onClick = { invite = true }) { Text(stringResource(R.string.chat_group_invite)) }
                    TextButton(onClick = { deleting = true }) { Text(stringResource(R.string.chat_group_delete), color = MaterialTheme.colorScheme.error) }
                } else if (group.joined) TextButton(onClick = { leaving = true }) { Text(stringResource(R.string.chat_group_leave)) }
            }
        }
    }
    if (leaving) AlertDialog(onDismissRequest = { leaving = false }, title = { Text(stringResource(R.string.chat_group_leave)) },
        text = { Text(stringResource(R.string.chat_group_leave_confirm)) },
        confirmButton = { TextButton(onClick = { chat.leaveGroup(); leaving = false }) { Text(stringResource(R.string.chat_group_leave)) } },
        dismissButton = { TextButton(onClick = { leaving = false }) { Text(stringResource(R.string.cancel)) } })
    if (invite) AlertDialog(onDismissRequest = { invite = false }, title = { Text(stringResource(R.string.chat_group_invite)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            s.contacts.filter { !it.group && it.address !in group?.members.orEmpty() && it.address !in s.blocked }.forEach { contact ->
                TextButton(onClick = { chat.inviteGroupMember(contact.address); invite = false }) { Text(contact.name) }
            }
        } }, confirmButton = { TextButton(onClick = { invite = false }) { Text(stringResource(R.string.close)) } })
    removing?.let { address -> AlertDialog(onDismissRequest = { removing = null }, title = { Text(stringResource(R.string.chat_group_remove)) },
        text = { Text(stringResource(R.string.chat_group_remove_confirm, address)) },
        confirmButton = { TextButton(onClick = { chat.removeGroupMember(address); removing = null }) { Text(stringResource(R.string.chat_group_remove)) } },
        dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.cancel)) } }) }
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text(stringResource(R.string.chat_group_delete)) },
        text = { Text(stringResource(R.string.chat_group_delete_confirm)) },
        confirmButton = { TextButton(onClick = { chat.deleteGroup(); deleting = false }) { Text(stringResource(R.string.chat_group_delete)) } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun ContactShareDialog(chat: ChatViewModel, onClose: () -> Unit) {
    val s by chat.state.collectAsState()
    var selected by androidx.compose.runtime.remember { mutableStateOf<ChatContact?>(null) }
    AlertDialog(onDismissRequest = onClose, title = { Text(stringResource(R.string.chat_share_contact)) },
        text = { if (selected == null) Column(Modifier.verticalScroll(rememberScrollState())) {
            s.contacts.filter { !it.group && it.address !in s.blocked }.forEach { contact ->
                TextButton(onClick = { selected = contact }) { Text(contact.name) }
            }
        } else Text(stringResource(R.string.chat_share_contact_confirm, selected!!.address)) },
        confirmButton = { if (selected != null) TextButton(onClick = { chat.shareContact(selected!!.address); onClose() }) { Text(stringResource(R.string.send)) } },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun LocationShareDialog(chat: ChatViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    var location by androidx.compose.runtime.remember { mutableStateOf<String?>(null) }
    var loading by androidx.compose.runtime.remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { location = ChatPlatform.location(context); loading = false }
    AlertDialog(onDismissRequest = onClose, title = { Text(stringResource(R.string.chat_location)) },
        text = { Column {
            if (loading) { CircularProgressIndicator(); Text(stringResource(R.string.chat_location_loading)) }
            else if (location == null) Text(stringResource(R.string.chat_location_failed))
            else { Text(stringResource(R.string.chat_location_confirm)); Spacer(Modifier.height(12.dp)); Text(location!!.removePrefix("geo:")) }
        } }, confirmButton = { TextButton(enabled = location != null, onClick = { location?.let(chat::shareLocation); onClose() }) { Text(stringResource(R.string.send)) } },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun VoiceCaptureDialog(chat: ChatViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val file = androidx.compose.runtime.remember { ChatPlatform.captureFile(context, "m4a") }
    val recorder = androidx.compose.runtime.remember { android.media.MediaRecorder(context) }
    var seconds by androidx.compose.runtime.remember { mutableStateOf(0) }
    var finished by androidx.compose.runtime.remember { mutableStateOf(false) }
    val discard = {
        if (!finished) {
            finished = true
            runCatching { recorder.stop() }
            recorder.release()
            file.delete()
        }
    }
    val finish = {
        if (!finished) {
            finished = true
            runCatching { recorder.stop() }.also { recorder.release() }
                .onSuccess { chat.selectMedia(Uri.fromFile(file)) }
                .onFailure { file.delete(); chat.showError(UiText(R.string.chat_record_failed)) }
            onClose()
        }
    }
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(Unit) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) { discard(); onClose() }
        }
        lifecycle.addObserver(observer)
        runCatching {
            recorder.setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(64_000)
            recorder.setAudioSamplingRate(22_050)
            recorder.setOutputFile(file.absolutePath)
            recorder.prepare(); recorder.start()
        }.onFailure { chat.showError(UiText(R.string.chat_record_failed)); onClose() }
        onDispose {
            lifecycle.removeObserver(observer)
            discard()
        }
    }
    LaunchedEffect(Unit) { while (seconds < 120) { delay(1000); seconds++ }; finish() }
    AlertDialog(onDismissRequest = onClose, title = { Text(stringResource(R.string.chat_record)) },
        text = { Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Mic, null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(12.dp)); Text(stringResource(R.string.chat_recording, seconds))
        } }, confirmButton = { TextButton(onClick = finish, enabled = seconds > 0) { Text(stringResource(R.string.chat_record_stop)) } },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun ChatImageViewer(path: String, onClose: () -> Unit) {
    val context = LocalContext.current
    var failed by androidx.compose.runtime.remember(path) { mutableStateOf(false) }
    val bitmap by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, path) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { ChatPhotoEditor.decode(context, Uri.fromFile(java.io.File(path))) }.getOrNull()
        }
        failed = value == null
    }
    var zoom by androidx.compose.runtime.remember(path) { mutableStateOf(1f) }
    var offset by androidx.compose.runtime.remember(path) { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, stringResource(R.string.close)) }
                Text(stringResource(R.string.chat_view_image), Modifier.weight(1f), fontWeight = FontWeight.Medium)
                IconButton(onClick = { zoom = 1f; offset = androidx.compose.ui.geometry.Offset.Zero }) {
                    Icon(Icons.Outlined.ZoomOutMap, stringResource(R.string.chat_image_fit))
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black).clipToBounds()
                .pointerInput(path) {
                    detectTransformGestures { _, pan, scale, _ ->
                        zoom = (zoom * scale).coerceIn(1f, 5f)
                        val proposed = offset + pan
                        offset = androidx.compose.ui.geometry.Offset(proposed.x.coerceIn(-size.width * (zoom - 1) / 2, size.width * (zoom - 1) / 2),
                            proposed.y.coerceIn(-size.height * (zoom - 1) / 2, size.height * (zoom - 1) / 2))
                    }
                }.pointerInput(path) { detectTapGestures(onDoubleTap = {
                    zoom = if (zoom > 1f) 1f else 2.5f; offset = androidx.compose.ui.geometry.Offset.Zero
                }) }, contentAlignment = Alignment.Center) {
                bitmap?.let { androidx.compose.foundation.Image(it.asImageBitmap(), stringResource(R.string.chat_file_preview),
                    Modifier.fillMaxSize().graphicsLayer(scaleX = zoom, scaleY = zoom, translationX = offset.x, translationY = offset.y),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit) }
                    ?: if (failed) Text(stringResource(R.string.chat_file_unavailable), color = Color.White)
                    else CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun ChatImageEditor(selection: MediaSelection, initialCaption: String, busy: Boolean, progress: Int?, error: UiText?,
    onClearError: () -> Unit, onClose: () -> Unit, onSend: (android.graphics.Bitmap, PhotoEdits, String) -> Unit) {
    val context = LocalContext.current
    var failed by androidx.compose.runtime.remember(selection.uri) { mutableStateOf(false) }
    val bitmap by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, selection.uri) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { ChatPhotoEditor.decode(context, Uri.parse(selection.uri)) }.getOrNull()
        }
        failed = value == null
    }
    var history by rememberSaveable(selection.uri) { mutableStateOf(listOf(PhotoEdits())) }
    var cursor by rememberSaveable(selection.uri) { mutableStateOf(0) }
    val edits = history[cursor]
    var caption by rememberSaveable(selection.uri) { mutableStateOf(initialCaption) }
    var tool by rememberSaveable(selection.uri) { mutableStateOf(PhotoTool.PREVIEW) }
    var crop by rememberSaveable(selection.uri) { mutableStateOf(PhotoRect()) }
    var color by rememberSaveable { mutableStateOf(android.graphics.Color.WHITE) }
    var textDialog by rememberSaveable { mutableStateOf(false) }
    var text by rememberSaveable { mutableStateOf("") }
    var pendingText by rememberSaveable { mutableStateOf("") }
    var discard by rememberSaveable { mutableStateOf(false) }
    fun commit(next: PhotoEdits) {
        if (next == edits || next.marks.size > 100) return
        history = (history.take(cursor + 1) + next).takeLast(30); cursor = history.lastIndex
    }
    val close = { if (!busy) { if (cursor > 0 || pendingText.isNotEmpty() || caption != initialCaption) discard = true else onClose() } }
    androidx.compose.ui.window.Dialog(onDismissRequest = close,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = !busy, dismissOnClickOutside = false)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = close, enabled = !busy) { Icon(Icons.Outlined.Close, stringResource(R.string.close)) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { cursor--; tool = PhotoTool.PREVIEW }, enabled = cursor > 0 && !busy) { Icon(Icons.Outlined.Undo, stringResource(R.string.chat_image_undo)) }
                IconButton(onClick = { cursor++; tool = PhotoTool.PREVIEW }, enabled = cursor < history.lastIndex && !busy) { Icon(Icons.Outlined.Redo, stringResource(R.string.chat_image_redo)) }
                Spacer(Modifier.weight(1f))
                TextButton(enabled = bitmap != null && !busy && tool == PhotoTool.PREVIEW, onClick = { bitmap?.let { onSend(it, edits, caption) } }) { Text(stringResource(R.string.send)) }
            }
            ChatError(error, onClearError)
            progress?.let { androidx.compose.material3.LinearProgressIndicator(it / 100f, Modifier.fillMaxWidth())
                Text(stringResource(R.string.chat_media_progress, it), fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally)) }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().background(Color.Black), contentAlignment = Alignment.Center) {
                bitmap?.let { image ->
                    val visible = if (tool == PhotoTool.CROP) PhotoRect() else edits.crop
                    val ratio = image.width * visible.width / (image.height * visible.height)
                    val width = minOf(maxWidth, maxHeight * ratio)
                    PhotoEditCanvas(image, edits, if (busy) PhotoTool.PREVIEW else tool, color, crop, { crop = it },
                        { stroke -> commit(edits.copy(marks = edits.marks + stroke)) },
                        { point -> if (pendingText.isNotBlank()) {
                            commit(edits.copy(marks = edits.marks + PhotoMark.Label(pendingText, point, color)))
                            pendingText = ""; tool = PhotoTool.PREVIEW
                        } }, Modifier.width(width))
                } ?: if (failed) Text(stringResource(R.string.chat_file_unavailable), color = Color.White)
                    else CircularProgressIndicator()
            }
            if (tool != PhotoTool.PREVIEW) {
                Text(stringResource(when (tool) {
                    PhotoTool.CROP -> R.string.chat_image_crop_hint
                    PhotoTool.TEXT -> R.string.chat_image_text_hint
                    else -> R.string.chat_image_draw_hint
                }), Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 12.sp)
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (tool == PhotoTool.CROP) TextButton(enabled = !busy, onClick = { crop = PhotoRect() }) { Text(stringResource(R.string.chat_image_reset_crop)) }
                    else listOf(android.graphics.Color.WHITE, android.graphics.Color.BLACK, 0xFFE85A5A.toInt(), 0xFFFFD166.toInt(), 0xFF4DB6AC.toInt()).forEachIndexed { index, value ->
                        IconButton(onClick = { color = value }, enabled = !busy, modifier = Modifier.size(44.dp)) {
                            Box(Modifier.size(26.dp).background(Color(value), androidx.compose.foundation.shape.CircleShape)
                                .border(if (color == value) 3.dp else 1.dp, if (color == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, androidx.compose.foundation.shape.CircleShape)
                                .semantics { contentDescription = context.getString(R.string.chat_image_color, index + 1) })
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(enabled = !busy, onClick = {
                        if (tool == PhotoTool.CROP) commit(edits.copy(crop = crop))
                        pendingText = ""; tool = PhotoTool.PREVIEW
                    }) { Text(stringResource(R.string.chat_image_done)) }
                }
            } else androidx.compose.material3.OutlinedTextField(caption, { caption = it.take(8_000) }, enabled = !busy,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), maxLines = 3,
                placeholder = { Text(stringResource(R.string.chat_attachment_caption)) }, shape = RoundedCornerShape(24.dp))
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                PhotoToolButton(Icons.Outlined.Crop, R.string.chat_image_crop, !busy && bitmap != null) { crop = edits.crop; tool = PhotoTool.CROP }
                PhotoToolButton(Icons.Outlined.TextFields, R.string.chat_image_text, !busy && bitmap != null) { text = ""; textDialog = true }
                PhotoToolButton(Icons.Outlined.Brush, R.string.chat_image_draw, !busy && bitmap != null) { tool = PhotoTool.DRAW }
            }
        }
    }
    if (textDialog) AlertDialog(onDismissRequest = { textDialog = false }, title = { Text(stringResource(R.string.chat_image_text)) },
        text = { androidx.compose.material3.OutlinedTextField(text, { text = it.take(200) }, maxLines = 4,
            label = { Text(stringResource(R.string.chat_image_text)) }) },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { pendingText = text; tool = PhotoTool.TEXT; textDialog = false }) { Text(stringResource(R.string.chat_image_add_text)) } },
        dismissButton = { TextButton(onClick = { textDialog = false }) { Text(stringResource(R.string.cancel)) } })
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text(stringResource(R.string.chat_image_discard)) },
        text = { Text(stringResource(R.string.chat_image_original_kept)) },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.chat_image_discard)) } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun PhotoToolButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: Int, enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled) { Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(26.dp)); Spacer(Modifier.height(4.dp)); Text(stringResource(label))
    } }
}
