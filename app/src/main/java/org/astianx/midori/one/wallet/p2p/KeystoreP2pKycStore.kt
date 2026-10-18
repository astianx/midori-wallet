// Copyright 2026 Midori. Licensed under the Apache License, Version 2.0.
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
import org.json.JSONObject

/** Account-bound KYC journal. Independent key; unreadable data is never replaced. */
internal class KeystoreP2pKycStore(context: Context, preferences: String = "midori_p2p_kyc_mainnet_v1",
    private val alias: String = "midori.mainnet.p2p.kyc.v1") : P2pKycStore {
    private val prefs = context.applicationContext.getSharedPreferences(preferences, Context.MODE_PRIVATE)
    private val expected = mutableMapOf<String, String?>()
    companion object { private val lock = Any() }
    private fun ownerKey(owner: String) = owner.lowercase().also { require(it.matches(Regex("0x[a-f0-9]{40}"))) }
    private fun key(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val old = store.getKey(alias, null) as? SecretKey
        if (old != null) return old
        check(create && !store.containsAlias(alias) && prefs.all.isEmpty())
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        }.generateKey()
    }
    override suspend fun load(owner: String): P2pKycRecord? = withContext(Dispatchers.IO) {
        synchronized(lock) {
            try {
                val id = ownerKey(owner); val encoded = prefs.getString(id, null)
                val record = encoded?.let {
                    val j = JSONObject(it); check(j.getInt("version") == 1)
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, key(false), GCMParameterSpec(128, Base64.decode(j.getString("iv"), Base64.NO_WRAP)))
                    cipher.updateAAD("midori:p2p:kyc:1:$id".toByteArray())
                    val plain = cipher.doFinal(Base64.decode(j.getString("ciphertext"), Base64.NO_WRAP))
                    try { P2pKycJson.record(JSONObject(plain.toString(Charsets.UTF_8))).also { r -> check(r.owner.equals(owner, true)) } }
                    finally { plain.fill(0) }
                }
                expected[id] = encoded; record
            } catch (_: Exception) { throw P2pOrderFailure("local_storage_failed") }
        }
    }
    override suspend fun save(owner: String, record: P2pKycRecord) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            try {
                val id = ownerKey(owner)
                check(expected.containsKey(id) && expected[id] == prefs.getString(id, null) && record.owner.equals(owner, true))
                val plain = P2pKycJson.record(record).toString().toByteArray()
                try {
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key(true))
                    cipher.updateAAD("midori:p2p:kyc:1:$id".toByteArray())
                    val encoded = JSONObject().put("version", 1).put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                        .put("ciphertext", Base64.encodeToString(cipher.doFinal(plain), Base64.NO_WRAP)).toString()
                    check(prefs.edit().putString(id, encoded).commit()); expected[id] = encoded
                } finally { plain.fill(0) }
            } catch (_: Exception) { throw P2pOrderFailure("local_storage_failed") }
        }
    }
}
