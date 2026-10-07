package org.astianx.midori.one.chat

import java.util.Base64
import java.util.Date
import java.util.TreeMap
import java.util.TreeSet
import org.jivesoftware.smackx.omemo.internal.OmemoCachedDeviceList
import org.jivesoftware.smackx.omemo.internal.OmemoDevice
import org.jivesoftware.smackx.omemo.signal.SignalOmemoStore
import org.jivesoftware.smackx.omemo.trust.OmemoFingerprint
import org.jivesoftware.smackx.omemo.trust.OmemoTrustCallback
import org.jivesoftware.smackx.omemo.trust.TrustState
import org.jxmpp.jid.BareJid
import org.whispersystems.libsignal.IdentityKey
import org.whispersystems.libsignal.IdentityKeyPair
import org.whispersystems.libsignal.state.PreKeyRecord
import org.whispersystems.libsignal.state.SessionRecord
import org.whispersystems.libsignal.state.SignedPreKeyRecord

/** Serializes libsignal records; all cryptography remains in the upstream implementation. */
internal class EncryptedOmemoStore(private val records: ChatRecords) : SignalOmemoStore() {
    private fun jid(jid: BareJid) = Base64.getUrlEncoder().withoutPadding().encodeToString(jid.toString().toByteArray())
    private fun root(d: OmemoDevice) = "omemo/${jid(d.jid)}/${d.deviceId}/"
    private fun peer(u: OmemoDevice, d: OmemoDevice) = "${root(u)}peer/${jid(d.jid)}/${d.deviceId}/"
    private fun read(k: String) = records.get(k)?.let { Base64.getDecoder().decode(it) }
    private fun write(k: String, bytes: ByteArray) = records.put(k, Base64.getEncoder().encodeToString(bytes))
    private fun remove(k: String) = records.put(k, null)
    private fun date(k: String): Date? = records.get(k)?.toLong()?.let(::Date)
    private fun <T> indexed(prefix: String, decode: (ByteArray) -> T): TreeMap<Int, T> = TreeMap<Int, T>().apply {
        records.keys(prefix).forEach { k -> put(k.removePrefix(prefix).toInt(), decode(read(k)!!)) }
    }

    override fun localDeviceIdsOf(localUser: BareJid) = TreeSet<Int>().apply {
        val prefix = "omemo/${jid(localUser)}/"
        records.keys(prefix).filter { it.endsWith("/identity") && !it.contains("/peer/") }.forEach {
            add(it.removePrefix(prefix).substringBefore('/').toInt())
        }
    }
    override fun loadOmemoIdentityKeyPair(u: OmemoDevice) = read("${root(u)}identity")?.let(::IdentityKeyPair)
    override fun storeOmemoIdentityKeyPair(u: OmemoDevice, k: IdentityKeyPair) = write("${root(u)}identity", k.serialize())
    override fun removeOmemoIdentityKeyPair(u: OmemoDevice) = remove("${root(u)}identity")
    override fun loadOmemoIdentityKey(u: OmemoDevice, d: OmemoDevice) = read("${peer(u,d)}identity")?.let { IdentityKey(it, 0) }
    override fun storeOmemoIdentityKey(u: OmemoDevice, d: OmemoDevice, k: IdentityKey) = write("${peer(u,d)}identity", k.serialize())
    override fun removeOmemoIdentityKey(u: OmemoDevice, d: OmemoDevice) = remove("${peer(u,d)}identity")
    override fun loadOmemoPreKey(u: OmemoDevice, id: Int) = read("${root(u)}pre/$id")?.let(::PreKeyRecord)
    override fun storeOmemoPreKey(u: OmemoDevice, id: Int, k: PreKeyRecord) = write("${root(u)}pre/$id", k.serialize())
    override fun removeOmemoPreKey(u: OmemoDevice, id: Int) = remove("${root(u)}pre/$id")
    override fun loadOmemoPreKeys(u: OmemoDevice) = indexed("${root(u)}pre/", ::PreKeyRecord)
    override fun loadOmemoSignedPreKey(u: OmemoDevice, id: Int) = read("${root(u)}signed/$id")?.let(::SignedPreKeyRecord)
    override fun storeOmemoSignedPreKey(u: OmemoDevice, id: Int, k: SignedPreKeyRecord) = write("${root(u)}signed/$id", k.serialize())
    override fun removeOmemoSignedPreKey(u: OmemoDevice, id: Int) = remove("${root(u)}signed/$id")
    override fun loadOmemoSignedPreKeys(u: OmemoDevice) = indexed("${root(u)}signed/", ::SignedPreKeyRecord)
    override fun loadRawSession(u: OmemoDevice, d: OmemoDevice) = read("${peer(u,d)}session")?.let(::SessionRecord)
    override fun storeRawSession(u: OmemoDevice, d: OmemoDevice, s: SessionRecord) = write("${peer(u,d)}session", s.serialize())
    override fun removeRawSession(u: OmemoDevice, d: OmemoDevice) = remove("${peer(u,d)}session")
    override fun containsRawSession(u: OmemoDevice, d: OmemoDevice) = records.get("${peer(u,d)}session") != null
    override fun loadAllRawSessionsOf(u: OmemoDevice, contact: BareJid): Map<Int, SessionRecord> {
        val prefix = "${root(u)}peer/${jid(contact)}/"
        return records.keys(prefix).filter { it.endsWith("/session") }.associate {
            it.removePrefix(prefix).substringBefore('/').toInt() to SessionRecord(read(it))
        }
    }
    override fun removeAllRawSessionsOf(u: OmemoDevice, contact: BareJid) {
        records.keys("${root(u)}peer/${jid(contact)}/").filter { it.endsWith("/session") }.forEach(::remove)
    }
    override fun storeOmemoMessageCounter(u: OmemoDevice, d: OmemoDevice, c: Int) = records.put("${peer(u,d)}counter", c.toString())
    override fun loadOmemoMessageCounter(u: OmemoDevice, d: OmemoDevice) = records.get("${peer(u,d)}counter")?.toInt() ?: 0
    override fun setDateOfLastReceivedMessage(u: OmemoDevice, d: OmemoDevice, date: Date) = records.put("${peer(u,d)}received", date.time.toString())
    override fun getDateOfLastReceivedMessage(u: OmemoDevice, d: OmemoDevice) = date("${peer(u,d)}received")
    override fun setDateOfLastDeviceIdPublication(u: OmemoDevice, d: OmemoDevice, date: Date) = records.put("${peer(u,d)}published", date.time.toString())
    override fun getDateOfLastDeviceIdPublication(u: OmemoDevice, d: OmemoDevice) = date("${peer(u,d)}published")
    override fun setDateOfLastSignedPreKeyRenewal(u: OmemoDevice, date: Date) = records.put("${root(u)}renewed", date.time.toString())
    override fun getDateOfLastSignedPreKeyRenewal(u: OmemoDevice) = date("${root(u)}renewed")
    override fun loadCachedDeviceList(u: OmemoDevice, contact: BareJid): OmemoCachedDeviceList {
        fun ids(part: String) = records.get("${root(u)}list/${jid(contact)}/$part")?.split(',')
            ?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
        return OmemoCachedDeviceList(ids("active"), ids("inactive"))
    }
    override fun storeCachedDeviceList(u: OmemoDevice, contact: BareJid, list: OmemoCachedDeviceList) {
        fun changed(part: String, ids: Set<Int>) {
            val key = "${root(u)}list/${jid(contact)}/$part"
            val value = ids.sorted().joinToString(",")
            if (records.get(key) != value) records.put(key, value)
        }
        changed("active", list.activeDevices)
        changed("inactive", list.inactiveDevices)
    }
    /** Same cache merge as Smack's device-list refresh; network reads have already completed. */
    fun mergePublishedDevices(u: OmemoDevice, contact: BareJid, ids: Set<Int>) {
        val cached = loadCachedDeviceList(u, contact)
        ids.filterNot { cached.contains(it) }.forEach { id ->
            setDateOfLastDeviceIdPublication(u, OmemoDevice(contact, id), Date())
        }
        cached.merge(ids)
        storeCachedDeviceList(u, contact, cached)
    }
    override fun purgeOwnDeviceKeys(u: OmemoDevice) { records.keys(root(u)).forEach(::remove) }

    fun trustFor(account: String) = object : OmemoTrustCallback {
        // Trust is scoped to both identity and device. A changed key never inherits trust.
        private fun key(d: OmemoDevice, f: OmemoFingerprint) = "trust/$account/${d.jid}/${d.deviceId}/$f"
        override fun getTrust(d: OmemoDevice, f: OmemoFingerprint) =
            records.get(key(d, f))?.let(TrustState::valueOf) ?: TrustState.undecided
        override fun setTrust(d: OmemoDevice, f: OmemoFingerprint, state: TrustState) = records.put(key(d, f), state.name)
    }
}
