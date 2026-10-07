package org.astianx.midori.one

import android.content.Context
import to.tether.wdk.core.EntropyResult
import to.tether.wdk.core.SeedAndEntropyResult
import to.tether.wdk.core.WdkCore

internal data class WalletCredentials(val key: String, val seed: String)

internal interface WalletStore {
    fun hasCredentials(): Boolean
    fun load(): WalletCredentials?
    fun save(key: String, seed: String): Boolean
    fun clear()
}

/** Small boundary for fault tests; the ViewModel still owns all operations and state. */
internal interface WalletClient {
    suspend fun initializeWDK(encryptionKey: String, encryptedSeed: String, config: String)
    suspend fun getAddress(network: String): String
    suspend fun getBalance(network: String): String
    suspend fun generateEntropyAndEncrypt(wordCount: Int): EntropyResult
    suspend fun getMnemonicFromEntropy(encryptedEntropy: String, encryptionKey: String): String
    suspend fun getSeedAndEntropyFromMnemonic(mnemonic: String): SeedAndEntropyResult
    suspend fun callMethod(methodName: String, network: String, accountIndex: Int, args: String): Any
    suspend fun dispose()
    fun close()
}

internal class WdkWalletClient(context: Context) : WalletClient {
    private val core = WdkCore(context)
    override suspend fun initializeWDK(encryptionKey: String, encryptedSeed: String, config: String) =
        core.initializeWDK(encryptionKey, encryptedSeed, config)
    override suspend fun getAddress(network: String) = core.getAddress(network)
    override suspend fun getBalance(network: String) = core.getBalance(network)
    override suspend fun generateEntropyAndEncrypt(wordCount: Int) = core.generateEntropyAndEncrypt(wordCount)
    override suspend fun getMnemonicFromEntropy(encryptedEntropy: String, encryptionKey: String) =
        core.getMnemonicFromEntropy(encryptedEntropy, encryptionKey)
    override suspend fun getSeedAndEntropyFromMnemonic(mnemonic: String) = core.getSeedAndEntropyFromMnemonic(mnemonic)
    override suspend fun callMethod(methodName: String, network: String, accountIndex: Int, args: String) =
        core.callMethod(methodName, network, accountIndex, args)
    override suspend fun dispose() = core.dispose()
    override fun close() = core.close()
}
