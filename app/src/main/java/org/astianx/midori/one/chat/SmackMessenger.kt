package org.astianx.midori.one.chat

import java.util.Date
import java.util.UUID
import org.astianx.midori.one.BuildConfig
import org.astianx.midori.one.R
import org.astianx.midori.one.UiText
import org.jivesoftware.smack.ConnectionConfiguration
import org.jivesoftware.smack.ConnectionListener
import org.jivesoftware.smack.ReconnectionManager
import org.jivesoftware.smack.XMPPConnection
import org.jivesoftware.smack.filter.StanzaTypeFilter
import org.jivesoftware.smack.packet.Message
import org.jivesoftware.smack.packet.Presence
import org.jivesoftware.smack.packet.Stanza
import org.jivesoftware.smack.roster.AbstractRosterListener
import org.jivesoftware.smack.roster.Roster
import org.jivesoftware.smack.tcp.XMPPTCPConnection
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration
import org.jivesoftware.smackx.blocking.BlockingCommandManager
import org.jivesoftware.smackx.carbons.CarbonManager
import org.jivesoftware.smackx.carbons.packet.CarbonExtension
import org.jivesoftware.smackx.chat_markers.element.ChatMarkersElements
import org.jivesoftware.smackx.csi.ClientStateIndicationManager
import org.jivesoftware.smackx.delay.DelayInformationManager
import org.jivesoftware.smackx.mam.MamManager
import org.jivesoftware.smackx.omemo.OmemoConfiguration
import org.jivesoftware.smackx.omemo.OmemoManager
import org.jivesoftware.smackx.omemo.OmemoMessage
import org.jivesoftware.smackx.omemo.element.OmemoElement
import org.jivesoftware.smackx.omemo.internal.OmemoDevice
import org.jivesoftware.smackx.omemo.listener.OmemoMessageListener
import org.jivesoftware.smackx.omemo.signal.SignalOmemoService
import org.jivesoftware.smackx.receipts.DeliveryReceiptManager
import org.jivesoftware.smackx.receipts.DeliveryReceiptRequest
import org.jxmpp.jid.Jid
import org.jxmpp.jid.impl.JidCreate

/** Blocking network API. The ViewModel serializes calls on its IO dispatcher. No plaintext send API. */
internal class SmackMessenger(
    private val records: ChatRecords,
    private val devCertificatePin: String = if (BuildConfig.DEBUG) BuildConfig.CHAT_DEV_CERT_SHA256 else "",
    private val connectionFactory: (XMPPTCPConnectionConfiguration) -> XMPPTCPConnection = ::XMPPTCPConnection
) : ChatTransport {
    private var connection: XMPPTCPConnection? = null
    private var omemo: OmemoManager? = null
    private var groups: GroupTransport? = null
    private lateinit var events: ChatEvents
    private lateinit var account: ChatAccount
    @Volatile private var ready = false
    @Volatile private var active = true

    override fun connect(account: ChatAccount, events: ChatEvents) {
        disconnect()
        this.account = account
        this.events = events
        val store = initializeOmemo(records)
        events.connection(ChatConnection.CONNECTING)
        val jid = JidCreate.entityBareFrom(account.address)
        val config = XMPPTCPConnectionConfiguration.builder()
            .setXmppDomain(jid.domain.toString()).setUsernameAndPassword(jid.localpart, account.password)
            .setHost(account.host).setPort(account.port).setResource("MiWallet-${UUID.randomUUID().toString().take(8)}")
            .setSecurityMode(ConnectionConfiguration.SecurityMode.required)
            .setCompressionEnabled(false).setConnectTimeout(15_000)
        ChatTls.applyLocalPin(config, account, devCertificatePin)
        val conn = connectionFactory(config.build())
        conn.setUseStreamManagement(true)
        conn.setUseStreamManagementResumption(true)
        connection = conn
        conn.addConnectionListener(object : ConnectionListener {
            override fun connectionClosed() { ready = false; events.connection(ChatConnection.DISCONNECTED) }
            override fun connectionClosedOnError(e: Exception) { ready = false; events.connectionFailed(e) }
            override fun authenticated(connection: XMPPConnection, resumed: Boolean) {
                if (resumed && omemo != null) { ready = true; events.connection(ChatConnection.ONLINE) }
            }
        })
        // Receipts acknowledge only messages we successfully decrypted, not plaintext or failed decryptions.
        DeliveryReceiptManager.getInstanceFor(conn).apply {
            autoReceiptMode = DeliveryReceiptManager.AutoReceiptMode.disabled
            addReceiptReceivedListener { from, _, id, _ -> events.receipt(from.asBareJid().toString(), id, MessageDelivery.DELIVERED) }
        }
        conn.addAsyncStanzaListener({ stanza ->
            val message = stanza as Message
            val from = message.from?.asBareJid()?.toString() ?: return@addAsyncStanzaListener
            ChatMarkersElements.DisplayedExtension.from(message)?.let {
                events.receipt(from, it.id, MessageDelivery.READ)
            }
            if (message.type == Message.Type.error) return@addAsyncStanzaListener
            if (message.body != null && !message.hasExtension("encrypted", OMEMO_NAMESPACE)) {
                receive(message, "", false, MessageProtection.REJECTED_PLAINTEXT)
            }
        }, StanzaTypeFilter.MESSAGE)
        Roster.getInstanceFor(conn).apply {
            subscriptionMode = Roster.SubscriptionMode.manual
            addSubscribeListener { from, _ -> events.subscriptionRequested(from.asBareJid().toString()); null }
            addRosterListener(object : AbstractRosterListener() {
                override fun entriesAdded(addresses: Collection<Jid>) = publishRoster()
                override fun entriesUpdated(addresses: Collection<Jid>) = publishRoster()
                override fun entriesDeleted(addresses: Collection<Jid>) = publishRoster()
                override fun presenceChanged(presence: Presence) = publishRoster()
            })
        }
        try {
            // Register decryption listeners before authentication: offline delivery can begin at login.
            val manager = OmemoManager.getInstanceFor(conn)
            omemo = manager
            manager.setTrustCallback(store.trustFor(account.address))
            groups = SmackGroups(conn, manager, account.address, records, events, store)
            manager.addOmemoMessageListener(object : OmemoMessageListener {
                override fun onOmemoMessageReceived(stanza: Stanza, decrypted: OmemoMessage.Received) {
                    if (stanza is Message && decrypted.body != null) {
                        receive(stanza, decrypted.body, false)
                        if (DeliveryReceiptRequest.from(stanza) != null) {
                            runCatching { conn.sendStanza(DeliveryReceiptManager.receiptMessageFor(stanza)) }
                        }
                    }
                }
                override fun onOmemoCarbonCopyReceived(direction: CarbonExtension.Direction, copy: Message,
                    wrapping: Message, decrypted: OmemoMessage.Received) {
                    if (decrypted.body != null) receive(copy, decrypted.body, true)
                }
            })
            conn.connect().login()
            check(conn.isSecureConnection)
            events.connection(ChatConnection.SECURING)
            manager.initialize()
            events.fingerprint(manager.ownFingerprint.toString())
            ready = true
            publishRoster()
            runCatching {
                val blocking = BlockingCommandManager.getInstanceFor(conn)
                if (blocking.isSupportedByServer) events.blocked(blocking.blockList.map { it.toString() }.toSet())
            }
            runCatching { if (CarbonManager.getInstanceFor(conn).isSupportedByServer) CarbonManager.getInstanceFor(conn).enableCarbons() }
            syncArchive()
            runCatching { groups!!.restore() }.onFailure { events.failure(UiText(R.string.chat_group_restore_error)) }
            setActive(active)
            events.connection(ChatConnection.ONLINE)
            org.jivesoftware.smackx.ping.PingManager.getInstanceFor(conn).apply {
                pingInterval = 120
                registerPingFailedListener {
                    ready = false
                    events.connectionFailed(java.io.IOException("Server ping timed out"))
                }
            }
            // Smack resumes a stream when possible. A full reconnect is initialized explicitly on retry/resume.
            ReconnectionManager.getInstanceFor(conn).disableAutomaticReconnection()
        } catch (e: Exception) {
            ready = false
            runCatching { conn.disconnect() }
            throw e
        }
    }

    private fun publishRoster() {
        val conn = connection ?: return
        val roster = Roster.getInstanceFor(conn)
        events.contacts(roster.entries.map { entry -> ChatContact(entry.jid.toString(),
            entry.name?.takeIf { it.isNotBlank() } ?: entry.jid.localpartOrNull.toString(), roster.getPresence(entry.jid).isAvailable) })
    }

    private fun receive(stanza: Message, body: String, historical: Boolean,
        protection: MessageProtection = MessageProtection.ENCRYPTED, timestamp: Long? = null) {
        if (stanza.type !in setOf(Message.Type.chat, Message.Type.normal)) return
        val from = stanza.from?.asEntityBareJidIfPossible()?.toString() ?: return
        val outgoing = from == account.address
        val peer = if (outgoing) stanza.to?.asEntityBareJidIfPossible()?.toString() ?: return else from
        val id = stanza.stanzaId ?: return // Stable IDs are required for safe archive/live deduplication.
        events.message(ChatPayload.decode(ChatMessage(id, peer, body, outgoing,
            timestamp ?: DelayInformationManager.getDelayTimestamp(stanza)?.time ?: System.currentTimeMillis(),
            protection = protection, markable = ChatMarkersElements.MarkableExtension.from(stanza) != null, sender = from)), historical)
    }

    private fun syncArchive() {
        val conn = connection ?: return
        val manager = omemo ?: return
        val mam = MamManager.getInstanceFor(conn)
        if (!mam.isSupported) return
        val watermarkKey = "archive/${account.address}"
        val watermark = records.get(watermarkKey)?.toLongOrNull()
        val args = MamManager.MamQueryArgs.builder().setResultPageSizeTo(100)
        if (watermark != null) args.limitResultsSince(Date((watermark - 60_000).coerceAtLeast(0))) else args.queryLastPage()
        var query = mam.queryArchive(args.build())
        var latest = watermark ?: 0L
        do {
            query.page.forwarded.forEach { forwarded ->
                val stanza = forwarded.forwardedStanza
                val stamp = forwarded.delayInformation?.stamp?.time ?: System.currentTimeMillis()
                val encrypted = stanza.getExtension(javax.xml.namespace.QName(OMEMO_NAMESPACE, "encrypted")) as? OmemoElement
                if (encrypted != null) {
                    // Lost keys cannot decrypt a new device's old archive. Never invent the original text.
                    val decrypted = runCatching { manager.decrypt(stanza.from.asBareJid(), encrypted) }.getOrNull()
                    if (decrypted?.body != null) receive(stanza, decrypted.body, true, timestamp = stamp)
                    else receive(stanza, "", true, MessageProtection.UNREADABLE, stamp)
                } else if (stanza.body != null) receive(stanza, "", true, MessageProtection.REJECTED_PLAINTEXT, stamp)
                latest = maxOf(latest, stamp)
            }
            if (query.isComplete || watermark == null) break
            query.pageNext(100)
        } while (true)
        if (latest > 0) records.put(watermarkKey, latest.toString())
    }

    override fun send(peer: String, body: String, id: String) {
        val conn = connection ?: throw ChatException(UiText(R.string.chat_offline_error))
        val manager = omemo ?: throw ChatException(UiText(R.string.chat_encryption_error))
        if (!ready || !conn.isAuthenticated) throw ChatException(UiText(R.string.chat_offline_error))
        if (groups?.knows(peer) == true) { groups!!.send(peer, body, id); return }
        val recipient = JidCreate.entityBareFrom(peer)
        manager.requestDeviceListUpdateFor(recipient)
        if (!manager.contactSupportsOmemo(recipient)) throw ChatException(UiText(R.string.chat_no_secure_device))
        val encrypted = manager.encrypt(recipient, body)
        if (encrypted.isMissingRecipients || encrypted.intendedDevices.none { it.jid == recipient }) {
            throw ChatException(UiText(R.string.chat_encryption_error))
        }
        val builder = org.jivesoftware.smack.packet.StanzaBuilder.buildMessage(id)
            .addExtension(DeliveryReceiptRequest())
            .addExtension(ChatMarkersElements.MarkableExtension.INSTANCE)
        val message = encrypted.buildMessage(builder, recipient)
        check(message.body == null && message.hasExtension("encrypted", OMEMO_NAMESPACE))
        conn.sendStanza(message)
    }

    override fun devices(peer: String): List<ChatDevice> {
        val manager = omemo ?: throw ChatException(UiText(R.string.chat_offline_error))
        val recipients = if (groups?.knows(peer) == true) groups!!.recipients(peer) else setOf(peer)
        return (recipients + account.address).flatMap { address ->
            val jid = JidCreate.entityBareFrom(address)
            manager.requestDeviceListUpdateFor(jid)
            manager.getActiveFingerprints(jid).filterKeys { it != manager.ownDevice }.map { (device, fingerprint) ->
                ChatDevice(device.jid.toString(), device.deviceId, fingerprint.toString(), manager.isTrustedOmemoIdentity(device, fingerprint))
            }
        }
    }

    override fun trust(device: ChatDevice, trusted: Boolean) {
        val manager = omemo ?: throw ChatException(UiText(R.string.chat_offline_error))
        val identity = OmemoDevice(JidCreate.entityBareFrom(device.address), device.id)
        // Recheck the locally known identity. Trust remains tied to this exact fingerprint.
        val current = manager.getFingerprint(identity)
        if (current.toString() != device.fingerprint) throw ChatException(UiText(R.string.chat_device_changed))
        if (trusted) manager.trustOmemoIdentity(identity, current) else manager.distrustOmemoIdentity(identity, current)
    }

    override fun loadProfile(peer: String): PublishedChatProfile {
        val conn = connection ?: throw ChatException(UiText(R.string.chat_offline_error))
        check(ready && conn.isAuthenticated)
        return SmackProfiles.load(conn, peer)
    }

    override fun publishProfile(value: PublishedChatProfile) {
        val conn = connection ?: throw ChatException(UiText(R.string.chat_offline_error))
        check(ready && conn.isAuthenticated)
        SmackProfiles.publish(conn, value)
    }

    override fun addContact(peer: String) {
        val conn = connection ?: throw ChatException(UiText(R.string.chat_offline_error))
        Roster.getInstanceFor(conn).createItemAndRequestSubscription(JidCreate.entityBareFrom(peer), peer.substringBefore('@'), null)
        publishRoster()
    }

    override fun answerSubscription(peer: String, approve: Boolean) {
        val conn = connection ?: throw ChatException(UiText(R.string.chat_offline_error))
        val address = JidCreate.entityBareFrom(peer)
        conn.sendStanza(conn.stanzaFactory.buildPresenceStanza().to(address)
            .ofType(if (approve) Presence.Type.subscribed else Presence.Type.unsubscribed).build())
        if (approve) Roster.getInstanceFor(conn).createItemAndRequestSubscription(address, peer.substringBefore('@'), null)
        publishRoster()
    }

    override fun block(peer: String, blocked: Boolean) {
        val conn = connection ?: throw ChatException(UiText(R.string.chat_offline_error))
        val manager = BlockingCommandManager.getInstanceFor(conn)
        val jids = listOf<Jid>(JidCreate.entityBareFrom(peer))
        if (blocked) manager.blockContacts(jids) else manager.unblockContacts(jids)
        events.blocked(manager.blockList.map { it.toString() }.toSet())
    }

    override fun markRead(peer: String, id: String) {
        val conn = connection ?: return
        if (groups?.knows(peer) == true) return
        if (ready && conn.isAuthenticated) conn.sendStanza(conn.stanzaFactory.buildMessageStanza()
            .to(JidCreate.entityBareFrom(peer)).ofType(Message.Type.chat)
            .addExtension(ChatMarkersElements.DisplayedExtension(id)).build())
    }

    override fun setActive(active: Boolean) {
        this.active = active
        val conn = connection ?: return
        if (conn.isAuthenticated && ClientStateIndicationManager.isSupported(conn)) {
            if (active) ClientStateIndicationManager.active(conn) else ClientStateIndicationManager.inactive(conn)
        }
    }

    override fun disconnect() {
        ready = false
        connection?.let { runCatching { it.disconnect() } }
        connection = null
        omemo = null
        groups = null
    }

    override fun createGroup(name: String, members: Set<String>) = requireNotNull(groups).create(name, members)
    override fun joinGroup(room: String) = requireNotNull(groups).join(room)
    override fun groupInfo(room: String) = requireNotNull(groups).info(room)
    override fun inviteToGroup(room: String, peer: String) = requireNotNull(groups).invite(room, peer)
    override fun removeGroupMember(room: String, peer: String) = requireNotNull(groups).remove(room, peer)
    override fun deleteGroup(room: String) = requireNotNull(groups).delete(room)
    override fun leaveGroup(room: String) = requireNotNull(groups).leave(room)
    override fun requestUpload(size: Long): UploadSlot {
        val conn = connection ?: throw ChatException(UiText(R.string.chat_offline_error))
        val manager = org.jivesoftware.smackx.httpfileupload.HttpFileUploadManager.getInstanceFor(conn)
        if (!manager.isUploadServiceDiscovered) manager.discoverUploadService()
        if (!manager.isUploadServiceDiscovered) throw ChatException(UiText(R.string.chat_upload_unavailable))
        val slot = manager.requestSlot("${UUID.randomUUID()}.bin", size, "application/octet-stream")
        return UploadSlot(slot.putUrl.toString(), slot.getUrl.toString(), slot.headers)
    }
    override fun validateMediaUrl(url: String) {
        val uri = java.net.URI(url)
        val domain = account.address.substringAfter('@')
        val local = BuildConfig.DEBUG && devCertificatePin.isNotEmpty() && domain == "localhost" && uri.host == "localhost" && uri.port == 5443
        val remote = domain != "localhost" && (uri.host == domain || uri.host?.endsWith(".$domain") == true) && uri.port in setOf(-1, 443)
        if (uri.scheme != "https" || uri.userInfo != null || uri.fragment != null || !(local || remote))
            throw ChatException(UiText(R.string.chat_file_origin_error))
    }

    companion object {
        const val OMEMO_NAMESPACE = "eu.siacs.conversations.axolotl"
        private var cryptoStore: EncryptedOmemoStore? = null
        @Synchronized private fun initializeOmemo(records: ChatRecords): EncryptedOmemoStore {
            cryptoStore?.let { return it }
            SignalOmemoService.acknowledgeLicense()
            SignalOmemoService.setup()
            OmemoConfiguration.setAddOmemoHintBody(false)
            OmemoConfiguration.setIgnoreReadOnlyDevices(false)
            val store = EncryptedOmemoStore(records)
            (SignalOmemoService.getInstance() as SignalOmemoService).setOmemoStoreBackend(store)
            cryptoStore = store
            return store
        }
    }
}
