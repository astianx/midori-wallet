package org.astianx.midori.one.wallet

import androidx.lifecycle.ViewModelStore
import java.math.BigInteger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
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
        val feeTokens = mutableListOf<String>()
        val quotedFeeTokens = mutableListOf<String>()
        val quotedAmounts = mutableListOf<String>()
        var gaslessFee = "3000"
        var gaslessAddress = "0x" + "1".repeat(40)
        var userOperationReceipt: String? = null
        var broadcasts = 0
        val networks = mutableListOf<String>()
        override suspend fun initializeWDK(encryptionKey: String, encryptedSeed: String, config: String) {
            check(!failInitialization) { "Simulated provider failure" }
            initializedConfig = config
        }
        override suspend fun getAddress(network: String): String {
            networks += network
            return when (network) {
                "base" -> "0x" + "1".repeat(40)
                "base-gasless" -> gaslessAddress
                else -> error("Unexpected network: $network")
            }
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
                assertTrue(network == "bitcoin" || network == "base")
                return "100"
            }
            if (methodName == "sendTransaction") {
                assertEquals("base", network)
                broadcasts++
                return "0x" + "e".repeat(64)
            }
            if (methodName == "getUserOperationReceipt") {
                assertEquals("base-gasless", network)
                assertEquals("\"" + "0x" + "a".repeat(64) + "\"", args)
                return userOperationReceipt ?: "null"
            }
            if (network == "base-gasless") {
                val call = org.json.JSONArray(args)
                val transfer = call.getJSONObject(0)
                val feeToken = call.getJSONObject(1)
                assertFalse(feeToken.getBoolean("isSponsored"))
                val feeAddress = feeToken.getJSONObject("paymasterToken").getString("address")
                return when (methodName) {
                    "quoteTransfer" -> {
                        quotedFeeTokens += feeAddress; quotedAmounts += transfer.getString("amount")
                        """{"fee":"$gaslessFee"}"""
                    }
                    "transfer" -> {
                        sentTokens += transfer.getString("token"); feeTokens += feeAddress; broadcasts++
                        "0x" + "a".repeat(64)
                    }
                    else -> error("Unexpected gasless method: $methodName")
                }
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
    fun clearingViewModelDoesNotWaitForNativeCloseOrChangeCredentials() = runTest(dispatcher) {
        val original = WalletCredentials("saved-test-key", "saved-test-seed")
        val store = Store(original)
        val client = Client()
        val closeStarted = CountDownLatch(1)
        val releaseClose = CountDownLatch(1)
        val closeFinished = CountDownLatch(1)
        val closeCalls = AtomicInteger()
        val closeThread = AtomicReference<Thread>()
        val clearingThread = AtomicReference<Thread>()
        val blockingClient = object : WalletClient by client {
            override fun close() {
                closeCalls.incrementAndGet()
                closeThread.set(Thread.currentThread())
                closeStarted.countDown()
                try {
                    releaseClose.await()
                    client.close()
                } finally {
                    closeFinished.countDown()
                }
            }
        }
        val vm = WalletViewModel(store, { blockingClient }, dispatcher)
        viewModels.put("wallet", vm)
        advanceUntilIdle()
        assertTrue(vm.state.value.isWdkInitialized)

        // Run the lifecycle caller separately so a regression fails with a bounded
        // timeout instead of hanging the test while the fake native close waits.
        val lifecycle = Executors.newSingleThreadExecutor()
        try {
            val cleared = lifecycle.submit {
                clearingThread.set(Thread.currentThread())
                viewModels.clear()
            }
            assertTrue("Client teardown never started", closeStarted.await(3, TimeUnit.SECONDS))
            cleared.get(3, TimeUnit.SECONDS)
            assertNotSame("Native teardown ran on the lifecycle caller", clearingThread.get(), closeThread.get())
            assertEquals(1L, closeFinished.count)
            assertFalse(client.closed)
            assertEquals(original, store.credentials)
            assertEquals(0, store.saves)
            assertEquals(0, store.clears)

            releaseClose.countDown()
            assertTrue("Client teardown did not finish after release", closeFinished.await(3, TimeUnit.SECONDS))
            assertTrue(client.closed)
            assertEquals(1, closeCalls.get())
            assertEquals(original, store.credentials)
            assertEquals(0, store.saves)
            assertEquals(0, store.clears)
        } finally {
            releaseClose.countDown()
            lifecycle.shutdown()
            lifecycle.awaitTermination(3, TimeUnit.SECONDS)
            closeFinished.await(3, TimeUnit.SECONDS)
        }
    }

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
        assertEquals(listOf("base"), recovered.networks)
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
        assertNull(vm.state.value.usdcBalance)
        assertNull(vm.state.value.usdtBalance)
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

    private fun sendViewModel(client: Client): WalletViewModel {
        val fees = object : BaseFeeProvider {
            override suspend fun chainId() = BigInteger.valueOf(8453)
            override suspend fun estimateGas(transaction: BaseTransaction) = BigInteger.valueOf(65000)
            override suspend fun l1FeeUpperBound(unsignedSize: Int): BigInteger {
                return BigInteger.valueOf(200)
            }
            override suspend fun operatorFee(gas: BigInteger) = BigInteger.valueOf(3)
        }
        return WalletViewModel(Store(WalletCredentials("test-key", "test-seed")), { client }, dispatcher, fees)
            .also { viewModels.put("wallet", it) }
    }

    @Test
    fun stablecoinTransferNeedsNoEthAndPaysTheFeeInTheSentToken() = runTest(dispatcher) {
        val client = Client().apply { nativeBalance = "0"; tokenBalance = "20000000" }
        val vm = sendViewModel(client)
        advanceUntilIdle()
        vm.updateSendAddress("0x" + "2".repeat(40))
        vm.updateSendAmount("1")
        vm.prepareSend()
        advanceUntilIdle()
        val review = vm.state.value.sendReview!!
        assertEquals(Asset.USDC, review.feeAsset)
        assertEquals("3000", review.estimatedFee)
        assertEquals("0.003 USDC", review.feeDisplay)
        assertEquals(listOf(BaseMainnet.USDC_CONTRACT), client.quotedFeeTokens)
        vm.doSend()
        advanceUntilIdle()
        assertEquals(listOf(BaseMainnet.USDC_CONTRACT), client.sentTokens)
        assertEquals(listOf(BaseMainnet.USDC_CONTRACT), client.feeTokens)
        val tx = vm.state.value.pendingTransactions.single()
        assertEquals(TxStatus.BROADCAST, tx.status)
        assertEquals("0x" + "a".repeat(64), tx.userOperation)
        assertNull("A UserOperation hash is not an explorer transaction", tx.hash)
        assertNull(tx.explorerUrl)
    }

    @Test
    fun includedUserOperationProvidesTheExplorerHashAndRefreshesBalances() = runTest(dispatcher) {
        val bundleHash = "0x" + "c".repeat(64)
        val client = Client().apply {
            tokenBalance = "20000000"
            userOperationReceipt = """{"success":true,"receipt":{"transactionHash":"$bundleHash"}}"""
        }
        val vm = sendViewModel(client)
        advanceUntilIdle()
        vm.updateSendAddress("0x" + "2".repeat(40))
        vm.updateSendAmount("1")
        vm.prepareSend()
        advanceUntilIdle()
        vm.doSend()
        advanceUntilIdle()
        val tx = vm.state.value.pendingTransactions.single()
        assertEquals(TxStatus.INCLUDED, tx.status)
        assertEquals(bundleHash, tx.hash)
        assertEquals("https://basescan.org/tx/$bundleHash", tx.explorerUrl)
    }

    @Test
    fun revertedUserOperationIsNotReportedAsSent() = runTest(dispatcher) {
        val client = Client().apply {
            tokenBalance = "20000000"
            userOperationReceipt = """{"success":false,"receipt":{"transactionHash":"0x${"d".repeat(64)}"}}"""
        }
        val vm = sendViewModel(client)
        advanceUntilIdle()
        vm.updateSendAddress("0x" + "2".repeat(40))
        vm.updateSendAmount("1")
        vm.prepareSend()
        advanceUntilIdle()
        vm.doSend()
        advanceUntilIdle()
        assertEquals(TxStatus.FAILED_ON_CHAIN, vm.state.value.pendingTransactions.single().status)
    }

    @Test
    fun maxOfOneStablecoinPaysTheFeeWithTheOtherOne() = runTest(dispatcher) {
        val client = Client().apply { tokenBalance = "7000000"; usdtBalance = "1234567" }
        val vm = sendViewModel(client)
        advanceUntilIdle()
        vm.updateSendAsset(Asset.USDT)
        vm.setSendMax()
        vm.updateSendAddress("0x" + "2".repeat(40))
        vm.prepareSend()
        advanceUntilIdle()
        assertEquals(Asset.USDC, vm.state.value.sendReview?.feeAsset)
        assertEquals(listOf(BaseMainnet.USDC_CONTRACT), client.quotedFeeTokens)
    }

    @Test
    fun sendingTheWholeBalanceAdjustsTheAmountVisiblyAndThenSends() = runTest(dispatcher) {
        val client = Client().apply { tokenBalance = "20000000"; usdtBalance = "0" }
        val vm = sendViewModel(client)
        advanceUntilIdle()
        vm.updateSendAddress("0x" + "2".repeat(40))
        vm.setSendMax()
        assertEquals("20", vm.state.value.sendAmount)
        vm.prepareSend()
        runCurrent()
        assertNull("The user reviews the adjusted amount, never a silent one", vm.state.value.sendReview)
        assertEquals(UiText(R.string.send_amount_adjusted), vm.state.value.toastMessage)
        // Fee 3000 plus a 15% margin (450) is left out of the balance.
        assertEquals("19.99655", vm.state.value.sendAmount)
        assertEquals(listOf("19500000"), client.quotedAmounts)
        vm.prepareSend()
        runCurrent()
        val review = vm.state.value.sendReview!!
        assertEquals("19996550", review.units)
        assertEquals(Asset.USDC, review.feeAsset)
        vm.doSend()
        advanceUntilIdle()
        assertEquals(listOf(BaseMainnet.USDC_CONTRACT), client.sentTokens)
    }

    @Test
    fun aBalanceThatCannotEvenPayTheFeeIsNotSent() = runTest(dispatcher) {
        val client = Client().apply { tokenBalance = "3000"; usdtBalance = "0" }
        val vm = sendViewModel(client)
        advanceUntilIdle()
        vm.updateSendAddress("0x" + "2".repeat(40))
        vm.setSendMax()
        vm.prepareSend()
        runCurrent()
        assertNull(vm.state.value.sendReview)
        assertEquals(UiText(R.string.error_insufficient_fee_token), vm.state.value.toastMessage)
        assertEquals("0.003", vm.state.value.sendAmount)
        assertEquals(0, client.broadcasts)
    }

    @Test
    fun anAdjustedAmountIsNeverAppliedToAReviewedFeeToken() = runTest(dispatcher) {
        val client = Client().apply { tokenBalance = "20000000"; usdtBalance = "0" }
        val vm = sendViewModel(client)
        advanceUntilIdle()
        vm.updateSendAddress("0x" + "2".repeat(40))
        vm.updateSendAmount("19.99655")
        vm.prepareSend()
        runCurrent()
        assertNotNull(vm.state.value.sendReview)
        // The balance shrinks after review: the reviewed amount must be blocked, not rewritten.
        client.tokenBalance = "19996600"
        vm.doSend()
        advanceUntilIdle()
        assertEquals(0, client.broadcasts)
        assertEquals("19.99655", vm.state.value.sendAmount)
        assertEquals(TxStatus.FAILED_BEFORE_BROADCAST, vm.state.value.pendingTransactions.single().status)
    }

    @Test
    fun unusuallyHighFeeIsRefusedAtTheQuote() = runTest(dispatcher) {
        val client = Client().apply { tokenBalance = "20000000"; gaslessFee = BaseMainnet.GASLESS_MAX_FEE.toString() }
        val vm = sendViewModel(client)
        advanceUntilIdle()
        vm.updateSendAddress("0x" + "2".repeat(40))
        vm.updateSendAmount("1")
        vm.prepareSend()
        runCurrent()
        assertNull(vm.state.value.sendReview)
        assertEquals(UiText(R.string.error_fee_too_high), vm.state.value.toastMessage)
    }

    @Test
    fun feeIncreaseAfterReviewStopsTheTransferBeforeSigning() = runTest(dispatcher) {
        val client = Client().apply { tokenBalance = "20000000" }
        val vm = sendViewModel(client)
        advanceUntilIdle()
        vm.updateSendAddress("0x" + "2".repeat(40))
        vm.updateSendAmount("1")
        vm.prepareSend()
        advanceUntilIdle()
        client.gaslessFee = "3001"
        vm.doSend()
        advanceUntilIdle()
        assertEquals(0, client.broadcasts)
        assertEquals(UiText(R.string.send_fee_changed), vm.state.value.pendingTransactions.single().error)
    }

    @Test
    fun aGaslessAccountThatIsNotTheWalletAddressFailsClosed() = runTest(dispatcher) {
        val client = Client().apply { tokenBalance = "20000000"; gaslessAddress = "0x" + "9".repeat(40) }
        val vm = sendViewModel(client)
        advanceUntilIdle()
        vm.updateSendAddress("0x" + "2".repeat(40))
        vm.updateSendAmount("1")
        vm.prepareSend()
        runCurrent()
        assertNull(vm.state.value.sendReview)
        assertEquals(UiText(R.string.error_gasless_account), vm.state.value.toastMessage)
        assertTrue(client.quotedFeeTokens.isEmpty())
        assertEquals(0, client.broadcasts)
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
        assertEquals("Sending all USDT leaves nothing for the fee, which USDC covers", listOf(BaseMainnet.USDC_CONTRACT), client.feeTokens)
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
