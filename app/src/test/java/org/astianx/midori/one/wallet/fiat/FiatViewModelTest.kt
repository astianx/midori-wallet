package org.astianx.midori.one.wallet.fiat

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.astianx.midori.one.wallet.Asset
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FiatViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = ViewModelStore()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { models.clear(); Dispatchers.resetMain() }

    private val gateway = object : FiatGateway {
        override val origin = "https://fiat.midori.test"
        override suspend fun capabilities() = FiatCapabilities(listOf(FiatAsset(Asset.USDC, true, true)),
            listOf(FiatCurrency("USD", 2, true, true)),
            listOf(FiatCountry("US", true, true), FiatCountry("CO", true, true)))
        override suspend fun offers(selection: FiatSelection) = emptyList<FiatOffer>()
        override suspend fun challenge(request: FiatRequest) = error("Unused")
        override suspend fun session(nonce: String, signature: String) = error("Unused")
    }

    private fun vm(country: String) = FiatViewModel(FiatOperation.BUY, gateway, "0x" + "1".repeat(40), "bc1test",
        { true }, { _, _, _ -> error("Unused") }, country).also { models.put("fiat", it) }

    @Test fun theWalletCountryIsSelectedAndCannotBeReplaced() = runTest(dispatcher) {
        val vm = vm("CO"); advanceUntilIdle()
        assertTrue(vm.state.value.locked); assertEquals("CO", vm.state.value.country)
        vm.country("US"); assertEquals("CO", vm.state.value.country)
        vm.enter(FiatOperation.SELL); advanceUntilIdle(); assertEquals("CO", vm.state.value.country)
    }

    @Test fun aWalletCountryUnsupportedByProvidersNeverAllowsComparison() = runTest(dispatcher) {
        val vm = vm("BR"); advanceUntilIdle()
        assertEquals("BR", vm.state.value.country); vm.amount("10"); advanceUntilIdle()
        assertFalse(vm.state.value.canCompare)
    }

    @Test fun withoutAWalletCountryTheCountryRemainsSelectable() = runTest(dispatcher) {
        val vm = vm(""); advanceUntilIdle()
        assertFalse(vm.state.value.locked); vm.country("US"); assertEquals("US", vm.state.value.country)
    }
}
