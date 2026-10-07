package org.astianx.midori.one.wallet

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.astianx.midori.one.R
import org.astianx.midori.one.UiText
import org.json.JSONObject

/** Mainnet credentials never read or overwrite the old wdk_wallet testnet store. */
internal class KeystoreWalletStore(context: Context) : WalletStore {
    private val prefs = context.getSharedPreferences("wdk_wallet_mainnet", Context.MODE_PRIVATE)
    private val keyAlias = "midori.mainnet.wallet.v1"
    private val cipher = WalletSecretCipher()

    override fun hasCredentials() = prefs.contains("credentials_v1")

    private fun existingKey(): SecretKey? = KeyStore.getInstance("AndroidKeyStore").run {
        load(null)
        getKey(keyAlias, null) as? SecretKey
    }

    private fun encryptionKey(): SecretKey = existingKey() ?: KeyGenerator.getInstance(
        KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
    ).run {
        init(KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build())
        generateKey()
    }

    override fun load(): WalletCredentials? {
        val envelope = prefs.getString("credentials_v1", null) ?: return null
        // Never replace a missing key while restoring: keep the encrypted data for recovery.
        val key = existingKey() ?: throw WalletException(UiText(R.string.error_local_key))
        return cipher.decrypt(envelope, key)
    }

    override fun save(key: String, seed: String): Boolean {
        val envelope = cipher.encrypt(WalletCredentials(key, seed), encryptionKey())
        return prefs.edit().putString("credentials_v1", envelope).commit()
    }

    override fun clear() {
        walletRequire(prefs.edit().clear().commit(), R.string.error_delete_wallet)
    }
}

/** Authenticated encryption binds the version and mainnet storage context. */
internal class WalletSecretCipher {
    private val aad = "midori:mainnet:wallet:v1".toByteArray(Charsets.UTF_8)

    fun encrypt(credentials: WalletCredentials, key: SecretKey): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(aad)
        val plain = JSONObject().put("key", credentials.key).put("seed", credentials.seed)
            .toString().toByteArray(Charsets.UTF_8)
        val encrypted = try { cipher.doFinal(plain) } finally { plain.fill(0) }
        val encoder = Base64.getEncoder()
        return "1:${encoder.encodeToString(cipher.iv)}:${encoder.encodeToString(encrypted)}"
    }

    fun decrypt(envelope: String, key: SecretKey): WalletCredentials {
        val parts = envelope.split(':')
        walletRequire(parts.size == 3 && parts[0] == "1", R.string.error_local_backup)
        val decoder = Base64.getDecoder()
        val iv = decoder.decode(parts[1])
        walletRequire(iv.size == 12, R.string.error_local_backup)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        cipher.updateAAD(aad)
        val plain = cipher.doFinal(decoder.decode(parts[2]))
        return try {
            val json = JSONObject(String(plain, Charsets.UTF_8))
            WalletCredentials(json.getString("key"), json.getString("seed")).also {
                walletRequire(it.key.isNotEmpty() && it.seed.isNotEmpty(), R.string.error_local_backup)
            }
        } finally { plain.fill(0) }
    }
}
