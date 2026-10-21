/* Copyright 2026 Astian. Licensed under the Apache License, Version 2.0. */
package org.astianx.midori.one.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.astianx.midori.one.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewChatSheet(chat: ChatViewModel, onDismiss: () -> Unit, onOpen: () -> Unit,
    onGroup: () -> Unit, onScan: () -> Unit) {
    val state by chat.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var deviceContacts by remember { mutableStateOf<List<DeviceChatContact>>(emptyList()) }
    var deviceRead by remember { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf<String?>(null) }
    var chosenName by remember { mutableStateOf("") }
    fun readContacts() { scope.launch {
        loading = true
        val result = withContext(Dispatchers.IO) { runCatching { readDeviceChatContacts(context) } }
        deviceContacts = result.getOrDefault(emptyList())
        denied = result.isFailure
        deviceRead = true
        loading = false
    } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) readContacts() else denied = true
    }
    val known = state.contacts.filter { !it.group && it.address !in state.blocked && it.address != state.account }
    val knownByAddress = known.associateBy { it.address }
    val combined = (known.map { DeviceChatContact(it.address,
        state.conversationOptions[it.address]?.alias?.takeIf(String::isNotBlank)
            ?: state.contactProfiles[it.address]?.name?.takeIf(String::isNotBlank) ?: it.name) } + deviceContacts)
        .distinctBy { it.address }.filter { it.address !in state.blocked && it.address != state.account }
        .filter { query.isBlank() || it.name.contains(query, true) || it.address.contains(query, true) }.sortedBy { it.name.lowercase() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.94f).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.new_chat), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                IconButton(onDismiss) { Icon(Icons.Outlined.Close, stringResource(R.string.close)) }
            }
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("chat-contact-search"), singleLine = true, label = { Text(stringResource(R.string.contacts_search)) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) })
            LazyColumn(Modifier.weight(1f).testTag("chat-contact-list"), contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    ContactAction(Icons.Outlined.GroupAdd, R.string.chat_group_new, onGroup)
                    ContactAction(Icons.Outlined.QrCodeScanner, R.string.contacts_scan, onScan)
                    ContactAction(Icons.Outlined.PersonAdd, R.string.contacts_add_link) { chosenName = ""; manual = "" }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
                items(combined, key = { it.address }) { contact ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable {
                        if (contact.address in knownByAddress) scope.launch {
                            chat.open(contact.address).join()
                            if (chat.state.value.selectedPeer == contact.address) { onDismiss(); onOpen() }
                        } else { chosenName = contact.name; manual = contact.address }
                    }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        PublishedProfileAvatar(state.contactProfiles[contact.address], contact.name, Modifier.size(48.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(contact.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(contact.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (combined.isEmpty()) item {
                    Text(stringResource(if (query.isNotBlank()) R.string.no_results else R.string.contacts_empty),
                        Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item {
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    Text(stringResource(R.string.contacts_device_hint), Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = {
                        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) readContacts()
                        else permission.launch(Manifest.permission.READ_CONTACTS)
                    }, enabled = !loading, modifier = Modifier.padding(horizontal = 8.dp).heightIn(min = 48.dp)) {
                        if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Outlined.Contacts, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.contacts_device_read))
                    }
                    if (denied || (deviceRead && deviceContacts.isEmpty())) Text(stringResource(if (denied) R.string.contacts_permission_denied else R.string.contacts_device_empty),
                        Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    manual?.let { input -> NewContactDialog(chat, input, chosenName, { manual = null }, {
        manual = null; onDismiss(); onOpen()
    }) }
}

@Composable
private fun ContactAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: Int, action: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = action).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(24.dp)); Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun NewContactDialog(chat: ChatViewModel, initialAddress: String = "", initialName: String = "",
    onDismiss: () -> Unit, onOpen: () -> Unit) {
    val state by chat.state.collectAsState()
    val scope = rememberCoroutineScope()
    var address by rememberSaveable(initialAddress) { mutableStateOf(initialAddress) }
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    var phone by rememberSaveable { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text(stringResource(R.string.contacts_add_link)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(address, { address = it; invalid = false }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(stringResource(R.string.contacts_address_link)) }, isError = invalid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), enabled = !saving)
            OutlinedTextField(name, { name = it.take(60).filterNot(Char::isISOControl) }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(stringResource(R.string.contacts_local_name)) }, enabled = !saving)
            OutlinedTextField(phone, { value -> phone = value.take(40).filter { it.isDigit() || it in "+ ()-." } }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(stringResource(R.string.contacts_local_phone)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), enabled = !saving)
            Text(stringResource(R.string.contacts_phone_local_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (invalid) Text(stringResource(R.string.chat_invalid_address), color = MaterialTheme.colorScheme.error)
            ChatError(state.error, chat::clearError)
        }
    }, confirmButton = { TextButton(onClick = {
        val resolved = runCatching { if (address.trim().startsWith('@')) resolveChatHandle(address, state.account) else parseChatContactLink(address) }.getOrNull()
        if (resolved == null || resolved == state.account) invalid = true else scope.launch {
            saving = true
            chat.addContactDetails(resolved, name, phone).join()
            if (chat.state.value.error == null) {
                chat.open(resolved).join()
                if (chat.state.value.selectedPeer == resolved) onOpen()
            }
            saving = false
        }
    }, enabled = !saving && address.isNotBlank()) { Text(stringResource(R.string.contacts_save_chat)) } },
        dismissButton = { TextButton(onDismiss, enabled = !saving) { Text(stringResource(R.string.cancel)) } })
}
