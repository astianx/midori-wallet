package org.astianx.midori.one.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.astianx.midori.one.R
import org.astianx.midori.one.UiText
import org.jivesoftware.smack.android.AndroidSmackInitializer
import org.jivesoftware.smack.sasl.SASLErrorException
import org.jivesoftware.smackx.omemo.exceptions.UndecidedOmemoIdentityException
import org.json.JSONArray
import org.json.JSONObject

class ChatViewModel internal constructor(
    private val records: ChatRecords,
    private val transportFactory: () -> ChatTransport,
    private val notifyMessage: (ChatMessage, Int) -> Unit = { _, _ -> },
    private val clearNotification: (String?) -> Unit = {},
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val media: ChatMedia? = null,
    private val artwork: ChatArtwork? = null,
    private val registerAccount: (ChatAccount) -> Unit = ChatRegistration::create,
    initialForeground: Boolean = true
) : ViewModel() {
    private val mutableState = MutableStateFlow(ChatUiState(restoringSession = true))
    val state: StateFlow<ChatUiState> = mutableState
    private val mutex = Mutex()
    private var transport: ChatTransport? = null
    private var credentials: ChatAccount? = null
    @Volatile private var generation = 0L
    private var draftSave: kotlinx.coroutines.Job? = null
    @Volatile private var foreground = initialForeground
    @Volatile private var autoRetryBlocked = false
    private var retryJob: kotlinx.coroutines.Job? = null
    private var retryAttempt = 0

    init {
        task {
            try {
                mutableState.update { it.copy(backgroundConnection = records.get("settings/background") != "false",
                    wallpaper = records.get("settings/wallpaper") == "true") }
                val appearance = records.get("settings/appearance")?.let { runCatching { ChatAppearance.fromJson(JSONObject(it)) }.getOrNull() }
                    ?: ChatAppearance(wallpaper = if (state.value.wallpaper) ChatWallpaperStyle.BOTANICAL else ChatWallpaperStyle.PLAIN)
                mutableState.update { it.copy(appearance = appearance, wallpaper = appearance.wallpaper != ChatWallpaperStyle.PLAIN) }
                records.get("session")?.let { saved ->
                    val json = JSONObject(saved)
                    val account = ChatAccount(json.getString("address"), json.getString("password"),
                        json.getString("host"), json.getInt("port"))
                    if (foreground || state.value.backgroundConnection) connectAccount(account)
                    else {
                        credentials = account
                        restoreAccountState(account, ChatConnection.DISCONNECTED)
                    }
                }
            } finally { mutableState.update { it.copy(restoringSession = false) } }
        }
    }

    private fun task(block: suspend () -> Unit) = viewModelScope.launch(io) {
        mutex.withLock {
            try { block() } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutableState.update { it.copy(error = errorText(e), busy = false) } }
        }
    }

    fun connect(address: String, password: String, host: String, port: String) = task {
        val normalized = runCatching { chatAddress(address) }.getOrElse { throw ChatException(UiText(R.string.chat_invalid_address)) }
        val number = port.toIntOrNull() ?: throw ChatException(UiText(R.string.chat_invalid_server))
        if (number !in 1..65535 || password.isEmpty() || host.isBlank() || host.contains('/') || host.contains(':')) {
            throw ChatException(UiText(R.string.chat_invalid_server))
        }
        connectAccount(ChatAccount(normalized, password, host.trim(), number))
    }

    fun register(address: String, password: String, host: String, port: String) = task {
        require(state.value.account.isBlank())
        val normalized = runCatching { chatAddress(address) }.getOrElse { throw ChatException(UiText(R.string.chat_invalid_address)) }
        val number = port.toIntOrNull()
        if (number == null || number !in 1..65535 || host.isBlank() || host.contains('/') || host.contains(':') || password.length < 12) {
            throw ChatException(UiText(R.string.profile_registration_fields))
        }
        mutableState.update { it.copy(busy = true, error = null) }
        val account = ChatAccount(normalized, password, host.trim(), number)
        registerAccount(account)
        connectAccount(account)
        mutableState.update { it.copy(busy = false) }
    }

    fun saveProfile(name: String, bio: String) = task {
        val profile = state.value.profile.copy(name = name.trim(), bio = bio.trim())
        profile.validate()
        updateProfile(profile)
    }
    fun setProfilePhoto(uri: android.net.Uri) = task {
        require(state.value.account.isNotBlank())
        val file = artwork?.import(uri) ?: throw ChatException(UiText(R.string.style_image_error))
        try { updateProfile(state.value.profile.copy(photo = file)) }
        catch (e: Exception) { artwork.file(file).delete(); throw e }
    }
    fun removeProfilePhoto() = task { updateProfile(state.value.profile.copy(photo = null)) }
    private fun updateProfile(profile: ChatProfile) {
        require(state.value.account.isNotBlank())
        val before = state.value
        mutableState.update { it.copy(profile = profile, error = null) }
        try { saveHistory() } catch (e: Exception) { mutableState.value = before; throw e }
        before.profile.photo?.takeIf { it != profile.photo }?.let { artwork?.file(it)?.delete() }
    }

    fun publishProfile() = task {
        requireOnline()
        mutableState.update { it.copy(profileLoading = true, error = null) }
        try {
            val editable = state.value.profile
            val photo = editable.photo?.let { publicProfilePhoto(requireNotNull(artwork).file(it)) }
            val value = PublishedChatProfile(editable.name, editable.bio, photo, photo?.let { "image/jpeg" })
            transport!!.publishProfile(value)
            mutableState.update { it.copy(publishedProfile = value) }
            saveHistory()
        } catch (_: Exception) { throw ChatException(UiText(R.string.profile_publish_error)) }
        finally { mutableState.update { it.copy(profileLoading = false) } }
    }
    fun withdrawProfile() = task {
        requireOnline()
        mutableState.update { it.copy(profileLoading = true, error = null) }
        try {
            transport!!.publishProfile(PublishedChatProfile())
            mutableState.update { it.copy(publishedProfile = null) }
            saveHistory()
        } catch (_: Exception) { throw ChatException(UiText(R.string.profile_publish_error)) }
        finally { mutableState.update { it.copy(profileLoading = false) } }
    }
    fun refreshPublishedProfile() = task {
        requireOnline()
        mutableState.update { it.copy(profileLoading = true, error = null) }
        try {
            val value = transport!!.loadProfile(state.value.account)
            mutableState.update { it.copy(publishedProfile = value.takeUnless { profile -> profile.name.isBlank() && profile.bio.isBlank() && profile.photoBase64 == null }) }
            saveHistory()
        } catch (_: Exception) { throw ChatException(UiText(R.string.profile_load_error)) }
        finally { mutableState.update { it.copy(profileLoading = false) } }
    }
    fun loadContactProfile(peer: String) = task {
        val address = chatAddress(peer)
        if (address in state.value.blocked) return@task
        requireOnline()
        mutableState.update { it.copy(profileLoading = true, error = null) }
        try {
            val value = transport!!.loadProfile(address)
            mutableState.update { it.copy(contactProfiles = cacheContactProfile(it.contactProfiles, address, value)) }
            saveHistory()
        } catch (_: Exception) { throw ChatException(UiText(R.string.profile_load_error)) }
        finally { mutableState.update { it.copy(profileLoading = false) } }
    }
    fun prepareContact(input: String) {
        val address = runCatching { parseChatContactLink(input) }.getOrNull()
        mutableState.update { it.copy(pendingContactAddress = address, error = if (address == null) UiText(R.string.chat_invalid_address) else null) }
    }
    fun dismissContactRequest() { mutableState.update { it.copy(pendingContactAddress = null) } }
    fun saveContactDetails(peer: String, name: String, phone: String) = task {
        val details = validatedContactDetails(name, phone)
        changeConversation(chatAddress(peer)) { it.copy(alias = details.first, phone = details.second) }
    }
    fun addContactDetails(input: String, name: String, phone: String) = task {
        val address = runCatching { parseChatContactLink(input) }.getOrElse { throw ChatException(UiText(R.string.chat_invalid_address)) }
        val details = validatedContactDetails(name, phone)
        requireOnline()
        transport!!.addContact(address)
        val before = state.value
        val options = before.conversationOptions[address] ?: ConversationOptions()
        mutableState.update { it.copy(contacts = (it.contacts + ChatContact(address)).distinctBy { contact -> contact.address },
            conversationOptions = it.conversationOptions + (address to options.copy(alias = details.first, phone = details.second)),
            error = null) }
        try { saveHistory() } catch (e: Exception) { mutableState.value = before; throw e }
    }

    private fun connectAccount(account: ChatAccount) {
        retryJob?.cancel()
        retryJob = null
        val token = ++generation
        transport?.disconnect()
        credentials = account
        restoreAccountState(account, ChatConnection.CONNECTING)
        val client = transportFactory()
        transport = client
        try {
            client.connect(account, object : ChatEvents {
                private fun current() = generation == token
                override fun connection(status: ChatConnection) {
                    if (!current()) return
                    updateConnection(status)
                    if (status in setOf(ChatConnection.ERROR, ChatConnection.DISCONNECTED)) scheduleRetry()
                }
                override fun connectionFailed(error: Exception) {
                    if (!current()) return
                    autoRetryBlocked = !canRetryConnection(error)
                    mutableState.update { it.copy(error = errorText(error)) }
                    updateConnection(ChatConnection.ERROR)
                    if (!autoRetryBlocked) scheduleRetry()
                }
                override fun contacts(contacts: List<ChatContact>) { task {
                    if (!current()) return@task
                    mutableState.update { s -> s.copy(contacts = (contacts + s.contacts.filter { old -> contacts.none { it.address == old.address } }).distinctBy { it.address }) }
                    saveHistory()
                } }
                override fun message(message: ChatMessage, historical: Boolean) { task {
                    if (!current() || message.peer in state.value.blocked) return@task
                    val before = state.value
                    val options = before.conversationOptions[message.peer] ?: ConversationOptions()
                    if (message.mediaKey in options.deletedIds || (options.deletedBefore > 0 && message.timestamp <= options.deletedBefore)) return@task
                    val index = before.messages.indexOfFirst { it.id == message.id && it.peer == message.peer && it.outgoing == message.outgoing &&
                        (it.sender.isBlank() || message.sender.isBlank() || it.sender == message.sender) }
                    if (index >= 0) return@task
                    val isOpen = foreground && before.selectedPeer == message.peer
                    val received = message.copy(read = message.outgoing || isOpen)
                    mutableState.update { s -> s.copy(messages = (s.messages + received).sortedBy { it.timestamp },
                        conversationOptions = s.conversationOptions + (message.peer to options.copy(hidden = false)),
                        contacts = if (s.contacts.any { it.address == message.peer }) s.contacts else s.contacts + ChatContact(message.peer)) }
                    saveHistory()
                    if (!historical && !message.outgoing && !isOpen && !options.muted && message.protection == MessageProtection.ENCRYPTED) {
                        notifyMessage(message, state.value.messages.count {
                            it.peer == message.peer && !it.read && !it.outgoing && it.protection == MessageProtection.ENCRYPTED
                        })
                    }
                    if (!historical && isOpen && before.readReceipts && message.markable && message.protection == MessageProtection.ENCRYPTED) {
                        client.markRead(message.peer, message.id)
                    }
                } }
                override fun receipt(peer: String, id: String, status: MessageDelivery) { task {
                    if (!current()) return@task
                    mutableState.update { s -> s.copy(messages = s.messages.map { it.withReceipt(peer, id, status) }) }
                    saveHistory()
                } }
                override fun fingerprint(value: String) { if (current()) mutableState.update { it.copy(ownFingerprint = value) } }
                override fun blocked(addresses: Set<String>) { task {
                    if (current()) { mutableState.update { it.copy(blocked = addresses) }; saveHistory() }
                } }
                override fun subscriptionRequested(peer: String) { task {
                    if (current() && peer !in state.value.blocked) {
                        mutableState.update { it.copy(requests = it.requests + peer) }; saveHistory()
                    }
                } }
                override fun group(group: ChatGroup) { task {
                    if (!current()) return@task
                    mutableState.update { st -> st.copy(groups = st.groups.filterNot { it.address == group.address } + group,
                        contacts = st.contacts.filterNot { it.address == group.address } + ChatContact(group.address, group.name, group.joined, true)) }
                    saveHistory()
                } }
                override fun invited(invitation: GroupInvitation) { task {
                    if (!current() || invitation.inviter in state.value.blocked) return@task
                    mutableState.update { it.copy(invitations = (it.invitations + invitation).distinctBy { v -> v.room }) }
                    saveHistory()
                } }
                override fun failure(error: UiText) { if (current()) mutableState.update { it.copy(error = error) } }
            })
            // Save only after TLS, authentication and OMEMO initialization succeed. Failures preserve old credentials.
            records.put("session", JSONObject().put("address", account.address).put("password", account.password)
                .put("host", account.host).put("port", account.port).toString())
            retryAttempt = 0
            autoRetryBlocked = false
        } catch (e: Exception) {
            autoRetryBlocked = !canRetryConnection(e)
            updateConnection(ChatConnection.ERROR)
            if (!autoRetryBlocked) scheduleRetry()
            throw e
        }
    }

    private fun updateConnection(status: ChatConnection) {
        mutableState.update { it.copy(connection = status,
            contacts = if (status == ChatConnection.ONLINE) it.contacts else it.contacts.map { c -> c.copy(online = false) }) }
    }

    private fun canRetryConnection(error: Exception) = !ChatTls.isIdentityFailure(error) &&
        error !is SASLErrorException && error !is ChatException &&
        (error is java.io.IOException || error is org.jivesoftware.smack.SmackException.NoResponseException ||
            error is org.jivesoftware.smack.SmackException.ConnectionException ||
            error is org.jivesoftware.smack.SmackException.NotConnectedException)

    private fun scheduleRetry() {
        if (autoRetryBlocked || retryJob?.isActive == true) return
        val token = generation
        val wait = (5_000L shl retryAttempt.coerceAtMost(4)).coerceAtMost(60_000L)
        retryAttempt++
        retryJob = viewModelScope.launch {
            delay(wait)
            task {
                if (generation == token && !autoRetryBlocked && canConnectInBackground() &&
                    state.value.connection in setOf(ChatConnection.ERROR, ChatConnection.DISCONNECTED)) {
                    credentials?.let(::connectAccount)
                }
            }
        }
    }

    private fun restoreAccountState(account: ChatAccount, connection: ChatConnection) {
        val restored = records.get("history/${account.address}")?.let(::readHistory) ?: ChatUiState()
        mutableState.value = restored.copy(account = account.address, connection = connection,
            restoringSession = state.value.restoringSession,
            backgroundConnection = state.value.backgroundConnection, connectionServiceRunning = state.value.connectionServiceRunning,
            wallpaper = state.value.wallpaper, appearance = state.value.appearance,
            selectedPeer = state.value.selectedPeer.takeIf { state.value.account == account.address }, error = null)
    }

    private fun canConnectInBackground() = foreground ||
        (state.value.backgroundConnection && state.value.connectionServiceRunning)

    fun retry() = task { autoRetryBlocked = false; retryAttempt = 0; credentials?.let(::connectAccount) }
    fun networkAvailable() = task {
        if (!autoRetryBlocked && canConnectInBackground() &&
            state.value.connection in setOf(ChatConnection.ERROR, ChatConnection.DISCONNECTED)) credentials?.let(::connectAccount)
    }
    fun networkLost() = task {
        if (credentials == null || autoRetryBlocked) return@task
        ++generation
        retryJob?.cancel()
        retryJob = null
        transport?.disconnect()
        updateConnection(ChatConnection.DISCONNECTED)
        scheduleRetry()
    }
    fun setConnectionServiceRunning(running: Boolean) {
        mutableState.update { it.copy(connectionServiceRunning = running) }
        if (running) networkAvailable()
    }
    fun setBackgroundConnection(enabled: Boolean) = task {
        records.put("settings/background", enabled.toString())
        mutableState.update { it.copy(backgroundConnection = enabled) }
    }
    fun setWallpaper(enabled: Boolean) = setAppearance(state.value.appearance.copy(
        wallpaper = if (enabled) ChatWallpaperStyle.BOTANICAL else ChatWallpaperStyle.PLAIN))

    fun setAppearance(value: ChatAppearance, peer: String? = null) = task { saveAppearance(value, peer) }
    private fun saveAppearance(value: ChatAppearance, peer: String?) {
        ChatAppearance.fromJson(value.toJson()) // Validate every entry point, including imported themes.
        val previousPhoto = state.value.appearanceFor(peer).photo
        if (peer == null) {
            records.put("settings/appearance", value.toJson().toString())
            mutableState.update { it.copy(appearance = value, wallpaper = value.wallpaper != ChatWallpaperStyle.PLAIN) }
        } else changeConversation(peer) { it.copy(appearance = value) }
        previousPhoto?.takeIf { it != value.photo }?.let(::deleteUnusedArtwork)
    }
    fun resetConversationAppearance(peer: String) = task {
        val previous = state.value.conversationOptions[peer]?.appearance?.photo
        changeConversation(peer) { it.copy(appearance = null) }
        previous?.let(::deleteUnusedArtwork)
    }
    fun setConversationAlias(peer: String, name: String) = task {
        require(name.length <= 60 && name.none { it.isISOControl() })
        changeConversation(peer) { it.copy(alias = name.trim()) }
    }
    fun importWallpaper(uri: android.net.Uri, peer: String? = null) = task {
        val file = artwork?.import(uri) ?: throw ChatException(UiText(R.string.style_image_error))
        try { saveAppearance(state.value.appearanceFor(peer).copy(wallpaper = ChatWallpaperStyle.PHOTO, photo = file), peer) }
        catch (e: Exception) { artwork.file(file).delete(); throw e }
    }
    private fun deleteUnusedArtwork(name: String) {
        // An override in another account may still reference a formerly global photo.
        // A read failure preserves the file rather than risking data loss.
        runCatching {
            val keys = records.keys("history/") + "settings/appearance"
            if (keys.none { records.get(it)?.contains(name) == true }) artwork?.file(name)?.delete()
        }
    }
    fun clearError() { mutableState.update { it.copy(error = null) } }
    fun open(peer: String) = task {
        val address = runCatching { resolveChatHandle(peer, state.value.account) }.getOrElse { throw ChatException(UiText(R.string.chat_invalid_address)) }
        var discarded: MediaSelection? = null
        mutableState.update {
            discarded = it.pendingMedia.takeIf { _ -> it.selectedPeer != address }
            it.copy(selectedPeer = address, devices = emptyList(), error = null,
            pendingMedia = if (it.selectedPeer == address) it.pendingMedia else null,
            conversationOptions = it.conversationOptions + (address to (it.conversationOptions[address] ?: ConversationOptions()).copy(hidden = false)),
            contacts = if (it.contacts.any { c -> c.address == address }) it.contacts else it.contacts + ChatContact(address)) }
        discarded?.let(::releaseSelectedCapture)
        markVisibleRead(address)
        saveHistory()
    }
    fun closeConversation() {
        var discarded: MediaSelection? = null
        mutableState.update {
            discarded = it.pendingMedia
            it.copy(selectedPeer = null, pendingMedia = null, devices = emptyList(), error = null)
        }
        discarded?.let { selection -> task { releaseSelectedCapture(selection) } }
    }
    fun pinConversation(peer: String, pinned: Boolean) = task {
        changeConversation(peer) { it.copy(pinned = pinned) }
    }
    fun muteConversation(peer: String, muted: Boolean) = task {
        changeConversation(peer) { it.copy(muted = muted) }
        if (muted) clearNotification(peer)
    }
    private fun changeConversation(peer: String, change: (ConversationOptions) -> ConversationOptions) {
        val before = state.value
        if (before.contacts.none { it.address == peer }) return
        mutableState.update { it.copy(conversationOptions = it.conversationOptions + (peer to change(it.conversationOptions[peer] ?: ConversationOptions()))) }
        try { saveHistory() } catch (e: Exception) { mutableState.value = before; throw e }
    }
    /** Local deletion only. Retain the cutoff so MAM and delayed group history cannot restore erased messages. */
    fun deleteConversation(peer: String) = task {
        val before = state.value
        if (before.contacts.none { it.address == peer }) return@task
        val removed = before.messages.filter { it.peer == peer }.map { it.mediaKey }.toSet()
        val previous = before.conversationOptions[peer] ?: ConversationOptions()
        // Remote timestamps must not push the cutoff into the future and suppress later messages.
        val options = previous.copy(hidden = true, pinned = false, deletedBefore = System.currentTimeMillis(),
            deletedIds = previous.deletedIds + removed)
        mutableState.update { it.copy(messages = it.messages.filterNot { m -> m.peer == peer }, drafts = it.drafts - peer,
            conversationOptions = it.conversationOptions + (peer to options), mediaFiles = it.mediaFiles - removed,
            selectedPeer = it.selectedPeer.takeUnless { selected -> selected == peer }) }
        try { saveHistory() } catch (e: Exception) { mutableState.value = before; throw e }
        removed.mapNotNull { before.mediaFiles[it] }.forEach { media?.removePreview(it) }
        clearNotification(peer)
    }
    fun addContact(address: String) = task {
        val peer = runCatching { chatAddress(address) }.getOrElse { throw ChatException(UiText(R.string.chat_invalid_address)) }
        requireOnline()
        transport!!.addContact(peer)
        mutableState.update { it.copy(contacts = (it.contacts + ChatContact(peer)).distinctBy { c -> c.address }) }
        saveHistory()
    }

    fun updateDraft(text: String) {
        val peer = state.value.selectedPeer ?: return
        if (text.length > 8_000) return
        mutableState.update { it.copy(drafts = it.drafts + (peer to text)) }
        draftSave?.cancel()
        draftSave = viewModelScope.launch { delay(500); task { saveHistory() }.join() }
    }
    fun send(body: String) = task {
        if (body.isBlank()) return@task
        sendContent(body)
    }
    private fun sendContent(body: String, attachment: ChatAttachment? = null, contact: String? = null, location: String? = null,
        clearDraft: Boolean = true, targetPeer: String? = null) {
        val s = state.value
        val peer = targetPeer ?: s.selectedPeer ?: return
        if (body.length > 8_000) throw ChatException(UiText(R.string.chat_message_too_long))
        requireOnline()
        if (peer in s.blocked) throw ChatException(UiText(R.string.chat_contact_blocked))
        if (s.groups.any { it.address == peer && !it.joined }) throw ChatException(UiText(R.string.chat_group_not_joined))
        val message = ChatMessage(UUID.randomUUID().toString(), peer, body, true, System.currentTimeMillis(),
            MessageDelivery.SENDING, read = true, sender = s.account, attachment = attachment, sharedContact = contact, location = location)
        mutableState.update { it.copy(messages = it.messages + message, busy = true, error = null) }
        try {
            saveHistory()
            transport!!.send(peer, ChatPayload.encode(message), message.id)
            mutableState.update { current -> current.copy(messages = current.messages.map {
                it.withReceipt(peer, message.id, MessageDelivery.SENT)
            }, drafts = if (clearDraft && current.drafts[peer] == s.drafts[peer]) current.drafts - peer else current.drafts,
                pendingMedia = if (clearDraft) null else current.pendingMedia, busy = false, mediaProgress = null) }
        } catch (e: Exception) {
            mutableState.update { current -> current.copy(messages = current.messages.map {
                if (it.id == message.id) it.copy(delivery = MessageDelivery.FAILED) else it
            }, busy = false, mediaProgress = null, error = errorText(e)) }
        }
        saveHistory()
    }
    fun retryMessage(message: ChatMessage) = task {
        val s = state.value
        val original = s.messages.find { it.id == message.id && it.peer == message.peer && it.outgoing } ?: return@task
        if (original.peer != s.selectedPeer || original.delivery != MessageDelivery.FAILED) return@task
        requireOnline()
        if (original.peer in s.blocked) throw ChatException(UiText(R.string.chat_contact_blocked))
        if (s.groups.any { it.address == original.peer && !it.joined }) throw ChatException(UiText(R.string.chat_group_not_joined))
        // Reuse the same ID and record so duplicate taps and delayed receipts cannot create a second message.
        mutableState.update { current -> current.copy(busy = true, error = null, messages = current.messages.map {
            if (it.id == original.id && it.peer == original.peer && it.outgoing) it.copy(delivery = MessageDelivery.SENDING) else it
        }) }
        try {
            saveHistory()
            transport!!.send(original.peer, ChatPayload.encode(original), original.id)
            mutableState.update { current -> current.copy(messages = current.messages.map {
                it.withReceipt(original.peer, original.id, MessageDelivery.SENT)
            }) }
        } catch (e: Exception) {
            mutableState.update { current -> current.copy(error = errorText(e), messages = current.messages.map {
                if (it.id == original.id && it.peer == original.peer && it.outgoing) it.copy(delivery = MessageDelivery.FAILED) else it
            }) }
        } finally { mutableState.update { it.copy(busy = false) } }
        saveHistory()
    }
    fun selectMedia(uri: android.net.Uri, videoNote: Boolean = false, asDocument: Boolean = false,
        expectedPeer: String? = state.value.selectedPeer, expectedAccount: String = state.value.account): kotlinx.coroutines.Job {
        // Arguments bind picker/camera results to the conversation and account that launched them.
        return task {
            val service = requireNotNull(media)
            val current = state.value
            if (expectedPeer == null || current.selectedPeer != expectedPeer || current.account != expectedAccount) {
                releaseSelectedCapture(MediaSelection(uri.toString(), "", "application/octet-stream", -1))
                return@task
            }
            // File/provider IO must never run inside StateFlow's retryable CAS callback.
            val selection = service.describe(uri, videoNote, asDocument)
            var installed = false
            var replaced: MediaSelection? = null
            mutableState.update {
                installed = it.selectedPeer == expectedPeer && it.account == expectedAccount
                replaced = if (installed) it.pendingMedia else null
                if (installed) it.copy(pendingMedia = selection, error = null) else it
            }
            if (!installed) releaseSelectedCapture(selection)
            else replaced?.takeIf { it.uri != selection.uri }?.let(::releaseSelectedCapture)
        }
    }
    fun dismissMedia() {
        val selected = state.value.pendingMedia
        mutableState.update { it.copy(pendingMedia = if (it.pendingMedia == selected) null else it.pendingMedia) }
        // The same mutex as uploads defers removal until an in-flight send has saved its preview.
        if (selected != null) task { releaseSelectedCapture(selected) }
    }
    private fun releaseSelectedCapture(selection: MediaSelection) {
        val service = media ?: return
        val path = service.ownCapturePath(selection) ?: return
        if (state.value.pendingMedia?.uri != selection.uri && path !in state.value.mediaFiles.values) {
            service.removeOwnCapture(selection)
        }
    }
    internal fun sendSelectedMedia(caption: String, image: android.graphics.Bitmap? = null, edits: PhotoEdits = PhotoEdits(),
        quality: PhotoQuality = PhotoQuality.STANDARD): kotlinx.coroutines.Job {
        // Bind the intended recipient and file at the user's tap, before waiting for serialized IO.
        val selectedState = state.value
        val original = selectedState.pendingMedia
        val peer = selectedState.selectedPeer
        return task {
            if (original == null || peer == null || state.value.pendingMedia != original) return@task
            requireOnline()
            var exportedSelection: MediaSelection? = null
            mutableState.update { it.copy(busy = true, mediaProgress = 0, error = null) }
            try {
                val service = requireNotNull(media)
                val selected = if (image != null) {
                    val file = service.exportImage(original, edits, quality)
                    service.describe(android.net.Uri.fromFile(file)).also { exportedSelection = it }
                } else original
                val attachment = service.upload(selected, transport!!) { progress -> mutableState.update { it.copy(mediaProgress = progress) } }
                sendContent(caption, attachment = attachment, clearDraft = !original.videoNote, targetPeer = peer)
                state.value.messages.lastOrNull { it.outgoing && it.peer == peer && it.attachment == attachment }?.let { message ->
                    mutableState.update { it.copy(pendingMedia = if (it.pendingMedia == original) null else it.pendingMedia) }
                    val path = runCatching { service.previewCopy(selected).absolutePath }.getOrNull()
                        // If copying fails, keep the original app capture as the message preview.
                        // This never claims an edited photo preview is its unedited source.
                        ?: service.ownCapturePath(selected)
                    if (path != null) {
                        mutableState.update { it.copy(mediaFiles = it.mediaFiles + (message.mediaKey to path)) }
                        saveHistory()
                        releaseSelectedCapture(original)
                    }
                }
            } finally {
                exportedSelection?.let(::releaseSelectedCapture)
                mutableState.update { it.copy(busy = false, mediaProgress = null) }
            }
        }
    }
    /** The inline recorder keeps its private file on upload failure and never consumes the text draft. */
    internal fun sendVoiceRecording(peer: String, source: java.io.File, onComplete: (Boolean) -> Unit): kotlinx.coroutines.Job {
        val expectedAccount = state.value.account
        val expectedGeneration = generation
        val accepted = java.util.concurrent.atomic.AtomicBoolean(false)
        return task {
            if (expectedAccount.isBlank() || state.value.account != expectedAccount || generation != expectedGeneration) return@task
            requireOnline()
            val initial = state.value
            if (peer in initial.blocked) throw ChatException(UiText(R.string.chat_contact_blocked))
            if (initial.groups.any { it.address == peer && !it.joined }) throw ChatException(UiText(R.string.chat_group_not_joined))
            mutableState.update { it.copy(busy = true, mediaProgress = 0, error = null) }
            try {
                val service = requireNotNull(media)
                val selection = service.describe(android.net.Uri.fromFile(source))
                require(selection.mime == "audio/mp4")
                val attachment = service.upload(selection, transport!!) { progress -> mutableState.update { it.copy(mediaProgress = progress) } }
                try { sendContent("", attachment = attachment, clearDraft = false, targetPeer = peer) }
                finally {
                    // Once a message exists, another tap must not create a duplicate after a persistence error.
                    accepted.set(state.value.messages.any { it.peer == peer && it.outgoing && it.attachment == attachment })
                }
                state.value.messages.lastOrNull { it.peer == peer && it.outgoing && it.attachment == attachment }?.let { message ->
                    // A FAILED message is retried through its existing action, never automatically.
                    runCatching { service.previewCopy(selection).absolutePath }.getOrNull()?.let { path ->
                        mutableState.update { it.copy(mediaFiles = it.mediaFiles + (message.mediaKey to path)) }
                        saveHistory()
                    }
                }
            } finally { mutableState.update { it.copy(busy = false, mediaProgress = null) } }
        }.also { job -> job.invokeOnCompletion { onComplete(accepted.get()) } }
    }
    fun downloadMedia(message: ChatMessage) = task {
        val file = message.attachment ?: return@task
        if (message.peer != state.value.selectedPeer || message !in state.value.messages) return@task
        requireOnline()
        mutableState.update { it.copy(busy = true, mediaProgress = 0, error = null) }
        try {
            val path = requireNotNull(media).download(file, transport!!) { progress -> mutableState.update { it.copy(mediaProgress = progress) } }.absolutePath
            mutableState.update { it.copy(mediaFiles = it.mediaFiles + (message.mediaKey to path)) }
            saveHistory()
        } finally { mutableState.update { it.copy(busy = false, mediaProgress = null) } }
    }
    fun shareContact(address: String) = state.value.selectedPeer.let { peer ->
        task { if (peer != null) sendContent("", contact = chatAddress(address), clearDraft = false, targetPeer = peer) }
    }
    fun shareLocation(value: String) = state.value.selectedPeer.let { peer ->
        task { if (peer != null) { ChatPayload.validateLocation(value); sendContent("", location = value, clearDraft = false, targetPeer = peer) } }
    }
    fun showError(text: UiText) { mutableState.update { it.copy(error = text) } }
    fun createGroup(name: String, members: Set<String>) = task {
        requireOnline()
        mutableState.update { it.copy(busy = true, error = null) }
        val group = transport!!.createGroup(name.trim(), members.map(::chatAddress).toSet())
        mutableState.update { it.copy(selectedPeer = group.address, busy = false) }
    }
    fun acceptInvitation(invitation: GroupInvitation) = task {
        requireOnline()
        transport!!.joinGroup(invitation.room)
        mutableState.update { it.copy(invitations = it.invitations.filterNot { v -> v.room == invitation.room }, selectedPeer = invitation.room) }
        saveHistory()
    }
    fun declineInvitation(invitation: GroupInvitation) = task {
        mutableState.update { it.copy(invitations = it.invitations - invitation) }; saveHistory()
    }
    fun refreshGroup() = task { requireOnline(); state.value.selectedPeer?.let { transport!!.groupInfo(it) } }
    fun rejoinGroup() = task { requireOnline(); state.value.selectedPeer?.let { transport!!.joinGroup(it) } }
    fun inviteGroupMember(address: String) = task { requireOnline(); state.value.selectedPeer?.let { transport!!.inviteToGroup(it, chatAddress(address)) } }
    fun removeGroupMember(address: String) = task { requireOnline(); state.value.selectedPeer?.let { transport!!.removeGroupMember(it, chatAddress(address)) } }
    fun deleteGroup() = task { requireOnline(); state.value.selectedPeer?.let { transport!!.deleteGroup(it) } }
    fun leaveGroup() = task { requireOnline(); state.value.selectedPeer?.let { transport!!.leaveGroup(it) } }

    fun loadDevices() = task {
        requireOnline()
        val peer = state.value.selectedPeer ?: state.value.account
        mutableState.update { it.copy(busy = true, error = null) }
        val devices = transport!!.devices(peer)
        mutableState.update { it.copy(devices = devices, busy = false) }
    }
    fun trust(device: ChatDevice, trusted: Boolean) = task {
        requireOnline()
        transport!!.trust(device, trusted)
        mutableState.update { s -> s.copy(devices = s.devices.map { if (it == device) it.copy(trusted = trusted) else it }, error = null) }
    }
    fun blockSelected() = task {
        requireOnline()
        val s = state.value
        val peer = s.selectedPeer ?: return@task
        transport!!.block(peer, peer !in s.blocked)
    }
    fun setReadReceipts(enabled: Boolean) = task {
        mutableState.update { it.copy(readReceipts = enabled) }
        saveHistory()
    }
    fun answerSubscription(peer: String, approve: Boolean) = task {
        requireOnline()
        transport!!.answerSubscription(peer, approve)
        mutableState.update { it.copy(requests = it.requests - peer) }
        saveHistory()
    }
    private fun requireOnline() {
        if (state.value.connection != ChatConnection.ONLINE) throw ChatException(UiText(R.string.chat_offline_error))
    }
    fun setForeground(value: Boolean) = task {
        foreground = value
        if (!value) saveHistory()
        transport?.setActive(value)
        if (!value && !state.value.backgroundConnection) {
            ++generation
            retryJob?.cancel()
            transport?.disconnect()
            updateConnection(ChatConnection.DISCONNECTED)
        }
        if (value) {
            state.value.selectedPeer?.let { markVisibleRead(it); saveHistory() }
            if (!autoRetryBlocked && state.value.connection in setOf(ChatConnection.DISCONNECTED, ChatConnection.ERROR)) credentials?.let(::connectAccount)
        }
    }
    private fun markVisibleRead(peer: String) {
        val s = state.value
        val last = s.messages.lastOrNull { it.peer == peer && !it.outgoing && it.markable && it.protection == MessageProtection.ENCRYPTED }
        mutableState.update { it.copy(messages = it.messages.map { m -> if (m.peer == peer) m.copy(read = true) else m }) }
        clearNotification(peer)
        if (s.readReceipts && s.connection == ChatConnection.ONLINE && last != null) transport?.markRead(peer, last.id)
    }
    fun logout() = task {
        ++generation
        retryJob?.cancel()
        autoRetryBlocked = false
        transport?.disconnect()
        transport = null
        credentials = null
        media?.clearPreviews()
        records.put("session", null)
        mutableState.value = ChatUiState(backgroundConnection = state.value.backgroundConnection, wallpaper = state.value.wallpaper, appearance = state.value.appearance)
        clearNotification(null)
    }

    private fun saveHistory() {
        val s = state.value
        if (s.account.isBlank()) return
        val json = JSONObject().put("readReceipts", s.readReceipts).put("blocked", JSONArray(s.blocked.toList()))
            .put("requests", JSONArray(s.requests.toList())).put("profile", s.profile.toJson())
            .put("publishedProfile", s.publishedProfile?.toJson())
            .put("contactProfiles", JSONObject(s.contactProfiles.mapValues { it.value.toJson() }))
            .put("contacts", JSONArray(s.contacts.map { JSONObject().put("address", it.address).put("name", it.name).put("group", it.group) }))
            .put("drafts", JSONObject(s.drafts))
            .put("mediaFiles", JSONObject(s.mediaFiles))
            .put("conversationOptions", JSONObject(s.conversationOptions.mapValues { (_, o) -> JSONObject()
                .put("pinned", o.pinned).put("muted", o.muted).put("hidden", o.hidden).put("deletedBefore", o.deletedBefore)
                .put("deletedIds", JSONArray(o.deletedIds.toList())).put("appearance", o.appearance?.toJson())
                .put("alias", o.alias).put("phone", o.phone) }))
            .put("groups", JSONArray(s.groups.map { JSONObject().put("address", it.address).put("name", it.name)
                .put("members", JSONArray(it.members.toList())).put("owner", it.owner).put("joined", it.joined) }))
            .put("invitations", JSONArray(s.invitations.map { JSONObject().put("room", it.room).put("inviter", it.inviter) }))
            .put("messages", JSONArray(s.messages.map { m -> JSONObject().put("id", m.id).put("peer", m.peer)
                .put("body", ChatPayload.encode(m)).put("sender", m.sender).put("outgoing", m.outgoing).put("timestamp", m.timestamp).put("delivery", m.delivery.name)
                .put("protection", m.protection.name).put("read", m.read).put("markable", m.markable) }))
        records.put("history/${s.account}", json.toString())
    }
    private fun readHistory(value: String): ChatUiState {
        val json = JSONObject(value)
        val contacts = json.getJSONArray("contacts")
        val messages = json.getJSONArray("messages")
        val blocked = json.getJSONArray("blocked")
        val groups = json.optJSONArray("groups") ?: JSONArray()
        val invitations = json.optJSONArray("invitations") ?: JSONArray()
        val drafts = json.optJSONObject("drafts") ?: JSONObject()
        val requests = json.optJSONArray("requests") ?: JSONArray()
        val options = json.optJSONObject("conversationOptions") ?: JSONObject()
        val files = json.optJSONObject("mediaFiles") ?: JSONObject()
        val profiles = json.optJSONObject("contactProfiles") ?: JSONObject()
        return ChatUiState(readReceipts = json.optBoolean("readReceipts", true),
            profile = json.optJSONObject("profile")?.let(ChatProfile::fromJson) ?: ChatProfile(),
            publishedProfile = json.optJSONObject("publishedProfile")?.let(PublishedChatProfile::fromJson),
            contactProfiles = profiles.keys().asSequence().take(200).associateWith { PublishedChatProfile.fromJson(profiles.getJSONObject(it)) },
            mediaFiles = files.keys().asSequence().associateWith { files.getString(it) }.filterValues { media?.hasPreview(it) == true },
            conversationOptions = options.keys().asSequence().associateWith { key -> options.getJSONObject(key).let {
                val ids = it.optJSONArray("deletedIds") ?: JSONArray()
                ConversationOptions(it.optBoolean("pinned"), it.optBoolean("muted"), it.optBoolean("hidden"), it.optLong("deletedBefore"),
                    (0 until ids.length()).map { index -> ids.getString(index) }.toSet(),
                    it.optJSONObject("appearance")?.let { value -> runCatching { ChatAppearance.fromJson(value) }.getOrNull() }, it.optString("alias"), it.optString("phone")) } },
            drafts = drafts.keys().asSequence().associateWith { drafts.getString(it) },
            groups = (0 until groups.length()).map { groups.getJSONObject(it).let { g ->
                val members = g.getJSONArray("members")
                ChatGroup(g.getString("address"), g.getString("name"), (0 until members.length()).map { members.getString(it) }.toSet(),
                    g.optBoolean("owner"), false) } },
            invitations = (0 until invitations.length()).map { invitations.getJSONObject(it).let { i -> GroupInvitation(i.getString("room"), i.getString("inviter")) } },
            requests = (0 until requests.length()).map { requests.getString(it) }.toSet(),
            blocked = (0 until blocked.length()).map { blocked.getString(it) }.toSet(),
            contacts = (0 until contacts.length()).map { contacts.getJSONObject(it).let { c -> ChatContact(c.getString("address"), c.getString("name"), group = c.optBoolean("group")) } },
            messages = (0 until messages.length()).map { messages.getJSONObject(it).let { m ->
                val status = MessageDelivery.valueOf(m.getString("delivery"))
                ChatPayload.decode(ChatMessage(m.getString("id"), m.getString("peer"), m.getString("body"), m.getBoolean("outgoing"), m.getLong("timestamp"),
                    if (status == MessageDelivery.SENDING) MessageDelivery.FAILED else status,
                    MessageProtection.valueOf(m.getString("protection")), m.getBoolean("read"), m.optBoolean("markable"), sender = m.optString("sender")))
            } })
    }
    override fun onCleared() {
        ++generation
        // Socket close can block; never run it on the UI thread.
        val current = transport
        Thread { current?.disconnect() }.start()
    }
    private fun errorText(e: Exception): UiText = when {
        e is ChatException -> e.text
        e is UndecidedOmemoIdentityException -> UiText(R.string.chat_verify_first)
        ChatTls.isIdentityFailure(e) -> UiText(R.string.chat_tls_error)
        e is SASLErrorException -> UiText(R.string.chat_login_error)
        e is org.jivesoftware.smack.XMPPException.XMPPErrorException && e.stanzaError.condition ==
            org.jivesoftware.smack.packet.StanzaError.Condition.subscription_required -> UiText(R.string.chat_subscription_required)
        else -> UiText(R.string.chat_connection_error)
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T {
            AndroidSmackInitializer.initialize(context)
            val vault = ChatVault.forApp(context)
            val notifications = ChatNotifications(context)
            return ChatViewModel(vault, { SmackMessenger(vault) }, notifications::show, notifications::clear,
                media = ChatMedia(context.applicationContext), artwork = ChatArtwork(context.applicationContext), initialForeground = false) as T
        }
    }
}
