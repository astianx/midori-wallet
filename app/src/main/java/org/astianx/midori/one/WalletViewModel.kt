package org.astianx.midori.one

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import to.tether.wdk.core.WdkCore
import java.math.BigDecimal
import java.util.UUID

enum class Screen { Welcome, RestoreError, Create, Import, Home, Send, Receive, Sign }
enum class Network { ETH, BTC }
enum class Asset(val network: Network, val decimals: Int) {
    USDC(Network.ETH, 6), ETH(Network.ETH, 18), BTC(Network.BTC, 8)
}

const val USDC_SEPOLIA_CONTRACT = "0x1c7D4B196Cb0C7B01d743Fbc6116a902379C7238"

enum class TxStatus { PENDING, BROADCAST, UNKNOWN, FAILED_BEFORE_BROADCAST }

internal fun isValidVerificationResult(result: Any): Boolean = result is Boolean && result

internal fun toSmallestUnit(amount: String, decimals: Int): String? = try {
    val units = BigDecimal(amount).movePointRight(decimals).toBigIntegerExact()
    units.takeIf { it > java.math.BigInteger.ZERO }?.toString()
} catch (_: Exception) {
    null
}

data class PendingTx(
    val id: String = UUID.randomUUID().toString(),
    val network: Network,
    val asset: Asset = if (network == Network.ETH) Asset.ETH else Asset.BTC,
    val toAddress: String,
    val amount: String,
    val status: TxStatus = TxStatus.PENDING,
    val hash: String? = null,
    val error: String? = null
) {
    val explorerUrl: String?
        get() = when {
            hash == null -> null
            network == Network.ETH -> "https://sepolia.etherscan.io/tx/$hash"
            else -> "https://blockbook.tbtc-1.zelcore.io/tx/$hash"
        }

    val statusLabel: String
        get() = when (status) {
            TxStatus.PENDING -> "Submitting to network..."
            TxStatus.BROADCAST -> "Broadcast · confirmation not checked"
            TxStatus.UNKNOWN -> "Submission uncertain · check network"
            TxStatus.FAILED_BEFORE_BROADCAST -> "No se emitió · revisa saldo, gas y conexión"
        }
}

data class WalletUiState(
    val currentScreen: Screen = Screen.Welcome,
    val seedPhrase: List<String> = emptyList(),
    val importWords: List<String> = List(12) { "" },
    val importError: Boolean = false,
    val toastMessage: String? = null,
    val toastIsSuccess: Boolean = false,
    val isLoading: Boolean = false,
    val statusText: String = "",
    val restoreError: String? = null,
    val ethAddress: String = "",
    val ethBalance: String = "0",
    val usdcBalance: String? = null,
    val btcAddress: String = "",
    val btcBalance: String = "0",
    val isWdkInitialized: Boolean = false,
    val sendAsset: Asset = Asset.USDC,
    val sendAddress: String = "",
    val sendAmount: String = "",
    val pendingTransactions: List<PendingTx> = emptyList(),
    val receiveAsset: Asset = Asset.USDC,
    val signNetwork: Network = Network.ETH,
    val signMessage: String = "Login to MyDApp\nTimestamp: 1711036800\nNonce: a3f8c2",
    val signResult: String? = null,
    val verifyResult: Boolean? = null
)

class WalletViewModel(private val context: Context) : ViewModel() {
    private val _state = MutableStateFlow(WalletUiState())
    val state: StateFlow<WalletUiState> = _state.asStateFlow()

    private val prefs = context.getSharedPreferences("wdk_wallet", Context.MODE_PRIVATE)
    private var wdkCore: WdkCore? = null
    private var encryptionKey = ""
    private var encryptedSeed = ""
    private var isRefreshingBalance = false

    // NOTE: must be declared before the init block below — Kotlin runs property
    // initializers and init blocks in declaration order, and the restore coroutine
    // (launched on Dispatchers.Main.immediate) reads this synchronously during
    // construction. Declared after init, it would still be null at that point.
    private val wdkConfig = """
    {
      "networks": {
        "sepolia": {
            "blockchain": "sepolia",
            "config": {
            "chainId": 11155111,
              "provider": "https://ethereum-sepolia.publicnode.com"
            }
        },
        "bitcoin": {
          "blockchain": "bitcoin",
          "config": {
          "client": {
            "type": "blockbook-http",
            "clientConfig": {
              "url": "https://blockbook.tbtc-1.zelcore.io/api"
            }
          },
          "network": "testnet"
          }
        }
      }
    }
    """.trimIndent()

    init {
        val savedKey = prefs.getString("encryptionKey", null)
        val savedSeed = prefs.getString("encryptedSeed", null)
        if (!savedKey.isNullOrEmpty() && !savedSeed.isNullOrEmpty()) restoreWallet()
    }

    private suspend fun initializeWallet(key: String, seed: String): Pair<String, String> {
        val wdk = getOrCreateClient()
        wdk.initializeWDK(encryptionKey = key, encryptedSeed = seed, config = wdkConfig)
        val ethAddress = wdk.getAddress(network = "sepolia")
        val btcAddress = wdk.getAddress(network = "bitcoin")
        require(ethAddress.isNotBlank() && btcAddress.isNotBlank()) { "Wallet addresses are unavailable" }
        return ethAddress to btcAddress
    }

    fun restoreWallet() {
        val key = prefs.getString("encryptionKey", null)
        val seed = prefs.getString("encryptedSeed", null)
        if (key.isNullOrEmpty() || seed.isNullOrEmpty()) return
        if (_state.value.isLoading) return
        viewModelScope.launch {
            update { copy(isLoading = true, statusText = "Restoring wallet...", restoreError = null) }
            try {
                val (eth, btc) = initializeWallet(key, seed)
                encryptionKey = key
                encryptedSeed = seed
                update { copy(isWdkInitialized = true, ethAddress = eth, btcAddress = btc,
                    currentScreen = Screen.Home, isLoading = false, statusText = "") }
                fetchBalance()
            } catch (e: Exception) {
                discardClient()
                update { copy(currentScreen = Screen.RestoreError, isWdkInitialized = false,
                    isLoading = false, statusText = "", restoreError = e.message ?: "Unknown error") }
            }
        }
    }

    private suspend fun saveCredentials(key: String, seed: String) {
        val saved = withContext(kotlinx.coroutines.Dispatchers.IO) {
            prefs.edit().putString("encryptionKey", key).putString("encryptedSeed", seed).commit()
        }
        check(saved) { "Could not save the wallet on this device" }
    }

    private fun clearCredentials() {
        prefs.edit().clear().apply()
    }

    private fun getOrCreateClient(): WdkCore {
        return wdkCore ?: WdkCore(context).also { wdkCore = it }
    }

    private suspend fun discardClient() {
        val old = wdkCore
        wdkCore = null
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            try { old?.close() } catch (_: Exception) {}
        }
    }

    private fun update(transform: WalletUiState.() -> WalletUiState) {
        _state.value = _state.value.transform()
    }

    fun navigate(screen: Screen) {
        val destination = if (screen == Screen.Welcome &&
            prefs.contains("encryptionKey") && prefs.contains("encryptedSeed") &&
            !_state.value.isWdkInitialized) Screen.RestoreError else screen
        update { copy(currentScreen = destination) }
    }

    fun showToast(message: String, success: Boolean = false) {
        update { copy(toastMessage = message, toastIsSuccess = success) }
        viewModelScope.launch {
            delay(2500)
            if (_state.value.toastMessage == message) {
                update { copy(toastMessage = null) }
            }
        }
    }

    fun createWallet() {
        if (prefs.contains("encryptionKey") || prefs.contains("encryptedSeed")) {
            navigate(Screen.RestoreError)
            return
        }
        viewModelScope.launch {
            update { copy(isLoading = true, statusText = "Generating seed phrase...") }
            try {
                val wdk = getOrCreateClient()
                val entropy = wdk.generateEntropyAndEncrypt(wordCount = 12)
                encryptionKey = entropy.encryptionKey
                encryptedSeed = entropy.encryptedSeedBuffer

                val mnemonic = wdk.getMnemonicFromEntropy(
                    encryptedEntropy = entropy.encryptedEntropyBuffer,
                    encryptionKey = entropy.encryptionKey
                )
                val words = mnemonic.trim().split(" ")
                update { copy(seedPhrase = words, currentScreen = Screen.Create, isLoading = false, statusText = "") }
            } catch (e: Exception) {
                update { copy(isLoading = false, statusText = "") }
                showToast("Error: ${e.message}")
            }
        }
    }

    fun confirmSeedAndInitialize() {
        viewModelScope.launch {
            update { copy(isLoading = true, statusText = "Initializing wallet...") }
            try {
                val (eth, btc) = initializeWallet(encryptionKey, encryptedSeed)
                saveCredentials(encryptionKey, encryptedSeed)
                update { copy(isWdkInitialized = true, ethAddress = eth, btcAddress = btc,
                    seedPhrase = emptyList(), currentScreen = Screen.Home, isLoading = false, statusText = "") }
                fetchBalance()
            } catch (e: Exception) {
                discardClient()
                update { copy(isLoading = false, statusText = "") }
                showToast("WDK init failed: ${e.message}")
            }
        }
    }

    fun updateImportWord(index: Int, word: String) {
        val words = _state.value.importWords.toMutableList()
        words[index] = word
        update { copy(importWords = words) }
    }

    fun pasteImportWords(clipText: String) {
        val words = clipText.trim().split("\\s+".toRegex())
        val importWords = _state.value.importWords.toMutableList()
        for (i in 0 until minOf(words.size, 12)) {
            importWords[i] = words[i].lowercase()
        }
        update { copy(importWords = importWords, importError = false) }
    }

    fun doImport() {
        val words = _state.value.importWords.map { it.trim().lowercase() }
        val filled = words.filter { it.isNotEmpty() }
        if (filled.size < 12) {
            update { copy(importError = true) }
            return
        }
        update { copy(importError = false) }

        val mnemonic = words.joinToString(" ")
        viewModelScope.launch {
            update { copy(isLoading = true, statusText = "Importing wallet...") }
            try {
                val wdk = getOrCreateClient()
                val result = wdk.getSeedAndEntropyFromMnemonic(mnemonic = mnemonic)
                val (eth, btc) = initializeWallet(result.encryptionKey, result.encryptedSeedBuffer)
                saveCredentials(result.encryptionKey, result.encryptedSeedBuffer)
                encryptionKey = result.encryptionKey
                encryptedSeed = result.encryptedSeedBuffer
                update { copy(isWdkInitialized = true, ethAddress = eth, btcAddress = btc,
                    importWords = List(12) { "" }, currentScreen = Screen.Home,
                    isLoading = false, statusText = "", restoreError = null) }
                fetchBalance()
            } catch (e: Exception) {
                discardClient()
                update { copy(isLoading = false, statusText = "") }
                showToast("Import failed: ${e.message}")
            }
        }
    }

    fun fetchBalance() {
        if (!_state.value.isWdkInitialized || wdkCore == null || isRefreshingBalance) return
        isRefreshingBalance = true
        viewModelScope.launch {
            try {
                try {
                    val ethBal = wdkCore!!.getBalance(network = "sepolia")
                    update { copy(ethBalance = ethBal) }
                } catch (e: Exception) {
                    android.util.Log.w("WDK", "ETH balance fetch failed: ${e.message}")
                }
                try {
                    val tokenBalance = wdkCore!!.callMethod(
                        methodName = "getTokenBalance", network = "sepolia", accountIndex = 0,
                        args = JSONObject.quote(USDC_SEPOLIA_CONTRACT)
                    ).toString()
                    require(tokenBalance.matches(Regex("[0-9]+"))) { "Invalid USDC balance" }
                    update { copy(usdcBalance = tokenBalance) }
                } catch (e: Exception) {
                    update { copy(usdcBalance = null) }
                    android.util.Log.w("WDK", "USDC balance fetch failed: ${e.message}")
                }
                try {
                    val btcBal = wdkCore!!.getBalance(network = "bitcoin")
                    update { copy(btcBalance = btcBal) }
                } catch (e: Exception) {
                    android.util.Log.w("WDK", "BTC balance fetch failed: ${e.message}")
                }
            } finally {
                isRefreshingBalance = false
            }
        }
    }

    private fun extractFee(result: Any): String {
        if (result is JSONObject) {
            val fee = result.opt("fee")
            if (fee != null) return fee.toString()
        }
        return result.toString()
    }

    private fun extractTxHash(result: Any): String {
        val candidate = when (result) {
            is JSONObject -> result.optString("hash", "")
            is String -> if (result.trim().startsWith("{")) {
                JSONObject(result).optString("hash", "")
            } else result.trim()
            else -> ""
        }
        require(candidate.matches(Regex("^(0x)?[a-fA-F0-9]{64}$"))) {
            "WDK did not return a valid transaction hash"
        }
        return candidate
    }

    fun updateSendAsset(asset: Asset) {
        update { copy(sendAsset = asset, sendAmount = "", sendAddress = "") }
    }

    fun updateSendAddress(address: String) {
        update { copy(sendAddress = address) }
    }

    fun updateSendAmount(amount: String) {
        update { copy(sendAmount = amount) }
    }

    fun updateReceiveAsset(asset: Asset) {
        update { copy(receiveAsset = asset) }
    }

    fun updateSignNetwork(network: Network) {
        update { copy(signNetwork = network, signResult = null, verifyResult = null) }
    }

    fun updateSignMessage(message: String) {
        update { copy(signMessage = message, signResult = null, verifyResult = null) }
    }

    fun setSendMax() {
        if (_state.value.sendAsset == Asset.USDC) {
            update { copy(sendAmount = maxSendAmount) }
        }
    }

    fun doSend() {
        val s = _state.value
        if (s.isLoading) return
        val address = s.sendAddress.trim()
        val amount = s.sendAmount.trim()
        if (address.isEmpty() || amount.isEmpty()) {
            showToast("Please fill in address and amount")
            return
        }
        if (!s.isWdkInitialized || wdkCore == null) {
            showToast("Wallet not initialized")
            return
        }

        val asset = s.sendAsset
        val valueInSmallestUnit = toSmallestUnit(amount, asset.decimals) ?: run {
            showToast("Invalid amount")
            return
        }
        if (asset.network == Network.ETH && !address.matches(Regex("0x[a-fA-F0-9]{40}"))) {
            showToast("Invalid Sepolia address")
            return
        }
        if (asset.network == Network.ETH && address.equals("0x0000000000000000000000000000000000000000", ignoreCase = true)) {
            showToast("La dirección cero no puede recibir este envío")
            return
        }
        if (asset == Asset.USDC) {
            val balance = s.usdcBalance?.toBigIntegerOrNull()
            if (balance == null) {
                showToast("USDC balance unavailable. Refresh and try again.")
                return
            }
            if (valueInSmallestUnit.toBigInteger() > balance) {
                showToast("Insufficient USDC balance")
                return
            }
        }

        val networkRpc = if (asset.network == Network.ETH) "sepolia" else "bitcoin"
        val tx = PendingTx(network = asset.network, asset = asset, toAddress = address, amount = amount)
        val txId = tx.id

        update { copy(pendingTransactions = pendingTransactions + tx,
            isLoading = true, statusText = "Submitting transaction...") }

        viewModelScope.launch {
            var submissionStarted = false
            try {
                val argsJson = if (asset == Asset.USDC) {
                    JSONObject().put("token", USDC_SEPOLIA_CONTRACT)
                        .put("recipient", address).put("amount", valueInSmallestUnit).toString()
                } else {
                    JSONObject().put("to", address)
                        .put("value", valueInSmallestUnit).toString()
                }
                if (asset == Asset.USDC) {
                    val quoted = wdkCore!!.callMethod(
                        methodName = "quoteTransfer", network = "sepolia", accountIndex = 0,
                        args = argsJson
                    )
                    val fee = extractFee(quoted).toBigIntegerOrNull()
                    require(fee != null && fee > java.math.BigInteger.ZERO) { "Unable to quote USDC gas" }
                    require(s.ethBalance.toBigIntegerOrNull()?.let { it >= fee } == true) {
                        "Insufficient Sepolia ETH for gas"
                    }
                }
                submissionStarted = true
                val result = wdkCore!!.callMethod(
                    methodName = if (asset == Asset.USDC) "transfer" else "sendTransaction",
                    network = networkRpc,
                    accountIndex = 0,
                    args = argsJson
                )
                val hash = extractTxHash(result)
                updateTx(txId, TxStatus.BROADCAST, hash = hash)
                update { copy(sendAddress = "", sendAmount = "") }
                showToast("Broadcast to network; confirmation not checked", success = true)
                fetchBalance()
            } catch (e: Exception) {
                if (submissionStarted) {
                    updateTx(txId, TxStatus.UNKNOWN, error = e.message)
                    showToast("Submission uncertain. Check your address before retrying.")
                } else {
                    updateTx(txId, TxStatus.FAILED_BEFORE_BROADCAST, error = e.message)
                    showToast(e.message ?: "No se pudo preparar la transferencia")
                }
            } finally {
                update { copy(isLoading = false, statusText = "") }
            }
        }
    }

    private fun updateTx(id: String, status: TxStatus, hash: String? = null, error: String? = null) {
        val txList = _state.value.pendingTransactions.toMutableList()
        val index = txList.indexOfFirst { it.id == id }
        if (index >= 0) {
            txList[index] = txList[index].copy(status = status, hash = hash, error = error)
            update { copy(pendingTransactions = txList) }
        }
    }

    private fun jsonEncodeString(value: String): String {
        val escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }

    fun doSign() {
        val message = _state.value.signMessage.trim()
        if (message.isEmpty()) return
        if (!_state.value.isWdkInitialized || wdkCore == null) {
            showToast("Wallet not initialized")
            return
        }
        update { copy(verifyResult = null) }

        val network = if (_state.value.signNetwork == Network.ETH) "sepolia" else "bitcoin"
        viewModelScope.launch {
            update { copy(isLoading = true, statusText = "Signing message...") }
            try {
                val result = wdkCore!!.callMethod(
                    methodName = "sign",
                    network = network,
                    accountIndex = 0,
                    args = jsonEncodeString(message)
                )
                update { copy(signResult = result.toString(), currentScreen = Screen.Sign, isLoading = false, statusText = "") }
                showToast("Message signed successfully", success = true)
            } catch (e: Exception) {
                update { copy(isLoading = false, statusText = "") }
                showToast("Sign failed: ${e.message}")
            }
        }
    }

    fun doVerify() {
        val signature = _state.value.signResult ?: return
        val message = _state.value.signMessage.trim()
        if (message.isEmpty()) return
        if (!_state.value.isWdkInitialized || wdkCore == null) {
            showToast("Wallet not initialized")
            return
        }

        val network = if (_state.value.signNetwork == Network.ETH) "sepolia" else "bitcoin"
        viewModelScope.launch {
            update { copy(isLoading = true, statusText = "Verifying signature...") }
            try {
                val argsArray = JSONArray().apply {
                    put(message)
                    put(signature)
                }
                val result = wdkCore!!.callMethod(
                    methodName = "verify",
                    network = network,
                    accountIndex = 0,
                    args = argsArray.toString()
                )
                val valid = isValidVerificationResult(result)
                update { copy(verifyResult = valid, isLoading = false, statusText = "") }
                showToast(if (valid) "Signature is valid" else "Signature is invalid", success = valid)
            } catch (e: Exception) {
                update { copy(verifyResult = false, isLoading = false, statusText = "") }
                showToast("Verify failed: ${e.message}")
            }
        }
    }

    fun deleteWallet() {
        viewModelScope.launch {
            try { wdkCore?.dispose() } catch (_: Exception) {}
            val old = wdkCore
            wdkCore = null
            encryptionKey = ""
            encryptedSeed = ""
            clearCredentials()
            update {
                WalletUiState()
            }
            showToast("Wallet deleted")
            withContext(kotlinx.coroutines.Dispatchers.IO) {
                try { old?.close() } catch (_: Exception) {}
            }
        }
    }

    fun resetImport() {
        update { copy(importWords = List(12) { "" }, importError = false) }
    }

    fun resetSign() {
        update { copy(
            signMessage = "Login to MyDApp\nTimestamp: 1711036800\nNonce: a3f8c2",
            signResult = null,
            verifyResult = null
        ) }
    }

    fun resetSend() {
        update { copy(sendAddress = "", sendAmount = "", sendAsset = Asset.USDC) }
    }

    val formattedUsdcBalance: String
        get() = _state.value.usdcBalance?.let { formatBalance(it, 6, "USDC") } ?: "— USDC"

    val formattedEthBalance: String
        get() = formatBalance(_state.value.ethBalance, 18, "ETH")

    val formattedBtcBalance: String
        get() = formatBalance(_state.value.btcBalance, 8, "BTC")

    private fun formatBalance(raw: String, decimals: Int, symbol: String): String = try {
        "${BigDecimal(raw).movePointLeft(decimals).stripTrailingZeros().toPlainString()} $symbol"
    } catch (_: NumberFormatException) {
        "— $symbol"
    }

    val receiveAddress: String
        get() = if (_state.value.receiveAsset.network == Network.ETH) _state.value.ethAddress else _state.value.btcAddress

    val receiveLabel: String
        get() = when (_state.value.receiveAsset) {
            Asset.USDC -> "USDC de prueba · Ethereum Sepolia"
            Asset.ETH -> "Tu dirección de ETH · Ethereum Sepolia"
            Asset.BTC -> "Tu dirección de BTC · Bitcoin testnet4"
        }

    val sendFee: String
        get() {
            val s = _state.value
            if (s.sendAsset == Asset.USDC) return "Gas en ETH Sepolia · se cotiza al enviar"
            return "Variable · se calcula al enviar"
        }

    val sendNetworkLabel: String
        get() = if (_state.value.sendAsset.network == Network.ETH) "Ethereum Sepolia" else "Bitcoin testnet4"

    val sendTokenLabel: String
        get() = _state.value.sendAsset.name

    val maxSendAmount: String
        get() {
            val s = _state.value
            if (s.sendAsset != Asset.USDC) return "0"
            return s.usdcBalance?.let { BigDecimal(it).movePointLeft(6).stripTrailingZeros().toPlainString() } ?: "0"
        }

    override fun onCleared() {
        super.onCleared()
        wdkCore?.close()
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return WalletViewModel(context.applicationContext) as T
        }
    }
}
