package org.astianx.midori.one.wallet

import androidx.lifecycle.ViewModelStore
import java.math.BigInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.astianx.midori.one.AppFeature
import org.astianx.midori.one.MemoryAppEntryStore
import org.astianx.midori.one.R
import org.astianx.midori.one.Screen
import org.astianx.midori.one.UiText
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import to.tether.wdk.core.EntropyResult
import to.tether.wdk.core.SeedAndEntropyResult

@OptIn(ExperimentalCoroutinesApi::class)
class WalletRecoveryTest {
    private val dispatcher = StandardTestDispatcher()
    private val viewModels = ViewModelStore()

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() {
        viewModels.clear()
        Dispatchers.resetMain()
    }

    private class Store(var credentials: WalletCredentials? = null) : WalletStore {
        var clears = 0
        var saves = 0
        var failLoad = false
        var failClear = false
        override fun hasCredentials() = credentials != null
        override fun load(): WalletCredentials? {
            check(!failLoad) { "Local key unavailable" }
            return credentials
        }
        override fun save(key: String, seed: String): Boolean {
            saves++
            credentials = WalletCredentials(key, seed)
            return true
        }
        override fun clear() {
            check(!failClear) { "Storage unavailable" }
            clears++
            credentials = null
        }
    }

    private class Client : WalletClient {
        var failInitialization = false
        var failImport = false
        var failBalance = false
        var closed = false
        var initializedConfig = ""
        var nativeBalance = "0"
        var tokenBalance = "0"
        var usdtBalance = "0"
        val sentTokens = mutableListOf<String>()
        var broadcasts = 0
        val networks = mutableListOf<String>()
        override suspend fun initializeWDK(encryptionKey: String, encryptedSeed: String, config: String) {
            check(!failInitialization) { "Simulated provider failure" }
            initializedConfig = config
        }
        override suspend fun getAddress(network: String): String {
            networks += network
            return if (network == "base") "0x" + "1".repeat(40) else "bc1q" + "q".repeat(38)
        }
        override suspend fun getBalance(network: String): String {
            check(!failBalance) { "Simulated balance outage" }
            return nativeBalance
        }
        override suspend fun getSeedAndEntropyFromMnemonic(mnemonic: String): SeedAndEntropyResult {
            check(!failImport) { "Invalid mnemonic" }
            return SeedAndEntropyResult("test-key", "test-encrypted-seed", "test-entropy")
        }
        override suspend fun callMethod(methodName: String, network: String, accountIndex: Int, args: String): Any {
            check(!failBalance) { "Simulated balance outage" }
            if (methodName == "quoteSendTransaction") {
                assertEquals("bitcoin", network)
                return "100"
            }
            assertEquals("base", network)
            return when (methodName) {
                "getTokenBalance" -> {
                    when (org.json.JSONTokener(args).nextValue()) {
                        BaseMainnet.USDC_CONTRACT -> tokenBalance
                        BaseMainnet.USDT_CONTRACT -> usdtBalance
                        else -> error("Wrong token contract")
                    }
                }
                "quoteTransfer" -> "100"
                "transfer" -> { sentTokens += org.json.JSONObject(args).getString("token"); broadcasts++; "0x" + "a".repeat(64) }
                else -> error("Unexpected method: $methodName")
            }
        }
        override suspend fun generateEntropyAndEncrypt(wordCount: Int): EntropyResult = error("Unused")
        override suspend fun getMnemonicFromEntropy(encryptedEntropy: String, encryptionKey: String): String = error("Unused")
        override suspend fun dispose() = Unit
        override fun close() { closed = true }
    }

    private fun viewModel(store: Store, factory: () -> Client) =
        WalletViewModel(store, factory, dispatcher).also { viewModels.put("wallet", it) }

    @Test
    fun transientRestoreFailureKeepsCredentialsAndRetryUsesANewClient() = runTest(dispatcher) {
        val original = WalletCredentials("saved-test-key", "saved-test-seed")
        val store = Store(original)
        val failed = Client().apply { failInitialization = true }
        val recovered = Client()
        var clients = 0
        val vm = viewModel(store) { if (clients++ == 0) failed else recovered }
        advanceUntilIdle()
        assertEquals(Screen.RestoreError, vm.state.value.currentScreen)
        // SDK prose is not cached in the UI; this resolves in the current locale.
        assertEquals(UiText(R.string.error_restore), vm.state.value.restoreError)
        assertEquals(original, store.credentials)
        assertEquals(0, store.clears)
        assertTrue(failed.closed)
        vm.restoreWallet()
        advanceUntilIdle()
        assertEquals(Screen.Chats, vm.state.value.currentScreen)
        assertTrue(vm.state.value.isWdkInitialized)
        assertEquals(original, store.credentials)
        assertEquals(0, store.saves)
        assertEquals(listOf("base", "bitcoin"), recovered.networks)
        assertEquals(BaseMainnet.wdkConfig, recovered.initializedConfig)
    }

    @Test
    fun invalidImportStaysOnImportAndOnlySuccessfulRetrySaves() = runTest(dispatcher) {
        val store = Store()
        var fail = true
        val vm = viewModel(store) { Client().apply { failImport = fail } }
        vm.navigate(Screen.Import)
        vm.selectCountry("CO")
        // Artificial words are consumed by a fake, never by a real wallet.
        vm.pasteImportWords(List(12) { "invalid" }.joinToString(" "))
        vm.doImport()
        advanceUntilIdle()
        assertEquals(Screen.Import, vm.state.value.currentScreen)
        assertNull(store.credentials)
        assertEquals(0, store.saves)
        fail = false
        vm.doImport()
        advanceUntilIdle()
        assertEquals(Screen.Chats, vm.state.value.currentScreen)
        assertEquals(1, store.saves)
        assertTrue(vm.state.value.importWords.all { it.isEmpty() })
    }

    @Test
    fun importRequiresACountryAndBindsItToTheWallet() = runTest(dispatcher) {
        val store = Store()
        val vm = viewModel(store) { Client() }
        vm.navigate(Screen.Import)
        vm.pasteImportWords(List(12) { "word" }.joinToString(" "))
        vm.doImport()
        assertEquals(UiText(R.string.country_required), vm.state.value.toastMessage)
        advanceUntilIdle()
        assertEquals(Screen.Import, vm.state.value.currentScreen)
        assertNull(store.credentials)
        vm.selectCountry("not-a-country")
        assertEquals("", vm.state.value.pendingCountry)
        vm.selectCountry("BR")
        vm.doImport()
        advanceUntilIdle()
        assertEquals(Screen.Chats, vm.state.value.currentScreen)
        assertEquals("BR", vm.state.value.country)
        assertNotNull(store.credentials)
    }

    @Test
    fun boundCountrySurvivesRestartAndCannotBeChanged() = runTest(dispatcher) {
        val store = Store()
        val countries = MemoryWalletCountryStore()
        val first = WalletViewModel(store, { Client() }, dispatcher, countryStore = countries).also { viewModels.put("first", it) }
        first.selectCountry("CO")
        first.pasteImportWords(List(12) { "word" }.joinToString(" "))
        first.doImport()
        advanceUntilIdle()
        assertEquals("CO", first.state.value.country)
        first.selectCountry("BR")
        first.confirmCountry()
        assertEquals("CO", first.state.value.country)
        val restarted = WalletViewModel(store, { Client() }, dispatcher, countryStore = countries).also { viewModels.put("restarted", it) }
        advanceUntilIdle()
        assertEquals("CO", restarted.state.value.country)
        restarted.selectCountry("BR")
        restarted.confirmCountry()
        assertEquals("CO", restarted.state.value.country)
        assertFalse(countries.bind(restarted.state.value.ethAddress, "BR"))
    }

    @Test
    fun existingWalletWithoutCountryCanBindItExactlyOnce() = runTest(dispatcher) {
        val store = Store(WalletCredentials("test-key", "test-seed"))
        val vm = viewModel(store) { Client() }
        advanceUntilIdle()
        assertTrue(vm.state.value.isWdkInitialized)
        assertEquals("", vm.state.value.country)
        vm.confirmCountry()
        assertEquals("", vm.state.value.country)
        vm.selectCountry("PE")
        vm.confirmCountry()
        assertEquals("PE", vm.state.value.country)
        vm.selectCountry("BR")
        vm.confirmCountry()
        assertEquals("PE", vm.state.value.country)
    }

    @Test
    fun balanceOutageRetainsWalletAndDoesNotDisplayZeroOrStaleBalances() = runTest(dispatcher) {
        val store = Store(WalletCredentials("test-key", "test-seed"))
        val client = Client()
        val vm = viewModel(store) { client }
        advanceUntilIdle()
        assertEquals("0", vm.state.value.usdcBalance)
        client.failBalance = true
        vm.fetchBalance()
        advanceUntilIdle()
        assertEquals(Screen.Chats, vm.state.value.currentScreen)
        assertNull(vm.state.value.ethBalance)
        assertNull(vm.state.value.usdcBalance)
        assertNull(vm.state.value.btcBalance)
        assertNotNull(store.credentials)
        assertEquals(0, store.clears)
    }

    @Test
    fun failedRecoveryPhraseImportAlsoPreservesAnExistingWallet() = runTest(dispatcher) {
        val original = WalletCredentials("saved-test-key", "saved-test-seed")
        val store = Store(original)
        val vm = viewModel(store) { Client().apply { failInitialization = true; failImport = true } }
        advanceUntilIdle()
        vm.navigate(Screen.Import)
        vm.selectCountry("CO")
        vm.pasteImportWords(List(12) { "invalid" }.joinToString(" "))
        vm.doImport()
        advanceUntilIdle()
        assertEquals(Screen.Import, vm.state.value.currentScreen)
        assertEquals(original, store.credentials)
        assertEquals(0, store.saves)
        assertEquals(0, store.clears)
    }

    private fun sendViewModel(client: Client, failL1: () -> Boolean = { false }): WalletViewModel {
        val fees = object : BaseFeeProvider {
            override suspend fun chainId() = BigInteger.valueOf(8453)
            override suspend fun estimateGas(transaction: BaseTransaction) = BigInteger.valueOf(65000)
            override suspend fun l1FeeUpperBound(unsignedSize: Int): BigInteger {
                check(!failL1()) { "L1 provider failed" }
                return BigInteger.valueOf(200)
            }
            override suspend fun operatorFee(gas: BigInteger) = BigInteger.valueOf(3)
        }
        return WalletViewModel(Store(WalletCredentials("test-key", "test-seed")), { client }, dispatcher, fees)
            .also { viewModels.put("wallet", it) }
    }

    @Test
    fun enoughEthForL2AloneDoesNotAuthorizeATokenBroadcast() = runTest(dispatcher) {
        val client = Client().apply { nativeBalance = "1000000"; tokenBalance = "1000000" }
        val vm = sendViewModel(client)
        advanceUntilIdle()
        vm.updateSendAddress("0x" + "2".repeat(40))
        vm.updateSendAmount("1")
        vm.prepareSend()
        advanceUntilIdle()
        assertNotNull(vm.state.value.sendReview)
        // Funds disappear after review: the fresh check must still block submission.
        client.nativeBalance = "150"
        vm.doSend()
        advanceUntilIdle()
        assertEquals(0, client.broadcasts)
        assertEquals(TxStatus.FAILED_BEFORE_BROADCAST, vm.state.value.pendingTransactions.single().status)
        assertEquals(UiText(R.string.error_insufficient_gas), vm.state.value.pendingTransactions.single().error)
    }

    @Test
    fun failedL1LookupStopsBroadcastEvenWithEnoughEth() = runTest(dispatcher) {
        val client = Client().apply { nativeBalance = "1000000"; tokenBalance = "1000000" }
        var failL1 = false
        val vm = sendViewModel(client) { failL1 }
        advanceUntilIdle()
        vm.updateSendAddress("0x" + "2".repeat(40))
        vm.updateSendAmount("1")
        vm.prepareSend()
        advanceUntilIdle()
        assertNotNull(vm.state.value.sendReview)
        failL1 = true
        vm.doSend()
        advanceUntilIdle()
        assertEquals(0, client.broadcasts)
        assertEquals(TxStatus.FAILED_BEFORE_BROADCAST, vm.state.value.pendingTransactions.single().status)
        assertEquals(UiText(R.string.error_prepare_transfer), vm.state.value.pendingTransactions.single().error)
    }
    @Test
    fun localKeyFailureDoesNotCrashOrEraseCredentialsAndCanRetry() = runTest(dispatcher) {
        val store = Store(WalletCredentials("test-key", "test-seed")).apply { failLoad = true }
        val vm = viewModel(store) { Client() }
        advanceUntilIdle()
        assertEquals(Screen.RestoreError, vm.state.value.currentScreen)
        assertEquals(0, store.clears)
        assertNotNull(store.credentials)
        store.failLoad = false
        vm.restoreWallet()
        advanceUntilIdle()
        assertEquals(Screen.Chats, vm.state.value.currentScreen)
    }

    @Test
    fun usdtBalanceMaxAndTransferUseUsdtRatherThanUsdc() = runTest(dispatcher) {
        val client = Client().apply { nativeBalance = "1000000"; tokenBalance = "7000000"; usdtBalance = "1234567" }
        val vm = sendViewModel(client)
        advanceUntilIdle()
        assertEquals("7000000", vm.state.value.usdcBalance)
        assertEquals("1234567", vm.state.value.usdtBalance)
        vm.updateSendAsset(Asset.USDT)
        vm.setSendMax()
        assertEquals("1.234567", vm.state.value.sendAmount)
        vm.updateSendAddress("0x" + "2".repeat(40))
        vm.prepareSend()
        advanceUntilIdle()
        assertNotNull(vm.state.value.sendReview)
        vm.doSend()
        advanceUntilIdle()
        assertEquals(listOf(BaseMainnet.USDT_CONTRACT), client.sentTokens)
        assertEquals(Asset.USDT, vm.state.value.pendingTransactions.single().asset)
        assertEquals(TxStatus.BROADCAST, vm.state.value.pendingTransactions.single().status)
    }

    @Test
    fun usdcBalanceCannotCoverAnInsufficientUsdtBalance() = runTest(dispatcher) {
        val client = Client().apply { nativeBalance = "1000000"; tokenBalance = "7000000"; usdtBalance = "1" }
        val vm = sendViewModel(client)
        advanceUntilIdle()
        vm.updateSendAsset(Asset.USDT)
        vm.updateSendAddress("0x" + "2".repeat(40))
        vm.updateSendAmount("1")
        vm.prepareSend()
        assertEquals(UiText(R.string.dollars_conversion_required), vm.state.value.toastMessage)
        assertNull(vm.state.value.sendReview)
        advanceUntilIdle()
        assertEquals(0, client.broadcasts)
        assertTrue(vm.state.value.pendingTransactions.isEmpty())
    }

    @Test
    fun wrongBitcoinProviderStopsBeforeWdkBroadcast() = runTest(dispatcher) {
        val client = Client().apply { nativeBalance = "1000000" }
        var wrongNetwork = false
        val vm = WalletViewModel(Store(WalletCredentials("test-key", "test-seed")), { client }, dispatcher,
            bitcoinNetworkVerifier = BitcoinNetworkVerifier { check(!wrongNetwork) { "Wrong Bitcoin chain" } })
            .also { viewModels.put("wallet", it) }
        advanceUntilIdle()
        vm.updateSendAsset(Asset.BTC)
        vm.updateSendAddress("bc1q" + "q".repeat(38))
        vm.updateSendAmount("0.00001")
        vm.prepareSend()
        advanceUntilIdle()
        assertNotNull(vm.state.value.sendReview)
        wrongNetwork = true
        vm.doSend()
        advanceUntilIdle()
        assertEquals(0, client.broadcasts)
        assertEquals(TxStatus.FAILED_BEFORE_BROADCAST, vm.state.value.pendingTransactions.single().status)
    }

    @Test
    fun failedDeletionKeepsWalletUsableAndReportsALocalizableError() = runTest(dispatcher) {
        val original = WalletCredentials("test-key", "test-seed")
        val store = Store(original).apply { failClear = true }
        val client = Client()
        val vm = viewModel(store) { client }
        advanceUntilIdle()
        vm.deleteWallet()
        runCurrent()
        assertEquals(UiText(R.string.error_delete_wallet), vm.state.value.toastMessage)
        assertEquals(original, store.credentials)
        assertTrue(vm.state.value.isWdkInitialized)
        assertEquals(Screen.Chats, vm.state.value.currentScreen)
        assertFalse(client.closed)
    }

    @Test
    fun introductionCanOpenChatsWithoutCreatingWalletAndSurvivesRestart() = runTest(dispatcher) {
        val entry = MemoryAppEntryStore()
        val store = Store()
        var clients = 0
        val vm = WalletViewModel(store, { clients++; Client() }, dispatcher, appEntryStore = entry)
        viewModels.put("first-entry", vm)
        assertEquals(Screen.Welcome, vm.state.value.currentScreen)
        vm.enterApp()
        assertEquals(Screen.Chats, vm.state.value.currentScreen)
        assertTrue(entry.hasEntered())
        assertEquals(0, clients)
        assertEquals(0, store.saves)
        assertNull(store.credentials)
        val restarted = WalletViewModel(store, { clients++; Client() }, dispatcher, appEntryStore = entry)
        viewModels.put("restarted-entry", restarted)
        assertEquals(Screen.Chats, restarted.state.value.currentScreen)
        assertEquals(0, clients)
    }

    @Test
    fun mainNavigationNeverOpensFinancialActionsWithoutAnInitializedWallet() = runTest(dispatcher) {
        val empty = viewModel(Store()) { Client() }
        empty.enterApp()
        for (destination in listOf(Screen.Send, Screen.Receive, Screen.Sign)) {
            empty.navigate(destination)
            assertEquals(Screen.Home, empty.state.value.currentScreen)
        }
        val saved = Store(WalletCredentials("key", "seed"))
        val failed = viewModel(saved) { Client().apply { failInitialization = true } }
        advanceUntilIdle()
        failed.enterApp()
        assertEquals(Screen.Chats, failed.state.value.currentScreen)
        failed.navigate(Screen.Home)
        assertEquals(Screen.RestoreError, failed.state.value.currentScreen)
        assertNotNull(saved.credentials)
        assertEquals(0, saved.clears)
    }

    @Test
    fun nestedMessagingDetailsReturnToTheFeatureAndOriginalTab() = runTest(dispatcher) {
        val vm = viewModel(Store()) { Client() }
        vm.enterApp()
        vm.navigate(Screen.More)
        vm.openFeature(AppFeature.PROFILE)
        vm.navigate(Screen.Messaging)
        assertEquals(Screen.Feature, vm.state.value.messagingReturnScreen)
        vm.navigate(vm.state.value.messagingReturnScreen)
        assertEquals(AppFeature.PROFILE, vm.state.value.feature)
        vm.navigate(vm.state.value.detailReturnScreen)
        assertEquals(Screen.More, vm.state.value.currentScreen)
    }

}
