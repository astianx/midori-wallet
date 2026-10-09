package org.astianx.midori.one.wallet.p2p

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.astianx.midori.one.wallet.BaseMainnet
import org.astianx.midori.one.wallet.fiat.FiatOperation
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class P2pTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val co = P2pCountry("CO", "COP", "COP", "bank_transfer", true, true, true)
    private val br = P2pCountry("BR", "BRL", "BRL", "pix", false, true, true)
    private val now = 1700000000L
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }
    private fun json() = JSONObject().put("version",1).put("chainId",8453).put("asset","USDC")
        .put("token",BaseMainnet.USDC_CONTRACT).put("diamond",P2P_DIAMOND).put("execution","READ_ONLY")
        .put("country","CO").put("fiat","COP").put("currency","COP").put("paymentMethod","bank_transfer")
        .put("alpha",true).put("buy",true).put("sell",true).put("decimals",6)
        .put("buyPrice","3182690000").put("sellPrice","3088620000").put("smallOrderThreshold","10000000")
        .put("smallOrderFixedFeeBuy","0").put("smallOrderFixedFeeSell","0").put("block","52350136")
        .put("quotedAt",now).put("expiresAt",now+60)

    @Test fun exactProtocolRatesKeepPrecisionCountryAndShortExpiry() {
        val parsed = parseP2pRate(json(),co,now)
        assertEquals("3182.69",p2pDecimal(parsed.buyPrice)); assertEquals("3088.62",p2pDecimal(parsed.sellPrice))
        assertTrue(parsed.validAt(now)); assertFalse(parsed.validAt(now+60)); assertFalse(parsed.validAt(now-6))
        assertEquals(listOf(co),parseP2pCatalog(json().put("countries",JSONArray().put(json()))))
        assertEquals("0.000001",p2pDecimal("1"))
    }
    @Test fun wrongChainsTokensContractsCountriesAndAmountsAreRejected() {
        for ((key,value) in listOf("chainId" to 1,"chainId" to 8453.1,"token" to "0x"+"1".repeat(40),
            "diamond" to "0x"+"2".repeat(40),"asset" to "USDT","execution" to "READY","country" to "BR",
            "fiat" to "USD","decimals" to 18,"buyPrice" to "0","sellPrice" to "1e6",
            "smallOrderFixedFeeBuy" to "-1","block" to "0","expiresAt" to now+61,"quotedAt" to now+10)) {
            assertTrue(key,runCatching { parseP2pRate(json().put(key,value),co,now) }.isFailure)
        }
        assertFalse(p2pInteger("9".repeat(79))); assertFalse(p2pInteger("01"))
        assertTrue(runCatching { parseP2pCatalog(json().put("countries",JSONArray().put(json()).put(json()))) }.isFailure)
    }
    @Test fun referenceRatesNeverNeedWalletCredentialsAndChangingCountryDiscardsLateResponses() = runTest(dispatcher) {
        val pending = CompletableDeferred<P2pRate>()
        var requested: P2pCountry? = null
        val gateway = object : P2pGateway {
            override suspend fun catalog() = listOf(co,br)
            override suspend fun rates(country: P2pCountry): P2pRate {
                requested=country
                return withContext(NonCancellable) { pending.await() }
            }
        }
        val vm=P2pViewModel(FiatOperation.BUY,gateway).also { store.put("p2p",it) }
        advanceUntilIdle(); vm.country("CO"); vm.rates(); runCurrent()
        vm.country("BR")
        pending.complete(P2pRate(co,"3182690000","3088620000","10000000","0","0","52350136",
            System.currentTimeMillis()/1000,System.currentTimeMillis()/1000+60))
        advanceUntilIdle()
        assertEquals(co,requested); assertEquals("BR",vm.state.value.country); assertNull(vm.state.value.rate)
        assertFalse(vm.state.value.busy)
        vm.operation(FiatOperation.SELL); assertEquals("",vm.state.value.country)
    }
    @Test fun unavailableBackendAndInvalidRateNeverPretendToBeAnOrder() = runTest(dispatcher) {
        val unavailable=P2pViewModel(FiatOperation.BUY,null).also { store.put("missing",it) }
        assertNotNull(unavailable.state.value.error); assertTrue(unavailable.state.value.countries.isEmpty())
        assertFalse(unavailable.state.value.configured)
        val gateway=object:P2pGateway {
            override suspend fun catalog()=listOf(co)
            override suspend fun rates(country:P2pCountry)=P2pRate(br,"1","1","0","0","0","1",now,now+60)
        }
        val vm=P2pViewModel(FiatOperation.BUY,gateway).also { store.put("invalid",it) }
        advanceUntilIdle();vm.country("CO");vm.rates();advanceUntilIdle()
        assertNull(vm.state.value.rate);assertNotNull(vm.state.value.error)
    }
    @Test fun switchingOperationDuringInitialLoadRestartsCatalogAndIgnoresCancelledResponse() = runTest(dispatcher) {
        val first=CompletableDeferred<List<P2pCountry>>()
        var requests=0
        val gateway=object:P2pGateway {
            override suspend fun catalog():List<P2pCountry> {
                requests++
                return if(requests==1) withContext(NonCancellable) { first.await() } else listOf(co)
            }
            override suspend fun rates(country:P2pCountry):P2pRate = error("No rate requested")
        }
        val vm=P2pViewModel(FiatOperation.BUY,gateway).also { store.put("reload",it) }
        runCurrent();vm.operation(FiatOperation.SELL);runCurrent()
        first.complete(emptyList());advanceUntilIdle()
        assertEquals(2,requests);assertEquals(FiatOperation.SELL,vm.state.value.operation)
        assertEquals(listOf(co),vm.state.value.countries);assertFalse(vm.state.value.busy)
    }
}
