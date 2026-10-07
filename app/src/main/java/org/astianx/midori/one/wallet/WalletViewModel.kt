package org.astianx.midori.one.wallet

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.math.BigDecimal
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.astianx.midori.one.AppEntryPreferences
import org.astianx.midori.one.AppEntryStore
import org.astianx.midori.one.AppFeature
import org.astianx.midori.one.IntroSlide
import org.astianx.midori.one.MemoryAppEntryStore
import org.astianx.midori.one.R
import org.astianx.midori.one.Screen
import org.astianx.midori.one.UiText
import org.json.JSONArray
import org.json.JSONObject

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
    val asset: Asset = if (network == Network.BASE) Asset.ETH else Asset.BTC,
    val toAddress: String,
    val amount: String,
    val status: TxStatus = TxStatus.PENDING,
    val hash: String? = null,
    val error: UiText? = null
) {
    val explorerUrl: String?
        get() = when {
            hash == null -> null
            else -> "${network.explorer}/tx/$hash"
        }

    val statusLabel: UiText
        get() = when (status) {
            TxStatus.PENDING -> UiText(R.string.tx_submitting)
            TxStatus.BROADCAST -> UiText(R.string.tx_broadcast)
            TxStatus.UNKNOWN -> UiText(R.string.tx_unknown)
            TxStatus.FAILED_BEFORE_BROADCAST -> UiText(R.string.tx_failed)
        }
}

data class WalletUiState(
    val currentScreen: Screen = Screen.Welcome,
    val introductionPage: Int = 0,
    val hasEnteredApp: Boolean = false,
    val feature: AppFeature = AppFeature.CONTACTS,
    val detailReturnScreen: Screen = Screen.Chats,
    val marketAsset: Asset = Asset.USDC,
    val messagingReturnScreen: Screen = Screen.Welcome,
    val seedPhrase: List<String> = emptyList(),
    val importWords: List<String> = List(12) { "" },
    val importError: Boolean = false,
    val toastMessage: UiText? = null,
    val toastIsSuccess: Boolean = false,
    val isLoading: Boolean = false,
    val refreshingBalances: Boolean = false,
    val statusText: UiText? = null,
    val restoreError: UiText? = null,
    val ethAddress: String = "",
    val ethBalance: String? = null,
    val tokenBalances: Map<Asset, String> = emptyMap(),
    val btcAddress: String = "",
    val btcBalance: String? = null,
    val isWdkInitialized: Boolean = false,
    val sendAsset: Asset = Asset.USDC,
    val sendAddress: String = "",
    val sendAmount: String = "",
    val pendingTransactions: List<PendingTx> = emptyList(),
    val receiveAsset: Asset = Asset.USDC,
    val signNetwork: Network = Network.BASE,
    val signMessage: String = "",
    val signResult: String? = null,
    val verifyResult: Boolean? = null
) {
    val usdcBalance: String? get() = tokenBalances[Asset.USDC]
    val usdtBalance: String? get() = tokenBalances[Asset.USDT]
}

class WalletViewModel internal constructor(
    private val walletStore: WalletStore,
    private val clientFactory: () -> WalletClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val baseFeeProvider: BaseFeeProvider = HttpBaseFeeProvider(),
    private val bitcoinNetworkVerifier: BitcoinNetworkVerifier = HttpBitcoinNetworkVerifier(),
    private val appEntryStore: AppEntryStore = MemoryAppEntryStore()
) : ViewModel() {
    constructor(context: Context) : this(KeystoreWalletStore(context), { WdkWalletClient(context) },
        appEntryStore = AppEntryPreferences(context))

    private val _state = MutableStateFlow(WalletUiState(
        currentScreen = if (appEntryStore.hasEntered()) Screen.Chats else Screen.Welcome,
        hasEnteredApp = appEntryStore.hasEntered()
    ))
    val state: StateFlow<WalletUiState> = _state.asStateFlow()

    private var wdkCore: WalletClient? = null
    private var encryptionKey = ""
    private var encryptedSeed = ""
    private var isRefreshingBalance = false

    init {
        if (walletStore.hasCredentials()) restoreWallet()
    }

    private suspend fun initializeWallet(key: String, seed: String): Pair<String, String> {
        val wdk = getOrCreateClient()
        wdk.initializeWDK(encryptionKey = key, encryptedSeed = seed, config = BaseMainnet.wdkConfig)
        val ethAddress = wdk.getAddress(network = Network.BASE.wdkName)
        val btcAddress = wdk.getAddress(network = "bitcoin")
        walletRequire(ethAddress.isNotBlank() && btcAddress.isNotBlank(), R.string.address_unavailable)
        return ethAddress to btcAddress
    }

    fun restoreWallet() {
        if (_state.value.isLoading) return
        viewModelScope.launch {
            update { copy(isLoading = true, statusText = UiText(R.string.status_restoring), restoreError = null) }
            try {
                val (key, seed) = withContext(ioDispatcher) { walletStore.load() }
                    ?: throw WalletException(UiText(R.string.error_local_backup))
                val (eth, btc) = initializeWallet(key, seed)
                encryptionKey = key
                encryptedSeed = seed
                update { copy(isWdkInitialized = true, ethAddress = eth, btcAddress = btc,
                    currentScreen = Screen.Chats, hasEnteredApp = true, isLoading = false, statusText = null) }
                appEntryStore.markEntered()
                fetchBalance()
            } catch (e: Exception) {
                discardClient()
                update { copy(currentScreen = Screen.RestoreError, isWdkInitialized = false,
                    isLoading = false, statusText = null, restoreError = e.toUiText(R.string.error_restore)) }
            }
        }
    }

    private suspend fun saveCredentials(key: String, seed: String) {
        val saved = withContext(ioDispatcher) {
            walletStore.save(key, seed)
        }
        walletRequire(saved, R.string.error_save_wallet)
    }

    private fun clearCredentials() {
        walletStore.clear()
    }

    private fun getOrCreateClient(): WalletClient {
        return wdkCore ?: clientFactory().also { wdkCore = it }
    }

    private suspend fun discardClient() {
        val old = wdkCore
        wdkCore = null
        withContext(ioDispatcher) {
            try { old?.close() } catch (_: Exception) {}
        }
    }

    private fun update(transform: WalletUiState.() -> WalletUiState) {
        _state.update { it.transform() }
    }

    fun navigate(screen: Screen) {
        val destination = when {
            screen in setOf(Screen.Home, Screen.Send, Screen.Receive, Screen.Sign) &&
                !_state.value.isWdkInitialized && walletStore.hasCredentials() -> Screen.RestoreError
            screen in setOf(Screen.Send, Screen.Receive, Screen.Sign) && !_state.value.isWdkInitialized -> Screen.Home
            else -> screen
        }
        update { copy(currentScreen = destination,
            messagingReturnScreen = if (destination == Screen.Messaging && currentScreen != Screen.Messaging)
                currentScreen else messagingReturnScreen,
            detailReturnScreen = if (destination == Screen.Help) currentScreen else detailReturnScreen) }
    }

    fun enterApp() {
        appEntryStore.markEntered()
        update { copy(currentScreen = Screen.Chats, hasEnteredApp = true) }
    }

    fun openFeature(feature: AppFeature) {
        update { copy(feature = feature, detailReturnScreen = currentScreen, currentScreen = Screen.Feature) }
    }

    fun openMarketAsset(asset: Asset) {
        update { copy(marketAsset = asset, currentScreen = Screen.MarketAsset) }
    }

    fun updateIntroductionPage(page: Int) {
        if (page in IntroSlide.entries.indices) update { copy(introductionPage = page) }
    }

    fun showToast(message: UiText, success: Boolean = false) {
        update { copy(toastMessage = message, toastIsSuccess = success) }
        viewModelScope.launch {
            delay(2500)
            if (_state.value.toastMessage == message) {
                update { copy(toastMessage = null) }
            }
        }
    }

    fun createWallet() {
        if (_state.value.isLoading) return
        if (walletStore.hasCredentials()) {
            navigate(Screen.RestoreError)
            return
        }
        viewModelScope.launch {
            update { copy(isLoading = true, statusText = UiText(R.string.status_generating)) }
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
                update { copy(seedPhrase = words, currentScreen = Screen.Create, isLoading = false, statusText = null) }
            } catch (e: Exception) {
                update { copy(isLoading = false, statusText = null) }
                showToast(e.toUiText(R.string.error_generate))
            }
        }
    }

    fun confirmSeedAndInitialize() {
        if (_state.value.isLoading) return
        viewModelScope.launch {
            update { copy(isLoading = true, statusText = UiText(R.string.status_initializing)) }
            try {
                val (eth, btc) = initializeWallet(encryptionKey, encryptedSeed)
                saveCredentials(encryptionKey, encryptedSeed)
                update { copy(isWdkInitialized = true, ethAddress = eth, btcAddress = btc,
                    seedPhrase = emptyList(), currentScreen = Screen.Chats, hasEnteredApp = true, isLoading = false, statusText = null) }
                appEntryStore.markEntered()
                fetchBalance()
            } catch (e: Exception) {
                discardClient()
                update { copy(isLoading = false, statusText = null) }
                showToast(e.toUiText(R.string.error_initialize))
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
        if (_state.value.isLoading) return
        val words = _state.value.importWords.map { it.trim().lowercase() }
        val filled = words.filter { it.isNotEmpty() }
        if (filled.size < 12) {
            update { copy(importError = true) }
            return
        }
        update { copy(importError = false) }

        val mnemonic = words.joinToString(" ")
        viewModelScope.launch {
            update { copy(isLoading = true, statusText = UiText(R.string.status_importing)) }
            try {
                val wdk = getOrCreateClient()
                val result = wdk.getSeedAndEntropyFromMnemonic(mnemonic = mnemonic)
                val (eth, btc) = initializeWallet(result.encryptionKey, result.encryptedSeedBuffer)
                saveCredentials(result.encryptionKey, result.encryptedSeedBuffer)
                encryptionKey = result.encryptionKey
                encryptedSeed = result.encryptedSeedBuffer
                update { copy(isWdkInitialized = true, ethAddress = eth, btcAddress = btc,
                    importWords = List(12) { "" }, currentScreen = Screen.Chats, hasEnteredApp = true,
                    isLoading = false, statusText = null, restoreError = null) }
                appEntryStore.markEntered()
                fetchBalance()
            } catch (e: Exception) {
                discardClient()
                update { copy(isLoading = false, statusText = null) }
                showToast(e.toUiText(R.string.error_import))
            }
        }
    }

    fun fetchBalance() {
        if (!_state.value.isWdkInitialized || wdkCore == null || isRefreshingBalance) return
        isRefreshingBalance = true
        update { copy(refreshingBalances = true) }
        viewModelScope.launch {
            try {
                try {
                    val ethBal = wdkCore!!.getBalance(network = Network.BASE.wdkName)
                    update { copy(ethBalance = ethBal) }
                } catch (e: Exception) {
                    update { copy(ethBalance = null) }
                }
                Asset.entries.filter { it.isToken && it.isAvailable }.forEach { asset ->
                    try {
                        val tokenBalance = wdkCore!!.callMethod(
                            methodName = "getTokenBalance", network = asset.network.wdkName, accountIndex = 0,
                            args = JSONObject.quote(requireNotNull(asset.contract))
                        ).toString()
                        require(tokenBalance.matches(Regex("[0-9]+"))) { "Invalid token balance" }
                        update { copy(tokenBalances = tokenBalances + (asset to tokenBalance)) }
                    } catch (e: Exception) {
                        update { copy(tokenBalances = tokenBalances - asset) }
                    }
                }
                try {
                    val btcBal = wdkCore!!.getBalance(network = "bitcoin")
                    update { copy(btcBalance = btcBal) }
                } catch (e: Exception) {
                    update { copy(btcBalance = null) }
                }
            } finally {
                isRefreshingBalance = false
                update { copy(refreshingBalances = false) }
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
        if (_state.value.sendAsset.isToken && _state.value.sendAsset.isAvailable) {
            update { copy(sendAmount = maxSendAmount) }
        }
    }

    fun doSend() {
        val s = _state.value
        if (s.isLoading) return
        val address = s.sendAddress.trim()
        val amount = s.sendAmount.trim()
        if (address.isEmpty() || amount.isEmpty()) {
            showToast(UiText(R.string.error_send_fields))
            return
        }
        if (!s.isWdkInitialized || wdkCore == null) {
            showToast(UiText(R.string.error_not_initialized))
            return
        }

        val asset = s.sendAsset
        if (!asset.isAvailable) {
            showToast(asset.unavailableReason ?: UiText(R.string.error_asset_unavailable))
            return
        }
        val valueInSmallestUnit = toSmallestUnit(amount, asset.decimals) ?: run {
            showToast(UiText(R.string.error_amount))
            return
        }
        if (asset.network == Network.BASE && !address.matches(Regex("0x[a-fA-F0-9]{40}"))) {
            showToast(UiText(R.string.error_base_address))
            return
        }
        if (asset.network == Network.BASE && address.equals("0x0000000000000000000000000000000000000000", ignoreCase = true)) {
            showToast(UiText(R.string.error_zero_address))
            return
        }
        if (asset.network == Network.BTC && !isBitcoinMainnetAddress(address)) {
            showToast(UiText(R.string.error_btc_address))
            return
        }
        if (asset.isToken) {
            val balance = s.tokenBalances[asset]?.toBigIntegerOrNull()
            if (balance == null) {
                showToast(UiText(R.string.error_balance_unavailable, listOf(asset.name)))
                return
            }
            if (valueInSmallestUnit.toBigInteger() > balance) {
                showToast(UiText(R.string.error_balance_insufficient, listOf(asset.name)))
                return
            }
        }

        val networkRpc = if (asset.network == Network.BASE) Network.BASE.wdkName else Network.BTC.wdkName
        val tx = PendingTx(network = asset.network, asset = asset, toAddress = address, amount = amount)
        val txId = tx.id

        update { copy(pendingTransactions = pendingTransactions + tx,
            isLoading = true, statusText = UiText(R.string.status_submitting)) }

        viewModelScope.launch {
            var submissionStarted = false
            try {
                val argsJson = if (asset.isToken) {
                    JSONObject().put("token", requireNotNull(asset.contract))
                        .put("recipient", address).put("amount", valueInSmallestUnit).toString()
                } else {
                    JSONObject().put("to", address)
                        .put("value", valueInSmallestUnit).toString()
                }
                if (asset.network == Network.BASE) {
                    val quoted = wdkCore!!.callMethod(
                        methodName = if (asset.isToken) "quoteTransfer" else "quoteSendTransaction",
                        network = Network.BASE.wdkName, accountIndex = 0, args = argsJson
                    )
                    val executionFee = extractFee(quoted).toBigIntegerOrNull()
                        ?: throw WalletException(UiText(R.string.error_gas_quote))
                    val transaction = baseTransaction(asset, s.ethAddress, address, valueInSmallestUnit.toBigInteger())
                    val fee = quoteBaseTotalFee(transaction, executionFee, baseFeeProvider)
                    val gasBalance = wdkCore!!.getBalance(network = Network.BASE.wdkName)
                    update { copy(ethBalance = gasBalance) }
                    walletRequire(gasBalance.toBigIntegerOrNull()?.let { it >= fee + transaction.value } == true,
                        R.string.error_insufficient_gas)
                } else {
                    bitcoinNetworkVerifier.verify()
                    // WDK validates the checksum/network and available UTXOs during quotation.
                    wdkCore!!.callMethod(methodName = "quoteSendTransaction", network = Network.BTC.wdkName,
                        accountIndex = 0, args = argsJson)
                }
                submissionStarted = true
                val result = wdkCore!!.callMethod(
                    methodName = if (asset.isToken) "transfer" else "sendTransaction",
                    network = networkRpc,
                    accountIndex = 0,
                    args = argsJson
                )
                val hash = extractTxHash(result)
                updateTx(txId, TxStatus.BROADCAST, hash = hash)
                update { copy(sendAddress = "", sendAmount = "") }
                showToast(UiText(R.string.broadcast_notice), success = true)
                fetchBalance()
            } catch (e: Exception) {
                if (submissionStarted) {
                    updateTx(txId, TxStatus.UNKNOWN, error = UiText(R.string.unknown_submission_notice))
                    showToast(UiText(R.string.unknown_submission_notice))
                } else {
                    updateTx(txId, TxStatus.FAILED_BEFORE_BROADCAST, error = e.toUiText(R.string.error_prepare_transfer))
                    showToast(e.toUiText(R.string.error_prepare_transfer))
                }
            } finally {
                update { copy(isLoading = false, statusText = null) }
            }
        }
    }

    private fun updateTx(id: String, status: TxStatus, hash: String? = null, error: UiText? = null) {
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
            showToast(UiText(R.string.error_not_initialized))
            return
        }
        update { copy(verifyResult = null) }

        val network = if (_state.value.signNetwork == Network.BASE) Network.BASE.wdkName else Network.BTC.wdkName
        viewModelScope.launch {
            update { copy(isLoading = true, statusText = UiText(R.string.status_signing)) }
            try {
                val result = wdkCore!!.callMethod(
                    methodName = "sign",
                    network = network,
                    accountIndex = 0,
                    args = jsonEncodeString(message)
                )
                update { copy(signResult = result.toString(), currentScreen = Screen.Sign, isLoading = false, statusText = null) }
                showToast(UiText(R.string.message_signed), success = true)
            } catch (e: Exception) {
                update { copy(isLoading = false, statusText = null) }
                showToast(e.toUiText(R.string.error_sign))
            }
        }
    }

    fun doVerify() {
        val signature = _state.value.signResult ?: return
        val message = _state.value.signMessage.trim()
        if (message.isEmpty()) return
        if (!_state.value.isWdkInitialized || wdkCore == null) {
            showToast(UiText(R.string.error_not_initialized))
            return
        }

        val network = if (_state.value.signNetwork == Network.BASE) Network.BASE.wdkName else Network.BTC.wdkName
        viewModelScope.launch {
            update { copy(isLoading = true, statusText = UiText(R.string.status_verifying)) }
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
                update { copy(verifyResult = valid, isLoading = false, statusText = null) }
                showToast(UiText(if (valid) R.string.signature_valid else R.string.signature_invalid), success = valid)
            } catch (e: Exception) {
                update { copy(verifyResult = false, isLoading = false, statusText = null) }
                showToast(e.toUiText(R.string.error_verify))
            }
        }
    }

    fun deleteWallet() {
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) { clearCredentials() }
            } catch (e: Exception) {
                showToast(e.toUiText(R.string.error_delete_wallet))
                return@launch
            }
            try { wdkCore?.dispose() } catch (_: Exception) {}
            val old = wdkCore
            wdkCore = null
            encryptionKey = ""
            encryptedSeed = ""
            update {
                WalletUiState(currentScreen = Screen.Chats, hasEnteredApp = true)
            }
            showToast(UiText(R.string.wallet_deleted))
            withContext(ioDispatcher) {
                try { old?.close() } catch (_: Exception) {}
            }
        }
    }

    fun resetImport() {
        update { copy(importWords = List(12) { "" }, importError = false) }
    }

    fun resetSign() {
        update { copy(
            signMessage = "",
            signResult = null,
            verifyResult = null
        ) }
    }

    fun resetSend() {
        update { copy(sendAddress = "", sendAmount = "", sendAsset = Asset.USDC) }
    }

    val formattedUsdtBalance: String
        get() = _state.value.usdtBalance?.let { formatBalance(it, 6, "USDT") } ?: "— USDT"

    val formattedUsdcBalance: String
        get() = _state.value.usdcBalance?.let { formatBalance(it, 6, "USDC") } ?: "— USDC"

    val formattedEthBalance: String
        get() = formatBalance(_state.value.ethBalance, 18, "ETH")

    val formattedBtcBalance: String
        get() = formatBalance(_state.value.btcBalance, 8, "BTC")

    private fun formatBalance(raw: String?, decimals: Int, symbol: String): String = try {
        "${BigDecimal(requireNotNull(raw)).movePointLeft(decimals).stripTrailingZeros().toPlainString()} $symbol"
    } catch (_: IllegalArgumentException) {
        "— $symbol"
    }

    val receiveQrContent: String?
        get() = receiveRequest(_state.value.receiveAsset, receiveAddress)

    val receiveAddress: String
        get() = if (!_state.value.receiveAsset.isAvailable) "" else if (_state.value.receiveAsset.network == Network.BASE) _state.value.ethAddress else _state.value.btcAddress

    val receiveLabel: UiText
        get() = when (_state.value.receiveAsset) {
            Asset.USDC -> UiText(R.string.receive_usdc)
            Asset.USDT -> UiText(R.string.receive_usdt)
            Asset.ETH -> UiText(R.string.receive_eth)
            Asset.BTC -> UiText(R.string.receive_btc)
        }

    val sendFee: UiText
        get() {
            val s = _state.value
            if (s.sendAsset.network == Network.BASE) return UiText(R.string.base_fee_hint)
            return UiText(R.string.btc_fee_hint)
        }

    val sendNetworkLabel: String
        get() = _state.value.sendAsset.network.label

    val sendTokenLabel: String
        get() = _state.value.sendAsset.name

    val maxSendAmount: String
        get() {
            val s = _state.value
            if (!s.sendAsset.isToken || !s.sendAsset.isAvailable) return "0"
            return s.tokenBalances[s.sendAsset]?.let { BigDecimal(it).movePointLeft(s.sendAsset.decimals).stripTrailingZeros().toPlainString() } ?: "0"
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
