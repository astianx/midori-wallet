package org.astianx.midori.one.chat

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
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
    private var records: Map<String, String>? = null
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
            check(bytes.size > 29 && bytes[0] == 1.toByte())
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(false), GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
            cipher.updateAAD(aad)
            val plain = cipher.doFinal(bytes.copyOfRange(13, bytes.size))
            try {
                val json = JSONObject(String(plain, Charsets.UTF_8))
                json.keys().asSequence().associateWith { json.getString(it) }
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
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(!file.baseFile.exists()))
        cipher.updateAAD(aad)
        val plain = JSONObject(updated).toString().toByteArray(Charsets.UTF_8)
        val encrypted = try { cipher.doFinal(plain) } finally { plain.fill(0) }
        val stream = file.startWrite()
        try {
            stream.write(byteArrayOf(1) + cipher.iv + encrypted)
            file.finishWrite(stream)
            records = updated
        } catch (e: Exception) {
            file.failWrite(stream)
            throw e
        }
    }
}
