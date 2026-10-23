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
import org.astianx.midori.one.BuildConfig
import org.astianx.midori.one.wallet.fiat.*
import org.astianx.midori.one.wallet.p2p.*

enum class TxStatus { PENDING, BROADCAST, INCLUDED, FAILED_ON_CHAIN, UNKNOWN, FAILED_BEFORE_BROADCAST }

private const val USER_OPERATION_POLL_MS = 3_000L
private const val USER_OPERATION_POLLS = 40

private data class SendQuote(val fee: String, val feeAsset: Asset)

internal fun isValidVerificationResult(result: Any): Boolean = result is Boolean && result

internal fun toSmallestUnit(amount: String, decimals: Int): String? = try {
    require(decimals in 0..18 && amount.matches(Regex("[0-9]{1,78}(\\.[0-9]{0,$decimals})?")))
    val units = BigDecimal(amount).movePointRight(decimals).toBigIntegerExact()
    units.takeIf { it > java.math.BigInteger.ZERO && it.bitLength() <= 256 }?.toString()
} catch (_: Exception) {
    null
}

/** WDK bridges can return a JSON object or its serialized representation. */
internal fun quotedFee(result: Any): String? = try {
    val fee = when (result) {
        is JSONObject -> result.opt("fee")?.toString()
        is String -> if (result.trim().startsWith("{")) JSONObject(result).opt("fee")?.toString() else result
        else -> null
    }
    fee?.takeIf { it.matches(Regex("[0-9]{1,78}")) && it.toBigInteger().bitLength() <= 256 }
} catch (_: Exception) { null }

data class PendingTx(
    val id: String = UUID.randomUUID().toString(),
    val network: Network,
    val asset: Asset,
    val toAddress: String,
    val amount: String,
    val status: TxStatus = TxStatus.PENDING,
    val hash: String? = null,
    val error: UiText? = null,
    /** ERC-4337 operation hash; it only becomes an explorer transaction hash once a bundle includes it. */
    val userOperation: String? = null
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
            TxStatus.INCLUDED -> UiText(R.string.tx_included)
            TxStatus.FAILED_ON_CHAIN -> UiText(R.string.tx_failed_onchain)
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
    val country: String = "",
    val pendingCountry: String = "",
    val tokenBalances: Map<Asset, String> = emptyMap(),
    val isWdkInitialized: Boolean = false,
    val sendAsset: Asset = Asset.USDC,
    val sendAddress: String = "",
    val sendAmount: String = "",
    val p2pVerificationRequested: Boolean = false,
    val p2pVerificationCountry: String = "",
    val sendReview: SendReview? = null,
    val pendingTransactions: List<PendingTx> = emptyList(),
    val receiveAsset: Asset = Asset.USDC,
    val receiveReturnScreen: Screen = Screen.Home,
    val fiatReturnScreen: Screen = Screen.Home,
    val fiatOperation: FiatOperation = FiatOperation.BUY,
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
    private val appEntryStore: AppEntryStore = MemoryAppEntryStore(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val countryStore: WalletCountryStore = MemoryWalletCountryStore()
) : ViewModel() {
    constructor(context: Context) : this(KeystoreWalletStore(context), { WdkWalletClient(context) },
        appEntryStore = AppEntryPreferences(context), countryStore = WalletCountryPreferences(context))

    private val _state = MutableStateFlow(WalletUiState(
        currentScreen = if (appEntryStore.hasEntered()) Screen.Chats else Screen.Welcome,
        hasEnteredApp = appEntryStore.hasEntered()
    ))
    val state: StateFlow<WalletUiState> = _state.asStateFlow()

    private var wdkCore: WalletClient? = null
    private var encryptionKey = ""
    private var encryptedSeed = ""
    private var isRefreshingBalance = false
    private var sendRevision = 0L

    init {
        if (walletStore.hasCredentials()) restoreWallet()
    }

    private suspend fun initializeWallet(key: String, seed: String): String {
        val wdk = getOrCreateClient()
        wdk.initializeWDK(encryptionKey = key, encryptedSeed = seed, config = BaseMainnet.wdkConfig)
        val ethAddress = wdk.getAddress(network = Network.BASE.wdkName)
        walletRequire(ethAddress.isNotBlank(), R.string.address_unavailable)
        return ethAddress
    }

    fun restoreWallet() {
        if (_state.value.isLoading) return
        viewModelScope.launch {
            update { copy(isLoading = true, statusText = UiText(R.string.status_restoring), restoreError = null) }
            try {
                val (key, seed) = withContext(ioDispatcher) { walletStore.load() }
                    ?: throw WalletException(UiText(R.string.error_local_backup))
                val eth = initializeWallet(key, seed)
                encryptionKey = key
                encryptedSeed = seed
                update { copy(isWdkInitialized = true, ethAddress = eth,
                    country = countryStore.country(eth).orEmpty(),
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
        if (screen != Screen.Send) dismissSendReview()
        val destination = when {
            screen in setOf(Screen.Home, Screen.Send, Screen.Receive, Screen.Funding, Screen.Sign, Screen.Fiat, Screen.P2p) &&
                !_state.value.isWdkInitialized && walletStore.hasCredentials() -> Screen.RestoreError
            screen in setOf(Screen.Send, Screen.Receive, Screen.Funding, Screen.Sign, Screen.Fiat, Screen.P2p) && !_state.value.isWdkInitialized -> Screen.Home
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
        if (feature == AppFeature.BUY) { openFunding(FiatOperation.BUY); return }
        update { copy(feature = feature, detailReturnScreen = currentScreen, currentScreen = Screen.Feature) }
    }

    fun openFiat(operation: FiatOperation) {
        update { copy(fiatOperation = operation, fiatReturnScreen = if (currentScreen == Screen.Funding) Screen.Funding else Screen.Home) }
        navigate(Screen.Fiat)
    }

    fun openFunding(operation: FiatOperation) {
        update { copy(fiatOperation = operation) }
        navigate(Screen.Funding)
    }

    fun openReceive(asset: Asset, returnTo: Screen = Screen.Home) {
        require(returnTo == Screen.Home || returnTo == Screen.Funding)
        update { copy(receiveAsset = asset, receiveReturnScreen = returnTo) }
        navigate(Screen.Receive)
    }

    internal fun openP2pVerification(country: String = "") {
        if (!state.value.isWdkInitialized) return
        update { copy(p2pVerificationRequested = true, p2pVerificationCountry = country) }
        navigate(Screen.P2p)
    }
    internal fun finishP2pVerification() { update { copy(p2pVerificationRequested = false) } }

    /** WDK remains the sole signer. Journal callback is durable before this adapter broadcasts. */
    internal fun p2pWalletPort(): P2pWalletPort = object : P2pWalletPort {
        override fun isCurrent(owner: String) = state.value.isWdkInitialized && wdkCore != null &&
            state.value.ethAddress.equals(owner, true)
        private suspend fun estimate(owner: String, to: String, data: String, requiredUsdc: String): String {
            p2pRequire(isCurrent(owner), "wallet_changed")
            p2pRequire(to.equals(P2P_DIAMOND, true) || to.equals(BaseMainnet.USDC_CONTRACT, true) ||
                to.equals(org.astianx.midori.one.wallet.p2p.P2P_REPUTATION_MANAGER, true))
            p2pRequire(data.matches(Regex("0x[a-fA-F0-9]{8,20000}")) && data.length % 2 == 0 && p2pInteger(requiredUsdc))
            val client = checkNotNull(wdkCore)
            val args = JSONObject().put("to", to).put("value", "0").put("data", data).toString()
            val execution = quotedFee(client.callMethod("quoteSendTransaction", Network.BASE.wdkName, 0, args))
                ?.toBigIntegerOrNull() ?: throw P2pOrderFailure("gas_unavailable")
            p2pRequire(execution.signum() > 0, "gas_unavailable")
            val fee = quoteBaseTotalFee(BaseTransaction(owner, to, java.math.BigInteger.ZERO, data), execution, baseFeeProvider)
            val balance = client.getBalance(Network.BASE.wdkName).toBigIntegerOrNull()
            p2pRequire(balance != null && balance >= fee, "insufficient_gas")
            if (requiredUsdc != "0") {
                val usdc = client.callMethod("getTokenBalance", Network.BASE.wdkName, 0,
                    JSONObject.quote(BaseMainnet.USDC_CONTRACT)).toString().toBigIntegerOrNull()
                p2pRequire(usdc != null && usdc >= requiredUsdc.toBigInteger(), "insufficient_usdc")
            }
            p2pRequire(isCurrent(owner) && client === wdkCore, "wallet_changed")
            return fee.toString()
        }
        override suspend fun quote(owner: String, to: String, data: String, requiredUsdc: String) =
            withContext(Dispatchers.Main.immediate) { estimate(owner, to, data, requiredUsdc) }
        override suspend fun submit(owner: String, to: String, data: String, maxGas: String, requiredUsdc: String,
            beforeBroadcast: suspend () -> Unit): String = withContext(Dispatchers.Main.immediate) {
            p2pRequire(!state.value.isLoading, "wallet_busy")
            update { copy(isLoading = true) }
            try {
                val client = checkNotNull(wdkCore)
                val fee = estimate(owner, to, data, requiredUsdc)
                p2pRequire(p2pInteger(maxGas, true) && fee.toBigInteger() <= maxGas.toBigInteger(), "quote_changed")
                beforeBroadcast()
                p2pRequire(isCurrent(owner) && client === wdkCore, "wallet_changed")
                val args = JSONObject().put("to", to).put("value", "0").put("data", data).toString()
                val hash = extractTxHash(client.callMethod("sendTransaction", Network.BASE.wdkName, 0, args))
                p2pRequire(isCurrent(owner) && client === wdkCore, "wallet_changed")
                fetchBalance()
                if (hash.startsWith("0x")) hash else "0x$hash"
            } finally { update { copy(isLoading = false) } }
        }
    }

    internal suspend fun signP2pVerification(record: org.astianx.midori.one.wallet.p2p.P2pKycRecord,
        expiresAt: Long, origin: String): String {
        check(origin == fiatBackendOrigin(BuildConfig.FIAT_BACKEND_URL))
        val client = checkNotNull(wdkCore)
        fun current() = check(state.value.isWdkInitialized && client === wdkCore && state.value.ethAddress.equals(record.owner, true))
        current()
        val message = org.astianx.midori.one.wallet.p2p.kycMessage(origin, record, expiresAt)
        val signature = client.callMethod("sign", Network.BASE.wdkName, 0, JSONObject.quote(message)).toString()
        current(); check(signature.matches(Regex("0x[a-fA-F0-9]{130}"))); return signature
    }

    internal suspend fun signFiatChallenge(request: FiatRequest, challenge: FiatChallenge, origin: String): String {
        check(origin == fiatBackendOrigin(BuildConfig.FIAT_BACKEND_URL))
        val client = checkNotNull(wdkCore)
        fun checkAccount() {
            val current = state.value
            check(current.isWdkInitialized && client === wdkCore &&
                current.ethAddress.equals(request.identityAddress, ignoreCase = true) &&
                request.address == current.ethAddress)
        }
        checkAccount()
        val message = fiatChallengeMessage(origin, challenge, request)
        val signature = client.callMethod("sign", Network.BASE.wdkName, 0, JSONObject.quote(message)).toString()
        checkAccount()
        check(signature.matches(Regex("0x[a-fA-F0-9]{130}")))
        return signature
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

    fun selectCountry(code: String) {
        if (walletCountryValid(code) && !_state.value.isLoading && _state.value.country.isBlank()) update { copy(pendingCountry = code) }
    }

    fun confirmCountry() {
        val current = _state.value
        if (!current.isWdkInitialized || current.country.isNotBlank() || !walletCountryValid(current.pendingCountry)) return
        if (countryStore.bind(current.ethAddress, current.pendingCountry)) {
            update { copy(country = countryStore.country(ethAddress).orEmpty()) }
        } else showToast(UiText(R.string.error_country))
    }

    private fun bindWalletCountry(address: String): String =
        countryStore.country(address) ?: _state.value.pendingCountry.takeIf { countryStore.bind(address, it) }.orEmpty()

    fun confirmSeedAndInitialize() {
        if (_state.value.isLoading) return
        if (!walletCountryValid(_state.value.pendingCountry)) {
            showToast(UiText(R.string.country_required))
            return
        }
        viewModelScope.launch {
            update { copy(isLoading = true, statusText = UiText(R.string.status_initializing)) }
            try {
                val eth = initializeWallet(encryptionKey, encryptedSeed)
                saveCredentials(encryptionKey, encryptedSeed)
                val boundCountry = bindWalletCountry(eth)
                update { copy(isWdkInitialized = true, ethAddress = eth, country = boundCountry,
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
        if (!walletCountryValid(_state.value.pendingCountry)) {
            showToast(UiText(R.string.country_required))
            return
        }
        update { copy(importError = false) }

        val mnemonic = words.joinToString(" ")
        viewModelScope.launch {
            update { copy(isLoading = true, statusText = UiText(R.string.status_importing)) }
            try {
                val wdk = getOrCreateClient()
                val result = wdk.getSeedAndEntropyFromMnemonic(mnemonic = mnemonic)
                val eth = initializeWallet(result.encryptionKey, result.encryptedSeedBuffer)
                saveCredentials(result.encryptionKey, result.encryptedSeedBuffer)
                encryptionKey = result.encryptionKey
                encryptedSeed = result.encryptedSeedBuffer
                val boundCountry = bindWalletCountry(eth)
                update { copy(isWdkInitialized = true, ethAddress = eth, country = boundCountry,
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
                val tokenSnapshot = mutableMapOf<Asset, String>()
                Asset.entries.filter { it.isToken && it.isAvailable }.forEach { asset ->
                    try {
                        val tokenBalance = wdkCore!!.callMethod(
                            methodName = "getTokenBalance", network = asset.network.wdkName, accountIndex = 0,
                            args = JSONObject.quote(requireNotNull(asset.contract))
                        ).toString()
                        require(tokenBalance.matches(Regex("[0-9]+"))) { "Invalid token balance" }
                        tokenSnapshot[asset] = tokenBalance
                    } catch (e: Exception) {
                        // An unavailable holding cannot silently count as zero in the account total.
                    }
                }
                update { copy(tokenBalances = tokenSnapshot.toMap()) }
            } finally {
                isRefreshingBalance = false
                update { copy(refreshingBalances = false) }
            }
        }
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
        dismissSendReview()
        update { copy(sendAsset = asset, sendAmount = "", sendAddress = "") }
    }

    fun updateSendAddress(address: String) {
        dismissSendReview()
        update { copy(sendAddress = address) }
    }

    fun updateSendAmount(amount: String) {
        dismissSendReview()
        update { copy(sendAmount = amount) }
    }

    fun updateReceiveAsset(asset: Asset) {
        update { copy(receiveAsset = asset) }
    }

    fun updateSignMessage(message: String) {
        update { copy(signMessage = message, signResult = null, verifyResult = null) }
    }

    fun setSendMax() {
        if (_state.value.sendAsset.isAvailable) {
            dismissSendReview()
            update { copy(sendAmount = maxSendAmount) }
        }
    }

    private fun validateSend(s: WalletUiState): String? {
        val address = s.sendAddress.trim()
        val amount = s.sendAmount.trim()
        if (address.isEmpty() || amount.isEmpty()) {
            showToast(UiText(R.string.error_send_fields))
            return null
        }
        if (!s.isWdkInitialized || wdkCore == null) {
            showToast(UiText(R.string.error_not_initialized))
            return null
        }

        val asset = s.sendAsset
        if (!asset.isAvailable) {
            showToast(asset.unavailableReason ?: UiText(R.string.error_asset_unavailable))
            return null
        }
        val valueInSmallestUnit = toSmallestUnit(amount, asset.decimals) ?: run {
            showToast(UiText(R.string.error_amount))
            return null
        }
        if (address.startsWith("@")) {
            showToast(UiText(R.string.dollars_tag_pending))
            return null
        }
        if (!address.matches(Regex("0x[a-fA-F0-9]{40}"))) {
            showToast(UiText(R.string.error_base_address))
            return null
        }
        if (address.equals("0x0000000000000000000000000000000000000000", ignoreCase = true)) {
            showToast(UiText(R.string.error_zero_address))
            return null
        }
        val balance = s.tokenBalances[asset]?.toBigIntegerOrNull()
        if (balance == null) {
            showToast(UiText(R.string.error_balance_unavailable, listOf(asset.name)))
            return null
        }
        if (valueInSmallestUnit.toBigInteger() > balance) {
            val conversion = DigitalDollarAccount.funding(
                valueInSmallestUnit.toBigInteger(), asset, s.tokenBalances) == DollarFunding.CONVERSION_REQUIRED
            showToast(if (conversion) UiText(R.string.dollars_conversion_required)
                else UiText(R.string.error_balance_insufficient, listOf(asset.name)))
            return null
        }
        return valueInSmallestUnit
    }

    /** Stablecoin transfers go through the paymaster account; the fee is paid in USDC or USDT. */
    private suspend fun quoteSend(s: WalletUiState, units: String, feeAsset: Asset? = null): SendQuote {
        val asset = s.sendAsset
        val client = requireNotNull(wdkCore)
        val amount = units.toBigInteger()
        verifyGaslessAccount(client, s.ethAddress)
        val balances = freshStableBalances(client)
        val balance = balances[asset]
        if (balance == null || balance < amount) throw WalletException(
            UiText(R.string.error_balance_insufficient, listOf(asset.name)))
        val quote = quoteGaslessTransfer(client, asset, s.sendAddress.trim(), amount, balances, feeAsset)
        return SendQuote(quote.fee.toString(), quote.feeAsset)
    }

    fun dismissSendReview() {
        sendRevision++
        update { copy(sendReview = null) }
    }

    fun prepareSend() {
        val s = _state.value
        if (s.isLoading) return
        dismissSendReview()
        val units = validateSend(s) ?: return
        val revision = sendRevision
        update { copy(isLoading = true) }
        viewModelScope.launch {
            try {
                val quote = quoteSend(s, units)
                if (sendRevision == revision) update { copy(sendReview = SendReview(
                    s.sendAsset, s.sendAddress.trim(), s.sendAmount.trim(), units, quote.fee, clock() + 60_000,
                    quote.feeAsset)) }
            } catch (e: GaslessAmountAdjustment) {
                if (sendRevision == revision) {
                    update { copy(sendAmount = BigDecimal(e.maxAmount).movePointLeft(s.sendAsset.decimals)
                        .stripTrailingZeros().toPlainString()) }
                    showToast(UiText(R.string.send_amount_adjusted))
                }
            } catch (e: Exception) {
                showToast(e.toUiText(R.string.error_prepare_transfer))
            } finally { update { copy(isLoading = false) } }
        }
    }

    fun doSend() {
        val s = _state.value
        if (s.isLoading) return
        val review = s.sendReview
        if (review == null || !review.matches(s.sendAsset, s.sendAddress, s.sendAmount, clock())) {
            dismissSendReview()
            showToast(UiText(R.string.send_review_expired))
            return
        }
        val valueInSmallestUnit = validateSend(s) ?: run { dismissSendReview(); return }
        val asset = s.sendAsset
        val address = review.address
        val amount = review.amount
        val revision = sendRevision

        val tx = PendingTx(network = asset.network, asset = asset, toAddress = address, amount = amount)
        val txId = tx.id

        update { copy(pendingTransactions = pendingTransactions + tx,
            isLoading = true, statusText = UiText(R.string.status_submitting)) }

        viewModelScope.launch {
            var submissionStarted = false
            try {
                val quote = quoteSend(s, valueInSmallestUnit, review.feeAsset)
                walletRequire(sendRevision == revision && review.matches(s.sendAsset, s.sendAddress, s.sendAmount, clock()),
                    R.string.send_review_expired)
                walletRequire(quote.fee.toBigInteger() <= review.estimatedFee.toBigInteger(), R.string.send_fee_changed)
                dismissSendReview()
                submissionStarted = true
                val result = wdkCore!!.callMethod(
                    methodName = "transfer",
                    network = BaseMainnet.GASLESS_NETWORK,
                    accountIndex = 0,
                    args = gaslessArgs(asset, address, valueInSmallestUnit, quote.feeAsset)
                )
                val hash = extractTxHash(result)
                updateTx(txId, TxStatus.BROADCAST, userOperation = hash)
                trackUserOperation(txId, hash)
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
                update { copy(isLoading = false, statusText = null, sendReview = null) }
            }
        }
    }

    private fun updateTx(
        id: String, status: TxStatus, hash: String? = null, error: UiText? = null, userOperation: String? = null
    ) {
        val txList = _state.value.pendingTransactions.toMutableList()
        val index = txList.indexOfFirst { it.id == id }
        if (index >= 0) {
            txList[index] = txList[index].copy(status = status, hash = hash, error = error,
                userOperation = userOperation ?: txList[index].userOperation)
            update { copy(pendingTransactions = txList) }
        }
    }

    /** The bundler accepts a UserOperation first; only its receipt proves inclusion and yields the explorer hash. */
    private fun trackUserOperation(txId: String, userOperation: String) {
        viewModelScope.launch {
            repeat(USER_OPERATION_POLLS) {
                delay(USER_OPERATION_POLL_MS)
                val client = wdkCore ?: return@launch
                val outcome = try {
                    parseUserOperationReceipt(client.callMethod("getUserOperationReceipt",
                        BaseMainnet.GASLESS_NETWORK, 0, JSONObject.quote(userOperation)))
                } catch (_: Exception) { null }
                if (outcome != null) {
                    updateTx(txId, if (outcome.success) TxStatus.INCLUDED else TxStatus.FAILED_ON_CHAIN,
                        hash = outcome.transactionHash)
                    fetchBalance()
                    return@launch
                }
            }
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

        val network = Network.BASE.wdkName
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

        val network = Network.BASE.wdkName
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
        if (state.value.isLoading) return
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
        dismissSendReview()
        update { copy(sendAddress = "", sendAmount = "", sendAsset = Asset.USDC) }
    }

    val formattedDollarBalance: String
        get() = DigitalDollarAccount.displayBalance(_state.value.tokenBalances) ?: "—"

    val formattedUsdtBalance: String
        get() = _state.value.usdtBalance?.let { formatBalance(it, 6, "USDT") } ?: "— USDT"

    val formattedUsdcBalance: String
        get() = _state.value.usdcBalance?.let { formatBalance(it, 6, "USDC") } ?: "— USDC"

    private fun formatBalance(raw: String?, decimals: Int, symbol: String): String = try {
        "${BigDecimal(requireNotNull(raw)).movePointLeft(decimals).stripTrailingZeros().toPlainString()} $symbol"
    } catch (_: IllegalArgumentException) {
        "— $symbol"
    }

    val receiveQrContent: String?
        get() = receiveRequest(_state.value.receiveAsset, receiveAddress)

    val receiveAddress: String
        get() = if (_state.value.receiveAsset.isAvailable) _state.value.ethAddress else ""

    val receiveLabel: UiText
        get() = when (_state.value.receiveAsset) {
            Asset.USDC -> UiText(R.string.receive_usdc)
            Asset.USDT -> UiText(R.string.receive_usdt)
        }

    val sendFee: UiText
        get() = UiText(R.string.gasless_fee_hint)

    val sendNetworkLabel: String
        get() = _state.value.sendAsset.network.label

    val sendTokenLabel: String
        get() = _state.value.sendAsset.name

    val maxSendAmount: String
        get() {
            val s = _state.value
            if (!s.sendAsset.isAvailable) return "0"
            return s.tokenBalances[s.sendAsset]?.let { BigDecimal(it).movePointLeft(s.sendAsset.decimals).stripTrailingZeros().toPlainString() } ?: "0"
        }

    override fun onCleared() {
        super.onCleared()
        val client = wdkCore
        wdkCore = null
        // Native worklet teardown can wait on IPC threads. Activity destruction must
        // not block the UI, and viewModelScope is already cancelled at this point.
        if (client != null) Thread({
            try { client.close() } catch (_: Exception) { /* No credentials are changed by teardown. */ }
        }, "midori-wallet-close").start()
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return WalletViewModel(context.applicationContext) as T
        }
    }
}
