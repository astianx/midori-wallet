package to.tether.wdk.starter

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
import java.util.Locale
import java.util.UUID

enum class Screen { Welcome, Create, Import, Home, Send, Receive, Sign }
enum class Network { ETH, BTC }

enum class TxStatus { PENDING, COMPLETED, FAILED }

internal fun isValidVerificationResult(result: Any): Boolean = result is Boolean && result

data class PendingTx(
    val id: String = UUID.randomUUID().toString(),
    val network: Network,
    val toAddress: String,
    val amount: String,
    var status: TxStatus = TxStatus.PENDING,
    var hash: String? = null,
    var error: String? = null
) {
    val explorerUrl: String?
        get() = when {
            status != TxStatus.COMPLETED || hash == null -> null
            network == Network.ETH -> "https://sepolia.etherscan.io/tx/$hash"
            else -> "https://blockbook.tbtc-1.zelcore.io/tx/$hash"
        }

    val statusLabel: String
        get() = when (status) {
            TxStatus.PENDING -> "Pending..."
            TxStatus.COMPLETED -> "Broadcast"
            TxStatus.FAILED -> "Failed: ${error ?: "Unknown"}"
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
    val ethAddress: String = "",
    val ethBalance: String = "0",
    val btcAddress: String = "",
    val btcBalance: String = "0",
    val isWdkInitialized: Boolean = false,
    val sendNetwork: Network = Network.ETH,
    val sendAddress: String = "",
    val sendAmount: String = "",
    val pendingTransactions: List<PendingTx> = emptyList(),
    val quotedFeeEth: String? = null,
    val quotedFeeBtc: String? = null,
    val receiveNetwork: Network = Network.ETH,
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
        if (!savedKey.isNullOrEmpty() && !savedSeed.isNullOrEmpty()) {
            encryptionKey = savedKey
            encryptedSeed = savedSeed
            viewModelScope.launch {
                update { copy(isLoading = true, statusText = "Restoring wallet...") }
                try {
                    val wdk = getOrCreateClient()
                    wdk.initializeWDK(encryptionKey = encryptionKey, encryptedSeed = encryptedSeed, config = wdkConfig)
                    update { copy(isWdkInitialized = true) }
                    try {
                        val ethAddr = wdk.getAddress(network = "sepolia")
                        update { copy(ethAddress = ethAddr) }
                    } catch (e: Exception) {
                        android.util.Log.w("WDK", "ETH address fetch failed: ${e.message}")
                    }
                    try {
                        val btcAddr = wdk.getAddress(network = "bitcoin")
                        update { copy(btcAddress = btcAddr) }
                    } catch (e: Exception) {
                        android.util.Log.w("WDK", "BTC address fetch failed: ${e.message}")
                    }
                    update { copy(currentScreen = Screen.Home, isLoading = false, statusText = "") }
                    fetchBalance()
                } catch (e: Exception) {
                    prefs.edit().clear().apply()
                    encryptionKey = ""
                    encryptedSeed = ""
                    update { copy(isLoading = false, statusText = "") }
                    showToast("Failed to restore wallet: ${e.message}")
                }
            }
        }
    }

    private fun saveCredentials() {
        prefs.edit().putString("encryptionKey", encryptionKey).putString("encryptedSeed", encryptedSeed).apply()
    }

    private fun clearCredentials() {
        prefs.edit().clear().apply()
    }

    private fun getOrCreateClient(): WdkCore {
        return wdkCore ?: WdkCore(context).also { wdkCore = it }
    }

    private fun update(transform: WalletUiState.() -> WalletUiState) {
        _state.value = _state.value.transform()
    }

    fun navigate(screen: Screen) {
        update { copy(currentScreen = screen) }
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
                saveCredentials()
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
            val wdk = getOrCreateClient()
            try {
                wdk.initializeWDK(
                    encryptionKey = encryptionKey,
                    encryptedSeed = encryptedSeed,
                    config = wdkConfig
                )
                update { copy(isWdkInitialized = true) }
            } catch (e: Exception) {
                showToast("WDK init failed: ${e.message}")
            }

            try {
                val ethAddr = wdk.getAddress(network = "sepolia")
                update { copy(ethAddress = ethAddr) }
            } catch (e: Exception) {
                android.util.Log.w("WDK", "ETH address fetch failed: ${e.message}")
            }
            try {
                val btcAddr = wdk.getAddress(network = "bitcoin")
                update { copy(btcAddress = btcAddr) }
            } catch (e: Exception) {
                android.util.Log.w("WDK", "BTC address fetch failed: ${e.message}")
            }

            update { copy(currentScreen = Screen.Home, isLoading = false, statusText = "") }
            fetchBalance()
            fetchFeeQuotes()
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
            val wdk = getOrCreateClient()
            try {
                val result = wdk.getSeedAndEntropyFromMnemonic(mnemonic = mnemonic)
                encryptionKey = result.encryptionKey
                encryptedSeed = result.encryptedSeedBuffer
                saveCredentials()

                wdk.initializeWDK(
                    encryptionKey = encryptionKey,
                    encryptedSeed = encryptedSeed,
                    config = wdkConfig
                )
                update { copy(isWdkInitialized = true) }
            } catch (e: Exception) {
                showToast("Import failed: ${e.message}")
            }

            try {
                val ethAddr = wdk.getAddress(network = "sepolia")
                update { copy(ethAddress = ethAddr) }
            } catch (e: Exception) {
                android.util.Log.w("WDK", "ETH address fetch failed: ${e.message}")
            }
            try {
                val btcAddr = wdk.getAddress(network = "bitcoin")
                update { copy(btcAddress = btcAddr) }
            } catch (e: Exception) {
                android.util.Log.w("WDK", "BTC address fetch failed: ${e.message}")
            }

            update { copy(currentScreen = Screen.Home, isLoading = false, statusText = "") }
            fetchBalance()
            fetchFeeQuotes()
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

    fun fetchFeeQuotes() {
        if (!_state.value.isWdkInitialized || wdkCore == null) return
        val wdk = wdkCore!!
        val zeroPadAddress = "0x0000000000000000000000000000000000000000"
        val btcAddr = _state.value.btcAddress.ifEmpty { "tb1qw508d6qejxtdg4y5r3zarvary0c5xw7kxpjzsx" }

        viewModelScope.launch {
            try {
                val ethArgs = """{"to":"$zeroPadAddress","value":"1000"}"""
                val ethResult = wdk.callMethod(
                    methodName = "quoteSendTransaction",
                    network = "sepolia",
                    accountIndex = 0,
                    args = ethArgs
                )
                val fee = extractFee(ethResult)
                update { copy(quotedFeeEth = fee) }
            } catch (e: Exception) {
                android.util.Log.w("WDK", "ETH fee quote failed: ${e.message}")
            }
        }

        viewModelScope.launch {
            try {
                val btcArgs = """{"to":"$btcAddr","value":"1000","confirmationTarget":1}"""
                val btcResult = wdk.callMethod(
                    methodName = "quoteSendTransaction",
                    network = "bitcoin",
                    accountIndex = 0,
                    args = btcArgs
                )
                val fee = extractFee(btcResult)
                update { copy(quotedFeeBtc = fee) }
            } catch (e: Exception) {
                android.util.Log.w("WDK", "BTC fee quote failed: ${e.message}")
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

    private fun toSmallestUnit(amount: String, decimals: Int): String? {
        return try {
            val decimal = BigDecimal(amount)
            val multiplier = BigDecimal.TEN.pow(decimals)
            decimal.multiply(multiplier).toBigInteger().toString()
        } catch (e: Exception) { null }
    }

    private fun extractTxHash(result: Any): String {
        if (result is JSONObject) {
            val hash = result.optString("hash", "")
            if (hash.isNotEmpty()) return hash
        }
        val str = result.toString()
        if (str.startsWith("0x")) return str
        return try {
            val json = JSONObject(str)
            json.optString("hash", str)
        } catch (_: Exception) { str }
    }

    fun updateSendNetwork(network: Network) {
        update { copy(sendNetwork = network) }
    }

    fun updateSendAddress(address: String) {
        update { copy(sendAddress = address) }
    }

    fun updateSendAmount(amount: String) {
        update { copy(sendAmount = amount) }
    }

    fun updateReceiveNetwork(network: Network) {
        update { copy(receiveNetwork = network) }
    }

    fun updateSignNetwork(network: Network) {
        update { copy(signNetwork = network) }
    }

    fun updateSignMessage(message: String) {
        update { copy(signMessage = message) }
    }

    fun setSendMax() {
        update { copy(sendAmount = maxSendAmount) }
    }

    fun doSend() {
        val s = _state.value
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

        val decimals = if (s.sendNetwork == Network.ETH) 18 else 8
        val valueInSmallestUnit = toSmallestUnit(amount, decimals) ?: run {
            showToast("Invalid amount")
            return
        }

        val networkRpc = if (s.sendNetwork == Network.ETH) "sepolia" else "bitcoin"
        val tx = PendingTx(network = s.sendNetwork, toAddress = address, amount = amount)
        val txId = tx.id

        val txList = s.pendingTransactions.toMutableList()
        txList.add(tx)
        update { copy(pendingTransactions = txList, sendAddress = "", sendAmount = "") }
        showToast("Transaction submitted", success = true)

        viewModelScope.launch {
            try {
                val argsJson = """{"to":"$address","value":"$valueInSmallestUnit"}"""
                val result = wdkCore!!.callMethod(
                    methodName = "sendTransaction",
                    network = networkRpc,
                    accountIndex = 0,
                    args = argsJson
                )
                val hash = extractTxHash(result)
                updateTx(txId, TxStatus.COMPLETED, hash = hash)
                showToast("Transaction broadcast!", success = true)
                fetchBalance()
            } catch (e: Exception) {
                updateTx(txId, TxStatus.FAILED, error = e.message)
                showToast("Send failed: ${e.message}")
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

    fun clearCompletedTransactions() {
        val txList = _state.value.pendingTransactions.filter { it.status == TxStatus.PENDING }
        update { copy(pendingTransactions = txList) }
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
        update { copy(sendAddress = "", sendAmount = "", sendNetwork = Network.ETH) }
    }

    val formattedEthBalance: String
        get() {
            val wei = _state.value.ethBalance.toDoubleOrNull() ?: return "${_state.value.ethBalance} ETH"
            val eth = wei / 1_000_000_000_000_000_000.0
            return if (eth == 0.0) "0.0000 ETH" else String.format(Locale.US, "%.4f ETH", eth)
        }

    val formattedBtcBalance: String
        get() {
            val satoshis = _state.value.btcBalance.toDoubleOrNull() ?: return "${_state.value.btcBalance} BTC"
            val btc = satoshis / 100_000_000.0
            return if (btc == 0.0) "0.0000 BTC" else String.format(Locale.US, "%.4f BTC", btc)
        }

    val receiveAddress: String
        get() = if (_state.value.receiveNetwork == Network.ETH) _state.value.ethAddress else _state.value.btcAddress

    val receiveLabel: String
        get() = if (_state.value.receiveNetwork == Network.ETH) "Your Ethereum Sepolia address" else "Your Bitcoin Testnet address"

    val sendFee: String
        get() {
            val s = _state.value
            return if (s.sendNetwork == Network.ETH) {
                val feeWei = s.quotedFeeEth?.let { BigDecimal(it) } ?: return "Estimating..."
                val gwei = feeWei.divide(BigDecimal("1000000000"))
                "~${gwei.toPlainString()} Gwei"
            } else {
                val feeSats = s.quotedFeeBtc?.let { BigDecimal(it) } ?: return "Estimating..."
                "~${feeSats.toPlainString()} sats"
            }
        }

    val sendNetworkLabel: String
        get() = if (_state.value.sendNetwork == Network.ETH) "Ethereum Sepolia" else "Bitcoin Testnet"

    val sendTokenLabel: String
        get() = if (_state.value.sendNetwork == Network.ETH) "ETH" else "BTC"

    val maxSendAmount: String
        get() {
            val s = _state.value
            return if (s.sendNetwork == Network.ETH) {
                val balance = BigDecimal(s.ethBalance)
                val fee = s.quotedFeeEth?.let { BigDecimal(it) } ?: return "0"
                val feeWithMargin = fee.multiply(BigDecimal("1.2"))
                val maxWei = balance.subtract(feeWithMargin)
                if (maxWei <= BigDecimal.ZERO) "0"
                else maxWei.divide(BigDecimal("1000000000000000000")).toPlainString()
            } else {
                val balance = BigDecimal(s.btcBalance)
                val fee = s.quotedFeeBtc?.let { BigDecimal(it) } ?: return "0"
                val feeWithMargin = fee.multiply(BigDecimal("1.2"))
                val maxSats = balance.subtract(feeWithMargin)
                if (maxSats <= BigDecimal.ZERO) "0"
                else maxSats.divide(BigDecimal("100000000")).toPlainString()
            }
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
