package org.astianx.midori.one.chat

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject

internal interface ChatRecords {
    fun get(key: String): String?
    fun put(key: String, value: String?)
    fun keys(prefix: String): Set<String>
    fun <T> durableBatch(block: () -> T): T = block()
}

/** Chat credentials, identities, ratchets and history never share the wallet key or preferences. */
internal class ChatVault(context: Context, storageName: String = "messenger-v1") : ChatRecords {
    companion object {
        private var appVault: ChatVault? = null

        // Smack retains its store for the process lifetime. Share its record cache with
        // recreated ViewModels so a history write cannot overwrite newer ratchet keys.
        @Synchronized fun forApp(context: Context): ChatVault =
            appVault ?: ChatVault(context.applicationContext).also { appVault = it }
    }

    private val file = AtomicFile(File(context.noBackupFilesDir, "$storageName.enc"))
    private val alias = "midori.${storageName.replace('-', '.')}"
    private val aad = "midori:${storageName.replace('-', ':')}".toByteArray()
    private val wrappingAad = aad + ":data-key:v2".toByteArray()
    private class Envelope(val dataKey: SecretKey, val header: ByteArray)
    private var envelope: Envelope? = null
    private var records: Map<String, String>? = null
    private class EncodedRecord(val value: String, val json: String)
    private var encodedRecords = emptyMap<String, EncodedRecord>()
    private class Batch(val snapshot: Map<String, String>) {
        val changes = mutableMapOf<String, String?>()
        val reads = mutableSetOf<String>()
        val prefixes = mutableSetOf<String>()
    }
    private val batch = ThreadLocal<Batch>()

    private fun key(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        check(create) { "Chat encryption key unavailable" }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
            generateKey()
        }
    }

    private fun load(): Map<String, String> {
        records?.let { return it }
        val result = if (!file.baseFile.exists()) emptyMap() else {
            val bytes = file.readFully()
            check(bytes.size > 29)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            var loadedEnvelope: Envelope? = null
            val offset = when (bytes[0].toInt()) {
                1 -> {
                    cipher.init(Cipher.DECRYPT_MODE, key(false), GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
                    cipher.updateAAD(aad)
                    13
                }
                2 -> {
                    check(bytes.size > 89)
                    val unwrap = Cipher.getInstance("AES/GCM/NoPadding")
                    unwrap.init(Cipher.DECRYPT_MODE, key(false), GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
                    unwrap.updateAAD(wrappingAad)
                    val raw = unwrap.doFinal(bytes.copyOfRange(13, 61))
                    val dataKey = try { check(raw.size == 32); SecretKeySpec(raw, "AES") } finally { raw.fill(0) }
                    loadedEnvelope = Envelope(dataKey, bytes.copyOfRange(0, 61))
                    cipher.init(Cipher.DECRYPT_MODE, dataKey, GCMParameterSpec(128, bytes.copyOfRange(61, 73)))
                    cipher.updateAAD(aad + loadedEnvelope.header)
                    73
                }
                else -> error("Unknown chat vault format")
            }
            val plain = cipher.doFinal(bytes.copyOfRange(offset, bytes.size))
            try {
                val json = JSONObject(String(plain, Charsets.UTF_8))
                json.keys().asSequence().associateWith { json.getString(it) }.also { envelope = loadedEnvelope }
            } finally { plain.fill(0) }
        }
        records = result
        return result
    }

    @Synchronized override fun get(key: String): String? {
        val pending = batch.get() ?: return load()[key]
        pending.reads.add(key)
        return if (pending.changes.containsKey(key)) pending.changes[key] else pending.snapshot[key]
    }
    @Synchronized override fun keys(prefix: String): Set<String> {
        val pending = batch.get() ?: return load().keys.filter { it.startsWith(prefix) }.toSet()
        pending.prefixes.add(prefix)
        return applyChanges(pending.snapshot, pending.changes).keys.filter { it.startsWith(prefix) }.toSet()
    }

    @Synchronized override fun put(key: String, value: String?) {
        batch.get()?.let { pending ->
            pending.reads.add(key)
            pending.changes[key] = value
            return
        }
        persist(applyChanges(load(), mapOf(key to value)))
    }

    /** No lock is held during network/crypto work. Commit before returning any ciphertext.
     * Completed ratchet advances also persist when the block fails, as with individual writes.
     * Conflicting concurrent reads/writes abort the send instead of overwriting newer state.
     */
    override fun <T> durableBatch(block: () -> T): T {
        if (batch.get() != null) return block()
        val pending = synchronized(this) { Batch(load()) }
        batch.set(pending)
        try {
            val result = runCatching(block)
            synchronized(this) {
                val current = load()
                check(pending.reads.all { current[it] == pending.snapshot[it] } &&
                    pending.prefixes.all { prefix ->
                        current.keys.filter { it.startsWith(prefix) }.toSet() ==
                            pending.snapshot.keys.filter { it.startsWith(prefix) }.toSet()
                    }) { "Chat records changed during encryption" }
                if (pending.changes.isNotEmpty()) persist(applyChanges(current, pending.changes))
            }
            return result.getOrThrow()
        } finally { batch.remove() }
    }

    private fun applyChanges(original: Map<String, String>, changes: Map<String, String?>) = original.toMutableMap().apply {
        changes.forEach { (key, value) -> if (value == null) remove(key) else put(key, value) }
    }

    private fun persist(updated: Map<String, String>) {
        // Standard envelope encryption: Keystore protects a random, chat-only AES-256 data key.
        // Avoid routing the whole history through Keystore IPC on every incoming ratchet update.
        val wrappingKey = key(!file.baseFile.exists()) // A missing Keystore key still blocks writes.
        val currentEnvelope = envelope ?: newEnvelope(wrappingKey)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, currentEnvelope.dataKey)
        check(cipher.iv.size == 12)
        cipher.updateAAD(aad + currentEnvelope.header)
        // Incoming session confirmations change one record at a time. Reuse JSON escaping
        // for unchanged entries instead of scanning every character of the entire history.
        val encoded = updated.mapValues { (name, value) ->
            encodedRecords[name]?.takeIf { it.value == value }
                ?: EncodedRecord(value, JSONObject.quote(name) + ":" + JSONObject.quote(value))
        }
        val plain = encoded.values.joinToString(",", "{", "}") { it.json }.toByteArray(Charsets.UTF_8)
        val encrypted = try { cipher.doFinal(plain) } finally { plain.fill(0) }
        val stream = file.startWrite()
        try {
            stream.write(currentEnvelope.header + cipher.iv + encrypted)
            file.finishWrite(stream)
            records = updated
            envelope = currentEnvelope
            encodedRecords = encoded
        } catch (e: Exception) {
            file.failWrite(stream)
            throw e
        }
    }

    private fun newEnvelope(wrappingKey: SecretKey): Envelope {
        val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
        try {
            val wrap = Cipher.getInstance("AES/GCM/NoPadding")
            wrap.init(Cipher.ENCRYPT_MODE, wrappingKey)
            check(wrap.iv.size == 12)
            wrap.updateAAD(wrappingAad)
            return Envelope(SecretKeySpec(raw, "AES"), byteArrayOf(2) + wrap.iv + wrap.doFinal(raw))
        } finally { raw.fill(0) }
    }
}
