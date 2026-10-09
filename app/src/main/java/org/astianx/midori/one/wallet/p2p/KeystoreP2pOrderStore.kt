package org.astianx.midori.one.wallet.p2p

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Independent from the wallet and chat vaults. Failed reads never replace the encrypted journal. */
internal class KeystoreP2pOrderStore(context: Context,
    preferences: String = "midori_p2p_mainnet_v1", private val alias: String = "midori.mainnet.p2p.v1") : P2pOrderStore {
    private val prefs = context.applicationContext.getSharedPreferences(preferences, Context.MODE_PRIVATE)
    private val expected = mutableMapOf<String, String?>()
    companion object { private val lock = Any() }
    private fun ownerKey(owner: String) = owner.lowercase().also { require(it.matches(Regex("0x[a-f0-9]{40}"))) }
    private fun key(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(alias, null) as? SecretKey
        if (existing != null) return existing
        check(create && !store.containsAlias(alias) && prefs.all.isEmpty())
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }
    override suspend fun load(owner: String): List<P2pPrivateRecord> = withContext(Dispatchers.IO) {
        synchronized(lock) {
            try {
                val id = ownerKey(owner); val encoded = prefs.getString(id, null)
                val rows = if (encoded == null) emptyList() else {
                    val envelope = JSONObject(encoded); check(envelope.getInt("version") == 1)
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, key(false), GCMParameterSpec(128, Base64.decode(envelope.getString("iv"), Base64.NO_WRAP)))
                    cipher.updateAAD("midori:p2p:1:$id".toByteArray())
                    val plain = cipher.doFinal(Base64.decode(envelope.getString("ciphertext"), Base64.NO_WRAP))
                    try {
                        val json = JSONArray(plain.toString(Charsets.UTF_8)); check(json.length() <= 200)
                        (0 until json.length()).map { P2pJson.record(json.getJSONObject(it)) }.also { records ->
                            check(records.all { it.owner.equals(owner, true) } && records.map { it.localId }.distinct().size == records.size)
                        }
                    } finally { plain.fill(0) }
                }
                expected[id] = encoded; rows
            } catch (_: Exception) { throw P2pOrderFailure("local_storage_failed") }
        }
    }
    override suspend fun save(owner: String, records: List<P2pPrivateRecord>) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            try {
                val id = ownerKey(owner)
                check(expected.containsKey(id) && expected[id] == prefs.getString(id, null))
                check(records.size <= 200 && records.all { it.owner.equals(owner, true) })
                val plain = JSONArray().apply { records.forEach { put(P2pJson.record(it)) } }.toString().toByteArray(Charsets.UTF_8)
                try {
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key(true))
                    cipher.updateAAD("midori:p2p:1:$id".toByteArray())
                    val encoded = JSONObject().put("version", 1).put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                        .put("ciphertext", Base64.encodeToString(cipher.doFinal(plain), Base64.NO_WRAP)).toString()
                    check(prefs.edit().putString(id, encoded).commit()); expected[id] = encoded
                } finally { plain.fill(0) }
            } catch (_: Exception) { throw P2pOrderFailure("local_storage_failed") }
        }
    }
}
